# Local failure lab — inspect, break safely, restore

Use **synthetic ECAL data only**. ECAL is a separate application under `ecal/`; none of these exercises authorizes changing the existing Ark stack. Follow the root README's actual Compose/Makefile commands and service names. Never substitute an Ark project name or database volume.

## Readiness and normal checkout

From `ecal/`, run `make up`, then inspect `make logs` / the documented health command. A running container is not proof the API works: open the catalog and complete an approved simulated checkout. `make verify` runs unit/architecture and real PostgreSQL integration tests; `make e2e` requires the application to be running.

For a manual API example against the default local origin:

```sh
curl -i http://localhost:8090/api/catalog/products
curl -i -X POST http://localhost:8090/api/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 20000000-0000-0000-0000-000000000001' \
  -d '{"productId":"10000000-0000-0000-0000-000000000001","quantity":1,"paymentOutcome":"APPROVE"}'
```

Repeat the exact POST: expect 200 and the same saved order, not a second inventory/payment effect. Change quantity but retain the same key: expect 409. The example key is intentionally fixed for replay; use a new UUID for a genuinely new purchase. If the synthetic product is sold out, test in the disposable integration fixture rather than secretly bypassing inventory rules.

## Deterministic decline

Choose DECLINE in the UI or submit the same-shaped command under a different new UUID key. The result is a persisted CANCELLED/DECLINED order. Stock is released in the same local transaction. This is not a payment timeout or an actual credit-card failure. Replay that key to see the same cancellation without repeating effects.

## Oversell and key-race drills

Run the integration tests that coordinate concurrent HTTP requests:

- `concurrentDistinctOrdersCannotOversellTheLastUnit`: one unit, eight different logical purchases, one 201 and seven 409 outcomes.
- `concurrentSameKeyHasOneCommitAndSevenExactReplays`: one logical purchase, eight arrivals, one 201 and seven 200 replays with identical order data.

Inspect final database counts asserted by the tests. A race demonstrated with Mockito return values or an H2 substitute does not prove the PostgreSQL behavior. The Testcontainers database is disposable; its reset fixture must not target your running app's persisted volume.

## Late local failure

`notificationFailureRollsBackEvenTheSimulatedPaymentAndStock` injects a notification stub exception after stock/payment writes. Its assertions inspect the rolled-back order, payment, notification and inventory. The fixed exception is a **test fixture**, not a public failure-injection endpoint. There is no unsafe production toggle to crash arbitrary checkouts.

`inventoryCannotBeMutatedOutsideTheCheckoutTransaction` verifies a mutation adapter's MANDATORY propagation rejects an accidental non-transactional call. It prevents a future alternate entry point from silently weakening the coordination boundary.

## HTTP uncertainty and reload

The frontend tests simulate a lost response, malformed successful acknowledgement, proxy timeout and server500. They verify the frozen payload/key is retained. In the actual UI, an unresolved request is stored in session storage before sending and restored within that tab after reload. A closed session/device is not a global recovery system. Check persisted demo history before creating a new logical purchase after losing the client state.

Do not turn an uncertain timeout into “payment failed” or blindly use a new key. That distinction becomes critical when Phase 3 introduces non-transactional network effects.

## Database outage and recovery

Stop **only the ECAL database service** using the Compose command documented in the project README. Attempt a catalog/read/checkout request and inspect the controlled storage error. Restart that service, wait for its health check, and retry the unchanged command/key or inspect saved history. A lost response can straddle commit; a request error is not permission to mint another purchase.

Do not delete volumes for an outage drill. `docker compose down` stops the current Compose project; `down -v` additionally destroys its volumes. Destructive reset is an explicit disposable-data operation, not a normal update/recovery command. Committed data survives application restart only while the PostgreSQL volume survives.

## Backup and recovery design boundary

The NFR scorecard contains **proposed**, unmeasured RPO/RTO targets. No automatic off-machine backup or timed restore has been established by writing this runbook. A later rehearsal must export a consistent backup, restore to a separate disposable database, verify order/stock/payment invariants and record elapsed time and recoverable point. A Docker volume is persistence, not a backup.

## What this drill does not prove

No real provider authorization, external email, Kafka delivery, DLQ replay, distributed transaction, identity isolation, throughput SLO or cloud HA exists in the current slice. Each new failure boundary needs its own contract, durable recovery state and test—not merely another container in Compose.
