# ADR-013: Fixed demo identity and loopback-only exposure

**Status:** Accepted local restriction; authentication deferred to Phase 5

## Context

Identity is a later milestone. Pretending a buyerId header is authentication would create a misleading security claim.

## Decision — what and why

The server selects one demonstration buyer. Expose the unauthenticated lab only on loopback, use synthetic data, and label its UI/API accordingly. Plan authenticated identity, role and object-level checks before any shared/public deployment.

## Alternatives — why not now

A freely supplied buyerId looks multi-user but permits impersonation. An embedded default production password is unsafe. Adding full OIDC now would enlarge this slice beyond the core transaction learning goal.

## Consequences and trade-offs

All local users of the lab see the same demo history; this is not privacy isolation. Loopback is an exposure restriction, not authentication or defense against a hostile local machine.

## Failure behavior

Changing bindings/firewall makes data accessible without identity checks. The app must not be presented as publicly deployable simply because its Docker containers run. Do not enter real customer or payment data.

## Validation — how to prove it

Inspect Compose/host bindings and negative request tests. In Phase 5 add cross-user object access, roles, session revocation and CSRF/XSS/CORS/CSP tests before changing the exposure policy.

Actual execution evidence belongs in the project validation report; this ADR does not claim a test passed merely because it describes one.

## Revisit trigger and migration implications

Any requirement for multiple real buyers, administrator powers, real data or access from other machines. Authentication and authorization become prerequisites, not optional polish.

If superseded, retain this record and link the new decision, affected contracts/data, rollback approach and evidence.
