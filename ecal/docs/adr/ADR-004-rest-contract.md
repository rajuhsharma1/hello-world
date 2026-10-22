# ADR-004: REST and a checked-in OpenAPI baseline

**Status:** Accepted — current Phase 1

## Context

The UI needs three small resource-oriented operations: browse products, submit checkout and read order status/history. There is no demonstrated client aggregation problem.

## Decision — what and why

Use JSON REST with a checked-in OpenAPI 3.1 contract, explicit request validation, decimal-string money and RFC 9457 problem details. Keep domain logic out of the REST adapter.

## Alternatives — why not now

GraphQL can serve client-specific aggregate reads but adds schema/resolver, authorization and query-complexity concerns. A BFF becomes useful when independent backends or client-specific policies justify it. Neither adds value to this one-service slice.

## Consequences and trade-offs

A static contract is easy to review without binding the domain to documentation annotations, but can drift. Runtime/contract tests and frontend integration must keep it honest; a YAML file alone is not enforcement.

## Failure behavior

Malformed input is 400, missing resources 404, stock/idempotency conflicts 409. Return a stable code and safe detail; do not expose SQL exceptions or rely on clients parsing prose.

## Validation — how to prove it

Exercise the real HTTP API, verify required fields/statuses/problem type and replay behavior, and run the browser journey against the backend. Review contracts/openapi/commerce.yaml alongside controller changes.

Actual execution evidence belongs in the project validation report; this ADR does not claim a test passed merely because it describes one.

## Revisit trigger and migration implications

Multiple clients need materially different data shapes, API change cadence increases, or contract drift warrants code generation/runtime schema validation.

If superseded, retain this record and link the new decision, affected contracts/data, rollback approach and evidence.
