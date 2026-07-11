# ADR 0006 — Publicação confiável de eventos com Outbox Pattern

## Status
Aceito

## Contexto
Quando o `account-service` aprova uma transferência, ele precisa garantir duas ações: salvar a `Transaction`/`Entry` como `PENDING` no Postgres **e** disparar um `TransactionCreatedEvent` no SQS para que o `transaction-processor` faça a validação.

Fazer isso de forma independente (dar o `commit` no banco e depois o `send` no SQS) nos joga direto no clássico **problema da dupla escrita (dual-write)**:
* Se a aplicação cair logo após salvar no banco, a transferência fica em `PENDING` para sempre, esquecida.
* Se invertermos a ordem (enviar o evento antes), corremos o risco de notificar o SQS sobre uma transferência que acabou sofrendo rollback no banco.

Como Postgres e SQS não compartilham transações nativamente, e implementar um Commit de Duas Fases (2PC) traria uma complexidade absurda para o nosso cenário, precisávamos de uma solução mais elegante e resiliente.

## Decisão
Adotamos o **Outbox Pattern**. A partir de agora, a publicação do evento se torna parte da própria transação do banco de dados.

Quando o `TransferService` insere a transferência, o `OutboxRecorder` entra na mesma transação para salvar o evento serializado em JSON na tabela `outbox_events`. Dessa forma, é tudo ou nada: ou salvamos a transferência e o evento juntos, ou tudo sofre rollback.

Para esvaziar essa caixa de saída, um processo agendado (`OutboxPoller`, usando `@Scheduled`) varre periodicamente a tabela em busca de registros onde `published_at IS NULL`, envia as mensagens para o SQS via `SqsTemplate` e marca a data de publicação.

### Detalhes importantes da implementação:
* **Concorrência inteligente (`FOR UPDATE SKIP LOCKED`):** Para evitar que múltiplas instâncias do `account-service` tentem processar e duplicar os mesmos eventos, usamos o `SKIP LOCKED`. Cada instância pega um lote exclusivo de registros e pula o que já estiver sendo processado por outra, permitindo que o poller escale horizontalmente sem travar o banco.
* **Auditoria nativa (Soft-delete):** Não apagamos o registro após o envio; apenas preenchemos o `published_at`. Isso nos dá um histórico valioso para debug e auditoria (uma rotina separada cuidará de limpar dados muito antigos no futuro).
* **Isolamento de erros:** Se o envio de uma mensagem falhar, o poller apenas incrementa o contador de `attempts` e segue para a próxima. O evento que falhou será retentado no próximo ciclo.

> **Nota sobre entrega:** Esse modelo garante uma entrega do tipo **at-least-once** (pelo menos uma vez). Se o poller enviar a mensagem com sucesso, mas cair antes de atualizar o banco, ela será reenviada. Isso é perfeitamente seguro aqui porque o nosso consumidor (`transaction-processor`) é idempotente (conforme definido na ADR 0005).

---

## Consequências

### O lado bom (Positivas)
* **Confiabilidade:** Eliminamos o risco de perder eventos ou processar transferências fantasma. A publicação agora é garantida e eventualmente tudo se resolve.
* **Escalabilidade simples:** O poller cresce junto com as instâncias do `account-service` de forma nativa, graças ao `SKIP LOCKED`, sem precisar de ferramentas complexas de coordenação (como Redis ou Zookeeper).
* **Observabilidade:** Ficou muito fácil criar alertas. Se um evento tiver muitos `attempts` ou estiver com `published_at` nulo por muito tempo, sabemos na hora que algo está travado.

### Os trade-offs (Negativas)
* **Latência:** O evento não vai para o SQS no exato milissegundo em que a requisição bate na API; ele espera o próximo ciclo do poller (configurado para rodar a cada 1 segundo). Para o nosso fluxo, que já é assíncrono por natureza, isso é aceitável.
* **Manutenção:** Ganhamos mais uma tabela para monitorar, um componente agendado rodando em background e o débito técnico de criar a rotina de expurgo desses dados no futuro.
* **Assimetria na arquitetura:** O `transaction-processor` **não** usará esse padrão para responder ao `account-service` porque ele opera sem banco de dados (ADR 0003). A resposta dele continuará sendo um envio direto e *best-effort* para o SQS. É um risco calculado, mitigado pela idempotência de quem recebe.