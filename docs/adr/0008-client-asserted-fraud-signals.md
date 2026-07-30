# ADR 0008 - Sinais de fraude auto-declarados pelo cliente (device, geo, IP)

## Status
Aceito (com ressalva de segurança explícita)

## Contexto
Três das regras anti-fraude (ADR 0007 / `FRAUD_DETECTION.md`) dependem de dados que hoje são **auto-declarados pelo cliente**, capturados no `TransferController.requestContextOf` a partir de headers HTTP e propagados no `RequestContext`:

* `UnknownDeviceRule` ← header `X-Device-Id`
* `UnexpectedCountryRule` ← header `X-Geo-Country`
* `ImpossibleTravelRule` ← headers `X-Geo-Latitude` / `X-Geo-Longitude`

O problema é direto: **um fraudador controla exatamente os inputs dessas regras.** Basta trocar um header para o device parecer o de sempre, o país parecer o de casa e a geo parecer coerente. Uma regra de fraude não pode ser mais confiável que a origem do dado que ela consome, e aqui a origem é a parte não-confiável da requisição.

Vale separar o que é e o que não é confiável no motor atual:

| Sinal | Origem | Confiável? |
|---|---|---|
| `amount` | corpo da requisição, validado no servidor | ✅ |
| velocity | contado server-side por conta (Redis) | ✅ |
| destinatário denunciado | dado interno (Mongo do processor) | ✅ |
| perfil de valor | histórico server-side da conta | ✅ |
| **device** | header do cliente | ❌ forjável |
| **país / geo** | header do cliente | ❌ forjável |
| **IP** | `X-Forwarded-For` → `remoteAddr` | ⚠️ XFF forjável; só o socket é confiável |

Ou seja, o **núcleo do motor é server-confiável**; o problema se restringe às três regras de comportamento device/geo.

## Como isso se resolveria de verdade
O caminho da correção é claro, mas não cabe ao escopo:

* **Geo**: nunca confiar na geo enviada pelo cliente. Derivar **server-side a partir do IP de origem** com uma base de geolocalização (ex.: MaxMind GeoLite2). O cliente deixa de escolher o próprio país.
* **IP**: só confiar em `X-Forwarded-For` quando ele é escrito pelo *nosso* load balancer, com uma lista de *trusted proxies* configurada e o XFF do cliente descartado. Fora disso, usar o `remoteAddr` do socket.
* **Device**: fingerprint real vem de **attestation assinada** (Play Integrity / App Attest) ou de um token de device emitido no enrollment e verificado no servidor, não de um header cru.

## Decisão
Para o escopo deste projeto, **aceito conscientemente os sinais auto-declarados** e documentamos a vulnerabilidade aqui, em vez de implementar a correção. As regras device/geo continuam no motor como **demonstração da técnica de scoring**, não como controle de segurança pronto para produção.

O motivo é de custo/escopo, não de desconhecimento: fazer isso "de verdade" exigiria montar coisas que estão inteiramente fora de um teste técnico de backend rodando em floci:

* um **ambiente de cliente** real (SDK mobile com attestation) para produzir um device fingerprint verificável;
* um **ambiente análogo a produção** com topologia de rede real: load balancer, cadeia de proxies confiáveis, terminação TLS e a infraestrutura de geolocalização por IP.

Nada disso agregaria ao que o projeto se propõe a demonstrar (modelagem de domínio, consistência, o motor de fraude em si), e construir um simulacro desses ambientes seria esforço grande com pouco valor de avaliação.

## Consequências

### Positivas
* **Honestidade arquitetural.** A limitação fica registrada e localizável, não escondida. Quem lê sabe o que confiar e o que não confiar no motor.
* **Escopo preservado.** Não gastei tempo simulando LB/proxy/attestation para um teste de backend.
* **Correção é aditiva.** Trocar a origem da geo (header → resolver por IP) e endurecer o IP não muda o motor nem as regras: só muda quem preenche o `RequestContext`. O ponto de extensão já existe.

### Negativas (trade-offs)
* **As três regras device/geo não são controle real.** Contra um atacante que manipula headers, elas não valem, protegem só contra o ingênuo. Não devem ser tratadas como produção.
* **Risco de falsa sensação de segurança** se alguém subir isso como está. Mitigação recomendada (não obrigatória neste escopo): calibrar os scores para que essas três regras **nunca levem a `CRITICAL`/`BLOCK` sozinhas**, só agravem quando somadas a um sinal server-confiável.
* **A geo continua "stub".** Este ADR formaliza *por que* o stub permanece, em vez de deixar a impressão de que a geo é confiável.
