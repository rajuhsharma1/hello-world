# ADR-006: Explicit orchestration after transactional boundaries split

**Status:** Partially superseded by ADR-018: Phase 3 implements durable synchronous compensation. Event-driven execution/outbox and autonomous recovery remain Phase 4. The original proposal below describes the earlier baseline.

## Context

Today the local checkout coordinator can roll back one database. Future services with separate stores cannot undo each other through that transaction.

## Decision — what and why

Keep a synchronous application coordinator now. For the extracted workflow, propose a persisted Saga orchestrator with explicit steps, correlation, compensation and a NEEDS_REVIEW path. Do not call the current local transaction a distributed Saga.

## Alternatives — why not now

Choreography reduces a central coordinator but hides the full workflow across event handlers and complicates recovery reasoning. A monolithic distributed transaction is not an available shortcut over independent REST APIs/databases.

## Consequences and trade-offs

Orchestration makes state/recovery visible but creates a coordinator contract and potential bottleneck. Compensation is a business action, may fail, and is not a database rollback.

## Failure behavior

Future payment rejection after stock reservation releases inventory; an unknown authorization must be reconciled rather than blindly retried. The saga must survive coordinator restart and repeated messages.

## Validation — how to prove it

Phase 4 needs persisted-step crash/restart tests, duplicate commands, failed compensation and manual-review drills. Current decline tests prove only local release and commit.

Actual execution evidence belongs in the project validation report; this ADR does not claim a test passed merely because it describes one.

## Revisit trigger and migration implications

A committed business step crosses a process/database boundary. Agree on service ownership and idempotent compensation before extraction cutover.

If superseded, retain this record and link the new decision, affected contracts/data, rollback approach and evidence.
