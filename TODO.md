# TODO — O que falta para fechar o escopo do `TASK.md`

Estado atual: a **fundação** (concorrência, idempotência, outbox, saga coreografada,
ledger de dupla entrada, motor anti-fraude) está pronta. O que falta é
**acabamento e superfície** — endpoints CRUD, reprodutibilidade e entregáveis.

Legenda de prioridade: 🔴 bloqueia avaliação · 🟡 pedido no escopo · 🟢 diferencial.

> Verificado contra o repo em 2026-07-19. Só existe o `TransferController`
> (+ `DecisionListener` e o pipeline outbox/saga). Não há `AccountController`,
> extrato, DLQ, S3, Dockerfiles, README nem CI.

---

## Requisitos funcionais

### ✅ RF01 — Contas (feito)
- [x] `POST /accounts` — cria conta com saldo inicial (padrão zero), idempotente via
      `Idempotency-Key` (Redis + índice único em `accounts`).
- [x] `GET /accounts/{id}` — retorna dados da conta e saldo atual (404 via `ApiException`).
- [x] Saldo inicial modelado como double-entry real (conta-genesis, ver ADR 0010); saldo
      de conta de usuário nunca negativo.
- [x] `AccountController` + `AccountService` + DTOs (`CreateAccountRequest`, `AccountResponse`).
- [x] Migration `V3` (coluna idempotência + seed da genesis) e `AccountServiceTest` (6 testes).

### ✅ RF04 — Extrato (feito)
- [x] `GET /accounts/{id}/statement` — histórico de movimentações (débitos, créditos,
      transferências revertidas) com metadados de cada evento.
- [x] Fonte do extrato decidida: projeção a partir das `entries` (Postgres), não Mongo
      (ver ADR 0011, o ledger já é a fonte de verdade).
- [x] Paginação keyset por `sequence` (cursor), aproveitando o índice
      `idx_entries_account_id_sequence`.
- [x] Export em PDF armazenado no S3 com URL pré-assinada
      (`GET /accounts/{id}/statement/export?format=pdf`).

### ✅ RF05 — DLQ (feito)
- [x] Criar a fila DLQ e a *redrive policy* (`maxReceiveCount`) nas filas de entrada
      (`validate-transfer`) e de decisão (`transfer-decision`). Cada serviço
      provisiona a DLQ/policy da fila que ele consome, no boot
      (`ValidateTransferDlqProvisioner`, `TransferDecisionDlqProvisioner`; ver
      ADR 0012), no mesmo espírito do bucket S3 (ADR 0011), sem script externo.
- [x] Garantir que uma mensagem venenosa vá pra DLQ sem travar as demais: já
      valia antes (os listeners deixam exceção subir, SQS reencaminha), o que
      faltava era a `RedrivePolicy` para o SQS ter onde mandar a mensagem depois
      de N tentativas.
- [ ] (Opcional) Teste de integração provando o roteamento pra DLQ após N tentativas.

---

## Requisitos não-funcionais

### 🔴 Reprodutibilidade — `docker compose up` completo
Hoje o `compose.yaml` sobe só a infra (postgres/mongo/redis/**floci** — emulador
AWS compatível com LocalStack — e `otel-lgtm`). Os serviços Spring **não** estão
no compose e o floci sobe vazio, sem filas nem bucket.
- [ ] Adicionar `account-service` e `transaction-processor` ao `compose.yaml`
      (build a partir dos Dockerfiles).
- [ ] Dockerfile para cada serviço.
- [ ] Script de init do floci criando: filas SQS (entrada + decisão + **DLQ**)
      e o bucket S3 do comprovante.
- [ ] Portas do host: postgres/redis/mongo hoje expõem porta efêmera (resolvida
      pelo spring-boot-docker-compose). Fixar o que o avaliador precisa alcançar.
- [ ] Validar o fluxo ponta-a-ponta só com `docker compose up` (sem passo manual).

### 🟡 S3 — arquivo de extrato (feito; comprovante de transferência pendente)
- [x] Gerar o extrato em PDF e armazená-lo no S3 (via floci), expondo uma URL
      pré-assinada (ver RF04 e ADR 0011). O código de S3 (upload + presign +
      criação de bucket no boot) já existe em `S3StatementStore`.
- [ ] (Opcional) Reaproveitar o mesmo mecanismo para o comprovante da transferência
      concluída, que o `TASK.md` cita explicitamente.

### 🟡 CI — GitHub Actions (inexistente)
- [ ] `.github/workflows/ci.yml` rodando `build + testes` a cada push/PR.

### ✅ Documentação de API + Observabilidade (feito)
- [x] Swagger UI acessível (`/swagger-ui.html`, `/v3/api-docs`) — deps springdoc no
      build + `OpenApiConfig` presente no `account-service`.
- [x] Actuator ativo (`health,info,metrics,prometheus`) nos dois serviços.
- [x] Export OTLP para o `otel-lgtm` (Grafana em `:3000`).

---

## Entregáveis

### 🔴 README
- [ ] Instruções de execução (`docker compose up` deve bastar).
- [ ] Como rodar os testes, portas/endpoints, exemplos de chamada.

### 🟢 `DECISIONS.md`
- [x] **Coberto pelos 9 ADRs em `docs/adr`** (supera o pedido).
- [ ] (Opcional) Um `DECISIONS.md` curto no root apontando para os ADRs, caso o
      avaliador procure pelo nome exato citado no `TASK.md`.

### 🟡 Coleção de chamadas
- [ ] Arquivo `.http` (ou coleção Postman/Insomnia) com exemplos — **ou** oficializar
      o Swagger UI como substituto. Existe `scripts/manual_test.py`, mas ele só cobre
      o POST de transferência.

---

## Diferenciais (não obrigatórios)

- [ ] 🟢 Métricas customizadas no Actuator (taxa de falha de transferências, p99 de
      tempo de processamento) — nenhum `MeterRegistry`/`@Timed` no código ainda.
- [ ] 🟢 Teste de carga simples demonstrando comportamento sob concorrência.
- [x] 🟢 Outbox Pattern — **feito** (ADR 0006).
- [x] 🟢 Saga coreografada documentada — **feito** (ADR 0003/0004).

---

## Higiene de processo (aprendizado de mercado, não de capacidade)
- [ ] Git Flow: branches de feature, sem commit direto em `master`
      (o `TASK.md` pede isso explicitamente).
- [ ] Convenção de branch principal (`main` vs `master`) — o repo está em `master`.

---

### Resumo
A parte **cara de aprender** já está pronta. O que resta é quase tudo de superfície:
2 controllers (contas + extrato), DLQ, S3, empacotar tudo no compose, README e CI.
É o passo de maior retorno agora — transforma "fundação impressionante pela metade"
em "entrega redonda".
