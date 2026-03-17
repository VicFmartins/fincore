# FinCore Portfolio Snippets

This file contains concise copy for presenting FinCore on GitHub, a resume, or LinkedIn.

## GitHub Repo Description

Consistency-first digital wallet core with idempotent transfers, transactional outbox, Kafka event flow, durable consumer deduplication, observability, security, Docker Compose runtime, and Testcontainers integration tests.

## Resume / Project Section

Built `FinCore`, a Java 21 / Spring Boot financial core focused on correctness under concurrency and retries. Implemented idempotent transfers with pessimistic locking, atomic ledger persistence, transactional outbox publishing to Kafka, durable consumer deduplication, structured observability, API-key security, Docker Compose local runtime, and Testcontainers-backed integration tests.

## LinkedIn Featured Project Summary

FinCore is a backend portfolio project focused on financial consistency rather than CRUD. It demonstrates idempotent transfer execution, atomic ledger updates, transactional outbox publishing, Kafka-based event flow, replay-safe `transaction.completed` consumption, structured observability, stateless API-key security, and a reproducible Docker-based local environment with integration testing.

## Short Technical Highlights

- Financial consistency through atomic balance and ledger writes
- Durable idempotency for transfer and local funding requests
- Transactional outbox to avoid database/Kafka dual-write gaps
- Kafka consumer deduplication backed by PostgreSQL
- Prometheus, Grafana, structured logs, and correlation propagation
- Security baseline and full local runtime with Docker Compose
