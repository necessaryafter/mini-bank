Esse documento foi criado pelo Claude Opus, a ideia era criar um sistema complexo para aprimorar meu uso de Spring Boot e dos
serviços da AWS como o **SQS** que eu nunca tinha usado, pedi no modelo de teste técnico. 

## Contexto

Nosso cliente é uma instituição financeira digital que precisa expandir sua plataforma de contas e transferências. Você foi convidado(a) a implementar um serviço backend que represente um subconjunto real desse domínio: criação de contas, transferências entre contas e histórico de movimentações, com garantias de consistência e resiliência compatíveis com um ambiente financeiro real.

Este teste avalia sua capacidade de tomar decisões arquiteturais, não apenas de escrever código que funcione. Priorizamos clareza de raciocínio, tratamento correto de cenários de falha e organização de código sobre volume de features entregues.

---

## Objetivo

Implementar um serviço de **Carteira Digital** que permita:

1. Criar contas de usuário
2. Consultar saldo
3. Solicitar transferências entre contas
4. Processar transferências de forma assíncrona, confiável e idempotente
5. Consultar extrato/histórico de movimentações

---

## Requisitos Funcionais

### RF01 — Contas
- `POST /accounts`: cria uma conta com saldo inicial (padrão zero).
- `GET /accounts/{id}`: retorna dados da conta e saldo atual.
- Uma conta nunca pode ficar com saldo negativo.

### RF02 — Transferências
- `POST /accounts/{id}/transfers`: solicita uma transferência de uma conta origem para uma conta destino, com valor e uma chave de idempotência fornecida pelo cliente da API (`Idempotency-Key` no header).
- A solicitação deve ser aceita de forma **assíncrona**: o endpoint retorna `202 Accepted` com o status inicial da transferência, e o processamento real acontece em background via fila de mensagens.
- `GET /transfers/{id}`: consulta o status atual de uma transferência (`PENDING`, `COMPLETED`, `FAILED`, `REVERTED`).

### RF03 — Processamento de transferências
- Um consumidor assíncrono processa a transferência: debita da conta origem, credita na conta destino.
- Se o crédito falhar após o débito já ter sido efetivado, o sistema deve reverter o débito automaticamente (compensação).
- O reprocessamento da mesma mensagem (reentrega) **não pode gerar duplo débito/crédito**.

### RF04 — Extrato
- `GET /accounts/{id}/statement`: retorna o histórico de movimentações da conta (débitos, créditos, transferências revertidas), incluindo metadados de cada evento.

### RF05 — Resiliência
- Mensagens que falharem o processamento após múltiplas tentativas devem ser direcionadas a uma fila de mensagens mortas (DLQ), sem travar o processamento das demais.

---

## Requisitos Não Funcionais

| Área | Requisito |
|---|---|
| Linguagem | Kotlin |
| Framework | Spring Boot 3 |
| Persistência relacional | PostgreSQL, com migrações versionadas (Flyway) |
| Persistência de eventos/extrato | MongoDB |
| Cache / idempotência | Redis |
| Mensageria | SQS (via LocalStack, ambiente local) |
| Armazenamento de arquivos | S3 (via LocalStack) — usado para gerar e armazenar um comprovante da transferência |
| Testes | JUnit5 + MockK para testes unitários; Testcontainers para testes de integração |
| Validação | Bean Validation (`@Valid`, `@NotNull`, etc.) com tratamento de erros consistente |
| Observabilidade | Spring Actuator (health, metrics) |
| Documentação de API | OpenAPI/Swagger |
| Orquestração local | Docker Compose (subir toda a stack com um único comando) |
| CI | GitHub Actions executando build + testes a cada push/PR |
| Versionamento | Git Flow (branches de feature, sem commits diretos em `main`) |

---

## O que será avaliado

1. **Modelagem de domínio** — como você representa Conta, Transferência e Movimentação; uso correto de tipos para valores monetários.
2. **Consistência sob concorrência e falha** — idempotência real (não apenas teórica), tratamento de falha parcial (débito sem crédito), e como a reversão é implementada.
3. **Separação de responsabilidades** — organização em camadas (ou módulos) coerente, sem lógica de negócio vazando para controllers ou repositórios.
4. **Qualidade dos testes** — cobertura das regras de negócio críticas (não é sobre percentual, é sobre os cenários certos: saldo insuficiente, reentrega de mensagem, falha de crédito pós-débito).
5. **Tratamento de erros na API** — respostas HTTP consistentes e informativas, sem vazar stack trace ou detalhes internos.
6. **Decisões documentadas** — um `DECISIONS.md` (ou seção no README) explicando as escolhas arquiteturais relevantes e trade-offs considerados (ex: por que orquestração e não coreografia para a compensação, por que outbox pattern ou não, etc).
7. **Reprodutibilidade** — o avaliador deve conseguir rodar `docker compose up` e ter o ambiente completo funcional, sem passos manuais adicionais.

---

## Diferenciais (não obrigatórios, mas valorizados)

- Implementação do **Outbox Pattern** para publicação confiável de eventos.
- Métricas customizadas expostas via Actuator (ex: taxa de falha de transferências, p99 de tempo de processamento).
- Testes de carga simples demonstrando comportamento sob concorrência.
- Uso de Saga (orquestrada ou coreografada) documentado explicitamente na decisão de reversão.

---

## Entrega
- README com instruções de execução (`docker compose up` deve ser suficiente).
- `DECISIONS.md` com as principais decisões arquiteturais.
- Coleção Postman/Insomnia ou arquivo `.http` com exemplos de chamadas, **ou** Swagger UI acessível localmente.