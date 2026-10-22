# Observed validation — Phase 2 (with retained Phase 1 baseline)

Validated on 22 October 2026 in the provided Linux cloud workspace. This records observations, not production readiness or achieved NFR/SLA targets.

## Phase 2 observations

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
