# ADR-001: Modular monolith before microservices

**Status:** Accepted monolith baseline; Phase 3 uses the separate extraction experiment in ADR-017.

## Context

The immediate requirement is a correct, explainable checkout, not independent team deployment. The learner must see where the transaction begins and what a failure erases.

## Decision — what and why

Run Catalog, Orders, Inventory, Payment and Notification as feature modules inside one Spring Boot application. Depend on module APIs; keep SQL adapters internal. One PostgreSQL transaction spans the synchronous simulated workflow.

## Alternatives — why not now

Microservices make independent release/scale possible but add network partitions and distributed recovery before those needs exist. An unstructured monolith is simpler to scaffold but hides ownership and makes later extraction harder.

## Consequences and trade-offs

A single process/database is a failure and release domain. Package rules and focused contracts cost discipline but are cheaper than premature network boundaries. Extraction will require redesigning consistency, not simply adding HTTP annotations.

## Failure behavior

An unhandled infrastructure exception rolls back the local workflow. Losing the process after commit requires idempotent replay. A database outage makes the whole checkout unavailable.

## Validation — how to prove it

Run the module-boundary tests and PostgreSQL approval/decline/rollback integration tests; follow the HLD and LLD request path. A passing unit test alone does not prove transaction behavior.

Actual execution evidence belongs in the project validation report; this ADR does not claim a test passed merely because it describes one.

## Revisit trigger and migration implications

A capability needs independent team ownership, deployment cadence or scaling, and the team can fund contract/versioning/operational work. Record the boundary and migration strategy in a superseding ADR.

If superseded, retain this record and link the new decision, affected contracts/data, rollback approach and evidence.
