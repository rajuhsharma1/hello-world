# Phase 4 — detailed design

## Request and transaction sequence

1. `OrdersController` selects async behavior only when `ecal.events-enabled=true`. `OrderStore.claim` uses the existing buyer/key uniqueness constraint and immutable payload checks.
2. Before-insert/update trigger assigns the aggregate version; a separate **after** trigger emits the outbox row. This separation matters: PostgreSQL executes a before-insert trigger even for an insert ultimately discarded by `ON CONFLICT DO NOTHING`. Emitting there could create a phantom order event.
3. API returns the persisted snapshot with 202 for a nonterminal state. No Kafka call occurs in this request. A lost response retains the browser's original key and payload.
4. `EventPlatform.Relay` starts a bounded transaction, obtains owner-local advisory lock 427042, and reads the earliest unpublished sequence. It sends the versioned JSON envelope, keyed by order ID, then marks `published_at`. Failure rolls back the marker. Multiple relays serialize; per-row `SKIP LOCKED` would permit the later version to overtake the earlier one.
5. `EventInbox.receive` validates schema and key. For valid input it inserts inbox identity/fingerprint and runs the consumer handler in one transaction. The listener acknowledges only after `receive` returns. Any handler/DB error rolls back and retries indefinitely with a one-second backoff. The configured error handler does not recover by dropping the record after a finite number of failures.
6. Invalid JSON, null Kafka tombstones, unsupported schema, impossible Orders snapshot, key mismatch and reused event identity are durably quarantined before acknowledgement. Handler outages are never classified as poison. Quarantine stores the original content and a static reason without logging its raw body; a null Kafka value is represented explicitly as `<Kafka tombstone>` with its own reason.
7. `AsyncSaga` consumes only `OrderCreated` as activation. Its worker selects at most ten due rows per scan. The pre-existing 30-second token-fenced order lease protects local transitions across workers. Remote owner ledgers protect side effects even if a lease expires. Failed work gets persisted linear backoff capped at 30 seconds, without a retry-count cutoff. Terminal work is deleted. Deleting work after terminal commit is safe: a crash before deletion finds the terminal order and performs no new command.
8. `Reports.apply` upserts a full snapshot only when its version is greater. The inbox and projection commit together. Monthly confirmed revenue uses the order creation month in `Asia/Kolkata`; pending, cancelled and rejected totals are separate and are not revenue.

## Schema additions

Existing V1 migrations are unchanged. Phase 4 explicitly adds `classpath:db/events` (V2) plus `classpath:db/events-<owner>` (V3) to each operational owner's locations. Reporting has its own V1 and the shared V2. The Phase 3 default locations still include only V1. Flyway schema history and all event state are preserved on normal shutdown.

| Table | Owner | Purpose |
|---|---|---|
| `event_outbox` | each service | UUID event ID, source, aggregate UUID, source version, type, JSONB snapshot, occurrence/context, published marker; partial index on pending sequence |
| `event_inbox` | each consumer | event UUID primary key plus SHA-256 fingerprint; deduplicates after offset-commit loss |
| `event_quarantine` | each consumer | original wire content, fingerprint, static reason, receive time; inspection/recovery source |
| `saga_work` | Orders | order FK, durable attempts, next due time, correlation/trace IDs |
| `purchase_order.event_version` | Orders | increments on creation and business status change, not lease updates |
| `order_projection` | Reporting | latest Orders version/status/amount/time; no operational foreign keys |

`EventContext.attach` copies MDC correlation and trace IDs into **transaction-local** PostgreSQL settings. The trigger includes those settings in the envelope. Orders activation persists them in its work row; the worker restores them for downstream HTTP and log correlation. This is propagation of context identifiers, not exported distributed tracing spans.

## Event catalogue

Topic `ecal.events.v1`, four partitions, replication factor one. Groups `orders-saga-v1` and `reporting-v1` independently receive all records. Key = aggregate/order UUID. Ordering exists inside a Kafka partition, not across producers or the whole topic.

| Source | Types | Version |
|---|---|---|
| Orders | `OrderCreated`, `OrderProgress`, `OrderConfirmed`, `OrderCancelled` | increments per persisted Orders state change |
| Inventory | `InventoryReserved`, `InventoryRejected`, `InventoryReleased` | decision 1, release 2; a release-before-reserve tombstone can begin at 2 |
| Payment | `PaymentAuthorized`, `PaymentFailed` | immutable result 1 |

`OrderCancelled` carries either `CANCELLED` (decline compensated) or `REJECTED` (no stock); inspect the payload status. All envelopes use schemaVersion 1. Unknown additive fields are tolerated. Unsupported schema versions are quarantined; deploy a compatible consumer before replaying them. See [wire contract](../../contracts/events/README.md).

## HTTP and browser behavior

The Phase 3 route shapes remain, but async POST changes acknowledgement semantics. This is a breaking behavioral change for a client that expects a terminal 201 response. The lab deploys a paired `events` UI on an isolated port and dataset; a production rolling rollout should introduce an explicit API version or negotiated async preference before mixing clients. 202 means stored, not completed. GET stays read-only. React's `events` build displays pending snapshots and polls an acknowledged order ID every 1.5 seconds until a validated terminal snapshot. It retains its operation key during pending/read failures and validates the polled ID, product, quantity and payment outcome before clearing it. On reload an unresolved key is restored; replaying that identity discovers the same order, while background work continues independently. Reporting is refreshed separately and explicitly advertises lag. Nginx resolves API owner names through Docker DNS with a five-second cache: a dependency restart may change its IP, and permanent startup-time resolution would leave the public route pointing at the wrong service. The live outage lab verifies the public reporting URL as well as the projection database.

## Failure proof

[EventWorkflowIT](../../backend/distributed/integration-tests/src/test/java/com/ecal/distributed/system/EventWorkflowIT.java) uses real PostgreSQL and Kafka, four credentials and actual HTTP. Its schedulers are controlled for deterministic boundaries. It covers durable acceptance, duplicate POST, relay crash after broker acceptance, payment outage and compensation without client replay, consumer DB commit before offset acknowledgement, poison/schema quarantine, out-of-order/additive snapshots, transaction rollback, broker pause/recovery and Orders restart. The consumer crash test injects the exception exactly at acknowledgement; it is a deterministic crash-window test, not an OS process-kill claim. The live failure lab exercises ordinary continuously running background workers.
