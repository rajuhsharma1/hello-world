# Delivery roadmap and review gates

This roadmap is a sequence of experiments, not a requirement to deploy every tool. Phase 0 documents are a reviewable draft; the current delivery implements only the first small Phase 1 slice. A reviewer has not yet approved every assumption.

| Phase | Small objective | Evidence to exit | Revisit / rollback point |
|---|---|---|---|
| 0 — Discover | Clarify one purchase journey, invariants, assumptions, HLD/LLD, quality scenarios and threats | Runnable backend/frontend plus traceable requirements and explicit open questions | Revise requirements/ADRs after stakeholder review; preserve measured facts separately |
| 1 — Transactional core | Catalog, one-product orders, stock reservation, deterministic payment and notification stub | UI/REST persisted checkout; real PostgreSQL race, replay, decline and rollback tests | Current modular-monolith baseline; no network transaction introduced |
| 2 — Data and performance | Measure a representative query/workload; indexes, bounded pagination, optional cache/import experiment | Same workload/hardware before/after, p50/p95/p99 and database plans; tested partial-commit policy for import | Remove ineffective index/cache; do not claim gains without measurements |
| 3 — Extract boundaries | Extract Orders, Inventory and Payment only when their API/ownership boundaries are understood | Versioned contracts, per-service persistence, timeouts and failure tests for partial success | Keep the monolith runnable; migration includes backfill, routing cutover and rollback ownership |
| 4 — Events/workflow | Transactional outbox, relay, explicit Saga, deduplication and reporting projection | Crash/duplicate/poison/reordering drills; projection lag shown; unknown payment reconciled | Replay-safe migration; do not drop source events or disable deduplication on rollback |
| 5 — Buyer/admin security | Authenticated actors, object-level authorization, secure browser session; richer React data flows | Cross-user negative tests, CSRF/XSS/session tests, buyer/admin E2E | Remove public exposure until controls pass; global state/GraphQL only if a measured UI need exists |
| 6 — Operations/cloud design | Metrics/traces, resilience and CI; AWS alternatives and recovery runbooks | Reproducible load/failure evidence and reviewed cost/RPO/RTO assumptions | No paid infrastructure without explicit authorization; no claim of multi-AZ recovery from diagrams |
| 7 — Review/rehearse | Reconcile actual architecture, final decision catalog, customer presentation and interview drills | Another engineer can clone, run, break, restore and explain using the repository | Archive superseded ADRs and working baseline instead of silently rewriting history |

## Next recommended slice

Before extraction, finish Phase 1 with richer domain edge cases only if the baseline exposes a learning gap: multi-line order lock ordering, explicit cancellation/refund semantics, and bounded order-history pagination are candidates. Then measure a real query in Phase 2. Do not add Redis merely to satisfy a vocabulary checklist.

## Governance light enough to use

Each change links requirement → decision → code → test/evidence in the traceability matrix. An ADR changes from proposed to accepted only when the decision is implemented or consciously adopted for the current scope; “deferred” does not mean delivered. Record a superseding ADR for a material change such as separate databases. The engineering gate is runnable evidence, not document volume.
