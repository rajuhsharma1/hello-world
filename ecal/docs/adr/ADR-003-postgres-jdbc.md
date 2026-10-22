# ADR-003: PostgreSQL, Flyway and explicit Spring JDBC

**Status:** Accepted — current Phase 1

## Context

Stock races and unique-key behavior are central learning goals. Persistence tests must exercise the same database semantics as the application.

## Decision — what and why

Use PostgreSQL 17.6 with Flyway versioned SQL migrations and Spring JDBC adapters. Money is fixed-scale numeric/BigDecimal. Module APIs hide SQL without inventing a generic repository framework.

## Alternatives — why not now

JPA/Hibernate is valuable for aggregate mapping and enterprise comparison, but hides the exact conditional update and affected-row semantics this slice teaches. H2 is not a substitute for PostgreSQL race/conflict behavior. DB2 is a later compatibility experiment, not an available production dependency.

## Consequences and trade-offs

SQL is visible and controllable; mapping and schema evolution require explicit work and PostgreSQL-specific constructs reduce portability. This is an intentional trade, not a claim that JDBC universally beats JPA.

## Failure behavior

Migration failure stops startup instead of permitting an unknown schema. Database checks defend against negative stock or invalid persisted values. Do not edit an already applied migration to make history look clean.

## Validation — how to prove it

Use Testcontainers PostgreSQL integration tests, Flyway startup and direct invariant assertions. Do not label an in-memory mock database a concurrency proof.

Actual execution evidence belongs in the project validation report; this ADR does not claim a test passed merely because it describes one.

## Revisit trigger and migration implications

Rich entity graphs or a measured maintenance burden justify a focused JPA comparison; a customer actually requires DB2; PostgreSQL maintenance policy requires an upgrade.

If superseded, retain this record and link the new decision, affected contracts/data, rollback approach and evidence.
