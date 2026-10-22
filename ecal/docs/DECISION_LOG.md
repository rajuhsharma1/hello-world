# Decision log

The attachment proposes an ambitious end-state. This log separates decisions needed for the **current runnable slice** from **future hypotheses**. Accepted does not mean production-certified; deferred does not mean implemented.

| ADR | Decision | Status / boundary |
|---|---|---|
| [ADR-001](adr/ADR-001-modular-monolith.md) | Modular monolith before microservices | Accepted — current Phase 1 |
| [ADR-002](adr/ADR-002-java-spring.md) | Java 21 and verified Spring Boot 4.1.1 | Accepted — current Phase 1 |
| [ADR-003](adr/ADR-003-postgres-jdbc.md) | PostgreSQL, Flyway and explicit Spring JDBC | Accepted — current Phase 1 |
| [ADR-004](adr/ADR-004-rest-contract.md) | REST and a checked-in OpenAPI baseline | Accepted — current Phase 1 |
| [ADR-005](adr/ADR-005-outbox.md) | Outbox when a durable external event is introduced | Deferred — Phase 4 design candidate |
| [ADR-006](adr/ADR-006-saga.md) | Explicit orchestration after transactional boundaries split | Deferred — Phase 4 design candidate |
| [ADR-007](adr/ADR-007-kafka.md) | Kafka as a later replay/streaming experiment | Deferred — Phase 4 option, not installed |
| [ADR-008](adr/ADR-008-database-ownership.md) | One database now, explicit ownership after extraction | Accepted locally; extraction policy deferred to Phase 3 |
| [ADR-009](adr/ADR-009-concurrency.md) | Database-atomic reservation and idempotent checkout claims | Accepted — current Phase 1 |
| [ADR-010](adr/ADR-010-cache.md) | Measure before adding Redis or Spring Cache | Deferred — Phase 2 experiment |
| [ADR-011](adr/ADR-011-frontend-state.md) | React local state for this vertical slice | Accepted — current Phase 1 |
| [ADR-012](adr/ADR-012-aws-deployment.md) | ECS/EKS comparison belongs to a reviewed later cloud design | Deferred — Phase 6, no resources provisioned |
| [ADR-013](adr/ADR-013-identity-boundary.md) | Fixed demo identity and loopback-only exposure | Accepted local restriction; authentication deferred to Phase 5 |
| [ADR-014](adr/ADR-014-reconciliation.md) | Separate safe request replay from remote-payment reconciliation | Accepted for HTTP replay; distributed policy deferred |

## How to review a decision

Ask for requirement, options, failure mode and evidence before accepting a technology name. Read [HLD](architecture/HLD.md) for the current system boundary, [LLD](architecture/LLD.md) for the transaction/SQL sequence and [traceability](TRACEABILITY_MATRIX.md) for the proof map. A material change should supersede an ADR, not silently erase the rationale.

## Official references for framework and database behavior

- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html) and [reference documentation](https://docs.spring.io/spring-boot/reference/). Check the pinned POM and actual build; current online documentation may describe a newer release.
- [Spring declarative transactions](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html): proxy boundaries, rollback and propagation.
- [PostgreSQL 17 READ COMMITTED](https://www.postgresql.org/docs/17/transaction-iso.html#XACT-READ-COMMITTED): statement snapshots and concurrent updates.
- [PostgreSQL INSERT / ON CONFLICT](https://www.postgresql.org/docs/17/sql-insert.html) and [constraints](https://www.postgresql.org/docs/17/ddl-constraints.html).
- [React state guidance](https://react.dev/learn/managing-state) and [useState](https://react.dev/reference/react/useState).
- [OpenAPI 3.1 specification](https://spec.openapis.org/oas/v3.1.0) and [RFC 9457 problem details](https://www.rfc-editor.org/rfc/rfc9457.html).
- [Testcontainers PostgreSQL module](https://java.testcontainers.org/modules/databases/postgres/): integration tests need a working container runtime.

Phase 2 accepted decisions: [ADR-015 keyset read models](adr/ADR-015-keyset-read-model.md), [ADR-016 bounded import](adr/ADR-016-bounded-import.md). [ADR-010](adr/ADR-010-cache.md) now records the measured reason to continue without an application cache.
