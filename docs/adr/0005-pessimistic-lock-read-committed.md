# ADR 0005 - Lock pessimista de conta e uso explícito de READ COMMITTED no capture

## Status
Aceito

## Contexto
O método `TransferCaptureService.capture()` tem uma missão crítica sob cenários de alta concorrência: ele precisa ler o saldo atual de uma conta e, com base nisso, decidir se aprova a transferência ou se cancela (`VOIDED`) os lançamentos pendentes. O desafio é que uma mesma conta pode estar envolvida em várias transferências simultâneas, e a própria mensagem do SQS pode ser reentregue por falha e acabar sendo processada em paralelo por duas threads diferentes.

A saída mais comum do mercado para esse tipo de conflito é o *optimistic locking* (aquela estratégia de colocar uma coluna `version` na tabela, fazer um `UPDATE ... WHERE version = ?` e tentar de novo se der erro). No entanto, descartamos essa opção aqui. Se uma mesma conta sofrer muita contenção (várias transferências batendo ao mesmo tempo na mesma origem ou destino), o optimistic locking vira um festival de retries repetidos. Como o nosso processamento assíncrono já precisa lidar naturalmente com falhas e reprocessamentos de mensagens, não queríamos empilhar mais uma camada de retry no código. Para o nosso fluxo, é muito melhor que a segunda transferência **espere ordenadamente** a primeira terminar (lock pessimista) do que **falhar e ter que rodar tudo de novo**.

Decidimos usar o clássico `SELECT ... FOR UPDATE` na linha da conta (através do método `lockAccount`, detalhado na ADR 0003) para funcionar como um mutex. Contudo, essa escolha escondia uma pegadinha perigosa: por padrão, o framework Exposed utiliza o nível de isolamento `REPEATABLE READ`, e não o `READ COMMITTED` (que é o padrão nativo do Postgres).

Sob o `REPEATABLE READ`, o snapshot da transação é travado logo na primeira consulta. Na prática, isso significava que mesmo após a nossa thread esperar pacientemente e conseguir o lock da conta, a consulta seguinte para pegar o saldo (`currentBalance()`) continuaria enxergando a foto do passado, ignorando o saldo real que a transação anterior tinha acabado de salvar. Como a linha da conta em si só sofre o lock e nunca é alterada diretamente (o que muda são as tabelas de transações), o Postgres não detectava isso como um conflito de serialização. O resultado era uma leitura silenciosamente desatualizada (um *lost update* invisível), e não um erro explícito.

## Decisão
O método `capture()` passa a usar obrigatoriamente `@Transactional(isolation = Isolation.READ_COMMITTED)`.

Deixamos isso explícito no código porque, embora seja o padrão do Postgres, não é o comportamento padrão do Exposed. Sem forçar essa configuração, o lock pessimista perde totalmente sua função de sincronizar as leituras de saldo, servindo apenas para enfileirar o tempo de execução, mas com dados velhos.

## Consequências

### O lado bom (Positivas)
* **Comportamento previsível:** O lock (`SELECT ... FOR UPDATE`) volta a funcionar como manda o figurino. Assim que uma transação libera a conta, a próxima thread na fila lê o saldo atualizado e fresquinho, e não um snapshot congelado no tempo.
* **Sem loops de retry:** O fluxo ficou muito mais simples de raciocinar em cenários de falha ou reentrega de mensagens. A segunda transferência simplesmente espera a sua vez, processa uma única vez com o saldo real e encerra o ciclo.

### Os trade-offs (Negativas)
* **Armadilha oculta no código:** Essa é uma configuração fácil de ser "corrigida" por engano por alguém no futuro. Como o `READ_COMMITTED` parece redundante para quem conhece o Postgres, um desenvolvedor desavisado poderia remover a anotação achando que estava limpando o código. Para blindar isso, deixamos o motivo bem documentado tanto aqui quanto no KDoc do método `capture()`.
* **Gargalo em contas muito movimentadas:** Uma alta contenção na mesma conta vai serializar o processo por completo (uma transferência de cada vez). O throughput de uma conta individual fica limitado pelo tempo que uma transação inteira leva para rodar. Esse comportamento é aceitável para o volume de transações que prevemos agora, mas não escalaria para uma conta global de altíssimo tráfego sem uma estratégia de particionamento ou balanceamento no futuro.