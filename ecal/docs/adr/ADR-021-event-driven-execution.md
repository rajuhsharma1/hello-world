# ADR 021 — transactionally recorded events and a hybrid orchestrated Saga

Status: accepted for the Phase 4 educational slice.

## Context

Phase 3 records recoverable progress but needs a caller to retry after an outage. Adding a background thread alone would not explain event delivery, replay or independent reporting. Publishing Kafka inside the request without an outbox creates a dual-write gap: the database can commit while the event is lost, or the event can appear while the database rolls back.

## Decision

Record domain facts using transaction-local PostgreSQL triggers and relay committed rows to Kafka. Give each consumer its own transactional inbox. Activate durable Orders work from `OrderCreated`, then reuse the tested HTTP coordinator and idempotent owner commands. Publish owner outcomes for audit and reporting; do not introduce a second command protocol simultaneously. Reporting owns a separate database and projects full Orders snapshots by monotonic source version. Invalid wire records are retained in a database quarantine; infrastructure failures keep retrying.

## Alternatives and consequences

| Alternative | Why not selected here | When to reconsider |
|---|---|---|
| Request-time DB + Kafka dual writes | neither order provides an atomic commit | avoid for durable business facts |
| Kafka transactions alone | cannot atomically commit a PostgreSQL business transaction | Kafka-to-Kafka transformations |
| CDC/Debezium outbox | adds connector operations and offsets before the delivery model is understood | higher throughput and established CDC platform |
| All commands over Kafka / choreography | increases protocol and distributed-state surface in this slice | independently evolving command consumers with clear Saga semantics |
| Poll Orders directly without events | solves background recovery but not independent delivery/reporting | smaller products without event consumers |
| Per-row parallel outbox relays | can reorder versions across replicas | partition/shard relays deliberately and benchmark |
| Kafka dead-letter topic only | retrying publication to it can itself fail | larger operations platform; retain durable evidence first |
| Delta revenue projection | duplicate/out-of-order events can corrupt totals | event ledger with ordered deduplicated deltas and reconciliation |

Triggers make direct SQL business updates participate in event recording, at the cost of PostgreSQL coupling and SQL behavior that must be read alongside Java. Tests exercise outbox rollback and duplicate POST behavior. The after-trigger choice also relies on PostgreSQL’s guarantee that it runs for rows actually inserted; the duplicate POST check is not a deterministic reproduction of two simultaneous losing inserts. Kafka delivery is at least once; business effects are deduplicated by durable identities. Inbox retention, broker replication, archival, operational replay, alerts and capacity remain explicit production responsibilities.

## Validation

See [Phase 4 LLD](../architecture/PHASE-04-LLD.md) and the current [validation ledger](../../VALIDATION.md). The pre-existing synchronous regression suite remains applicable with events disabled.

The async 202 acknowledgement changes the Phase 3 client contract. This lab pairs its UI and API in an isolated Compose project. A production rolling migration needs an explicit versioned endpoint or negotiated async preference, in addition to the data-ownership and event checkpoints; wire-shape compatibility alone does not make this behavioral change non-breaking.
