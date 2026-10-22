# ADR-007: Kafka as a later replay/streaming experiment

**Status:** Deferred — Phase 4 option, not installed

## Context

The blueprint includes replayable order events and independent reporting/notification consumers. The initial synchronous flow needs no broker.

## Decision — what and why

Introduce Kafka only when Phase 4 tests need event replay, partition ordering and independent consumer groups. Plan an order-ID partition key, versioned schemas and explicit lag visibility.

## Alternatives — why not now

RabbitMQ suits work queues/routing; SQS reduces broker operation for managed queues; EventBridge routes AWS events with different replay/order properties. An in-memory event bus is useful in process but not durable integration.

## Consequences and trade-offs

Kafka brings partition/consumer-group, retention, schema and operating cost. More partitions do not provide global ordering; idempotent producers do not imply exactly-once business effects in an unrelated database.

## Failure behavior

Future duplicate delivery, poison messages, consumer crashes and out-of-order versions must be visible and recoverable. There is no current broker outage behavior because no broker is deployed for ECAL.

## Validation — how to prove it

Phase 4 must demonstrate replay and duplicates with real broker tests and a documented partition/consumer policy, not mocked publish calls.

Actual execution evidence belongs in the project validation report; this ADR does not claim a test passed merely because it describes one.

## Revisit trigger and migration implications

A measured event-stream requirement exists and Kafka operational learning is worth the resource cost; otherwise compare the simpler managed queue option.

If superseded, retain this record and link the new decision, affected contracts/data, rollback approach and evidence.
