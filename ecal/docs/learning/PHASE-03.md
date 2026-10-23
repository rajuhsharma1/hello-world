# Phase 3 — service extraction without pretending HTTP is ACID

This phase extracts Orders, Inventory and simulated Payment into independently packaged Spring Boot services and databases. Read the [HLD](../architecture/PHASE-03-HLD.md), [LLD](../architecture/PHASE-03-LLD.md), [ADRs](../DECISION_LOG.md), [contracts](../../contracts/openapi/phase3-orders.yaml), and [migration runbook](../runbooks/PHASE-03-MIGRATION.md). The monolith remains runnable for comparison. No real money/email, public deployment or paid infrastructure is involved.

## Start on Windows without Make

Install/start Docker Desktop with Linux containers. From your cloned `hello-world` repository root:

```powershell
git fetch origin
git switch feature/ecal-phase-3
cd ecal
docker compose -f compose.phase3.yaml up --build --wait --wait-timeout 180
```

Open **http://localhost:8091**. The new lab starts with its own catalog/stock, not your old orders. Only the browser gateway publishes a loopback port; internal services/databases stay private. Existing Phase 2 at 8090 and Ark remain separate. First build needs downloads and more memory/disk than the monolith: three JVMs and three PostgreSQL instances are deliberate overhead, not evidence that service extraction improves efficiency.

```powershell
docker compose -f compose.phase3.yaml ps
docker compose -f compose.phase3.yaml logs --tail=80 api inventory payment
docker compose -f compose.phase3.yaml down
```

Normal down keeps all three data volumes. Do not add `-v`. GNU Make/Bash users may use `make phase3-up`, `phase3-down`, `phase3-logs`, `phase3-verify`, `phase3-e2e` and `phase3-failure-lab`. Existing `make up` still means the monolith; explicit names prevent accidental ownership switches.

## Exercise the working journey

Place approved and declined orders, reload history and view their snapshots. Decline only displays CANCELLED after Inventory confirms release. Use the same pending checkout button after uncertainty: product/quantity/outcome remain frozen. An intermediate history row can be PENDING, RESERVED, PAYMENT_UNKNOWN or RELEASE_PENDING; viewing it is read-only and never labels it a payment decline.

Run a real service-outage drill with other clients idle (Python 3.10+ and Docker required):

```powershell
python scripts/phase3_failure_lab.py
```

The drill saves an exact retry identity in `.runtime/`, stops only this lab's Payment container, submits a synthetic order, verifies 503/PAYMENT_UNKNOWN with held stock and no payment, restarts Payment, and replays the same key. It proves one order/reservation/payment and one stock decrement. Cleanup restores Payment even on failure. If the script exits before acknowledgement, use the printed file: `python scripts/phase3_failure_lab.py --resume .runtime/<printed-file>.json`. Keep the original file; do not generate another key to resolve uncertainty.

This live drill complements deterministic test proxies, which can discard an acknowledgement **after** a remote commit, return malformed 2xx, delay beyond a read deadline, or fail release. Those test-only endpoints/proxies are not part of service images.

## What, why, alternatives, tradeoffs, failures and proof

| Choice | Why here | Alternative / cost | Failure behavior / proof |
|---|---|---|---|
| Three services + independently owned PostgreSQL stores | Makes ownership and partial failure real after the local baseline | More JVMs, connections, storage, deployments and recovery work; monolith remains simpler | Credentials isolate test databases; ArchUnit blocks implementation imports; Compose uses separate instances |
| JDBC/Flyway/Java 21/Spring Boot 4.1.1 | Reuse pinned, already working SQL/concurrency stack while changing the deployment boundary | JPA could map richer entities; introducing it now would mix two experiments | Existing unique/conditional SQL semantics remain explicit; real PostgreSQL tests |
| Versioned HTTP + independent wire DTOs | Synchronous boundary is inspectable before adding messaging | Shared domain JAR couples releases; Kafka adds delivery/replay concerns now | Record/schema guards, real HTTP validation, additive-field and malformed-ACK tests |
| Persisted synchronous coordinator | Makes each acknowledged/uncertain step and compensation visible | Event-driven/background execution is Phase 4; caller retry currently drives recovery | Lost reservation/payment/release acknowledgements and Orders restart recover by original identity |
| Immutable reservation/payment ledgers | A retry is the same operation even after a timeout or JVM restart | In-memory maps lose identities; upserts can duplicate/overwrite effects | Same-key races, changed payload conflicts, last-unit race and release tombstone tests |
| Token-fenced lease, short local transactions | Reduce competing attempts without holding row locks/connections over HTTP | Holding a SQL transaction across HTTP ties availability to lock duration; a lease alone cannot suppress remote duplicates | Expiry/fencing test plus downstream ledger arbitration |
| Unknown payment holds stock | Authorization may have committed despite no response | Blind decline/release can sell the same stock twice; TTL needs reconciliation ownership | Real timeout after payment commit stays PAYMENT_UNKNOWN and same-ID replay finds authorization |
| Existing Nginx origin | Delivers one browser port and avoids needless CORS/BFF infrastructure | A Spring Cloud gateway/service registry is unnecessary for this static local topology | Built browser tests and private-port inspection |
| Correlation/W3C context in structured logs | Follow one attempt across all services and join attempts by order ID | Full OpenTelemetry export/collector/visualization is later work | Header/trace propagation test, response support reference, correlated live logs |
| Offline backfill with receipts | Preserves keys, snapshots, net stock and effect ownership without dual writes | Online CDC/backfill/routing requires lag monitoring and conflict reconciliation | Real-schema backfill test, partial-copy resume and target-drift refusal |

The [extraction ADR](../adr/ADR-017-service-extraction.md), [workflow ADR](../adr/ADR-018-durable-http-workflow.md), [HTTP/context ADR](../adr/ADR-019-http-observability.md), and [migration ADR](../adr/ADR-020-offline-ownership-cutover.md) develop these choices. These are educational architecture decisions, not claims that an employer/customer requested every tool.

## Why the new failure states matter

A timeout is evidence that the caller lacks an acknowledgement; it is not evidence the owner rolled back. The coordinator records PAYMENT_UNKNOWN before contacting Payment. A retry uses the same order ID and immutable amount/outcome. If Payment already committed, it returns that authorization; if not, the first operation is recorded once. Decline then creates RELEASE_PENDING, and release is independently idempotent. Cancellation appears only after that acknowledgement. Local terminal-state/notification failure cannot erase the already committed remote payment; replay recovers instead.

This synchronous compensating workflow already has Saga semantics. Phase 4 adds durable event execution/outbox, consumer deduplication and background recovery, not a magic global rollback. There is no automatic completion while the caller is absent. An operator must use the original checkout identity and inspect owners; unknown payments must never be resolved by inventing a new key or expiring stock blindly.

## Automated verification

With Java 21, Maven 3.9+, Node 24, Python 3.10+ and Docker, from `ecal`:

```powershell
mvn -f backend/distributed/pom.xml verify
npm.cmd ci --prefix frontend
npm.cmd test --prefix frontend
$env:VITE_ECAL_MODE="distributed"
npm.cmd run build --prefix frontend
# Install a browser once, then test the running Compose application:
npm.cmd exec --prefix frontend -- playwright install chromium
$env:ECAL_MODE="distributed"
$env:ECAL_WEB_URL="http://127.0.0.1:8091"
npm.cmd run test:e2e --prefix frontend
```

The migration integration test selects `python` on Windows and `python3` on Unix; set ECAL_PYTHON to an interpreter path if your installation differs. Native Windows execution was documented, while observed automated runs used Linux.

Use `npm` on macOS/Linux. To switch a host-built UI back to the monolith, clear VITE_ECAL_MODE or set `monolith`; it is a build-time choice, not a runtime API switch. One browser test for CSV is deliberately monolith-only; the distributed-boundary test is Phase 3-only. [VALIDATION](../../VALIDATION.md) records actual observations separately from tests merely present in source. CI now checks both modes; local execution does not claim hosted CI success.

## IntelliJ import and debugging

1. Open `ecal/backend/distributed/pom.xml` as a Maven project, not the repository's unrelated Ark root POM. Trust your checkout and wait for Maven sync.
2. Set Project SDK and Maven Runner JRE to Java 21. Maven lists service-support, inventory-service, payment-service, orders-service and integration-tests.
3. For database-only IDE development, first stop Docker application mode with `docker compose -f compose.phase3.yaml down` (no volume deletion), then run:

```powershell
docker compose -f compose.phase3.yaml -f compose.phase3.dev.yaml up -d --wait orders-db inventory-db payment-db
```

4. Run `InventoryApplication` (port 8093), `PaymentApplication` (8094), then `OrdersApplication` (8092), selecting each service module's classpath. Main methods select their module-specific YAML automatically. Default DB ports are 5546/5547/5545 respectively. If you changed passwords privately, set the matching ECAL_DB_PASSWORD on each Run Configuration.
5. Open a terminal in `ecal`, install frontend dependencies, then start Vite:

```powershell
$env:VITE_ECAL_MODE="distributed"
$env:ECAL_API_TARGET="http://127.0.0.1:8092"
npm.cmd run dev --prefix frontend
```

6. Open localhost:5173 and place an order. Put breakpoints at `Checkout`, `OrderStore.advance`, `InventoryStore.reserve`, and `PaymentStore.authorize`. Stop IDE processes before returning to full Docker mode. Dev DB ports are loopback only and are not published by normal Compose startup.

Spring Boot run support in IntelliJ Ultimate is convenient; plain Java main configurations and Maven import also work in Community. Do not run a parent aggregator as if it were an application.

## Interview questions with short model answers

- **Why not preserve the old transaction annotation?** JDBC transactions bind a local connection; a remote service commits its own database. A caller rollback cannot undo that remote commit.
- **Why not use a new UUID after timeout?** That describes a new business operation. Reusing the original order ID lets the owner return the already recorded result.
- **Does idempotency give exactly-once delivery?** No. Requests can repeat or overlap; constraints/identity comparison produce one recorded local effect for an operation identity.
- **Is compensation a rollback?** No. It is another business operation with its own identity and failure modes. RELEASE_PENDING is durable because release can fail.
- **Why not release stock on payment timeout?** Payment may have authorized. Stock must remain owned until reconciliation establishes the outcome.
- **Why a lease and also idempotent downstream commands?** A lease reduces overlapping work and fences local state, but an expired coordinator can still have an in-flight HTTP request. The owner ledger protects effects.
- **Can we revert to the old branch after new orders?** Code rollback is easy; data ownership rollback is not. The old database is stale and needs quiesced reconciliation before it may accept writes again.
- **Are trace IDs an observability system?** They correlate logs here. Exported spans, sampling policy, retention, dashboards and operational objectives remain separate work.

Exercises: draw the state after a committed-but-unacknowledged payment; predict stock after five identical releases; simulate a stale lease holder; explain an online migration alternative; identify what would need to change before accepting public traffic or a real payment provider. Use observed tests and logs rather than saying “microservices scale better.”
