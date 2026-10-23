# ECAL event contract v1

Transport: JSON UTF-8 on `ecal.events.v1`; key is the canonical order UUID, four partitions. Partition count is part of ordering strategy: changing it can move an order to another partition. Do not expand partitions during an in-flight ordering exercise without a migration plan.

```json
{
  "eventId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "source": "orders",
  "aggregateId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  "aggregateVersion": 4,
  "schemaVersion": 1,
  "type": "OrderConfirmed",
  "occurredAt": "2026-10-23T00:00:00Z",
  "correlationId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
  "traceId": "dddddddddddddddddddddddddddddddd",
  "payload": {
    "id": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    "productId": "10000000-0000-0000-0000-000000000001",
    "quantity": 1,
    "totalAmount": "499.00",
    "currency": "INR",
    "status": "CONFIRMED",
    "createdAt": "2026-10-23T00:00:00Z"
  }
}
```

IDs, source, positive version, schema 1, supported type, occurrence and object payload are required. Context IDs are nullable (for example maintenance SQL). Orders snapshots require matching order ID, product UUID, quantity 1–10, supported status, positive decimal money within numeric(14,2), INR and an ISO instant. Type/status pairs are checked for created, confirmed and cancelled events. Unknown additive fields are tolerated. A changed payload must use a new event ID; rewriting an existing event ID is quarantined.

Inventory payload: `{productId, quantity, state}`. Payment payload: `{amount, currency, status}`. These facts are emitted by the owning database, never used as an alternative authorization for the Orders coordinator. Their versions are source-local. See the [event catalogue](../../docs/architecture/PHASE-04-LLD.md).

Publisher serializes occurrence as ISO time. Payload monetary values are strings; downstream code must use exact decimal arithmetic for financial totals. A schema registry is not introduced in this slice. The envelope parser and real-broker integration tests are executable compatibility checks; they do not certify arbitrary future producers.

A null Kafka tombstone has no supported domain meaning on this fact topic. It is durably quarantined with a `<Kafka tombstone>` marker and reason, then acknowledged, so it cannot block subsequent records.
