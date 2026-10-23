# Phase 4 — run, break, recover, explain

This phase adds a runnable event-driven slice to the commerce lab. The Ark salon app remains a separate project. Phase 3's HTTP commands are preserved; Kafka activates an autonomous Saga and feeds an independent reporting projection. No real payment is sent.

## Start on Windows, step by step

1. Open Docker Desktop. Wait for the engine to say it is running, using Linux containers. Allow enough memory and disk for eleven containers; running all previous labs simultaneously costs considerably more space. You may stop a previous lab with its own `docker compose ... down` command; omit `-v` to retain its data.
2. Open PowerShell in your local cloned `hello-world` folder. This is wherever **you cloned the repository**, for example `C:\Users\YourName\Projects\hello-world`. `/workspace` is the cloud editor's location and is not a Windows folder.
3. Retrieve this branch:

   ```powershell
   git fetch origin
   git switch feature/ecal-phase-4
   ```

   If Git cannot find it automatically, use `git fetch origin feature/ecal-phase-4` followed by `git switch -c feature/ecal-phase-4 FETCH_HEAD`. If you already have local edits, preserve them before switching.

4. Enter the lab folder and check the files:

   ```powershell
   cd ecal
   Get-ChildItem compose.phase4.yaml
   docker compose version
   ```

5. Build and start:

   ```powershell
   docker compose -f compose.phase4.yaml up --build --wait --wait-timeout 240
   ```

   The first build downloads dependencies and can take several minutes. Make, Java and Node are unnecessary for this Docker route. If Docker Desktop reports insufficient disk, stop unused labs and remove disposable build cache, preserving volumes. If port 8095 is already occupied, stop the process using it before retrying. Container loopback health checks explicitly bypass proxies, including Docker Desktop configurations with a corporate HTTP proxy.

6. Open **http://localhost:8095**. Choose a product and place an approved simulated order. The pending acknowledgement is durable acceptance; the background worker subsequently confirms it. Try a decline: cancellation appears after stock release. The reporting section can lag; use **Refresh reporting** after the events arrive.
7. Inspect and stop:

   ```powershell
   docker compose -f compose.phase4.yaml ps
   docker compose -f compose.phase4.yaml logs --tail=80 api reporting
   docker compose -f compose.phase4.yaml down
   ```

   Start again with the same `up` command. Orders, events, broker logs and projections persist in the Phase 4 volumes. Do not use `down -v` for normal shutdown. The project publishes one loopback port; this is not a public-hosting or authentication setup.

## Optional compact setup for limited disk or memory

This runs the same four Java owners and four databases using **one physical PostgreSQL instance**. Each application role is an ordinary non-superuser, owns only its database, and cannot connect to another owner's database. The separate instance default isolates process/resource failures more strongly; compact mode shares that failure domain. Compose **2.24.4 or newer** is required.

From `ecal`, use both files consistently:

```powershell
docker compose -f compose.phase4.yaml -f compose.phase4.compact.yaml up --build --wait --wait-timeout 240
```

The browser remains **http://localhost:8095**. For the live failure lab use `python scripts/phase4_failure_lab.py --compact`. Stop using:

```powershell
docker compose -f compose.phase4.yaml -f compose.phase4.compact.yaml down
```

Compact mode uses a separate `compact-data` volume. It does not import a default stack's existing databases. Stop one topology before selecting the other, and keep the same file selection for subsequent starts. The IntelliJ configuration below uses the default four-instance topology; do not combine its dev override with compact mode. The cloud workspace runtime validation uses compact mode to fit its disk limit; both Compose configurations are validated, and logical ownership is tested with ordinary credentials.

## Open in IntelliJ

The simplest development workflow is to run Docker as above and open the source in IntelliJ. To debug the Java processes individually:

1. Open **`ecal/backend/distributed/pom.xml`** as a Maven project. Set Project SDK and Maven Runner JRE to Java 21. Reload Maven. Modules are Orders, Inventory, Payment, Reporting, service-support and integration-tests.
2. Stop the Phase 4 full stack before running the same owners from the IDE. Keep its volumes:

   ```powershell
   docker compose -f compose.phase4.yaml down
   docker compose -f compose.phase4.yaml -f compose.phase4.dev.yaml up -d --wait orders-db inventory-db payment-db reporting-db kafka
   ```

3. Create Application run configurations for `InventoryApplication`, `PaymentApplication`, `OrdersApplication`, `ReportingApplication`. Use each module's classpath. Each main method selects its module-specific YAML. Add these environment variables to **all four**:

   ```text
   ECAL_EVENTS_ENABLED=true
   ECAL_KAFKA_BOOTSTRAP=127.0.0.1:9094
   ```

4. Add the owner-specific variables below. Values shown are the local development defaults; if you changed a Compose password, use that value privately.

   | Main / port | ECAL_DB_URL | ECAL_DB_USER / ECAL_DB_PASSWORD | ECAL_FLYWAY_LOCATIONS |
   |---|---|---|---|
   | InventoryApplication / 8093 | jdbc:postgresql://127.0.0.1:5556/inventory | inventory / inventory_dev_only | classpath:db/inventory,classpath:db/events,classpath:db/events-inventory |
   | PaymentApplication / 8094 | jdbc:postgresql://127.0.0.1:5557/payment | payment / payment_dev_only | classpath:db/payment,classpath:db/events,classpath:db/events-payment |
   | OrdersApplication / 8092 | jdbc:postgresql://127.0.0.1:5555/orders | orders / orders_dev_only | classpath:db/orders,classpath:db/events,classpath:db/events-orders |
   | ReportingApplication / 8096 | jdbc:postgresql://127.0.0.1:5558/reporting | reporting / reporting_dev_only | omit; its YAML already includes reporting V1 and events V2 |

   Leave Orders' remote URLs at their defaults (8093 and 8094). Start Inventory and Payment, then Orders and Reporting. Stop existing IDE configurations using these same ports first.

5. In another PowerShell terminal inside `ecal`, start the UI:

   ```powershell
   npm.cmd ci --prefix frontend
   $env:VITE_ECAL_MODE="events"
   $env:ECAL_API_TARGET="http://127.0.0.1:8092"
   $env:ECAL_REPORTING_TARGET="http://127.0.0.1:8096"
   npm.cmd run dev --prefix frontend
   ```

   Open **http://localhost:5173**. Vite proxies both API owners through that browser origin. The normal Docker route still uses only 8095. Do not run Docker and IDE writers against the same databases at the same time.

## Verify and observe actual failures

From `ecal`, with Java 21, Maven, Node and Docker available. If resources are limited, stop the application first using its matching Compose `down` command (without `-v`); the tests create additional disposable containers:

```powershell
mvn -f backend/distributed/pom.xml verify
npm.cmd test --prefix frontend
$env:VITE_ECAL_MODE="events"
npm.cmd run build --prefix frontend
```

The Java suite creates disposable PostgreSQL databases and a real Kafka Testcontainer; it never resets the running application's business tables. The synchronous Phase 3 integration suite still runs with events disabled. Python 3 must be installed for the pre-existing offline migration integration scenario; set `ECAL_PYTHON` if your Python executable has a different name.

For browser checks, start the full Phase 4 Docker stack, then:

```powershell
npx.cmd --prefix frontend playwright install chromium
$env:ECAL_MODE="events"
$env:ECAL_WEB_URL="http://127.0.0.1:8095"
npm.cmd run test:e2e --prefix frontend
```

With Python 3 installed, run the live failure lab while other clients are idle:

```powershell
python scripts/phase4_failure_lab.py
```

It saves an ignored recovery receipt **before** POST, stops Payment and Reporting, accepts one order, observes `PAYMENT_UNKNOWN`, restores Payment, and uses GET only to observe automatic completion. It then restores Reporting and verifies catch-up plus one order/reservation/payment and one stock decrement. The stopped services are restored in `finally`. It creates a real simulated order and retains it. If interrupted, use the printed receipt:

```powershell
python scripts/phase4_failure_lab.py --resume .runtime/phase4-recovery-YOUR-KEY.json
```

Never invent a new key to recover an uncertain operation. Keep the receipt until recovery is complete.

Inspect delivery state without changing business records:

```powershell
docker compose -f compose.phase4.yaml exec orders-db psql -U orders -d orders -c "select count(*) as pending_events from event_outbox where published_at is null"
docker compose -f compose.phase4.yaml exec orders-db psql -U orders -d orders -c "select order_id,attempts,next_attempt_at from saga_work"
docker compose -f compose.phase4.yaml exec reporting-db psql -U reporting -d reporting -c "select fingerprint,reason,received_at from event_quarantine"
docker compose -f compose.phase4.yaml exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --describe --group reporting-v1
```

The last command exposes actual partition offsets and consumer lag. Compare it with the UI's last projection timestamp; they answer different questions. The lab retains all outbox and inbox rows, so monitor storage during extended use.

## Poison, replay, migration and rollback

Quarantined envelopes are acknowledged only after their original content is retained durably. Inspect them privately; deploy a compatible parser for unsupported schema versions. Re-publishing an unchanged valid envelope after a compatible deployment preserves its event ID and aggregate key. The future-schema test also demonstrates that a quarantined ID has not been marked successfully processed, so a subsequently compatible message can project it. Infrastructure/handler failures do not enter quarantine: they roll back and retry on the original offset.

Do not edit an already processed event ID to mean something different; this is detected as an identity collision. Reconstructing a malformed business event needs reconciliation against its owning database, a new correction event where semantics change, and operator review. A one-click replay UI and automated bulk replay are intentionally not implemented.

Phase 4 starts with fresh data. It does **not** run the Phase 3 migration script against the new databases: that script's assumptions predate outbox triggers and additional versions. There is no automatic Phase 3-to-4 historical backfill in this slice. Keep the Phase 3 volumes as the comparison/source. A future cutover must quiesce all writers, preserve keys and source versions, backfill projections deliberately, checkpoint consumer offsets and test a single-owner routing cutover before accepting writes.

For a code rollback, stop the Phase 4 writers/consumers first, preserve all four databases and Kafka volume, and return to the independent Phase 3 stack only as its existing dataset. That does **not** transfer Phase 4 orders. Do not attach Phase 3 code to a database with Phase 4 migrations, drop event state, disable deduplication, truncate the broker, or run two owners against the same data. Forward-fix a Phase 4 problem with identities and evidence intact.

## Explain the choices in an architect interview

| Choice | Reason | Alternative / tradeoff | Failure proof |
|---|---|---|---|
| PostgreSQL outbox trigger | atomic fact with each local state change, including direct SQL writes | application inserts are more visible in Java; CDC reduces polling but adds an operator platform | outbox insertion failure rolls back the order; conflicting insert emits no phantom event |
| Kafka with order partition key | independent consumers and retained event stream | queues may be simpler for one worker; a single broker is not highly available | actual broker pause leaves acceptance durable and relay resumes |
| Separate publish marker + inbox | makes the unavoidable duplicate window explicit | Kafka idempotence cannot include a PostgreSQL business commit | crash after send re-delivers one identity without duplicate effects |
| Event-activated work + HTTP commands | changes execution model while preserving tested command contracts | all-Kafka commands or choreography would add another protocol/state-machine change | Payment outage and Orders restart complete without caller replay |
| Persistent retry schedule + fenced lease | recovery survives worker restarts and replicas | lease expiry alone cannot undo remote effects; owner idempotency remains necessary | existing lease/remote replay tests plus new autonomous recovery |
| Full snapshot projection | monotonic versions tolerate duplicate/out-of-order state events | cannot apply this shortcut to financial deltas; separate database adds lag/operations | newer snapshot followed by older one never regresses |
| Database quarantine | retains poison evidence before offset acknowledgement | a dead-letter topic adds another broker publish failure to recover | bad/schema records are retained; subsequent compatible record succeeds |
| React validated polling | makes 202 distinct from business success; retains identity during outages | SSE/WebSocket would require more connection/reconnect machinery | pending/read-failure tests and page-close browser journey |

A strong explanation names the transaction boundary first: “The order and its event commit in Orders PostgreSQL. Kafka publication is later and may repeat. The Orders consumer commits work with its inbox before acknowledging; the worker reuses durable operation identities across HTTP. Reporting is an independent, version-guarded read model.” Then state the operational limits: one broker, modest serialized relay throughput, finite broker retention, no production load qualification or authenticated buyers yet.

Read [HLD](../architecture/PHASE-04-HLD.md), [LLD](../architecture/PHASE-04-LLD.md), [ADR 021](../adr/ADR-021-event-driven-execution.md) and [observed validation](../../VALIDATION.md).
