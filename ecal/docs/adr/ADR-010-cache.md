# ADR-010: Measure before adding Redis or Spring Cache

**Status:** Accepted — run without application caching after Phase 2 measurement; Redis remains deferred

## Context

Phase 2 measured a selective order-history workload before introducing a cache. Inventory remains authoritative database state.

## Decision — what and why

Continue without an application cache after the controlled Phase 2 index experiment. Any future cache proposal needs a key, TTL, invalidation owner and consistency contract. Never make a cached stock count the reservation authority.

## Alternatives — why not now

Caching immediately can hide inefficient SQL while introducing staleness. Index/query projection changes may solve the bottleneck more cheaply. Redis adds a separate availability and operating concern; local caches differ across JVMs.

## Consequences and trade-offs

The current choice spends a database query per read. A future cache might reduce load but must make freshness expectations explicit and handle stampedes/failures.

## Failure behavior

No cache means no stale-cache correctness bug. Future cache failure should have a tested fallback/load policy; invalidation failure may show stale availability while reservation still relies on PostgreSQL.

## Validation — how to prove it

Phase 2 records workload, concurrency, warmup, machine/DB limits and identical before/after p50/p95/p99. A future cache implementation must add stale/invalidation and failure tests. [The Phase 2 report](../../tests/performance/REPORT.md) records actual selective-query measurements: p95 about 37 ms without the status index versus 11–12 ms with it. This supports the current SQL fix; it does not establish catalog cache value or production capacity. No stale/invalidation test is claimed because no cache was introduced.

Actual execution evidence belongs in the project validation report; this ADR does not claim a test passed merely because it describes one.

## Revisit trigger and migration implications

A repeatable profile identifies read cost/DB pressure and expected benefit exceeds invalidation/operating complexity.

If superseded, retain this record and link the new decision, affected contracts/data, rollback approach and evidence.
