# ADR 0007 — transaction-processor ganha datastore próprio (Redis + Mongo) para fraude

## Status
Aceito (revisa a nota de "sem banco de dados" da ADR 0006)

## Contexto
As ADRs 0003 e 0006 partiram do princípio de que o `transaction-processor` era um serviço **stateless e sem banco**: ele só consumia da fila, decidia e devolvia a decisão. A nota final da ADR 0006 chega a dizer explicitamente que "o processor opera sem banco de dados".

Isso valia enquanto a validação dele era trivial (basicamente checar saldo — que, aliás, nem é responsabilidade dele; quem revalida saldo sob lock é o `TransferCaptureService`, ADR 0003/0005). Mas o motor anti-fraude descrito em `FRAUD_DETECTION.md` mudou o jogo: a maioria das regras é **stateful por natureza**:

* **Velocity check** — quantas transações a conta fez numa janela de tempo.
* **Destinatário denunciado** — uma blocklist persistida.
* **Perfil comportamental** — faixa de valor típica, horários habituais, aparelhos conhecidos, últimas geolocalizações do usuário.
* **Impossible travel** — comparar a localização atual com a última conhecida.

Nenhuma dessas regras cabe só no payload do evento: elas precisam de **histórico** e de **contadores**. Um serviço genuinamente stateless não consegue implementá-las.

## Decisão
O `transaction-processor` passa a ter um **datastore próprio**, do qual ele é o **dono exclusivo**:

* **Redis** — contadores de velocity (janela deslizante por conta) e cache quente da blocklist. TTL nativo resolve a expiração das janelas de graça.
* **MongoDB** — perfil comportamental do usuário e a blocklist durável de destinatários denunciados. (O `TASK.md` já pedia Mongo na stack, então encaixa.)

**Isso não contradiz a ADR 0003.** A ADR 0003 proíbe o processor **tocar no Postgres do `account-service`** — o anti-padrão de banco compartilhado, dois serviços escrevendo no schema que só um deveria possuir. Um Redis/Mongo que pertence **só** ao processor é exatamente o oposto: cada serviço com o seu próprio dado. O processor continua **sem nenhum acesso** ao Postgres do `account-service` (sem driver JDBC, sem Exposed no `build.gradle.kts`).

O que muda é apenas a nota da ADR 0006: "opera sem banco" vira "não acessa o banco *do account-service*, mas possui o seu próprio para estado de fraude".

### Detalhes de implementação (ambiente local)
* No `docker compose`, por simplicidade, o processor reaproveita as instâncias de Redis e Mongo já definidas no `compose.yaml`. O isolamento lógico é garantido por **namespace**: o processor usa um database Mongo próprio (`fraud`) e um prefixo de chave Redis próprio (`fraud:`), sem colidir com o `idempotency:` do `account-service`. Em produção, seriam clusters fisicamente separados.
* O `account-service` hoje não usa Mongo, então o Mongo é de fato exclusivo do processor no estado atual.

## Consequências

### Positivas
* **Regras realistas viram possíveis.** Velocity, blocklist e perfil comportamental deixam de ser teoria e passam a ter onde persistir estado.
* **Fronteira de propriedade preservada.** O processor ganha estado sem furar a regra de ouro da ADR 0003: ele continua fisicamente incapaz de escrever no ledger do `account-service`.
* **Ferramenta certa pra cada dado.** Contadores efêmeros com TTL no Redis; perfis e blocklist duráveis no Mongo.

### Negativas (trade-offs)
* **O processor deixa de ser stateless.** Ganha estado próprio para operar, manter, versionar schema de perfil e monitorar — some a simplicidade que a ADR 0006 celebrava.
* **Consistência do perfil é eventual e best-effort.** O perfil comportamental é alimentado a partir dos próprios eventos; um perfil frio (usuário novo) faz as regras de perfil se absterem em vez de bloquear — decisão deliberada para não punir falta de histórico.
* **Mais infraestrutura no boot do serviço.** O processor agora depende de Redis e Mongo estarem de pé para subir, não só da fila.
