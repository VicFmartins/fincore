# FinCore Architecture

## Scope

This document describes the runtime structure and main request and event flows of FinCore.

For a shorter interview-friendly architecture reference, see [c4-model.md](C:/Users/vitor/OneDrive/Documentos/Playground/fincore/docs/c4-model.md).

FinCore is a financial core responsible for:

- account creation
- local-only funding for validation
- idempotent transfers
- ledger persistence
- outbox-based event publication
- replay-safe consumption of `transaction.completed`

## C4-Style View

### System Context

Actors and systems:

- API client
  Creates accounts, funds accounts in local profile, reads balances, and submits transfers.
- FinCore
  Executes transactional wallet operations and emits integration events.
- PostgreSQL
  Stores accounts, transactions, ledger entries, outbox rows, processed-event state, and audit logs.
- Kafka
  Receives published transaction events and delivers completed-transaction events to consumers.
- Prometheus / Grafana
  Scrape and visualize runtime metrics.

### Container View

| Container | Responsibility |
| --- | --- |
| FinCore Spring Boot application | REST API, application orchestration, JPA adapters, Kafka adapters, security, and observability |
| PostgreSQL | Transactional source of truth for wallet state and integration coordination tables |
| Kafka | Asynchronous transport for transaction lifecycle events |
| Prometheus | Scrapes `/actuator/prometheus` |
| Grafana | Visualizes the exported metrics |

### Component View

| Component | Responsibility |
| --- | --- |
| `AccountController` | HTTP adapter for account creation and reads |
| `LocalFundingController` | Local-profile HTTP adapter for account funding |
| `TransactionController` | HTTP adapter for transfer submission |
| `AccountService` | Infrastructure orchestration for account-related use cases |
| `LocalFundingService` | Local operational funding workflow with transaction, ledger, and audit persistence |
| `TransferService` | Transfer orchestration boundary for logging, metrics, and failure handling |
| `TransferUseCase` | Application-level transfer workflow with idempotency, locking, ledger, and outbox writes |
| `OutboxPublisher` | Polls unpublished outbox rows and publishes them to Kafka |
| `TransactionCompletedConsumer` | Kafka listener for completed-transaction events |
| `ProcessTransactionCompletedEventUseCase` | Durable deduplication and completion-event processing decision |
| Persistence adapters | JPA implementations of application ports |

## Architectural Boundaries

### Domain

The `domain` module owns business invariants only.

- no Spring annotations
- no persistence annotations
- no HTTP or Kafka concepts
- no logging, metrics, security, or configuration concerns

Examples:

- `Account` prevents negative balances
- `Transaction` enforces valid status transitions and positive amounts
- `LedgerEntry` models debit and credit records

### Application

The `application` module owns use cases and ports.

- defines repository and outbox contracts
- coordinates domain objects
- contains application-specific exceptions
- remains independent from HTTP, Kafka, and JPA implementations

Examples:

- `CreateAccountUseCase`
- `GetAccountUseCase`
- `TransferUseCase`
- `ProcessTransactionCompletedEventUseCase`

### Infrastructure

The `infrastructure` module owns delivery and integration concerns.

- REST controllers and DTOs
- JPA entities and repositories
- adapters for application ports
- Kafka producer and consumer logic
- security filter chain and API-key authentication
- logging, correlation propagation, metrics, and health probes

This keeps framework and operational concerns outside the domain model.

## Request And Event Flows

### 1. Account Creation

1. Client calls `POST /accounts` with a write API key.
2. Security authenticates the request.
3. `AccountController` maps the request to `AccountService`.
4. `AccountService` opens correlation context and invokes `CreateAccountUseCase`.
5. `CreateAccountUseCase` creates a new domain `Account` with zero balance.
6. `AccountPersistenceAdapter` stores the account in PostgreSQL.
7. `AuditLogService` writes an immutable `ACCOUNT / CREATE` audit record in the same transaction.
8. Controller returns `201 Created` with the account DTO.

### 2. Local Funding

This flow exists only in the `local` profile.

1. Client calls `POST /accounts/{id}/fund` with a write API key, `amount`, and `idempotency_key`.
2. Security authenticates the request. The endpoint is not exposed outside the `local` profile.
3. `LocalFundingController` delegates to `LocalFundingService`.
4. `LocalFundingService` checks for an existing funding transaction using a reserved local-funding idempotency namespace.
5. If the key already exists, the previously stored transaction result is returned and the target account is not credited again.
6. If the key is new, the service ensures a reserved treasury account exists.
7. A funding transaction is created and moved through `PENDING -> PROCESSING -> COMPLETED`.
8. Treasury is debited and target account is credited.
9. Two ledger entries are persisted for the funding transaction.
10. An immutable audit record is written for successful funding.
11. Controller returns the funding result with resulting balance.

The funding path deliberately reuses the same persistence model as normal money movement so local validation remains realistic.

### 3. Transfer

1. Client calls `POST /transactions/transfer` with source, destination, amount, and `idempotency_key`.
2. Security authenticates the request and validation checks the DTO.
3. `TransactionController` delegates to `TransferService`.
4. `TransferService` opens correlation context, logs the request, and records a transfer-request audit entry.
5. `TransferExecutionService` starts a transaction and invokes `TransferUseCase`.
6. `TransferUseCase` looks up an existing transaction by `idempotency_key`.
7. If found, it returns the existing transaction result without executing the money movement again.
8. If not found, it creates a pending transaction and persists an outbox row for `transaction.created`.
9. Source and destination accounts are loaded with pessimistic locking in deterministic order.
10. Domain logic debits source and credits destination.
11. Updated balances are persisted.
12. Two ledger entries are stored for the transaction.
13. Transaction status moves to `COMPLETED`.
14. An outbox row for `transaction.completed` is persisted in the same transaction.
15. `TransferService` records metrics, writes success audit state, and returns the response.

Failure behavior:

- if the destination account is missing, balances and ledger entries do not commit
- if funds are insufficient, the transfer is marked failed and the request returns a business error
- request idempotency prevents duplicate money movement on retry

### 4. Outbox Publication

1. `OutboxPublisher` polls unpublished rows from PostgreSQL.
2. For each row, it builds a Kafka record with payload and headers such as:
   - `event_id`
   - aggregate identifiers
   - `correlation_id`
3. The record is published to Kafka.
4. If broker send succeeds, the outbox row is marked with `published_at`.
5. If broker send fails, the row remains unpublished and is retried later.

The business transaction does not depend on Kafka availability at commit time.

### 5. `transaction.completed` Consumption

1. Kafka delivers a `transaction.completed` message to `TransactionCompletedConsumer`.
2. The consumer extracts:
   - `event_id`
   - `transaction_id`
   - `correlation_id`
3. The consumer restores correlation context for structured logging.
4. A transactional service invokes `ProcessTransactionCompletedEventUseCase`.
5. The use case attempts to persist `event_id` in `processed_events`.
6. If the insert succeeds, the event is considered new and processing continues.
7. If the row already exists, the event is considered deduplicated and side effects are skipped.
8. Metrics and logs capture whether the message was processed or deduplicated.

This is the durable replay barrier for the consumer path.

## Persistence Model In Context

Main tables:

| Table | Role |
| --- | --- |
| `accounts` | Current account balance state |
| `transactions` | Transfer and local funding transaction lifecycle |
| `ledger_entries` | Immutable debit and credit records |
| `outbox` | Integration events awaiting publication or already published |
| `processed_events` | Durable deduplication state for consumed Kafka messages |
| `audit_logs` | Immutable audit trail for important actions |

## Reliability Patterns

### Request Idempotency

- protects against duplicate client retries
- uses durable state in `transactions`
- applies to transfer requests and local funding

### Pessimistic Locking

- prevents concurrent overspending
- ensures deterministic account locking order
- protects transfer balance correctness under contention

### Transactional Outbox

- prevents the "database committed but broker publish failed" gap
- keeps integration publication retryable
- makes pending integration work visible in PostgreSQL

### Consumer Deduplication

- protects against replay, restart, and at-least-once delivery
- uses `processed_events` instead of in-memory state
- keeps duplicate deliveries from triggering duplicate downstream work

### Audit Logging

- immutable insert-only records
- captures account creation, transfer request, transfer success/failure, and local funding
- includes correlation data for tracing

## Observability Model

### Logs

Structured logs cover:

- transfer started / completed / failed
- outbox publish success / failure
- consumer processed / deduplicated decisions

Important fields:

- `correlation_id`
- `transaction_id`
- `event_id`
- account identifiers
- status and deduplication outcome

### Metrics

Main counters:

- `fincore.transfer.requests.success`
- `fincore.transfer.requests.failure`
- `fincore.transfer.executions.success`
- `fincore.outbox.published`
- `fincore.outbox.publish.failures`
- `fincore.consumer.processed`
- `fincore.consumer.deduplicated`

Operational interpretation:

- request success is not the same as unique execution success
- idempotent replay should increase successful request count but not executed money movement count

### Health Probes

- `/actuator/health`
- `/actuator/health/readiness`
- `/actuator/health/liveness`

Readiness includes PostgreSQL and Kafka connectivity. Liveness stays lightweight.

## Security Model

FinCore currently uses stateless API-key authentication.

- `X-Api-Key` header
- write authority for protected POST endpoints
- read-only key cannot mutate state
- no domain logic is coupled to authentication concerns

Public endpoints remain available for health, metrics, and documentation access.

## Operational Notes

### Startup Dependencies

Normal startup order:

1. PostgreSQL
2. Kafka
3. FinCore
4. Prometheus and Grafana

### Local Profile

`local` profile behavior:

- enables the funding endpoint
- keeps the same core transfer consistency behavior
- is intended for validation and demos, not production deployment

### Testing

The infrastructure module carries the integration test coverage for:

- REST behavior
- security
- transfer consistency
- outbox publish and retry
- consumer deduplication
- local funding
- health endpoints
