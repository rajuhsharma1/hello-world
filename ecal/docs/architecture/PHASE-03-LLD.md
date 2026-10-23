# Phase 3 LLD — identities, transitions and recovery

Code root: `backend/distributed/`. Each service has its own Spring Boot main, POM, executable JAR and Flyway location. Build one owner with `mvn -pl inventory-service -am package` from that root; integration verification builds the reactor. Runtime schema access stays inside that owner.

## Request flow and local boundaries

```mermaid
sequenceDiagram
 participant UI as Buyer
 participant O as Orders
 participant OD as Orders DB
 participant I as Inventory
 participant P as Payment
 UI->>O: POST checkout + original key
 O->>OD: claim order / validate immutable replay payload
 O->>OD: acquire token lease (commit)
 O->>I: PUT reservation keyed by order ID
 I-->>O: RESERVED / durable REJECTED
 O->>OD: RESERVED then PAYMENT_UNKNOWN (local commits)
 O->>P: PUT authorization keyed by same order ID
 alt authorized acknowledgement
 P-->>O: AUTHORIZED
 O->>OD: CONFIRMED + notification (one local commit)
 else declined acknowledgement
 P-->>O: DECLINED
 O->>OD: RELEASE_PENDING + DECLINED
 O->>I: PUT idempotent release
 I-->>O: RELEASED
 O->>OD: CANCELLED + notification (one local commit)
 else timeout / malformed / unavailable
 O-->>UI: 503 + correlation reference; retry exact key/payload
 end
```

`Checkout` deliberately has no `@Transactional`. `OrderStore.claim/advance/lease/releaseLease` are separate proxied short transactions. A self-invoked annotation would not create a new boundary; worker/request thread transaction context also cannot turn an HTTP call into ACID. READ COMMITTED plus uniqueness and conditional updates supplies the required local arbitration.

`OrderState` is a framework-free transition rule. PENDING -> RESERVED or REJECTED; RESERVED -> PAYMENT_UNKNOWN; PAYMENT_UNKNOWN -> CONFIRMED or RELEASE_PENDING; RELEASE_PENDING -> CANCELLED. Terminal states have no outgoing transitions. SQL checks explicitly reject terminal NULL payment status. A declined authorization cannot become CANCELLED until release was acknowledged. `advance` updates expected status and current lease token together and records a terminal local notification in the same transaction.

## Identity and concurrency

Orders has a unique `(buyer_id,idempotency_key)`. It stores the product/name/price/total snapshot and requested outcome; replay compares product, quantity and outcome before returning or resuming the original order. Buyer is always server-selected; cursor or browser payload cannot select another buyer. The local simulator uses exact BigDecimal/numeric values, with decimal strings in responses.

The lease CAS stores a UUID token and database-clock expiry. A new coordinator can acquire after 30 seconds. The old token cannot advance state or release the newer token's lease. Remote commands may still overlap after expiry, so the idempotent ledger is the effect arbiter; do not describe the lease as a cross-service lock. The tests force expiry without sleeping and assert fencing.

Inventory inserts an order-keyed PENDING decision and atomically executes `available=available-quantity WHERE available>=quantity`, then commits RESERVED or REJECTED. Rejection returns normally so its identity commits; throwing through the transaction proxy would incorrectly erase the decision. Identical retry returns the stored decision; a changed product/quantity is 409. Release uses a conditional RESERVED -> RELEASED update and increments stock only for the winner. A release before a delayed reserve inserts a RELEASED tombstone with no increment; later reserve returns closed state and cannot consume stock. There is no reservation expiry/replenishment endpoint.

Payment inserts once with ON CONFLICT DO NOTHING, then reads the committed winner and compares amount/currency/outcome. It never overwrites an authorization. Same-ID PUT reconciles a lost acknowledgement, whether the previous attempt committed or never arrived. GET 404 cannot prove an in-flight authorization will not commit later. These are simulated local effects, not a real payment provider guarantee.

## HTTP and contracts

- Public primary API: `/api/v1/catalog/products[ /page ]`, `/api/v1/orders[ /page /{id} ]`. `/api/...` equivalents remain compatibility aliases for the existing UI.
- Inventory: `/internal/v1/stock`, PUT `/internal/v1/reservations/{orderId}`, PUT `.../{orderId}/release`.
- Payment: PUT/GET `/internal/v1/payments/{orderId}`.
- [Orders](../../contracts/openapi/phase3-orders.yaml), [Inventory](../../contracts/openapi/phase3-inventory.yaml), [Payment](../../contracts/openapi/phase3-payment.yaml) OpenAPI files define fields/outcomes. Structural record guards plus actual HTTP tests detect drift; this is not full OpenAPI certification.

`RemoteServices` uses its own wire records, not server entities or Java service APIs. It tolerates additive response fields but validates all acknowledgement identities, quantities, amounts, currency and expected outcomes. A 2xx with missing/mismatched business data is uncertain, not success. No redirect or application-level hidden retry is enabled; transport retransmission can still happen, which is why effect identities matter. Requests use finite configurable deadlines (connect 50–2000 ms, request 100–5000 ms; defaults 500/2000). No tail-latency or throughput target is claimed from those timeout choices.

A service outage or malformed acknowledgement leaves durable progress and returns 503. A concurrently owned lease is 503 CHECKOUT_IN_PROGRESS. Validation is 400; unknown product/order 404; identity conflict or durable stock rejection 409. Error bodies/logs do not expose SQL or bodies. Correlation references aid support without being authentication credentials. Per-request W3C IDs are distinct from the stable order/business identity.

History remains keyset-paged with limit 1–50 and status-scoped cursors. Payment status is nullable while processing; the UI renders that as unacknowledged. It retains the exact pending checkout in sessionStorage after uncertainty. Reloading history never mutates work. CSV import is kept in the monolith: the old cross-module transaction cannot initialize Inventory across HTTP atomically, so hiding that operation is a deliberate scope boundary, not a silent partial implementation.

## Recovery ownership

The caller resumes synchronous work with the original key/payload; Orders is the state owner, Inventory/Payment the effect owners. If the caller disappears, paused work remains until an operator/client replays it. Leases and idempotency rows are not automatically garbage-collected. Define retention and manual reconciliation before a real deployment. Phase 4 introduces background execution and event delivery/replay; there is no current promise of autonomous convergence.

See `DistributedCheckoutIT` for network/acknowledgement/DB failure, concurrency, restart, lease, authorization, trace and migration cases. Test-only proxies never ship in executable service JARs. The [live failure drill](../../scripts/phase3_failure_lab.py) stops only the Phase 3 Payment container and restores it in cleanup. [Migration](../runbooks/PHASE-03-MIGRATION.md) defines a separate offline ownership transfer.
