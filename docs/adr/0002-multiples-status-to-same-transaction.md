# 0002 - Dois enums de status: EntryStatus vs. TransactionStatus

## Status
Aceito

## Contexto
`Transaction` (a transferência como um todo, visível na API) e `Entry` (um lançamento individual numa conta,
parte do ledger de dupla entrada) precisam de status. A opção mais simples seria reusar um único enum pros dois,
já que os nomes se sobrepõem bastante.

O problema é que as duas coisas têm **máquinas de estado diferentes**:

- `Transaction`: `PENDING → COMPLETED | FAILED | REVERTED`. É o contrato exposto em `GET /transfers/{id}`.
- `Entry`: `PENDING → POSTED | REVERTED`. Uma entry nunca tem `FAILED`, porque ela só é criada depois que a
  transação já passou pela validação (reserva de saldo). Se a transação falha antes disso, nenhuma entry chega
  a existir.

Reusar um enum só faria `Entry.status = TransactionStatus.FAILED` compilar, mesmo sendo um estado que nunca
deveria acontecer na prática, um valor representável, mas sempre inválido.

## Decisão
Criei dois enums separados em `common.transaction`:

- `TransactionStatus` (`PENDING`, `COMPLETED`, `FAILED`, `REVERTED`) - usado só em `Transactions.status`.
- `EntryStatus` (`PENDING`, `POSTED`, `REVERTED`) - usado só em `Entries.status`.

Os dois usam `REVERTED` (não `REVERSED`) de propósito: é o mesmo conceito (um efeito já concretizado sendo
desfeito) e o `TASK.md` já fixa esse termo no contrato de `Transaction`. Não tem motivo de domínio pra soletrar
diferente nos dois enums, seria só ruído aumentando a chance de alguém usar o valor errado num `when`.

## Consequências

#### Positivas
- **Estados ilegais deixam de ser representáveis.** O compilador garante que uma `Entry` nunca fica em
  `FAILED`: não existe esse valor no tipo dela. Um `when` exaustivo sobre `EntryStatus` não precisa (nem pode)
  tratar um caso que nunca acontece de verdade.
- **Desacoplamento entre contrato de API e vocabulário interno do ledger.** `TransactionStatus` é ditado pelo
  `TASK.md` (o que a API expõe pro cliente). `EntryStatus` é detalhe de implementação do ledger. Se o contrato
  da API ganhar um novo status de transferência amanhã, isso não obriga a mexer no ciclo de vida da entry, e
  vice-versa.

#### Negativas
- Mais um tipo pra manter, com um caso (`POSTED` vs. `COMPLETED`) onde os vocabulários ainda divergem porque
  representam conceitos diferentes o suficiente pra não valer forçar o mesmo nome: `POSTED` é vocabulário de
  ledger contábil (a entry foi lançada), `COMPLETED` é vocabulário de produto (a transferência terminou com
  sucesso do ponto de vista do cliente). Ainda exige atenção pra não confundir qual enum usar em qual tabela.
