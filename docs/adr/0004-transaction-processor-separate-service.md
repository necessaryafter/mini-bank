# ADR 0004 — Separação do transaction-processor como microsserviço independente

## Status
Aceito

## Contexto
O processamento assíncrono das transferências (RF03) poderia muito bem ter ficado dentro do próprio `account-service` — bastaria um listener do SQS rodando na mesma aplicação, isolado em uma thread pool separada da que atende as requisições HTTP. Essa abordagem já seria o suficiente para cumprir o requisito literal de ser "assíncrono via fila".

No entanto, optamos por ir além e isolar esse processamento em um serviço totalmente separado e independente para deploy: o `transaction-processor`. Tomamos essa decisão porque o volume de transferências e o tráfego HTTP da API pública (`POST /accounts`, `GET /accounts/{id}`) crescem por motivos diferentes e em ritmos completamente distintos. Um pico repentino no processamento de transferências (como em finais de mês ou campanhas específicas) não pode, sob hipótese alguma, competir por CPU ou conexões de banco com os clientes que estão apenas tentando consultar seu saldo ou extrato.

## Decisão
Criamos o `transaction-processor` como um serviço Spring Boot independente. Ele possui características bem específicas: consome exclusivamente da fila SQS, não expõe nenhuma porta HTTP e não tem acesso direto a nenhum banco de dados (conforme detalhado na ADR 0003).

Com isso, ele pode ser escalado (em número de réplicas e consumidores) de forma 100% independente do `account-service`, além de permitir manutenção, desligamento ou atualizações em sua lógica de validação sem que precisemos encostar na API pública.

## Consequências

### O lado bom (Positivas)
* **Escalabilidade sob medida:** Conseguimos subir mais réplicas do `transaction-processor` para esvaziar uma fila cheia sem roubar os recursos (threads, pool de conexões HTTP) que servem os usuários na API. Da mesma forma, um pico de acessos ao extrato não atrasa o processamento das transferências.
* **Isolamento total de falhas:** Se um bug travar a lógica de validação ou se uma regra de negócio ficar lenta, o impacto fica restrito à fila. O `account-service` continua de pé atendendo os clientes normalmente. No pior cenário, as transferências demoram um pouco mais para processar, mas a API não cai.
* **Agilidade no deploy:** Podemos alterar, testar e subir novas regras de validação de transferências de forma isolada, sem a necessidade de gerar um novo deploy do `account-service`.

### Os trade-offs (Negativas)
* **Custo operacional mais alto:** Em vez de monitorar e gerenciar um único processo, agora temos dois artefatos para deploy, dois pipelines de CI/CD e duas frentes de observabilidade (métricas, logs e alertas compartilhados).
* **Consistência eventual mais complexa:** Como os serviços rodam em ambientes separados (e não são apenas duas threads dividindo a mesma memória), a comunicação de retorno precisa obrigatoriamente de uma infraestrutura de mensageria real (uma segunda fila SQS, como documentado na ADR 0003). Essa separação física é a causa raiz da complexidade de coordenação que tivemos que assumir.