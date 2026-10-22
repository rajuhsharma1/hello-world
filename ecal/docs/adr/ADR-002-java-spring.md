# ADR-002: Java 21 and verified Spring Boot 4.1.1

**Status:** Accepted — current Phase 1

## Context

The reference learning plan prefers Java 21 and a compatible Boot 4 release. Versions must be real and reproducible; familiarity with a prior repository is not a reason to silently inherit its Boot 3 stack.

## Decision — what and why

Use Java 21 and pin Spring Boot 4.1.1. The Maven Central release metadata was checked before implementation. Use Boot dependency management for its compatible Spring/JDBC/test ecosystem; pin additional libraries separately.

## Alternatives — why not now

Java 17 remains a plausible enterprise baseline but is not the requested primary learning target. Boot 3.5 is an alternative for an incompatible dependency; no such fallback is assumed. A moving latest version would make the lab irreproducible.

## Consequences and trade-offs

Modern APIs and a maintained framework baseline introduce migration differences from Boot 3, including starter/test packaging. A verified published artifact is not evidence of vendor support entitlement or that every library works; the actual build/tests are the compatibility check.

## Failure behavior

An incompatible plugin/starter prevents startup or testing. Keep the exact failure and dependency tree, correct it deliberately, and record a fallback ADR rather than disabling validation.

## Validation — how to prove it

Run `mvn verify` with Java 21 and the frontend build. Record actual JVM and pinned dependency versions in the validation report. Do not claim Boot 4 readiness from artifact existence alone.

Actual execution evidence belongs in the project validation report; this ADR does not claim a test passed merely because it describes one.

## Revisit trigger and migration implications

A reproducible incompatibility blocks the chosen slice, security maintenance changes, or a later library requires a different framework line.

If superseded, retain this record and link the new decision, affected contracts/data, rollback approach and evidence.
