# ADR 0010 — Transferência em revisão (UNDER_REVIEW) permanece PENDING

## Status
Aceito

## Contexto
O motor anti-fraude classifica cada transferência em uma banda de risco e o processor traduz isso numa decisão coreografada (ADR 0003): `APPROVED`, `REJECTED` ou `UNDER_REVIEW`. O account-service precisa aplicar cada uma. Duas encaixam direto no contrato de status do `TASK.md` (`PENDING`, `COMPLETED`, `FAILED`, `REVERTED`):

* `APPROVED` → `capture()` → `COMPLETED` (ou `FAILED` se faltar saldo).
* `REJECTED` → `FAILED` (com o motivo em `review_reason`). Não precisa de um status `BLOCKED`: fraude é uma transferência que falhou, com o porquê registrado.

Sobra o `UNDER_REVIEW` (banda HIGH): risco alto demais para auto-capturar, mas sem evidência clara de fraude. Ele não é `COMPLETED`, não é `FAILED` (ainda pode passar depois de uma revisão), e não é mais "aguardando processamento" no sentido original do `PENDING`. A tentação seria criar um status novo (`UNDER_REVIEW`), mas isso quebraria o contrato de API que o `TASK.md` fixa.

## Decisão
`UNDER_REVIEW` **mantém a transferência `PENDING`**. As entries ficam `PENDING` (não capturadas), então **nenhum saldo se move**, e o motivo da retenção é gravado em `review_reason` para auditoria. Não criamos status novo.

A resolução do hold — aprovar após verificação, pedir autenticação adicional, ou reprovar — dependeria de um **workflow de revisão (manual ou step-up auth) que está fora do escopo** deste projeto. Documentamos essa fronteira em vez de fingir o workflow, na mesma linha do ADR 0008.

## Consequências

### Positivas
* **Fica dentro do contrato do `TASK.md`.** Não inventamos um status que a API prometeu não ter.
* **Fiel ao modelo de risco.** HIGH continua sendo um desfecho distinto de CRITICAL — reter ≠ reprovar — em vez de colapsar os dois em `FAILED`.
* **Seguro por construção.** Entries `PENDING` significam saldo intacto; não há dinheiro preso nem efeito colateral. Idempotente: reentrega da decisão só regrava o mesmo motivo.

### Negativas (trade-offs)
* **`PENDING` fica sobrecarregado.** O cliente vê `PENDING` tanto para "na fila de processamento" quanto para "retido em revisão" e não distingue os dois. Mitigação: o motivo fica em `review_reason`, observável internamente.
* **Sem resolvedor, a transferência fica `PENDING` indefinidamente.** É intencional e documentado (a etapa de revisão é fora de escopo), mas um `PENDING` que nunca fecha pode parecer um bug para quem não leu este ADR.
