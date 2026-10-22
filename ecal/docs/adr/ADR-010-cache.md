# ADR-010: Measure before adding Redis or Spring Cache

**Status:** Deferred — Phase 2 experiment

## Context

No performance baseline demonstrates that catalog reads are a bottleneck. Inventory is authoritative database state.

## Decision — what and why

Ship without an application cache. In Phase 2, collect a controlled baseline before choosing a key, TTL, invalidation owner and consistency contract. Never make a cached stock count the reservation authority.

## Alternatives — why not now

Caching immediately can hide inefficient SQL while introducing staleness. Index/query projection changes may solve the bottleneck more cheaply. Redis adds a separate availability and operating concern; local caches differ across JVMs.

## Consequences and trade-offs

The current choice spends a database query per read. A future cache might reduce load but must make freshness expectations explicit and handle stampedes/failures.

## Failure behavior

No cache means no stale-cache correctness bug. Future cache failure should have a tested fallback/load policy; invalidation failure may show stale availability while reservation still relies on PostgreSQL.

## Validation — how to prove it

Phase 2 records workload, concurrency, warmup, machine/DB limits and identical before/after p50/p95/p99. Include stale/invalidation and failure tests. No performance result is claimed now.

Actual execution evidence belongs in the project validation report; this ADR does not claim a test passed merely because it describes one.

## Revisit trigger and migration implications

A repeatable profile identifies read cost/DB pressure and expected benefit exceeds invalidation/operating complexity.

If superseded, retain this record and link the new decision, affected contracts/data, rollback approach and evidence.
