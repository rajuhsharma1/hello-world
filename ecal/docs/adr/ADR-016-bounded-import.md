# ADR-016: Bounded, replayable CSV import with independent batch commits

**Status:** Accepted — implemented in Phase 2.

**Context.** Catalog import is a useful concurrency experiment only if resource bounds, transaction scope and recovery after partial success are observable. Current deployment is one loopback-only JVM.

**Decision.** Parse UTF-8 RFC4180 with Apache Commons CSV 1.14.1, not `split(",")`; validate file structure before writes. Accept 256 KiB / 1000 rows, batch by fifty, four workers, queue sixteen, one admitted file per process. A distinct proxied writer uses REQUIRES_NEW/READ_COMMITTED/ten-second timeout for product, initial stock and original-payload fingerprint. Join results synchronously. Return 200 with explicit committed/rolled-back batches; 400/413 before scheduling for malformed/oversized files and 429 for another active file. Existing ID plus identical original payload skips without changing stock. Conflicting payload rolls back its batch. No successful-batch compensation.

**Alternatives.** A single transaction gives whole-file atomicity but lengthens locks and hides partial-commit learning. One thread is simpler but does not exercise bounded concurrency. Unbounded futures/parallelStream lack an import-specific admission budget. A durable queue/job ledger would enable asynchronous status/recovery but requires persisted ownership and distributed admission; it belongs in a later workflow phase.

**Tradeoffs/failures.** Up to four of twelve pool connections are used by import work; other eight are not a guaranteed checkout reservation. A process-local semaphore is not distributed admission control. Worker completion order is nondeterministic; response order follows file batches. Crash/timeout can leave committed batches with no acknowledgement; replay the same file. Fingerprints are replay checks, not authentication. New edits/replenishment need a separate API, not rewriting import identities. Client IDs preserve identity across retries. No production upload/authentication guarantee is claimed.

**Proof.** `ImportConcurrencyTest` uses latches to exercise all twenty batches, peak four workers and 429 admission. `PhaseTwoIntegrationTestIT` checks real PostgreSQL batch rollback, mixed outcomes, corrected replay, preserved consumed stock, quoted CSV, limits and duplicate-ID structural rejection. Native browser upload/replay is checked separately. See [Phase 2](../learning/PHASE-02.md) and [observed validation](../../VALIDATION.md).

**Rollback.** Hide the import UI/route; existing imported products remain ordinary catalog data. Keep V5 fingerprint metadata for retry semantics. Do not drop products referenced by orders or erase applied migrations.

Infrastructure exceptions can occur around commit, so such batches report `UNKNOWN`, not a promise of rollback. `failedBatches` counts both ROLLED_BACK and UNKNOWN; created/skipped counts include only acknowledged committed batches. Replay the same file to resolve uncertainty. A unit test injects a connection-loss exception to guard this distinction.
