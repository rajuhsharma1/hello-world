# ADR-020: Offline backfill with per-owner receipts and explicit rollback limits

**Status:** Accepted — optional Phase 3 local migration tooling; startup remains a fresh isolated lab.

## Context and decision

Preserving old IDs, retry keys, snapshots, payment decisions and net stock is necessary for a meaningful ownership transfer. Require both writer sets stopped. Export one consistent read-only source snapshot with checksum. Preflight every target before any writes; accept fresh unmodified seeds or verified receipts of the same full dataset. Copy inventory/payment/orders independently, transactionally recording a snapshot receipt in each owner's database. Verify full projected rows. Resume a partial copy only from the exact same snapshot; reject target drift or other snapshots.

Historical confirmed reservations are RESERVED, cancellations RELEASED. Copy already-net available stock without applying historical decrements/releases again. Preserve Orders snapshots and owner-effect identities. COPY CSV transports JSON without interpolating product text into SQL. The source remains unchanged.

## Alternatives and tradeoffs

A fresh isolated lab is the default and lowest-risk comparison. Online backfill/CDC can reduce downtime but needs a high-water mark, lag monitoring, changed-row reconciliation and a controlled write cutover. Dual writes can diverge. Pretending all three copies share a transaction hides partial success. A checksum is content integrity, not signing/authentication. This local workflow assumes no external SQL writers; container-stop checks are not a distributed lock.

Downtime is explicit. A partial target is not safe to serve. Receipts permit recovery but must not be removed to force another dataset through. Phase 2 CSV fingerprints stay archived with the source because this distributed mode does not offer import retries.

## Failure, proof and rollback

Before target traffic, routing can return to the unchanged source. After new Phase 3 effects, the source is stale: keep new ownership and recover forward until a separately planned reverse backfill/reconciliation is complete. No automatic reverse migration is supplied or claimed.

[Migration runbook](../runbooks/PHASE-03-MIGRATION.md) gives exact Windows commands. The real-schema integration test proves dry-run, partial-owner resume, receipt replay, checksum/drift refusal, quoted/multiline data safety and original-key replay with unchanged effects. This is not a host-loss backup or production zero-downtime migration qualification.
