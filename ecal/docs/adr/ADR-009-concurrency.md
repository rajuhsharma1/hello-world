# ADR-009: Database-atomic reservation and idempotent checkout claims

**Status:** Accepted — current Phase 1

## Context

Two requests can read the last unit simultaneously. A client can retry after losing a response, and two retries can arrive on separate application threads.

## Decision — what and why

Use an affected-row-count conditional stock decrement and a non-negative DB constraint. Claim a UUID idempotency key with a unique constraint and ON CONFLICT DO NOTHING; compare the original semantic payload before replaying. Use one local READ COMMITTED transaction.

## Alternatives — why not now

Read-modify-write can oversell. JVM locks do not span processes. Optimistic version columns are valid for rich aggregate edits but need a conflict/retry policy. SELECT FOR UPDATE is useful for multi-field decisions but has explicit lock-holding/deadlock costs. Redis locking is not the source of truth.

## Consequences and trade-offs

Hot products serialize on their inventory row. The single-product scope avoids multi-row lock ordering; multi-line carts need a defined order. Replays of committed outcomes are stable; rejected commands do not retain a completed request record.

## Failure behavior

The losing reservation receives a conflict and its tentative order is rolled back. Same-key races wait for the winner's transaction; identical payload returns the winning result, changed payload is 409. A lost response requires retrying the same key, not charging again.

## Validation — how to prove it

Run simultaneous stock-one requests on separate connections and assert one success/no negative stock. Race identical keys and assert one order, payment effect and notification; test changed-payload conflict and rollback.

Actual execution evidence belongs in the project validation report; this ADR does not claim a test passed merely because it describes one.

## Revisit trigger and migration implications

Hot-row contention fails measured latency needs, multi-item reservations are introduced, or external payment effects require durable workflow/reconciliation.

If superseded, retain this record and link the new decision, affected contracts/data, rollback approach and evidence.
