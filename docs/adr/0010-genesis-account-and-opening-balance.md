# ADR 0010 - Saldo inicial via conta-genesis (double-entry)

## Status
Aceito

## Contexto
O `POST /accounts` permite abrir uma conta com saldo inicial. Só que o modelo não guarda saldo numa coluna: o saldo é derivado do `balance_after` da última `Entry` com status `POSTED` (ver `Account.currentBalance()`). Nascer com saldo não é setar um campo, precisa existir um lançamento creditando a conta.

Daí a pergunta contábil: de onde vem esse dinheiro? O ledger é de partidas dobradas. Toda transferência é um par `DEBIT` + `CREDIT` de mesmo valor, então a soma dos débitos sempre bate com a dos créditos. Um crédito de abertura solto (single-sided) quebraria isso: dinheiro creditado sem débito correspondente.

Tinha também uma armadilha de concorrência. Se a abertura debitasse uma conta compartilhada e tentasse manter o saldo dela atualizado, cada criação de conta faria um read-modify-write na mesma linha, serializando todas as aberturas e reintroduzindo o lost-update que a `TransferCaptureService` resolve com lock pessimista.

## Decisão
Uma conta-sistema (genesis) de id fixo (`00000000-0000-0000-0000-000000000000`), semeada pela migration `V3`, emite o dinheiro dos saldos de abertura.

Abrir uma conta com saldo inicial > 0 cria uma `Transaction` de abertura (`COMPLETED`) com duas entries POSTADAS de mesmo valor: um débito na genesis e um crédito na conta nova (esse com `balance_after` = valor inicial). A invariante double-entry continua fechando e o saldo inicial vira um lançamento auditável no extrato, não um valor mágico.

### Detalhes de implementação
- A genesis pode (e vai) ficar negativa. É uma conta de emissão/passivo: representa o dinheiro que o banco injetou no sistema. A regra "conta nunca fica negativa" do RF01 vale para contas de usuário, não para a emissora. Por isso a abertura posta as entries direto, sem passar pela checagem de saldo da `TransferCaptureService`.
- A genesis não mantém cursor de saldo: o débito dela é POSTADO com `balance_after = null` de propósito. Como esse saldo nunca é lido, não mantê-lo faz com que aberturas concorrentes não disputem a linha da genesis nem sofram lost-update, o que dispensa lock. A invariante é garantida pelas entries existirem com valores iguais; o `balance_after` é só um cursor denormalizado para leitura rápida de contas de usuário.
- Idempotência: o `POST /accounts` reusa o padrão das transferências (ADR 0005/0006). Reserva da `Idempotency-Key` no Redis (`SETNX`) para a corrida concorrente, com a coluna única `accounts.idempotency_key` (migration `V3`) como backstop durável.

## Consequências

### O lado bom (Positivas)
- Consistência contábil: o ledger fecha mesmo com saldos de abertura, o que torna auditoria e reconciliação triviais.
- Extrato coerente: o saldo inicial aparece como crédito real no histórico (RF04), com contraparte explícita, em vez de um estado inicial sem origem.
- Criação sem contenção: não rastrear o saldo da genesis evita serializar aberturas numa linha quente e dispensa lock.

### Os trade-offs (Negativas)
- Uma conta "mágica" no sistema: a genesis mora na mesma tabela `accounts` e, se alguém a consultar via `GET /accounts/{id}`, retorna saldo zero e um nome de sistema. Esconder ou segregar essa conta fica para depois.
- Entry POSTADA sem `balance_after`: quebra a expectativa de que toda entry postada tem o cursor preenchido. Deliberado, mas é uma pegadinha para quem ler o dado cru da genesis.
- Sem partida tripla: não modelamos de onde a genesis tira o dinheiro (equity, caixa externo). No escopo do teste, ela é a fronteira do sistema.
