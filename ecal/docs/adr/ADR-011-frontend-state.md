# ADR-011: React local state for this vertical slice

**Status:** Accepted — current Phase 1

## Context

Catalog, a single checkout form and recent order history have a small, visible state graph. Unknown submission outcomes need a stable command/key, not a global store by default.

## Decision — what and why

Use React/TypeScript components and Hooks, an explicit typed API boundary and local state. Persist the submitted command/key in browser session storage before sending so a reload in the same tab preserves uncertain-request recovery. Render user/server text as text, not HTML.

## Alternatives — why not now

Redux Toolkit/RTK Query is valuable when cross-route shared server state, cache invalidation and request deduplication become substantial. It is unnecessary ceremony for purely local form state. Uncontrolled global variables obscure retries and testing.

## Consequences and trade-offs

A lightweight client duplicates some request/loading/error logic. Session storage is tab/session-scoped, not a cross-device recovery ledger; it is acceptable for synthetic command IDs, not payment credentials. Explicit state remains understandable and tested; later consolidation should be driven by repeated needs rather than fashion.

## Failure behavior

A timeout/network error does not prove checkout failure. Keep the uncertain request retryable without silently changing its payload/key; show failures and refresh catalog/history after an acknowledged result.

## Validation — how to prove it

RTL tests cover loading, validation, error and safe retry behavior; a real backend E2E proves order persistence after a read/reload. Type-check and build the pinned frontend.

Actual execution evidence belongs in the project validation report; this ADR does not claim a test passed merely because it describes one.

## Revisit trigger and migration implications

Several routes/components need coordinated server cache, background refresh or complex mutation invalidation; consider RTK Query then.

If superseded, retain this record and link the new decision, affected contracts/data, rollback approach and evidence.
