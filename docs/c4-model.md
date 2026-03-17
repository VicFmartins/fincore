# FinCore C4-Style Model

This document is the compact architecture reference for FinCore. It is intended for GitHub readers and technical interview discussion.

## System Context

### Primary Actor

- API client
  Creates accounts, funds accounts in `local` profile, reads balances, and submits transfers.

### External Systems

| System | Relationship To FinCore |
| --- | --- |
| PostgreSQL | Primary transactional store for accounts, transactions, ledger entries, outbox rows, processed events, and audit logs |
| Kafka | Transport for published transaction lifecycle events and source of `transaction.completed` deliveries |
| Prometheus | Scrapes runtime metrics from `/actuator/prometheus` |
| Grafana | Visualizes Prometheus metrics through a pre-provisioned dashboard |

### System Responsibility

FinCore provides a consistency-first wallet core with:

- synchronous account and transfer APIs
- durable request idempotency
- atomic ledger persistence
- outbox-based event publication
- replay-safe completed-transaction consumption

## Container View

| Container | Technology | Responsibility |
| --- | --- | --- |
| FinCore Application | Spring Boot 3 / Java 21 | REST API, orchestration, persistence adapters, Kafka integration, security, metrics, and health probes |
| PostgreSQL | PostgreSQL | Source of truth for transactional state and reliability-supporting tables |
| Kafka | Kafka | Asynchronous broker for transaction event distribution |
| Prometheus | Prometheus | Metrics collection |
| Grafana | Grafana | Metrics visualization |

## Component View

### Inside The FinCore Application

| Component | Layer | Responsibility |
| --- | --- | --- |
| `AccountController` | Infrastructure | HTTP adapter for account creation and reads |
| `LocalFundingController` | Infrastructure | Local-only HTTP adapter for funding |
| `TransactionController` | Infrastructure | HTTP adapter for transfer submission |
| `AccountService` | Infrastructure | Account orchestration and audit boundary |
| `LocalFundingService` | Infrastructure | Local funding workflow using transaction, ledger, and audit persistence |
| `TransferService` | Infrastructure | Logging, metrics, correlation, and failure handling around transfer execution |
| `TransferUseCase` | Application | Idempotent transfer orchestration, locking, ledger, and outbox persistence |
| `ProcessTransactionCompletedEventUseCase` | Application | Deduplication and completed-event handling decision |
| Persistence adapters | Infrastructure | JPA-backed implementations of repository ports |
| `OutboxPublisher` | Infrastructure | Scheduled publication of unpublished outbox rows |
| `TransactionCompletedConsumer` | Infrastructure | Kafka listener for `transaction.completed` |

## Layer Model

### Domain

- pure business objects and invariants
- no Spring, JPA, HTTP, Kafka, logging, or security concerns
- examples: `Account`, `Transaction`, `LedgerEntry`

### Application

- use cases and ports
- orchestration of domain rules through abstractions
- examples: `CreateAccountUseCase`, `TransferUseCase`, `ProcessTransactionCompletedEventUseCase`

### Infrastructure

- REST, JPA, Kafka, security, metrics, health probes, and tests
- adapts frameworks and external systems to application ports

## Main Flows

### Account Creation

1. Client calls `POST /accounts`.
2. Security validates the write API key.
3. Controller delegates to `AccountService`.
4. `CreateAccountUseCase` creates a zero-balance account.
5. Account is stored in PostgreSQL.
6. Audit log is written in the same transaction.

### Local Funding

1. Client calls `POST /accounts/{id}/fund` in `local` profile.
2. Security validates the write API key.
3. `LocalFundingService` checks local-funding idempotency.
4. Treasury account is debited and target account is credited.
5. Funding transaction and ledger entries are written atomically.
6. Audit log is written.
7. Replay with the same key returns the original result without double credit.

### Transfer Execution

1. Client calls `POST /transactions/transfer`.
2. Request validation and security run in the infrastructure layer.
3. `TransferService` opens correlation context and records transfer-request audit state.
4. `TransferUseCase` checks idempotency and locks accounts pessimistically.
5. Source is debited, destination is credited, and ledger rows are written.
6. Transaction status becomes `COMPLETED`.
7. Outbox rows are stored in the same database transaction.

### Ledger Write

1. Every successful balance movement produces two ledger entries:
   - debit entry for the source
   - credit entry for the destination
2. Ledger rows are persisted in the same transaction as balance updates.

### Outbox Publish

1. `OutboxPublisher` polls unpublished rows from PostgreSQL.
2. It publishes them to Kafka with metadata such as `event_id` and `correlation_id`.
3. Rows are marked published only after broker send succeeds.
4. Failed sends leave rows pending for retry.

### Consumer Deduplication

1. Kafka delivers `transaction.completed`.
2. Consumer extracts `event_id`, `transaction_id`, and `correlation_id`.
3. `ProcessTransactionCompletedEventUseCase` attempts to insert `event_id` into `processed_events`.
4. Existing row means duplicate delivery and processing is skipped.
5. Metrics and structured logs record processed vs deduplicated outcomes.

## Operationally Relevant Components

### PostgreSQL

- transactional source of truth
- supports idempotency, locking, outbox, deduplication, and audit trails

### Kafka

- carries published transaction events
- delivers `transaction.completed` with at-least-once semantics

### Outbox Scheduler

- decouples business commit from broker availability
- turns pending outbox rows into published Kafka records

### `transaction.completed` Consumer

- restores correlation context
- applies durable deduplication
- prevents duplicate side effects during replay

### Prometheus And Grafana

- Prometheus scrapes `/actuator/prometheus`
- Grafana visualizes transfer, outbox, and consumer counters

## Interview Talking Points

- Why request idempotency and consumer deduplication are separate concerns
- Why ledger and balances must commit atomically
- Why outbox publication is safer than direct database + Kafka dual writes
- Why idempotent replay should not inflate execution-success metrics
- Why the local funding endpoint is profile-gated and not part of production behavior
