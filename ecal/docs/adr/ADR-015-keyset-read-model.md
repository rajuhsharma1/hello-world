# ADR-015: Keyset read projections and a status-selective index

**Status:** Accepted — implemented in Phase 2.

**Context.** The Phase 1 history endpoint is bounded to fifty rows. Deep browsing and status filtering need a bounded query; rare cancellations reveal wasted scans with the original buyer/time index.

**Decision.** Add versioned, endpoint/filter-scoped tuple cursors, limit 1–50, and `limit+1` lookahead. Keep old array endpoints compatible. Query immutable order snapshot columns directly into `OrderView`. Add a buyer/status/time/UUID covering index, retain buyer/time/UUID for unfiltered queries, and index catalog name/UUID. Do not restore aggregates or add an asynchronous reporting store.

**Alternatives.** OFFSET supports arbitrary page jumps but scans discarded rows and shifts with inserts. Exact count adds work and a misleading cross-request total. JPA is viable but adds mapping and fetch-plan decisions without a demonstrated need. Redis requires freshness/invalidation semantics before it can be judged.

**Tradeoffs/failures.** Extra index storage and write amplification; VACUUM visibility affects heap fetches. Cursor ordering is deterministic, not a snapshot across requests. Catalog renames can move items. Encoded cursors do not authorize access; buyer remains server-owned. Standard index creation locks writes during the small lab migration. A large deployment needs a separately reviewed nontransactional `CREATE INDEX CONCURRENTLY` rollout, invalid-index recovery and operational lock budgets. Rollback application routing can return to old endpoints while additive indexes remain; do not rewrite applied Flyway files.

**Proof.** [Measured experiment](../../tests/performance/REPORT.md); `PhaseTwoIntegrationTestIT` verifies pagination/ties/filter scope/server buyer and physical SQL counts. The report records the deliberately selective fixture and confines performance claims to it. Revisit index payload and pagination semantics when product usage and write profiles change.
