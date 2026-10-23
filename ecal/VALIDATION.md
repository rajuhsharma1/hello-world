# Observed validation — Phase 4 (with retained earlier baselines)

Latest Phase 4 validation: 23 October 2026 in the provided Linux cloud workspace. Earlier Phase 1/2 and Phase 3 observations are retained below. These are observations, not production readiness or achieved NFR/SLA targets.


## Phase 4 observations — 23 October 2026

The event-driven slice was built and exercised in this Linux cloud workspace. Earlier observations below are retained as historical evidence, not silently promoted to claims about the new topology.

| Check | Observed outcome | Practical limit |
|---|---|---|
| Distributed reactor `mvn verify` | **34 checks passed, none skipped**: 6 pure/architecture/structural contract checks, 18 synchronous HTTP/PostgreSQL scenarios, 10 event workflow scenarios. The final tombstone change was followed by another passing run of the 6 checks and 10 event scenarios | Disposable test databases use separate ordinary roles on one PostgreSQL 17.6 process; real Kafka 3.9.1 runs in its own Testcontainer |
| Durable acceptance and outbox | 202 acknowledgement precedes any reservation; duplicate POST returns the same order with one creation event; outbox insertion failure rolls back the order | The duplicate POST check is sequential; the after-trigger rule prevents losing inserts from publishing phantom rows by PostgreSQL semantics, not a claimed deterministic concurrent-event test |
| Delivery crash windows | Real broker acceptance followed by injected relay failure leaves the publish marker pending; re-publication produces one business effect. Consumer handler commit followed by injected acknowledgement failure replays safely | Consumer crash is injected exactly at acknowledgement, not an OS process-kill experiment; no Kafka/PostgreSQL exactly-once transaction claim |
| Autonomous recovery | Payment owner outage recovers to compensated cancellation without caller replay; Orders restart recovers committed acceptance; actual broker pause leaves an unpublished event and recovers after unpause | Schedulers are manually controlled in integration tests; the separate live exercise verifies continuously scheduled workers |
| Poison and ordering | Malformed JSON, unsupported schema and null Kafka tombstones are quarantined; a later compatible envelope succeeds. Newer reporting snapshot followed by an older/additive snapshot does not regress; handler failure rolls back inbox | Snapshot version handling is not a financial-delta ledger; no bulk replay UI or schema registry is claimed |
| Frontend | **17 React tests passed**; production TypeScript/Vite build passed in `events` mode and the web image built | Acceptance retains the operation identity; GET outage or mismatched polled snapshot cannot clear it |
| Browser | **4 Chromium scenarios passed** on final images: approval/decline/history, mobile keyboard/no overflow, lost-response/reload/same-key replay, page-close autonomous completion with eventual reports. Two scenarios intentionally skipped because they belong to monolith CSV import and the Phase 3-specific UI | Finite local browser coverage; no authenticated cross-user or load qualification |
| Live outage exercise | Payment and Reporting stopped; order accepted and observed in `PAYMENT_UNKNOWN`; Payment restored and GET-only observation reached `CONFIRMED`; exactly one order/reservation/payment and one stock decrement; Reporting caught up **through the public proxy URL** | Adds a real simulated order; other clients must be idle; recovery receipt saved before POST and retained |
| Runtime topology | All five application images built; **seven compact-stack containers healthy** with only browser loopback **8095** published. Full stack stopped/restarted with prior database/event data retained. Default, compact and IntelliJ/dev Compose configurations validate | Default eleven-container/four-PostgreSQL-instance topology is configuration-validated here; the cloud runtime uses four databases in one instance to fit disk. Hosted CI is configured but no remote CI outcome is asserted |
| Logical ownership and wire format | Runtime Orders/Inventory/Payment/Reporting roles are non-superusers; Inventory, Payment and Reporting lack CONNECT to Orders. Integration negative connection test passed. A broker record showed schema 1, ISO occurrence text and string money | Compact mode shares a physical process/failure domain; Kafka has no TLS/ACLs in this local lab |
| Broker restart/lag | Persistent Kafka restarted; both groups resumed retained offsets. CLI reported zero lag on populated partitions; unused partitions had log end 0 and no committed offset | Idle observation only; UI projection timestamp is explicitly not a broker lag metric or achieved SLO |
| Documentation/configuration | HLD, LLD, ADR 021, wire/API contracts, Windows/IntelliJ guide, traceability and configuration updated; local links resolve; shell syntax, YAML/config checks and `git diff --check` passed | Mentoring material documents tradeoffs and limits, not legal/employer/production certification |

Validation exposed and corrected three operational cases: temporary disk exhaustion from concurrent old stacks and build layers (old ECAL containers stopped with volumes retained; disposable cache removed), Nginx retaining an old Reporting container IP after stop/start (Docker DNS refresh plus public-route recovery assertion), and a workspace Docker client proxy returning 403 for loopback health checks (explicit proxy bypass). A null Kafka value initially could not fit the quarantine NOT NULL column; it now has an explicit tombstone marker and a passing regression check.

Phase 3 and monolith data volumes remain intact; their containers were stopped to fit the cloud disk budget. The Ark application's code, containers and data were not changed. No live dataset was automatically migrated. Authentication, exported OpenTelemetry spans, replicated Kafka durability, archival, production replay/capacity and cloud deployment remain outside this slice. The async 202 behavior requires its paired UI or an explicitly compatible client.

## Phase 3 observations — 23 October 2026

Same pinned Java 21/Spring Boot 4.1.1/PostgreSQL 17.6/Node 24 toolchain as the baseline; no unverified new framework version was introduced.

| Check | Observed outcome | Practical limit |
|---|---|---|
| Distributed reactor `mvn verify` | **24 tests passed, none skipped**: 2 pure workflow-state, 1 cross-service architecture, 3 structural OpenAPI and 18 real HTTP/PostgreSQL integration tests | Test applications share one disposable PostgreSQL process with three isolated service databases plus a real-schema legacy fixture; Compose physical isolation is checked separately |
| Service/effect boundaries | Three executable service artifacts, owner-specific migrations; architecture import guards and negative cross-owner DB connection test passed | Build uses a reactor/common HTTP support library; no domain entity/repository JAR is shared |
| Partial failure/reconciliation | Reservation/payment/release committed-but-unacknowledged recovery, actual payment read timeout, malformed/additive response cases, declined release failure, local final-commit failure and Orders restart passed | Simulated payment only; no autonomous background worker or real-provider reconciliation claim |
| Concurrency/state | Last-unit race, same-key race, changed downstream payload, release-before-reserve tombstone and stale-token fencing passed | Finite tested schedules, not an exactly-once transport or universal concurrency proof |
| Public/versioned reads | Validation, server price, buyer-scoped status, tied-timestamp order pages, catalog continuation and mismatched filter cursor checks passed | No performance/throughput qualification for the distributed topology |
| Offline migration | Actual Phase 2 Flyway schema exported; dry-run, partial-owner resume, receipt replay, full projected-row verification, checksum/drift rejection, quoted/multiline text safety and old-key replay passed | Isolated fixture only; no live monolith was migrated; no reverse backfill or host-loss restore qualification |
| CLI source guard | Export while source web/API were running was refused and no snapshot file was created | Local stopped-container guard assumes no external SQL writer |
| Correlation | Valid X-Correlation-ID/W3C trace identity and sampling flags propagated through both HTTP hops; UI support reference test passed | Correlated logs/context, not an OpenTelemetry exporter or tracing dashboard |
| Frontend | **14 component tests passed**; default and distributed production builds/typechecks passed | Mocked HTTP at component tier; compiled distributed mode tested separately through Nginx |
| Packaged distributed browser checks | **4 passed**, one monolith-only CSV case intentionally skipped: checkout/reload/decline, mobile/keyboard, lost browser-response replay, versioned API/three-service UI | Loopback Chromium and synthetic records; not comprehensive browser/accessibility certification |
| Live container-outage drill | Payment container stopped; POST returned 503 with PAYMENT_UNKNOWN, held stock, no payment. Restored Payment; exact-key recovery/replay produced one order/reservation/payment and one decrement | Run with other clients idle; recovery record retained in ignored `.runtime/` |
| Compose/runtime | All seven Phase 3 containers healthy; all three migrations applied, Nginx config passed; only loopback 8091 published and private service/DB host bindings absent | Three JVM/database overhead; environment limits are not production sizing |
| Preserved monolith regression | **34 backend tests passed, none skipped**; **4 monolith browser checks passed**, with the Phase 3-only case intentionally skipped | The monolith's existing database/volume remained intact at 8090; independent stacks use independent synthetic datasets |
| Docs/CI/config | HLD/LLD, four new ADRs, legacy-decision status updates, traceability, Windows/IntelliJ/migration guides, three versioned contracts and CI job updated; local links/YAML/Compose configs checked | Hosted CI outcome is not claimed from local runs |

The first fault test hit Inventory's cold classloading deadline before its intended Payment fault. A separate bounded same-key warm-up now ensures each fault reaches its intended dependency; actual read-timeout recovery remains tested explicitly. Resource-heavy image builds exhausted this workspace disk during initial database initialization. Unused build artifacts were cleared without deleting application data volumes, then initialization/startup completed. Neither incident is represented as a successful check before rerun.

[Phase 3 guide](docs/learning/PHASE-03.md), [HLD](docs/architecture/PHASE-03-HLD.md), [LLD](docs/architecture/PHASE-03-LLD.md), [migration ownership rules](docs/runbooks/PHASE-03-MIGRATION.md) and executable tests describe reproducible proof. Phase 3 has no public deployment, real payment/email, authentication, Kafka/outbox/relay, autonomous event execution, asynchronous reporting projection, cache or exported distributed traces. Phase 2 performance results are **not** promoted to a distributed latency/capacity claim. Ark services/data were preserved.

## Retained Phase 2 observations


All observations below were collected on 22 October 2026 in this workspace; hosted CI and production capacity are not implied.

| Check | Observed outcome | Practical limit |
|---|---|---|
| Backend `mvn verify` | **34 tests passed, none skipped**: 4 domain, 3 architecture, 2 OpenAPI, 2 import concurrency/uncertainty, 13 original checkout integration and 10 new PostgreSQL Phase 2 integration | Finite local interleavings; no external payment or distributed transaction |
| Import transactions/recovery | Valid batch commits; a later failing batch rolls back its earlier insert; corrected replay skips committed products; consumed stock stays reduced; changed import identity fails | Successful batches are not compensated; crash/commit ambiguity requires replay |
| CSV boundaries | Quoted commas/newlines, malformed UTF-8/unclosed quotes, duplicate IDs, 1000-row/256-KiB bounds, and actual multipart HTTP 413 handling passed | Loopback operator lab; no production upload/security certification |
| Worker/admission bounds | Latches exercise twenty batches, peak four worker calls, second file rejected 429; injected infrastructure failure reports UNKNOWN | Process-local admission, not a cluster-wide job scheduler |
| Read behavior | Keyset pagination visits all 54 fixture products once; equal-time UUID order, status cursor scope and server buyer filtering pass | Across-request pages are not a stable export snapshot |
| N+1 regression proof | Physical JDBC prepare instrumentation: catalog page sizes 1/20 each use two SELECTs, history one | JDBC proof; no claim of testing Hibernate behavior |
| Frontend | **11 component tests passed**; strict TypeScript and Vite build passed | HTTP mocked in component tier |
| Packaged browser journey | **4 Playwright tests passed** through built Nginx/Java/PostgreSQL: original checkout/reload/decline, mobile/keyboard, lost-response replay, and native CSV upload/paging/replay/filter/sample download | Uses synthetic application data; not a full accessibility or capacity audit |
| Images/runtime | Both multi-stage images built; ECAL db/api/web healthy; Nginx config test and functional gateway smoke passed; existing ECAL orders retained through additive V4/V5 migration | Trusted corporate CA/proxy override remained outside source; only loopback 8090 published |
| Query experiment | Four completed runs, 200000 orders, 600 measured requests/run, eight clients; p95 baseline 37.01–37.51 ms versus indexed 11.49–12.32 ms; zero HTTP errors | Deliberately selective warm-cache read experiment; no checkout throughput/NFR qualification |
| Plan/storage evidence | Baseline scans and filters 19980 rows, 701 buffer blocks; indexed plan touches four blocks with zero heap fetches; new covering index adds ~32.2 MiB on fixture | VACUUM visibility and real write mix affect plans/storage/cost |
| Documentation/contracts | HLD/LLD, ADR-015/016, cache decision, traceability, OpenAPI, Windows guide and benchmark artifacts updated; local Markdown links and Compose config checked | Discovery assumptions remain reviewable hypotheses |

[Performance report and raw artifacts](tests/performance/REPORT.md) record workload, percentiles, CPU/DB diagnostics and limitations. Import uses Apache Commons CSV 1.14.1. Cache remains deferred on the strength of the index experiment.

Phase 2 incidents: the first combined image build exhausted disk; it was stopped and unused build artifacts cleared without deleting application volumes. Sequential builds completed. A browser check caught a sample CSV download returning 403 because public files inherited a restrictive workspace umask; the web image now grants read/traverse access to its public assets, and all four browser tests passed. A jsdom required-file validity limitation was isolated in the component test; native submit/upload was verified in Chromium.

Current unimplemented work includes service extraction, Kafka/outbox/Saga, authentication, a separate asynchronous read store, application caching, distributed tracing, cloud deployment and backup/restore. This phase's synchronous SQL projection is not an event-driven CQRS implementation. Existing Ark services/data were preserved. No public deployment, paid resource or external message was sent.

## Retained Phase 1 baseline

The observations below describe the earlier Phase 1 delivery, before the Phase 2 additions above. In particular, its smaller test counts and lack of a benchmark are historical.

## Environment and verified versions

- Host Java: full JDK 21.0.12.1; Maven 3.9.9; Node 24.19.0; Docker Engine 28.4.0; Compose 2.40.3.
- Backend: Spring Boot 4.1.1 (release metadata/artifacts verified, then actually compiled and run), PostgreSQL 17.6, Testcontainers 2.0.5, ArchUnit 1.5.1.
- Frontend: React/React DOM 19.3.1, TypeScript 7.0.2, Vite 8.3.5, Vitest 4.1.12, React Testing Library 16.3.3, Playwright 1.64.1. Exact dependencies and integrity hashes are in the lockfile.
- Vitest 5.0.5 was initially evaluated but conflicted with current jest-dom assertion typings; verified Vitest 4.1.12 supports Vite 8 and passed strict compilation. No `skipLibCheck` or disabled assertions were used.

## Completed checks

| Check | Observed outcome | Practical limit |
|---|---|---|
| Backend `mvn verify` | **22 tests passed, none skipped**: 4 domain, 3 architecture, 2 structural OpenAPI and 13 real PostgreSQL integration tests | Finite tested interleavings, not a formal proof of all concurrent executions |
| Stock contention | Eight simultaneous HTTP checkouts for one unit: one accepted order, seven stock conflicts, stock zero, one payment effect | Single-product orders; no multi-row lock ordering exercise yet |
| Duplicate-key race | Eight concurrent requests with the same buyer/key/payload: one 201, seven 200, identical response JSON, one order/payment/notification | Local DB effects, not exactly-once distributed transport |
| Rollback/decline | Decline saves CANCELLED/DECLINED and releases stock; late notification exception rolls back all effects and exact-key retry succeeds | Payment/notification are local SQL simulations, not external providers |
| HTTP/contract | Unknown client price and invalid inputs rejected; stable problem details; framework 404/405/415 remain client errors; decimal/record contract guards pass | Structural schema checks plus focused HTTP assertions, not complete OpenAPI conformance certification |
| Database constraints | Negative stock and missing terminal payment are rejected; V3 migration closes PostgreSQL CHECK/NULL ambiguity | Does not substitute for the application state machine or operational migration review |
| Frontend component suite | **8 Vitest/React Testing Library tests passed** | Mocked HTTP at this tier; live behavior is checked separately |
| TypeScript/Vite | Strict typecheck and production build passed | No browser support certification or bundle performance target claimed |
| Live development E2E | **3 Playwright tests passed** against the real backend/database | Creates synthetic orders; not isolated from unrelated manual mutations during the race/replay case |
| Docker images/startup | Both normal multi-stage images built; `make up` completed with db/api/web healthy, only loopback 8090 published | Cloud-specific trusted CA/proxy override was needed; no public deployment was made |
| Persistence across startup modes | An order created through host development was retrieved unchanged after stopping that process and starting the Docker app against the retained ECAL volume | Container/volume retention, not a tested backup restore or host-loss recovery |
| Production-gateway browser E2E | **3 Playwright tests passed** against the built Nginx/Java/PostgreSQL stack: approve/decline+reload, mobile/keyboard, committed-response-loss replay | Same test scenarios as development mode, now exercising production assets/proxy/CSP |
| Gateway smoke | Catalog, persisted checkout/status/history, exact replay and conflicting-key rejection passed | Consumes a small amount of synthetic demo stock |
| Documents | Requirements, current/future diagrams, ADRs, traceability and learning guides reviewed; relative links and YAML parsed | Assumptions/NFR hypotheses still require stakeholder review |

Desktop/mobile screenshots of the live application were inspected. A populated-history mobile overflow was found and fixed by positioning the table scroll container; the page remained 390px wide at a 390px viewport. The same three browser checks also passed against the Docker production build.

## Setup incidents resolved

- Default npm cache was not writable; a workspace-owned cache was used, preserving frozen `npm ci` installs.
- Spring transactional adapters were made non-final so class-based proxies can apply advice; the integration suite verifies `MANDATORY` transaction enforcement.
- Test fault injection unwraps the advised notification spy before stubbing; otherwise configuring it invokes transaction advice outside checkout.
- Docker package downloads needed the same supported proxy route and trusted CA used by Maven. The override stays outside source; TLS verification remained enabled.
- The constrained cloud disk filled during image compilation, causing one disposable PostgreSQL test startup to fail before tests ran. Unused build cache was cleared without deleting application volumes, and final backend tests were rerun successfully with free space. Resource-heavy Docker compilation and database initialization were then sequenced.
- Package refresh and a browser invocation briefly overlapped; the latter did not execute tests. The browser suite was rerun after installation completed. Only completed runs are counted.

## Deliberately unimplemented / unmeasured

No real payment/email, authentication or buyer ownership isolation, Kafka/outbox/relay, Saga, CQRS projection, Redis cache, distributed trace/dashboard, cloud infrastructure, load benchmark, production security review or backup/restore drill is claimed. NFR availability, latency and RPO/RTO numbers remain design hypotheses in the quality scenarios. CI has been added; a hosted CI result is not claimed from a local run.

The original Ark stack and its databases were preserved. ECAL uses a separate Compose project, persistent volume and port. No paid resources or third-party messaging were provisioned.
