# Detecção de Fraudes nas transações

Bancos na vida real possui inúmeras regras de anti-fraude, validando os pagamentos para saber se eles são verdadeiros ou
possivelmente fraudulento. Tentando imitar levemente esse comportamento, a gente vai desenvolver esse motor anti-fraude
usando técnicas reais do mercado.

## Detecção via score
```
─ Regras de detecção
  ─ Valor acima de R$ 10.000
  ─ Número de transações em X período de tempo (Velocity Check)
  ─ Login de uma localizaçaõ incoerente com as últimas conexões (ex: Bruno mora em MG, mas a transação veio da Romênia)
  ─ Destinatário já denunciado anteriormente 
  ─ Aparelho que costuma ser usado para transações (ex: Bruno costuma usar Android, mas a transação veio de um iPhone)
  ─ Horário da transação (ex: Bruno faz transação normalmente entre 07h-21h, ms surgiu uma transação as 3 da manhã).
  ─ Valor incompatível (Se normalmente o usuário envia entre R$20 e R$300, hoje ele envia R$ 18.000)
  ─ Impossible Travel Check
```

Cada regra infringida aumenta o score da transação, e a ordenação dos scores deve ser algo tipo:
```
0-20 ► Baixo Risco
21-50 ► Médio Risco
51-80 ► Alto Risco 
>81 0 ► Crítico

LOW
► Aprovar

MEDIUM
► Aprovar
► Gerar evento de monitoramento

HIGH
► Colocar em análise manual
ou
► Solicitar autenticação adicional

CRITICAL
► Bloquear
► Congelar temporariamente o saldo
► Emitir alerta
```
E ações devem ou não ser tomadas em cada uma dessas situações, com o intuito de evitar pagamentos fraudulentos.



