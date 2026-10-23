# ADR-017: Extract Orders, Inventory and simulated Payment with owned persistence

**Status:** Accepted — Phase 3 learning stack; ADR-001 remains the monolith baseline.

## Context and decision

The monolith proves local invariants. Phase 3's learning objective is independently deployable boundaries and observable partial failure. Build three Spring Boot applications with separate executable artifacts, migrations, PostgreSQL instances/volumes and credentials. Orders retains Catalog and local notification records. Inventory owns stock/reservation ledger; Payment owns immutable simulation outcomes. Integrate through versioned HTTP, independent wire DTOs and checked-in OpenAPI. Shared support contains no domain entities, SQL repositories or service interfaces. A service cannot import another service's implementation.

## Alternatives and tradeoffs

Retaining the monolith remains the simpler deployment; there is no measured business need to scale these artificial services independently. Separate schemas on one shared application connection would not demonstrate the intended ownership boundary. One database process with separate roles is a valid cost compromise and is used in tests, while full Compose uses separate instances to make lifecycle/ownership visible. A shared domain-model JAR would couple release/schema changes; contracts with additive tolerant responses are chosen instead.

This costs additional JVMs, databases, connections, health checks, latency and coordination. Cross-database foreign keys disappear. New online catalog creation/import would require its own cross-owner protocol, so the old transactional CSV import stays in the monolith. No automatic migration or dual-write path is hidden in startup.

## Failures, proof and rollback

An owner can be unavailable while others remain healthy. The durable workflow in [ADR-018](ADR-018-durable-http-workflow.md) handles uncertainty; a JDBC rollback cannot erase another service's commit. ArchUnit, independent database-role negative tests, real HTTP/contract tests, Docker startup and browser checks provide proof locations. Actual observations are in [VALIDATION](../../VALIDATION.md), not inferred from diagrams.

Keep Phase 2 runnable. Ownership migration and data rollback constraints are in [ADR-020](ADR-020-offline-ownership-cutover.md). Revisit service boundaries only with independent ownership/lifecycle or workload evidence; do not equate a bounded context with one mandatory deployment.
