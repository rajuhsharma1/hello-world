# ADR-014: Separate safe request replay from remote-payment reconciliation

**Status:** Accepted for HTTP replay; distributed policy deferred

## Context

A response can be lost even when a database transaction commits. A future external payment provider can also accept an authorization before its response is lost, but that is a different consistency boundary.

## Decision — what and why

For this local simulator, replay the same checkout key/payload to resolve HTTP uncertainty. Do not implement fake network payment timeouts as if rollback proves provider failure. Later persist stable payment attempt IDs, unknown outcomes, reconciliation and bounded retry/DLQ policies.

## Alternatives — why not now

Blindly retrying a new key can create another purchase. Treating every timeout as decline can release stock for an already-authorized payment. Infinite retries hide poison messages and operational debt; a DLQ without a replay owner is only a holding area.

## Consequences and trade-offs

Current determinism makes transaction behavior teachable but does not prove real-provider correctness. Future reconciliation adds durable state, polling/event contracts, monitoring and possible manual review.

## Failure behavior

Today an unacknowledged HTTP result is uncertain until same-key replay/history confirms it. In the future, an unknown provider result stays unknown until queried/reconciled; compensate only from evidence and make replay idempotent.

## Validation — how to prove it

Current tests lose/replay a checkout result and assert one payment effect. Phase 4 must inject success-then-timeout, consumer crashes, poison events and exhausted retries, with observed recovery outcomes.

Actual execution evidence belongs in the project validation report; this ADR does not claim a test passed merely because it describes one.

## Revisit trigger and migration implications

The simulator becomes a separate process or a provider-like nontransactional side effect is introduced.

If superseded, retain this record and link the new decision, affected contracts/data, rollback approach and evidence.
