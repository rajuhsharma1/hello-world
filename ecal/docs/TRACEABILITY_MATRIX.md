# Requirement → decision → code → proof

This matrix maps the **current slice**, not the whole future blueprint. Test descriptions below identify executable proof locations; actual pass/fail outcomes belong in the project validation report. Review requirements in [PRODUCT_AND_REQUIREMENTS](requirements/PRODUCT_AND_REQUIREMENTS.md), quality scenarios in [QUALITY_SCENARIOS](requirements/QUALITY_SCENARIOS.md), and risks in [THREATS_AND_RISKS](requirements/THREATS_AND_RISKS.md).

## Current code and tests

All backend source paths below start at `backend/modular-monolith/src/main/java/com/ecal/`. The test source root is `backend/modular-monolith/src/test/java/com/ecal/`.

| Requirement / scenario | Decision | Implementation authority | Automated proof |
|---|---|---|---|
| BR-01, FR-01, US-01 — product browsing | ADR-003/004 | `catalog/internal/CatalogController`, `JdbcCatalog`; `inventory/api/Inventory` | `CheckoutIntegrationTestIT.missingResourcesReturn404AndCatalogContractUsesDecimalStrings`; React loading/catalog tests; Playwright checkout journey |
| FR-02, FR-03, T-01/T-05 — validate input and server price | ADR-003/004/009 | `OrderController`, `CheckoutService`, `Order`, strict unknown-property JSON config, Flyway constraints | `invalidInputsAndClientPriceAreProblemDetailsWithoutEffects`; `approvedOrderPersistsExactServerPriceAndIsVisibleInStatusAndHistory`; `OrderTest.monetaryTotalIsExactAndIndependentOfBinaryFloatingPoint` |
| FR-04, FR-11, US-01/05 — durable status/history | ADR-001/003/004 | `order/application/OrderView`, `JdbcOrderStore.byId/recent`, `OrderController` | Approved-order read/history integration test; Playwright confirms then reloads persisted history |
| BR-03, FR-05, QA-02 — replay without repeated effects | ADR-009/014 | `CheckoutService.replay`, `JdbcOrderStore.claim/byKey`, composite unique constraint | `exactReplayIsIdenticalIncludingTimestampAndHasNoRepeatedEffects`; `concurrentSameKeyHasOneCommitAndSevenExactReplays` assert one order/payment/notification |
| FR-06 — conflicting payload is rejected | ADR-009 | Product/quantity/requested outcome comparison in `CheckoutService.replay` | `changedPayloadUnderAnExistingKeyIsRejected`; original stock/payment effect unchanged |
| BR-02, FR-07, QA-01 — no overselling | ADR-009 | `inventory/internal/JdbcInventory.reserve`; `inventory_stock.available >= 0` | `concurrentDistinctOrdersCannotOversellTheLastUnit` uses a start barrier and separate HTTP transactions |
| FR-08 — rejected stock command leaves no partial order | ADR-001/009 | Transactional checkout plus zero-row conditional update conflict | `insufficientStockRollsBackClaimAndAllowsTheSameKeyAfterReplenishment` |
| FR-09 — legal aggregate state machine | ADR-001 | Framework-free `order/domain/Order` | `OrderTest.confirmedOrderRequiresReservationAndCannotBeCancelledLater`; `declinedPaymentHasExplicitCancelledTerminalState`; quantity/price invariant test |
| FR-09, QA-03 — terminal rows cannot persist a null payment result | ADR-003/009 | V3 terminal-payment database guard, in addition to domain state rules; SQL CHECK must explicitly reject NULL | Real PostgreSQL constraint assertion in `CheckoutIntegrationTestIT`; direct SQL cannot bypass this terminal invariant |
| BR-04, FR-10/14, QA-03 — decline is persisted, stock released | ADR-001/014 | `CheckoutService` decline branch, `LocalPaymentSimulator`, `RecordingNotifications` | `declinedPaymentReleasesReservationAndReplaysItsCancellation`; UI decline test; Playwright decline journey |
| QA-03 — exceptional failure rolls back all local effects | ADR-001/003 | One `@Transactional` service and transaction-required mutation adapters | `notificationFailureRollsBackEvenTheSimulatedPaymentAndStock`; `inventoryCannotBeMutatedOutsideTheCheckoutTransaction` |
| FR-12, QA-11, T-08 — controlled problem details | ADR-004 | `shared/ApiErrors`, `BusinessException`, safe server-error config | Validation/missing-resource/notification-failure HTTP integration tests; `unsupportedHttpRequestsRemainClientErrors` verifies framework 404/405/415 stay client errors; frontend error tests |
| FR-13, US-02, RISK-04 — uncertain submission keeps identity | ADR-011/014 | `frontend/src/api.ts` pending command/session storage and `App.tsx` locked retry state | RTL tests for lost response, session restoration, malformed 2xx, proxy timeout and server500; exact pending key/payload asserted |
| BR-05, QA-10 — maintainable boundaries | ADR-001/003/008 | API/internal package division and pure domain | `ArchitectureTest.aggregateDoesNotDependOnFrameworksOrPersistence`, `otherModulesCannotReachAnInternalAdapter`, `domainModulesDoNotFormDependencyCycles` |
| BR-06, QA-08, RISK-01 — isolated local operation | ADR-013 | Independent ECAL Compose project; loopback host bindings; fixed demo identity | Configuration inspection, executable startup and tests; not a claim of authenticated user isolation |
| QA-12 — accessible narrow-screen journey | ADR-011 | Labels, text rendering, status/error announcements and responsive UI | RTL form/status tests; Playwright mobile width and keyboard selection checks; no full WCAG audit implied |

## Current contracts and schema

- [OpenAPI](../contracts/openapi/commerce.yaml) is the reviewable HTTP baseline. `OpenApiContractTest.publishedSchemasMatchTheActualHttpRecords` compares schema field sets with actual request/response record components, and `publishedMoneyPatternAcceptsExactDecimalsAndRejectsFloatingFormats` guards the money pattern. This is **structural contract drift detection**, not a full OpenAPI validator or proof that every runtime value satisfies every schema rule. HTTP integration tests separately exercise actual statuses/bodies/errors.
- [V1 schema](../backend/modular-monolith/src/main/resources/db/migration/V1__commerce_slice.sql) starts the physical schema; V3 adds the terminal/non-null payment guard without rewriting applied V1 history. [V2 seed](../backend/modular-monolith/src/main/resources/db/migration/V2__demo_catalog.sql) supplies synthetic products.
- [Backend test directory](../backend/modular-monolith/src/test/java/com/ecal/) contains the domain, architecture, structural OpenAPI and real-database cases above.
- [Frontend test file](../frontend/src/App.test.tsx) covers the browser state machine; [E2E](../tests/e2e/checkout.spec.ts) crosses the real UI/API/persistence path.

## Deliberately unproven future claims

| Future claim | Current status | Required future proof |
|---|---|---|
| Kafka/outbox durability, deduplication, poison-message recovery | Not implemented; ADR-005/007 deferred | Broker/relay/consumer crash and replay drills |
| Distributed Saga / unknown payment reconciliation | Not implemented; current simulator is transactional and local | Independent-process partial-success and reconciliation tests |
| CQRS projection freshness | No separate projection or eventual-consistency UI | Lag measurement, duplicate/version handling and catch-up tests |
| Redis/cache performance | No cache or measured bottleneck | Same-workload before/after latency and invalidation/failure tests |
| Buyer/admin identity and cross-user security | Fixed demo buyer, loopback only | Authenticated principals, object-level denial, session/CSRF tests |
| p95/availability/RPO/RTO/cloud cost targets | Design hypotheses, not results | Hardware/workload benchmark, monitoring window, timed restore and dated provider assumptions |
| Multi-AZ/ECS/EKS recovery | No deployment | Reviewed architecture and actual rehearsal with observed outcomes |

A requirement is not “done” because an ADR mentions it. Update this map when scope or evidence changes; preserve the previous decision when it is superseded.

## Phase 2 extensions

| Learning requirement | Decision | Code | Executable proof / observed evidence |
|---|---|---|---|
| Bounded pagination and selective filtering | ADR-015 | `PageCursor`, `OrderHistoryQuery`, `CatalogController.page`, V4 indexes | PhaseTwoIntegrationTestIT keyset/tie/filter/buyer tests; frontend cursor/filter tests |
| N+1 and projection reasoning | ADR-003/015 | JDBC projection + bulk inventory availability | Physical SELECT counter: catalog page sizes 1 and 20 use two queries; history one |
| Measured SQL optimization | ADR-010/015 | V4 status covering index; `tests/performance/benchmark.py` | Same endpoint/dataset/hardware baseline/indexed two-round raw latencies and EXPLAIN in `tests/performance/observed-2026-10-22` |
| Bounded concurrent import and transaction policy | ADR-016 | `catalog/internal/importing`, Inventory.initializeStock, V5 | Latch-driven four-worker/admission test; PostgreSQL batch rollback, partial recovery and stock-preserving replay tests |
| UI/operator outcomes and contracts | ADR-004/011/016 | `CatalogImport`, cursor controls, OpenAPI | RTL partial outcome/filter/page tests; structural DTO guards; browser upload/replay |

These are Phase 2 learning extensions, not newly attributed employer business requirements. Their actual pass/fail observations are in `ecal/VALIDATION.md`.

## Phase 3 extensions and changed semantics

| Requirement / learning scenario | Decision | Implementation | Proof location |
|---|---|---|---|
| Independent service/data ownership | ADR-017 | Three service modules/migration locations and Compose databases | ArchUnit BoundariesTest; credential-isolation integration test; Compose port inspection |
| Versioned service contracts | ADR-019 | phase3-orders/inventory/payment OpenAPI; independent remote DTOs | ContractsTest structural guards plus real HTTP validation/replay/additive/malformed cases |
| No duplicate stock/payment effects under replay | ADR-018 | Reservation/payment ledgers; unique buyer/key and token-fenced order steps | Same-key race, last-unit race, release tombstone and changed-payload tests |
| Durable uncertainty and compensation | ADR-018 | PAYMENT_UNKNOWN/RELEASE_PENDING, no HTTP inside SQL transactions | Lost acknowledgements, payment timeout, local final-commit failure, failed compensation and Orders restart |
| Stable original order snapshots/history | ADR-017/018 | Orders-owned catalog/snapshots; keyset history; nullable pending payment status | Read/buyer-scope integration tests; UI paused result test; checkout/reload browser journey |
| Correlation across service hops | ADR-019 | RequestContext, structured logs, support reference | W3C/correlation integration test; UI reference test; live logs |
| Backfill/cutover/rollback ownership | ADR-020 | phase3_migrate.py export/preflight/copy/receipt/full-row verification | Real Phase 2 schema -> three owners, partial-copy resume, checksum/drift guards, original-key replay |
| Real outage and caller recovery | ADR-018/019 | phase3_failure_lab.py | Payment container stop/start, persisted unknown state, same-key replay, private DB effect counts |

In distributed mode, an insufficient-stock attempt is a persisted REJECTED order, unlike FR-08's rolled-back monolith claim. This semantic change is explicit in ADR-018 and the Phase 3 contract. Phase 2 CSV remains monolith-only; no cross-database atomic import is claimed. Phase 3's synchronous read projection is not an asynchronous CQRS store. Observed outcomes belong in `ecal/VALIDATION.md`.

## Phase 4 additions

| Goal | Decision/code | Proof |
|---|---|---|
| Business commit and event cannot diverge | ADR 021; V2 outbox + owner V3 after-triggers | EventWorkflowIT outbox rollback and duplicate POST/event checks |
| Broker delivery survives crash | EventPlatform.Relay + persisted publish marker | actual Kafka relay-ack crash and broker pause/recovery |
| Consumers tolerate re-delivery | EventInbox local transaction + manual ack | commit-before-ack injection and duplicate Kafka publication |
| Autonomous execution and compensation | AsyncSaga durable work + Checkout fenced HTTP coordinator | payment outage, Orders restart; no client replay |
| Poison and incompatible schemas remain inspectable | EventInbox quarantine | malformed/schema tests followed by successful next record |
| Eventual reporting cannot regress | Reports snapshot version CAS + separate credentials | newer-then-older test, negative DB access, live UI/report checks |
| UI distinguishes acceptance from completion | events mode, validated polling and retained operation key | React acceptance/read-outage tests and Phase 4 browser journey |
