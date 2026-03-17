# FinCore

![Java 21](https://img.shields.io/badge/Java-21-0b5fff?style=flat-square)
![Spring Boot 3](https://img.shields.io/badge/Spring_Boot-3-2f7d32?style=flat-square)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?style=flat-square)
![Kafka](https://img.shields.io/badge/Kafka-Event--Driven-111111?style=flat-square)
![Docker Compose](https://img.shields.io/badge/Docker_Compose-Local_Runtime-2496ed?style=flat-square)
![Testcontainers](https://img.shields.io/badge/Testcontainers-Integration_Tests-1f6feb?style=flat-square)

FinCore e um core de carteira digital orientado a consistencia, construido com Java 21, Spring Boot 3, PostgreSQL, Kafka, Flyway, Docker Compose e Testcontainers.

O projeto foca nos pontos mais sensiveis de um backend financeiro: movimentacao atomica de saldo, idempotencia duravel, integridade de ledger, publicacao confiavel de eventos, processamento seguro contra replay, observabilidade operacional e um ambiente local reprodutivel.

## Resumo Do Projeto

O FinCore modela justamente as partes de um backend financeiro que costumam falhar sob concorrencia e retries:

- movimentacao atomica de dinheiro
- idempotencia duravel
- persistencia de ledger
- publicacao assincrona de eventos sem dual write
- processamento seguro contra replay

Nao e um exemplo de CRUD. O repositorio foi estruturado em torno de corretude, fronteiras transacionais e preocupacoes operacionais de um backend de producao.

## Features

- criacao e consulta de contas
- funding de contas no perfil `local` para demos e validacao black-box
- transferencias idempotentes com pessimistic locking
- persistencia atomica de lancamentos de debito e credito no ledger junto com a atualizacao de saldo
- publicacao de eventos do ciclo de vida de transacoes por meio de transactional outbox
- consumo de `transaction.completed` com deduplicacao duravel
- logs estruturados, metricas, health probes, Prometheus e Grafana
- execucao local com Docker Compose e validacao com testes de integracao via Testcontainers

## Destaques Tecnicos

- consistencia financeira em primeiro lugar
  Usa `long` em centavos, atualizacoes atomicas de saldo e persistencia de ledger na mesma transacao.
- idempotencia baseada em estado duravel
  Requests duplicados de transferencia e funding local retornam o resultado ja persistido, sem reaplicar movimentacao financeira.
- confiabilidade event-driven
  O transactional outbox desacopla o commit no banco da entrega no Kafka sem perder eventos ja confirmados.
- consumo seguro contra replay
  A tabela `processed_events` impede efeitos colaterais duplicados em retries e restarts do consumer.
- visibilidade operacional
  Logs estruturados com correlacao, contadores Micrometer, scraping via Prometheus e dashboards no Grafana.
- runtime local com perfil de producao
  O Docker Compose sobe PostgreSQL, Kafka, Prometheus, Grafana e a aplicacao com health checks e ordenacao de startup.

## Problema De Negocio Resolvido

Servicos de carteira e ledger precisam lidar com:

- retries duplicados do cliente
- atualizacoes concorrentes de saldo
- falhas parciais entre commit no banco e publish no broker
- reentrega de mensagens no Kafka
- rastreabilidade para operacao e analise de incidentes

O FinCore trata esses cenarios com idempotencia baseada em banco, pessimistic locking, escrita transacional de ledger, transactional outbox, deduplicacao duravel no consumer, observabilidade estruturada e uma baseline pequena de seguranca stateless.

## Capacidades Principais

- criacao de contas e leitura de saldo
- funding local para validacao black-box e demos
- transferencias idempotentes entre contas
- ledger entries para toda movimentacao de saldo bem-sucedida
- publicacao de eventos em Kafka por meio de transactional outbox
- consumo de `transaction.completed` com deduplicacao duravel
- audit logging para criacao de conta, ciclo de vida da transferencia e funding local
- logs estruturados, propagacao de correlacao, metricas Micrometer, Prometheus e Grafana
- protecao de endpoints de escrita com API key stateless
- health, readiness e liveness probes

## Resumo De Arquitetura

O FinCore segue arquitetura hexagonal / clean architecture.

| Camada | Responsabilidade |
| --- | --- |
| `domain` | Modelo de negocio puro e invariantes. Sem Spring, JPA, HTTP, Kafka ou logging. |
| `application` | Use cases e ports. Define o que precisa acontecer e quais dependencias sao necessarias. |
| `infrastructure` | Runtime Spring Boot, REST, adapters JPA, adapters Kafka, seguranca, observabilidade, scheduling e testes de integracao. |

O dominio permanece livre de framework. A logica de aplicacao depende de ports, nao de implementacoes de infraestrutura. A infraestrutura adapta HTTP, PostgreSQL, Kafka e metricas para esses ports e use cases.

A documentacao detalhada de fluxos esta em [docs/architecture.md](C:/Users/vitor/OneDrive/Documentos/Playground/fincore/docs/architecture.md).
A referencia C4 condensada esta em [docs/c4-model.md](C:/Users/vitor/OneDrive/Documentos/Playground/fincore/docs/c4-model.md).
Os textos reutilizaveis para portfolio estao em [docs/portfolio-snippets.md](C:/Users/vitor/OneDrive/Documentos/Playground/fincore/docs/portfolio-snippets.md).

## Estrutura Dos Modulos

- `domain`
  Contem `Account`, `Transaction`, `LedgerEntry` e as invariantes de negocio, como valores positivos e saldos nao negativos.
- `application`
  Contem use cases como criacao de conta, consulta de conta, execucao de transferencia e processamento de eventos, alem de repository ports e outbox ports.
- `infrastructure`
  Contem configuracoes Spring, controllers, DTOs, entidades e repositories JPA, adapters de ports, publisher e consumer Kafka, filtros de seguranca, metricas, health checks e testes de integracao.

## Garantias Centrais De Consistencia

- Transferencias bem-sucedidas atualizam saldos, escrevem ledger entries, persistem estado da transacao e enfileiram outbox messages na mesma transacao de banco.
- Transferencias com falha fazem rollback conjunto de saldo, ledger, estado da transacao e outbox.
- Repeticoes da mesma transferencia com o mesmo `idempotency_key` nao executam a movimentacao financeira duas vezes.
- O publish do outbox pode ser reexecutado sem reaplicar estado de negocio.
- Retries no consumer de `transaction.completed` nao duplicam efeitos colaterais porque a deduplicacao fica persistida no PostgreSQL.
- O funding local no perfil `local` tambem escreve transacao, ledger e audit trail de forma atomica.

## Estrategia De Idempotencia

### Idempotencia Sincrona De Request

`POST /transactions/transfer` exige `idempotency_key`.

- A primeira execucao cria e conclui a transferencia.
- Uma repeticao com a mesma chave retorna o resultado da transacao ja persistida.
- Duplicidades concorrentes sao resolvidas pelo caminho duravel de idempotencia no repository de transacoes.

O endpoint de funding local usa a mesma abordagem:

- `POST /accounts/{id}/fund` exige `idempotency_key`
- requests duplicados retornam a mesma transacao de funding
- replay nao credita a conta novamente

### Idempotencia No Consumer

Mensagens `transaction.completed` sao deduplicadas por `event_id`.

- o consumer le `event_id` nos headers Kafka
- o processamento tenta persistir esse identificador em `processed_events`
- se a linha ja existir, o evento e tratado como previamente processado
- o estado de deduplicacao sobrevive a restart e retry

## Transactional Outbox

O FinCore evita dual write entre banco e broker persistindo eventos de integracao em uma tabela de outbox na mesma transacao que a escrita de negocio.

Fluxo da transferencia:

1. escreve saldos, ledger entries e estado da transacao
2. persiste outbox rows como `transaction.created` e `transaction.completed`
3. faz commit da transacao no banco
4. publica as outbox rows de forma assincrona no Kafka
5. marca as rows como publicadas apenas apos sucesso no broker

Se o Kafka estiver indisponivel depois do commit da transacao de negocio, a outbox row permanece no PostgreSQL e sera reprocessada posteriormente.

## Deduplicacao No Consumer

O FinCore consome `transaction.completed` de forma conservadora:

- `event_id` e obrigatorio
- o estado de deduplicacao fica em `processed_events`
- o handling e transacional
- entregas duplicadas sao registradas explicitamente em log
- replay apos restart nao reaplica efeitos colaterais

Isso evita que a semantica at-least-once do broker se transforme em comportamento duplicado no sistema.

## Observabilidade E Metricas

### Structured Logging

O FinCore emite logs estruturados para:

- fluxo de transferencia
- fluxo de publish do outbox
- fluxo do consumer de `transaction.completed`

Contexto relevante nos logs:

- `correlation_id`
- `event_id`
- `transaction_id`
- IDs de conta de origem e destino
- status da transferencia
- status do publish
- decisoes de deduplicacao

### Propagacao De Correlacao

- requests HTTP aceitam e retornam `X-Correlation-Id`
- o estado de correlacao e propagado para os logs
- o publish do outbox carrega metadados de correlacao nos headers Kafka
- o consumer restaura o contexto de correlacao a partir dos headers Kafka antes de logar

### Metricas Micrometer

Principais contadores:

- `fincore.transfer.requests.success`
- `fincore.transfer.requests.failure`
- `fincore.transfer.executions.success`
- `fincore.outbox.published`
- `fincore.outbox.publish.failures`
- `fincore.consumer.processed`
- `fincore.consumer.deduplicated`

Significado das metricas:

- `fincore.transfer.requests.success`
  Conta requests de transferencia bem-sucedidos, incluindo replays idempotentes seguros.
- `fincore.transfer.requests.failure`
  Conta requests de transferencia que falham depois de entrar no transfer service.
- `fincore.transfer.executions.success`
  Conta movimentacoes financeiras efetivamente executadas. Replay idempotente nao incrementa esse contador.

Metrics endpoint:

- `GET /actuator/prometheus`

Prometheus e Grafana fazem parte do Docker Compose. O dashboard preconfigurado `FinCore Overview` mostra taxa de execucao de transferencias, falhas de request, publishes do outbox e deduplicacoes no consumer.

## Resumo De Seguranca

O FinCore usa uma baseline pequena de seguranca stateless com API key.

- header: `X-Api-Key`
- permissao de escrita: `wallet:write`
- a chave read-only nao tem permissao de escrita
- nao ha sessoes, login pages ou CSRF state

Endpoints publicos:

- `GET /actuator/health`
- `GET /actuator/health/readiness`
- `GET /actuator/health/liveness`
- `GET /actuator/prometheus`
- endpoints OpenAPI

Endpoints protegidos de escrita:

- `POST /accounts`
- `POST /accounts/{id}/fund` apenas no perfil `local`
- `POST /transactions/transfer`

Respostas esperadas:

- chave ausente ou invalida -> `401`
- autenticado sem permissao suficiente -> `403`
- limite de requests de escrita excedido -> `429`

## Endpoint De Funding Local

O repositorio inclui um mecanismo de funding nao produtivo para demos, smoke tests e validacao black-box:

- endpoint: `POST /accounts/{id}/fund`
- disponibilidade: apenas no perfil `local`
- autenticacao: API key de escrita obrigatoria
- payload: `amount` e `idempotency_key`

Restricoes de seguranca:

- nao e habilitado no perfil de producao
- credita a conta por meio de uma treasury account reservada, sem mutacao direta de saldo
- escreve transacao, ledger e audit trail de forma atomica
- nao enfraquece as garantias centrais de consistencia

Esse endpoint existe para tornar o sistema validavel externamente sem depender de carga manual de saldo no banco.

## Health, Readiness E Liveness

Endpoints:

- `GET /actuator/health`
- `GET /actuator/health/readiness`
- `GET /actuator/health/liveness`

Comportamento:

- `/actuator/health`
  Endpoint geral de saude da aplicacao.
- `/actuator/health/readiness`
  Readiness probe. Inclui conectividade com PostgreSQL e Kafka.
- `/actuator/health/liveness`
  Liveness probe. Indica se o processo esta vivo.

No Docker Compose, o healthcheck do container da aplicacao usa readiness para que a stack so seja considerada saudavel quando banco e Kafka estiverem prontos para uso.

## Configuracao E Variaveis De Ambiente

Perfis:

- `local`
  Perfil padrao. Habilita o endpoint de funding local e espera configuracao de runtime via variaveis de ambiente.
- `prod`
  Perfil de producao. Mantem o mesmo padrao de carregamento de segredos, sem endpoints locais.

Variaveis obrigatorias:

| Variavel | Finalidade |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Perfil Spring ativo, normalmente `local` ou `prod` |
| `FINCORE_DB_URL` | JDBC URL do PostgreSQL |
| `FINCORE_DB_USER` | Usuario do PostgreSQL |
| `FINCORE_DB_PASSWORD` | Senha do PostgreSQL |
| `FINCORE_KAFKA_BOOTSTRAP` | Bootstrap servers do Kafka |
| `FINCORE_API_KEY_WRITE` | API key com permissao de escrita |
| `FINCORE_API_KEY_READ_ONLY` | API key read-only |

Variaveis operacionais opcionais:

| Variavel | Finalidade | Default |
| --- | --- | --- |
| `FINCORE_DB_NAME` | Nome do banco usado pelo PostgreSQL no Compose | `fincore` |
| `FINCORE_TRANSACTION_COMPLETED_TOPIC` | Topico Kafka consumido pelo listener de eventos concluidos | `transaction.completed` |
| `FINCORE_TRANSACTION_COMPLETED_CONSUMER_GROUP` | Consumer group para eventos concluidos | `fincore-transaction-completed` |
| `FINCORE_OUTBOX_BATCH` | Tamanho do batch do outbox | `50` |
| `FINCORE_OUTBOX_DELAY_MS` | Delay do scheduler do outbox | `2000` |
| `FINCORE_OUTBOX_INITIAL_DELAY_MS` | Delay inicial do scheduler do outbox | `1000` |
| `GRAFANA_ADMIN_USER` | Usuario admin do Grafana | `admin` |
| `GRAFANA_ADMIN_PASSWORD` | Senha do Grafana | obrigatoria para o Compose |

Comportamento fail-fast:

- o startup da aplicacao falha se valores obrigatorios de datasource, Kafka ou API keys estiverem ausentes
- a interpolacao do Compose falha se variaveis obrigatorias nao estiverem presentes em `.env` ou no ambiente

Exemplo de `.env` local:

```dotenv
SPRING_PROFILES_ACTIVE=local

FINCORE_DB_NAME=fincore
FINCORE_DB_URL=jdbc:postgresql://postgres:5432/fincore
FINCORE_DB_USER=fincore
FINCORE_DB_PASSWORD=change-me-db-password

FINCORE_KAFKA_BOOTSTRAP=kafka:29092

FINCORE_API_KEY_WRITE=change-me-write-key
FINCORE_API_KEY_READ_ONLY=change-me-read-only-key

GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=change-me-grafana-password
```

Arquivo template:

- [.env.example](C:/Users/vitor/OneDrive/Documentos/Playground/fincore/.env.example)

## Como Executar Localmente

### Opcao 1: Docker Compose

A stack do Compose inclui:

- aplicacao FinCore
- PostgreSQL
- Zookeeper
- Kafka
- Prometheus
- Grafana

Portas publicadas no host:

- FinCore: `8080`
- PostgreSQL: `5432`
- Kafka: `9092`
- Prometheus: `9090`
- Grafana: `3000`

Build e startup:

```bash
cp .env.example .env
docker compose build fincore
docker compose up -d
```

Rebuild e restart:

```bash
docker compose up -d --build
```

Parar a stack:

```bash
docker compose down
```

Remover volumes:

```bash
docker compose down -v
```

Acompanhar logs:

```bash
docker compose logs -f fincore
docker compose logs -f kafka
docker compose logs -f prometheus
docker compose logs -f grafana
```

URLs uteis:

- app: `http://localhost:8080`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

Credenciais locais padrao do Grafana:

- username: `admin`
- password: valor de `GRAFANA_ADMIN_PASSWORD`

### Opcao 2: Startup Com Maven

Suba as dependencias nesta ordem:

1. PostgreSQL
2. Kafka
3. aplicacao

Execute a partir da raiz do repositorio:

```bash
SPRING_PROFILES_ACTIVE=local \
FINCORE_DB_URL=jdbc:postgresql://localhost:5432/fincore \
FINCORE_DB_USER=fincore \
FINCORE_DB_PASSWORD=change-me-db-password \
FINCORE_KAFKA_BOOTSTRAP=localhost:9092 \
FINCORE_API_KEY_WRITE=change-me-write-key \
FINCORE_API_KEY_READ_ONLY=change-me-read-only-key \
mvn -pl infrastructure -am spring-boot:run
```

Observacoes:

- o Flyway executa no startup
- se as dependencias estiverem em containers e a aplicacao rodar no host, use `localhost` em vez dos nomes de servico do Compose

## Como Executar Os Testes

A cobertura de integracao usa Testcontainers e exige Docker.

Suite completa:

```bash
mvn -pl infrastructure -am test
```

Exemplos de execucao focada:

```bash
mvn --% -pl infrastructure -am test -Dtest=RestApiIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
mvn --% -pl infrastructure -am test -Dtest=SecurityIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
mvn --% -pl infrastructure -am test -Dtest=TransferIntegrationTest,TransactionCompletedConsumerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

## Comandos Maven E Desenvolvimento

Build e compilacao:

```bash
mvn clean verify
mvn -pl infrastructure -am -DskipTests compile
mvn -pl infrastructure -am -DskipTests package
```

Executar a app:

```bash
mvn -pl infrastructure -am spring-boot:run
```

Compose:

```bash
docker compose config
docker compose up -d
docker compose ps
docker compose down
```

## Exemplos De API

### Criar Conta

```bash
curl -X POST http://localhost:8080/accounts \
  -H "X-Api-Key: local-write-key" \
  -H "X-Correlation-Id: demo-create-account" \
  -H "Content-Type: application/json" \
  -d '{}'
```

Exemplo de resposta:

```json
{
  "id": "a50f4fd2-4332-4f7a-b093-c84c7f3458e5",
  "balance": 0
}
```

### Consultar Conta

```bash
curl http://localhost:8080/accounts/a50f4fd2-4332-4f7a-b093-c84c7f3458e5
```

### Fazer Funding Local

```bash
curl -X POST http://localhost:8080/accounts/a50f4fd2-4332-4f7a-b093-c84c7f3458e5/fund \
  -H "X-Api-Key: local-write-key" \
  -H "X-Correlation-Id: demo-local-fund" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 1000,
    "idempotency_key": "fund-20260317-0001"
  }'
```

Exemplo de resposta:

```json
{
  "account_id": "a50f4fd2-4332-4f7a-b093-c84c7f3458e5",
  "transaction_id": "fdb8b2d6-4f6d-46aa-8cc3-5555ec0f635d",
  "status": "COMPLETED",
  "balance": 1000,
  "idempotent_replay": false
}
```

### Transferir Fundos

```bash
curl -X POST http://localhost:8080/transactions/transfer \
  -H "X-Api-Key: local-write-key" \
  -H "X-Correlation-Id: demo-transfer" \
  -H "Content-Type: application/json" \
  -d '{
    "source_account_id": "e4ab77f2-07df-467f-8a63-52f92bfa4b36",
    "destination_account_id": "7fa4f118-f04a-4e70-b824-ab4db31d4e43",
    "amount": 250,
    "idempotency_key": "transfer-20260317-0001"
  }'
```

Exemplo de resposta:

```json
{
  "transaction_id": "9a0ac119-fd3a-421e-955c-3df3d1216f5d",
  "status": "COMPLETED"
}
```

### Health E Metrics

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/prometheus
```

### Formato Padrao De Erro

```json
{
  "timestamp": "2026-03-17T16:32:18.410Z",
  "status": 400,
  "error": "bad_request",
  "message": "amount must be greater than 0; idempotency_key is required",
  "path": "/transactions/transfer",
  "correlation_id": "corr-invalid-transfer"
}
```

## Guia Operacional

### API Keys

- mantenha a chave de escrita e a read-only separadas
- nao versiona chaves reais no repositorio
- prefira variaveis de ambiente para uso local e secret manager em producao
- endpoints de escrita exigem a chave de escrita mesmo no perfil `local`

### Prometheus E Grafana

- metrics endpoint do Prometheus: `GET /actuator/prometheus`
- UI do Prometheus: `http://localhost:9090`
- UI do Grafana: `http://localhost:3000`
- dashboard incluido: `FinCore Overview`

Uso esperado:

1. subir o Compose
2. gerar trafego com funding e transferencias
3. inspecionar os contadores no Prometheus ou no dashboard do Grafana

### Troubleshooting

- startup falha por propriedade ausente
  Verifique `.env` ou variaveis exportadas no ambiente.
- readiness esta `DOWN`
  Verifique primeiro a conectividade com PostgreSQL e Kafka.
- transferencia retorna `401`
  Verifique `X-Api-Key`.
- transferencia retorna `403`
  Verifique se a chave possui permissao de escrita.
- transferencia retorna `422`
  Verifique violacoes de regra de negocio, como saldo insuficiente.
- eventos nao estao sendo publicados
  Inspecione as outbox rows e os logs da aplicacao para falhas de publish.
- o consumer parece reaplicar trabalho
  Verifique `processed_events` e os logs de deduplicacao.
- o dashboard do Grafana esta vazio
  Verifique se o Prometheus esta saudavel e gere algum trafego na aplicacao.
- o endpoint de funding local nao aparece
  Confirme que a aplicacao esta rodando com `SPRING_PROFILES_ACTIVE=local`.

### Comportamento Do Perfil Local

O perfil `local` difere operacionalmente em um ponto importante:

- expoe o endpoint de funding para validacao e demos

Ele nao desabilita os principais mecanismos de consistencia. Transferencias, publish do outbox, deduplicacao no consumer, seguranca, audit logging e metricas continuam se comportando como no fluxo normal da aplicacao.

## Melhorias Futuras

- autenticacao mais forte com papeis ou tenant awareness
- rate limiting distribuido com Redis ou enforcement em API gateway
- read models mais ricos e APIs de consulta de ledger
- dead-letter handling e replay tooling para consumers Kafka
- alertas e dashboards mais orientados a latencia
- integracao com secret manager e rotacao automatizada de chaves

## Mapa Da Documentacao

- guia principal do projeto: [README.md](C:/Users/vitor/OneDrive/Documentos/Playground/fincore/README.md)
- guia de arquitetura: [docs/architecture.md](C:/Users/vitor/OneDrive/Documentos/Playground/fincore/docs/architecture.md)
- modelo C4: [docs/c4-model.md](C:/Users/vitor/OneDrive/Documentos/Playground/fincore/docs/c4-model.md)
- snippets de portfolio: [docs/portfolio-snippets.md](C:/Users/vitor/OneDrive/Documentos/Playground/fincore/docs/portfolio-snippets.md)
