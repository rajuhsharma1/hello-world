# ADR-005: Outbox when a durable external event is introduced

**Status:** Superseded for Phase 4 by [ADR-021](ADR-021-event-driven-execution.md): outboxes and relays are implemented. The original Phase 1 decision below is retained.

## Context

A future broker publish and a database commit cannot be made one ordinary local transaction. The current notification stub writes only to PostgreSQL.

## Decision — what and why

Do not add Kafka or an outbox to Phase 1. When external events are introduced, plan to commit the order change and outbox entry atomically, then relay asynchronously with consumer deduplication.

## Alternatives — why not now

Database write followed by direct publish loses events after a crash between the steps. Publish first can emit an event for a rolled-back order. Distributed two-phase commit changes operating assumptions and is not implicit Spring behavior.

## Consequences and trade-offs

An outbox adds storage, relay operation, lag monitoring and cleanup. It removes a dual-write gap but does not make delivery exactly once; a crash after publish before marking sent produces replay.

## Failure behavior

Future broker outage leaves durable pending rows. Recovery republishes after the broker returns, possibly more than once. None of these future guarantees is supplied by the current stub.

## Validation — how to prove it

Phase 4 must kill the relay/broker at commit/publish boundaries, assert eventual publication and prove replay-safe consumer business effects. Those tests are not part of the current completed slice.

Actual execution evidence belongs in the project validation report; this ADR does not claim a test passed merely because it describes one.

## Revisit trigger and migration implications

The first requirement to notify an external durable consumer. Revisit broker semantics, schema versioning, ordering and retention together.

If superseded, retain this record and link the new decision, affected contracts/data, rollback approach and evidence.
