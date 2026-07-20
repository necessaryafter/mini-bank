# 0003 - transaction-processor não acessa o Postgres do account-service

## Status
Aceito

## Contexto
No fluxo de transferência, `account-service` cria a `Transaction`/`Entry` como `PENDING` e publica no SQS.
`transaction-processor` consome, faz a validação de verdade (saldo suficiente, conta existe) e decide se a
transação vira `COMPLETED`/`FAILED`, ou se precisa de compensação (`REVERTED`).

O jeito mais rápido de implementar isso seria `transaction-processor` escrever direto nas tabelas
`transactions`/`entries` do Postgres do `account-service`, mas isso é exatamente o anti-padrão de "banco
compartilhado" entre microsserviços: dois serviços diferentes escrevendo no schema de dados que só um deles
deveria possuir. Já tomamos decisões anteriores pra garantir que só o dono do dado o manipula.

## Decisão
`transaction-processor` nunca acessa o Postgres do `account-service`. O `build.gradle.kts` dele não tem nenhuma
dependência de Exposed/driver JDBC, só `spring-cloud-aws-starter-sqs` além do `common`. A captura de fato
(`Entry.PENDING → POSTED/VOIDED`, `Transaction.PENDING → COMPLETED/FAILED`, e a compensação `→ REVERTED`)
é feita inteiramente por `TransferCaptureService`, dentro do `account-service`, sob lock de conta.

`transaction-processor` só decide e comunica a decisão de volta, via uma segunda fila SQS (coreografia: cada
serviço reage a eventos do outro, sem um orquestrador central) para o `account-service` aplicar.

## Consequências

#### Positivas
- A fronteira de propriedade dos dados fica real, não só documentada: é fisicamente impossível
  `transaction-processor` escrever numa tabela que ele não tem driver pra acessar.
- `account-service` pode evoluir o seu schema (migrations, índices, tipos de coluna) sem precisar coordenar com
  `transaction-processor`.
- O lock de conta (`SELECT ... FOR UPDATE`) tem um único ponto de entrada (`TransferCaptureService`), então é
  o único lugar que precisa garantir a consistência sob concorrência, não tem como outro serviço decidir
  aplicar uma entry por fora desse gate.

#### Negativas
- Mais uma fila SQS (decisão do processor de volta pro account-service) e mais uma etapa assíncrona: a
  transferência passa por duas viagens de mensagem (request, depois decisão) antes de resolver, em vez de uma.
  Precisa de idempotência nos dois lados (redelivery de qualquer uma das duas filas não pode duplicar efeito).
- `account-service` precisa expor um segundo consumer (pra fila de decisão), além do endpoint HTTP e do
  produtor da primeira fila.
