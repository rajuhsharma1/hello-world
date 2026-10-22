# Observed selective-history query experiment — 22 October 2026

Measured in this Linux cloud workspace (five logical CPUs visible to Python), Java 21.0.12.1, PostgreSQL 17.6. Full metadata, raw request latencies and plans are in [observed-2026-10-22](observed-2026-10-22/summary.json). The first measurement used the Phase 2 working tree based on commit 0217df3; `gitDirty: true` is recorded, not represented as a clean Phase 1 measurement. The harness runs the same Phase 2 endpoint in both variants and changes only the status index.

Fixture: 200000 synthetic orders for one buyer, 200 cancellations (0.1%), fixed timestamps, existing order/payment/notification records. GET `/api/orders/page?status=CANCELLED&limit=20`, eight concurrent clients, 100 warmup requests before each run, 600 measured requests per run. Two rounds reverse variant order. VACUUM ANALYZE and index creation are excluded from HTTP timing. Four runs completed with zero response errors.

| Round | Variant | p50 ms | p95 ms | p99 ms | Requests/sec | DB container CPU ms |
|---|---|---:|---:|---:|---:|---:|
| 1 | baseline | 16.85 | 37.01 | 46.33 | 411.1 | 2327.7 |
| 1 | indexed | 4.84 | 12.32 | 17.77 | 1355.0 | 108.1 |
| 2 | indexed | 4.73 | 11.49 | 20.30 | 1351.7 | 100.9 |
| 2 | baseline | 15.20 | 37.51 | 47.27 | 442.3 | 2279.1 |

The range of observed p95 falls from 37.01–37.51 ms to 11.49–12.32 ms. Both rounds show an improvement for this deliberately selective workload; this is not a universal speedup ratio or production SLA. CPU falls from about 2279–2328 ms to 101–108 ms per measured run. CPU sampling includes the database container's other activity and sampling overhead; it is not a SQL-only profiler.

## Why the plan changes

Baseline uses `order_buyer_recent`: buyer equality gives ordered rows, but `status` is a residual filter. To return 21 rows (20 + lookahead), EXPLAIN removes **19980** other rows and touches **701 shared buffer blocks**. Optimized uses `order_buyer_status_recent`: equality on buyer and status leads directly into the ordered range. EXPLAIN uses an **Index Only Scan**, with **zero heap fetches**, and touches **four shared buffer blocks** (one hit, three reads in the separately captured first plan). These are plan observations, not request latency measurements; later warm requests are a distinct measurement.

All purchase-order indexes together occupy 42491904 bytes in baseline versus 76259328 bytes with the covering status index: an additional **33767424 bytes (~32.2 MiB)** for this fixture. We have not measured write amplification or checkout throughput. A narrower index could reduce storage and update work while needing heap lookups; compare it under a mixed workload before choosing a production INCLUDE list.

## Decision and limits

Keep the index and bounded read projection; defer application caching ([ADR-010](../../docs/adr/ADR-010-cache.md)). This demonstrates a cheaper fix to the measured issue before introducing Redis invalidation and availability concerns.

Warm cache, one buyer, rare cancellations, no competing checkout writes, local network, modest duration and shared hardware are intentional constraints. PostgreSQL visibility and selectivity estimates affect plans. No confidence interval, CPU saturation point, import speedup, checkout throughput, cold-start behavior, production 10-checkouts/sec qualification or full Phase 0 NFR sign-off is claimed. Cumulative database counters in JSON are approximate because statistics flush asynchronously; use EXPLAIN for the reported per-query buffer observation. The fixture and harness are reusable so an engineer can change distributions and challenge this conclusion.
