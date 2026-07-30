# ADR 0011 - Extrato como projeção do ledger + PDF no S3

## Status
Aceito

## Contexto
O RF04 pede `GET /accounts/{id}/statement`: o histórico de movimentações da conta (débitos, créditos, transferências revertidas) com metadados de cada evento. Duas perguntas precisavam de resposta.

**De onde vem o extrato?** O `TASK.md` sugere MongoDB para "eventos/extrato". Só que o ledger canônico já vive em Postgres: cada movimento é uma `Entry` (par débito/crédito por transação), e o próprio saldo é derivado dele (`Account.currentBalance()`). Persistir os mesmos eventos também em Mongo criaria duas fontes de verdade para o mesmo dado, com o risco clássico de divergirem.

**Como paginar um histórico que só cresce?** Um extrato é append-only e pode ficar grande. Paginação por `OFFSET` degrada em páginas profundas e "escorrega" (pula ou repete linhas) quando novos lançamentos entram entre uma página e a próxima.

Havia ainda um pedido do autor, fora do escopo estrito do `TASK.md`: gerar um **comprovante/extrato em PDF e guardá-lo no S3** (o `TASK.md` cita S3 para o comprovante da transferência), para exercitar upload e presign de fato.

## Decisão
O extrato é uma **projeção de leitura sobre a tabela `entries`**, não um novo armazenamento. `StatementService` consulta `entries ⨝ transactions` filtrando por conta e ordenando por `sequence`. Mongo continua só no transaction-processor (domínio de fraude), como no ADR 0007.

Paginação **keyset** por `sequence` (cursor), aproveitando o índice `idx_entries_account_id_sequence` que já existe: `WHERE account = :id AND sequence < :cursor ORDER BY sequence DESC LIMIT :n`. Busca-se `n+1` linhas para saber se há página anterior sem um `COUNT`. O cliente segue o `nextCursor` para paginar para trás.

Filtro de status **`POSTED` + `REVERTED`**. Espelha `currentBalance()` (que só lê `POSTED`) e inclui reversões, que são eventos reais e auditáveis que o dono da conta precisa ver. Holds `PENDING`/`VOIDED` não movimentaram dinheiro e ficam de fora.

O export (`GET /accounts/{id}/statement/export?format=pdf`) renderiza o histórico completo em PDF (OpenPDF), sobe no S3 e devolve uma **URL pré-assinada** de curta duração. O account-service nunca faz proxy dos bytes — o download sai direto do S3.

### Detalhes de implementação
- **Bucket criado na subida da aplicação** (`S3StatementStore.ensureBucket`, em `ApplicationReadyEvent`), não por script. O floci sobe vazio e ainda não há script de provisionamento; criar o bucket no boot mantém `docker compose up` autossuficiente. `createBucket` em `us-east-1` é idempotente (recriar como dono retorna 200). A falha é engolida com `warn`: um contexto que nunca exporta extrato (a maioria dos testes) não pode falhar a subida só porque o S3 está indisponível — o primeiro export é que exporia o erro.
- **Path-style access ligado** (`spring.cloud.aws.s3.path-style-access-enabled=true`). Contra o floci, sem isso o SDK monta URLs virtual-hosted (`bucket.localhost`) que não resolvem, e isso também quebraria a URL pré-assinada.
- **Presign** via `S3Template.createSignedGetURL(bucket, key, ttl)`, TTL de 15 min.
- **Metadados por evento** vêm do join com `transactions` (descrição, id da transação), sem N+1.

## Consequências

### O lado bom (Positivas)
- Uma fonte de verdade só: o extrato nunca diverge do saldo, porque ambos leem as mesmas `entries`.
- Paginação estável e barata sob concorrência, alinhada ao índice que já existia.
- Demonstração de S3 ponta a ponta (upload + presign) sem acoplar o serviço ao tráfego do arquivo.

### Os trade-offs (Negativas)
- Cursor keyset expõe o `sequence` interno como token de paginação. É um detalhe de implementação vazando para a API; um cursor opaco (codificado) seria mais elegante, mas ficou fora de escopo.
- A conta-genesis aparece no extrato dela com entries de `balance_after` null (débitos de abertura); é a mesma pegadinha registrada no ADR 0010, não um caso novo.
- PDF gerado sob demanda a cada chamada, sem cache: aceitável para o volume do teste, mas relançaria o mesmo arquivo em chamadas repetidas.
- `REVERTED` está no filtro por completude, mas o caminho de reversão/compensação ainda não escreve esse status hoje — é preparação, não funcionalidade em uso.
