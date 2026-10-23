# ADR-008: One database now, explicit ownership after extraction

**Status:** Accepted monolith baseline; Phase 3 ownership and migration are implemented by ADR-017/020. The original baseline rationale below is retained.

## Context

A single local transaction is the simplest way to prove early invariants. Future independently deployed services must not coordinate by writing one another's tables.

## Decision — what and why

Use one PostgreSQL database for the monolith, with table responsibility enforced by module adapters. When extracting a service, migrate its data ownership and expose a contract; separate persistence becomes a design constraint rather than a shared-schema illusion.

## Alternatives — why not now

Database per module today would forfeit easy atomicity without current independent deployment value. Shared tables after extraction preserve coupling and make deployments/concurrency guarantees depend on unversioned SQL knowledge.

## Consequences and trade-offs

Early simplicity creates a real migration task later: backfill, ownership cutover, compatibility window and rollback. A separate schema is useful access organization but not proof of independent lifecycle or recovery.

## Failure behavior

A future dual-write migration may diverge; never assume two databases committed together. Define source-of-truth and reconciliation before cutover.

## Validation — how to prove it

Architecture rules and code review enforce current imports. Phase 3 must prove service contract tests and eliminate cross-owner database access; rehearse migration and rollback before removing the baseline.

Actual execution evidence belongs in the project validation report; this ADR does not claim a test passed merely because it describes one.

## Revisit trigger and migration implications

First service extraction or customer regulatory/tenancy ownership requirement.

If superseded, retain this record and link the new decision, affected contracts/data, rollback approach and evidence.
