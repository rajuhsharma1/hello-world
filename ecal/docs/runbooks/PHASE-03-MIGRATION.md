# Phase 2 -> Phase 3 offline ownership transfer

Default Phase 3 startup uses **fresh isolated lab databases** at port 8091. It does not silently move or erase the monolith's orders/stock at 8090. Use the following optional offline workflow to move a populated learning dataset. Keep other clients/operators idle; these stopped-container guards are local operational safeguards, not distributed admission control or protection against an administrator making SQL writes.

## Ownership and prerequisites

Before cutover the monolith owns every effect. After a verified cutover Phase 3's Inventory/Payment databases own their effects and Orders owns progress. Never allow both stacks to accept writes to the same logical dataset. Preserving keys/IDs and current stock matters more than copying a visually similar order list.

Requirements: Docker, Python 3.10+, the already initialized/migrated Phase 2 source and Phase 3 target. Targets must be fresh seeds with no orders/reservations/payments/notifications, or contain a verified receipt of this exact snapshot. Existing target business data, seed edits, other snapshots and post-copy drift are rejected; no erase/reset command is supplied.

The export is a local artifact containing synthetic business data; `.gitignore` excludes `migration-exports/`. SHA-256 detects content modification/replay mismatch, not authenticity or permission. Keep a copy of the verified export before switching owners. This is not a tested host-loss backup solution.

## Copy, verify and switch (PowerShell, from `ecal`)

1. Initialize Phase 3 once: `docker compose -f compose.phase3.yaml up --build --wait --wait-timeout 180`. Do not submit orders if you intend to migrate into these fresh targets.
2. Stop both writer sets, leaving their databases running:

```powershell
docker compose -f compose.yaml stop web api
docker compose -f compose.phase3.yaml stop web api inventory payment
```

3. Export one consistent read-only source snapshot. An existing output file is never overwritten:

```powershell
python scripts/phase3_migrate.py export --out migration-exports/phase2-snapshot.json
```

4. Run the dry-run validation, inspect its three-owner plan, then execute it:

```powershell
python scripts/phase3_migrate.py apply migration-exports/phase2-snapshot.json
python scripts/phase3_migrate.py apply migration-exports/phase2-snapshot.json --apply
```

5. Re-run the dry-run. Each owner must say `already verified`. The tool compares full projected rows as well as receipts, not just counts.
6. Switch traffic by starting only the Phase 3 services:

```powershell
docker compose -f compose.phase3.yaml up -d --no-build --wait --wait-timeout 180
```

7. Open `http://localhost:8091`, inspect old IDs/history, and replay an original completed key/payload if available: it must return its old result without another stock/payment effect. Keep the source writers stopped. Select a new source-of-truth explicitly in any saved links or local proxy; do not point the browser at 8090 accidentally.

## What the tool actually does

`phase3_migrate.py` exports catalog, net available stock, terminal orders, simulated payments and notification records in one SQL statement/read view. It validates source UUID/key uniqueness, complete product/stock and order/payment/notification coverage, terminal/payment consistency, exact totals and checksum. Only CONFIRMED/CANCELLED source orders are eligible. Source seed identities must still exist; a customized/deleted-source catalog requires a separately reviewed migration.

It derives RESERVED ledger rows for confirmed old orders and RELEASED tombstones for cancelled ones. **Available stock is copied as-is, never decremented or incremented again for the historical ledger.** Payment records retain order IDs, amounts, currency and requested outcome. Orders retains buyer/key, original money/name snapshots and creation time. Catalog products imported in Phase 2 are copied for browsing/checkout, but Phase 2's import-retry fingerprints remain archived in the source because distributed CSV import is not offered.

All three targets are preflighted before writes. Inventory, Payment, then Orders each commit their local copy and snapshot receipt atomically. COPY CSV carries JSON data without interpolating names/descriptions into SQL. Full projected-row comparison verifies each copy. A failure can leave some owners copied and others fresh; this is not an atomic three-database transaction. Keep writers stopped and rerun **the same snapshot**. Verified matching receipts skip completed owners; different content or drift blocks replay. Do not delete receipts or manually bypass the guard to make another snapshot fit.

## Rollback boundaries

- **Before Phase 3 accepts any new work:** stop its services, leave the copied targets intact, and start the original monolith API/web (`docker compose -f compose.yaml up -d --no-build --wait`). Source data was never mutated. Route back to 8090. This is a local routing rollback, not a database restore.
- **After Phase 3 accepts new work:** the monolith is stale. A blind routing flip would resurrect stock or lose orders/payment identities. **Automatic reverse backfill is not implemented.** Keep the new owner authoritative, recover forward, and plan a separate quiesced reverse migration/delta reconciliation before resuming old writers. Reconcile pending/unknown orders with their effect owners first. Preserving the old branch does not make data rollback automatically safe.

`DistributedCheckoutIT.offlineBackfill...` runs against a real disposable Phase 2 schema and separate target databases. It covers dry-run, partial-copy resume, exact receipt replay, hostile-looking quoted/multiline text, checksum rejection, post-copy drift rejection and old-key replay with unchanged stock/payment counts. It controls every writer directly; CLI stopped-container guards are an additional operational layer. No live source was migrated merely to execute that test.
