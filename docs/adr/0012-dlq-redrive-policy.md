# ADR 0012 - DLQ e redrive policy via bootstrap na subida (RF05)

## Status
Aceito

## Contexto
O RF05 pede que mensagens que falhem o processamento repetidamente sejam direcionadas a uma fila de mensagens mortas (DLQ), sem travar o processamento das demais. O sistema tem duas filas de entrada, cada uma com um único consumidor:

* `validate-transfer`, consumida por `TransactionListener` (transaction-processor).
* `transfer-decision`, consumida por `DecisionListener` (account-service).

Nenhum dos dois listeners engole exceção: se `TransferReviewService.review` ou `TransferDecisionService.apply` lançar (erro de deserialização, falha transitória de Mongo/Redis/Postgres, bug), a exceção sobe até o container do `@SqsListener`, a mensagem não é deletada e volta a ficar visível após o *visibility timeout*: o SQS já reencaminha (retry) por conta própria. O que falta é achar o piso: hoje as filas são criadas sob demanda pelo `SqsTemplate` (`QueueNotFoundStrategy.CREATE`, o padrão do spring-cloud-aws) sem nenhum atributo, então uma mensagem envenenada reencaminha para sempre e nunca sai da frente da fila.

Isso pede duas coisas que só existem como atributos da fila no próprio SQS: uma fila DLQ e uma `RedrivePolicy` (`deadLetterTargetArn` + `maxReceiveCount`) na fila de origem apontando para ela. Não é algo que o `@SqsListener` resolva sozinho: é provisionamento de infraestrutura.

Como no ADR 0011 (bucket S3 do extrato), o floci sobe vazio e o projeto não tem hoje um script de provisionamento (`docker-entrypoint-initaws.d` ou similar) rodando antes das aplicações.

## Decisão
Cada serviço garante, na subida, a DLQ e a *redrive policy* da fila que ele **consome**, não da que ele só publica. `TransactionListener` roda no transaction-processor, então é o transaction-processor que garante `validate-transfer-dlq` e a `RedrivePolicy` de `validate-transfer` (`ValidateTransferDlqProvisioner`); simetricamente, o account-service garante `transfer-decision-dlq` e a policy de `transfer-decision` (`TransferDecisionDlqProvisioner`). Cada um só cuida da fila cujo consumidor ele hospeda, porque é o consumidor quem sabe quantas tentativas fazem sentido antes de desistir de uma mensagem.

A implementação espelha `S3StatementStore.ensureBucket`: um `@EventListener(ApplicationReadyEvent::class)` que roda em todo boot, best-effort, com a falha apenas logada (`runCatching` + `log.warn`). `CreateQueue` e `SetQueueAttributes` são ambos idempotentes (recriar uma fila existente com os mesmos atributos, ou reaplicar a mesma `RedrivePolicy`, é sempre seguro), então não importa em que ordem os dois serviços sobem, nem que o outro lado já tenha auto-criado a mesma fila (sem atributos) publicando nela primeiro; o resultado converge para o mesmo estado.

`SqsAsyncClient` é injetado via `ObjectProvider<SqsAsyncClient>` em vez de injeção direta: `TransactionProcessorApplicationTests` sobe com `spring.cloud.aws.sqs.enabled=false`, cenário em que o bean nem existe, e um contexto sem broker acessível (a maioria dos testes) não pode falhar a subida por causa de um provisionamento best-effort.

`maxReceiveCount` é configurável (`carbonbank.sqs.validate-transfer.max-receive-count` / `carbonbank.sqs.transfer-decision.max-receive-count`, default 5 em ambos).

## Consequências

### Positivas
* **`docker compose up` continua autossuficiente.** Nenhum script externo de init a mais para manter, na mesma linha do bucket S3 (ADR 0011).
* **Convergente independente de ordem de subida.** Idempotência de `CreateQueue`/`SetQueueAttributes` resolve a corrida entre o auto-create do `SqsTemplate` (produtor) e o provisionamento explícito (consumidor).
* **Sem mudança nos listeners.** `TransactionListener`/`DecisionListener` já deixavam exceção subir; o SQS é quem decide, via `RedrivePolicy`, quando uma mensagem vai para a DLQ: a aplicação não precisa contar tentativas nem inspecionar `ApproximateReceiveCount` manualmente.

### Negativas (trade-offs)
* **Nada drena a DLQ.** Mensagens que chegam lá ficam só logadas implicitamente pelo SQS (visíveis via `ApproximateNumberOfMessages`); não há um consumidor, alerta ou processo de replay/inspeção. Fica documentado como próximo passo, não implementado.
* **`maxReceiveCount` é o mesmo para toda falha.** Não distingue erro transitório (Mongo momentaneamente fora) de erro permanente (payload inválido); ambos gastam as mesmas tentativas antes de cair na DLQ.
* **Boot best-effort, não uma garantia.** Se o floci não estiver de pé no boot (ou subir depois dos serviços), a fila acaba sendo criada sem `RedrivePolicy` pelo auto-create do `SqsTemplate` e só ganha a política no próximo restart do serviço consumidor; não há retry do provisionamento em background.
