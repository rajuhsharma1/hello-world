# Phase 2 — database performance and bounded concurrency

This phase extends the working Phase 1 checkout. Read the [HLD](../architecture/HLD.md), [LLD](../architecture/LLD.md), [read/index ADR](../adr/ADR-015-keyset-read-model.md), [import ADR](../adr/ADR-016-bounded-import.md), and [measured experiment](../../tests/performance/REPORT.md). The uploaded Bebo documents guide the exercise; they do not establish employer requirements. No services, Kafka or Redis have been added in this phase.

## What to run and observe

From your repository root, select `feature/ecal-phase-2`, then enter `ecal`. In Windows PowerShell, Docker Desktop must be running with Linux containers:

```powershell
git fetch origin
git switch feature/ecal-phase-2
cd ecal
docker compose -f compose.yaml up --build --wait --wait-timeout 180
```

Open `http://localhost:8090`. Existing lab orders survive the upgrade; Flyway adds V4 indexes and V5 import identities. Do not use `down -v`. Ark remains a separate application.

1. Place an approved and declined order. Filter history by outcome. Refresh resets history to page one, preserving the chosen filter.
2. Download the example CSV under **Catalog operations**, then upload it. Two products are created. Upload it again: both are skipped.
3. Buy one of those imported products, then replay the file. Its remaining stock stays reduced.
4. To see pagination, create a CSV with more than twenty different product UUIDs. Use Previous/Next products. History uses Newer/Older controls; there are no invented total counts.
5. To demonstrate a partial import, create 52 unique rows, with a negative price in row 52. The first batch of 50 commits. The second batch's two rows roll back. Correct the negative price and replay: 50 skip and two commit. A structural problem such as duplicate IDs rejects the entire file before any writes.

The [example CSV](../../frontend/public/examples/catalog-import.csv) is UTF-8 RFC4180, with exact headers `productId,name,description,price,currency,stock`. Limits: 256 KiB, 1000 rows; name 120 characters and nonblank; description 500; positive price fitting numeric(12,2) without rounding; currency INR; initial stock 0–1000000. Product IDs are stable import identities. Existing Phase 1 seed IDs cannot be overwritten through import. This is create-only; it is not a product editing or stock replenishment API.

## Why this design, explained at architect depth

**SQL before more infrastructure.** The existing buyer/time index orders history efficiently but cannot directly select a rare outcome. The experiment deliberately uses 0.1% cancellations: PostgreSQL walks roughly twenty thousand recent orders to obtain twenty-one cancellations. A composite `(buyer_id,status,created_at DESC,id DESC)` index first locates the buyer/status prefix and then walks the desired order. INCLUDE columns cover the response. The observed index-only plan has zero heap fetches after VACUUM. Visibility-map coverage can deteriorate under writes; “covering” does not guarantee a heap-free plan forever. Index storage and writes cost more; the report quantifies storage, but does not claim a measured checkout-write cost.

**Keyset instead of OFFSET.** The cursor carries the last `(created_at,id)` or `(name,id)` pair. UUID is a unique tie-breaker when timestamps/names match. SQL seeks beyond that tuple, fetches `limit+1`, returns at most `limit`, and only emits a next cursor if an extra row exists. A large OFFSET still walks and discards earlier rows. Keyset pagination cannot jump to an arbitrary numbered page and does not calculate an expensive total. The browser keeps a cursor stack to go backward. Each HTTP request has its own read view: inserts and renames can change later pages; this is not an export snapshot. Catalog stock is always an advisory read; checkout still makes the conditional reservation.

**Projection instead of aggregate hydration.** `OrderHistoryQuery` selects only response columns, directly into `OrderView`. It does not replay state transitions just to display history. This is a synchronous read projection against the primary database, not a separate eventually consistent CQRS store. Buyer ownership is still enforced by a server-selected SQL parameter, never by the cursor. Cursors are versioned and scoped to endpoint/status; base64 encoding provides neither authentication nor secrecy.

**JDBC instead of JPA.** This application already chose explicit JDBC (ADR-003). Phase 2 keeps SQL and affected-row semantics visible and avoids introducing a second persistence model. JPA could be appropriate for richer entity graph editing; lazy association traversal can create N+1 queries, and `JOIN FETCH` of collections combined with pagination needs care. DTO projections, entity graphs, or batch fetching each have different cardinality and query costs. We do not claim to fix Hibernate N+1 in an application without Hibernate. Instead the PostgreSQL integration test instruments physical JDBC statement preparation: catalog pages of 1 and 20 use two reads (products plus bulk stock), while history uses one. SQL count stays independent of row count.

**Bounded threads instead of parallelStream.** Four fixed workers and a queue of sixteen accept at most twenty batches (1000 / 50). One in-flight file is admitted per JVM; another gets 429. `parallelStream` shares a common pool with unrelated work; an unbounded executor queue can turn load into memory growth. Each worker calls a separate Spring bean through its transaction proxy. A `REQUIRES_NEW`, READ_COMMITTED, ten-second transaction covers catalog + initial inventory + import fingerprint for one batch. Spring transaction context is thread-bound; annotating the coordinator would not propagate a transaction into workers. The coordinator waits for all outcomes in input batch order, so this is synchronous HTTP, not a durable background job.

**Replay identity instead of blindly upserting stock.** A SHA-256 fingerprint stores normalized original import fields separately from current inventory. Same ID plus same original fields skips; same ID with different data, or a seed ID with no import fingerprint, fails that batch. The database primary key arbitrates simultaneous inserts. Rollback includes fingerprint, catalog, and stock. Existing orders and stock changes are never overwritten by import replay. The file is structurally validated before scheduling, but business-field validation occurs within each batch to make the partial-commit policy explicit and testable. Imported UUIDs and per-batch outcomes are the recovery mechanism; there is no job ledger to consult after a crash.

**No Redis yet.** The measured SQL index change solves the demonstrated read bottleneck without cache staleness, invalidation, stampede or another service. That is a decision supported by evidence, not proof that caching is never useful. Revisit when a representative profile shows residual read pressure, an acceptable freshness bound, and expected gains that justify operations cost.

## Failure and recovery exercises

| Failure | Durable state | Recovery |
|---|---|---|
| Bad header, duplicate UUID, invalid UTF-8, too many rows | No writes begin | Correct the file and upload |
| Invalid price/stock or existing-ID conflict in a batch | That batch rolls back; successful batches remain | Correct failed data and replay; retain successful IDs and original fields |
| Lost HTTP response, gateway timeout, process crash | Some batches may already be committed | Replay original file; identity checks skip committed rows |
| Another file is running | New request receives 429 | Wait for the current request, then retry |
| Slow locks/DB outage | Failing batch rolls back; other results may differ | Inspect per-batch result; restore DB and replay original file |
| Cursor reused with another filter or malformed cursor | 400; no mutation | Restart from page one |

Batch failures log batch index and exception type, without CSV contents or SQL. There is no compensation for committed batches. Per-process admission is not a cluster-wide lock. Graceful shutdown waits a bounded time; a killed process cannot return complete outcomes. Nginx and browser allow 120 seconds for import; finite per-batch database timeouts do not make end-to-end completion certain. Replaying the file resolves that uncertainty.

## How to prove it

From `ecal/`, with Java 21, Maven 3.9+, Node 24 and Docker:

```powershell
mvn -f backend/modular-monolith/pom.xml verify
npm.cmd ci --prefix frontend
npm.cmd test --prefix frontend
npm.cmd run build --prefix frontend
# With the Docker app running, and Playwright Chromium installed:
$env:ECAL_WEB_URL="http://127.0.0.1:8090"
npm.cmd run test:e2e --prefix frontend
```

On macOS/Linux use `npm` in place of `npm.cmd`; `make verify` and `make e2e` are shortcuts. See the README for browser installation. Testcontainers uses disposable databases and does not reset application data. Import concurrency tests use latches to prove four workers and reject a second file; database tests prove real rollback, replay, cursor ordering, filtering, isolation and query count. Browser tests cover native file upload and navigation. [VALIDATION](../../VALIDATION.md) distinguishes tests that exist from actual observed runs.

Run the standalone [benchmark](../../tests/performance/README.md) to reproduce the experiment. Never drop the index on your working application database to imitate the baseline; the harness creates its own database and cleans up its own container.

## Interview rehearsal

- Explain the equality-prefix/range/order relationship in the composite index. Why retain the existing buyer/time index for ALL status?
- Why can INCLUDE reduce heap I/O, and why can an index-only scan still fetch heap tuples?
- What changes if the cancellation rate becomes 50%, the buyer distribution is skewed, or writes dominate?
- Why can READ COMMITTED plus conditional UPDATE prevent overselling without SERIALIZABLE isolation? What anomaly remains for a multi-page read?
- Why is a transaction on a method called through `this` insufficient? Why don't request-thread transactions cross executor threads?
- What exactly does a partial import acknowledge? How does an HTTP timeout differ from a rolled-back transaction?
- Would changing successful imported rows on retry be safe? How would a real edit/replenishment API need its own version and audit policy?
- What evidence would justify caching or extracting a service? Describe the extra failure modes before naming another technology.

Next is Phase 3: service extraction and failure boundaries. The Phase 1 branch remains the runnable transactional baseline. Do not present Phase 3 events/security/cloud designs as delivered behavior.

Infrastructure exceptions can occur around commit, so such batches report `UNKNOWN`, not a promise of rollback. `failedBatches` counts both ROLLED_BACK and UNKNOWN; created/skipped counts include only acknowledged committed batches. Replay the same file to resolve uncertainty. A unit test injects a connection-loss exception to guard this distinction.
