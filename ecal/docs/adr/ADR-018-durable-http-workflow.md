# ADR-018: Durable synchronous compensation with identity-safe recovery

**Status:** Accepted — Phase 3; supersedes ADR-006's future-only orchestration assumption for this mode.

## Context and decision

After extraction there is no global transaction. Persist an order claim and each workflow step in Orders, then invoke the owner outside that local transaction. Use one stable order ID for reserve/release/payment operations. Persist PAYMENT_UNKNOWN before contacting Payment, and RELEASE_PENDING after a decline. Only acknowledge CANCELLED after release; only CONFIRMED after a validated authorization. Stock rejection is a durable REJECTED attempt in this mode. Terminal notification recording commits with the terminal order update locally.

Four nonterminal states make uncertainty visible. A caller replays the exact buyer/key/product/quantity/outcome to resume; GET never runs work. There is no background recovery worker yet. The browser saves key/payload before sending and freezes editing after uncertainty.

A 30-second database-clock lease reduces competing coordinators. Short local state writes match expected status plus lease token. A newer token fences the old writer/releaser. Because expired work can still have in-flight HTTP, immutable downstream identities/conditional SQL arbitrate effects. Release-before-reserve creates a tombstone; multiple releases cannot increment stock repeatedly. Do not expire stock while a payment is unknown.

## Alternatives and tradeoffs

Holding a row lock/connection across HTTP increases contention and still supplies no remote rollback. Immediate blind retry with a new identity duplicates effects. In-memory progress disappears on restart. Distributed transactions/XA do not apply to arbitrary HTTP owners. Event orchestration/outbox would provide autonomous retry/delivery recovery, but is reserved for Phase 4 so the effects of extraction can be inspected first.

This is already a synchronous Saga-like compensating workflow; calling it merely CRUD would hide that fact. Compensation can fail and is not rollback. Paused work can remain indefinitely without a caller/operator replay, and idempotency/lease rows grow without a retention policy. A real provider needs its own proven idempotency/reconciliation and manual-review policy; the simulator does not establish one.

## Failures, proof and rollback

Real HTTP tests inject committed-but-unacknowledged reservations/payments/releases, malformed 2xx, actual payment read timeout, failed compensation, local terminal DB failure, Orders restart, same-key contention, last-unit contention and stale lease writes. A live drill stops/restores the Payment container and proves one effect. These proof locations are separate from [observed validation](../../VALIDATION.md).

Returning to the monolith is a data ownership operation after new work, not just switching a Git branch. See [ADR-020](ADR-020-offline-ownership-cutover.md). Phase 4 extends execution/transport while retaining business identities and state semantics.
