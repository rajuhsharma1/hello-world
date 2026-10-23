# ADR-019: Bounded versioned HTTP and explicit log correlation

**Status:** Accepted — Phase 3.

## Context and decision

Use public `/api/v1` and private `/internal/v1` contracts; public `/api` aliases preserve the existing UI. Fixed private Compose service names suffice. Keep Nginx for one browser origin/assets/API proxy. Do not add a separate BFF, registry or cloud gateway without a requirement.

Use Java HttpClient with finite connect/request deadlines (defaults 500/2000 ms), no redirects or application-level immediate retry loop. Treat missing/mismatched 2xx business acknowledgements as uncertain. Independent client DTOs accept additive response fields. Propagate validated/generated UUID X-Correlation-ID and W3C trace ID/child span IDs/sampling flags; expose X-Correlation-ID and X-Trace-ID in responses. Structured logs correlate the attempt and stable order identity. UI errors show a support reference.

## Alternatives and tradeoffs

Infinite timeouts consume request capacity; aggressive retries amplify outages. Circuit breakers/backoff may be justified under measured sustained failure, but add policy and state now. gRPC has stronger generated contracts but adds another tooling experiment without a current need. Full OpenTelemetry export/collector/dashboards remain Phase 6 work; IDs alone are not complete distributed tracing. Gateway orchestration would obscure business ownership and is avoided.

HTTP dependencies compound latency/availability, even with short deadlines. Transport-level retransmission may still occur; immutable identities are required regardless of client retry configuration. A bounded timeout is not an achieved latency objective. These APIs are private/loopback learning surfaces with no authentication; trace IDs are not authorization.

## Failures, proof and revisit

A timeout never proves rollback. Real delayed-response tests and the durable coordinator establish recovery. Contract field guards complement live HTTP tests and additive/malformed response tests. Trace-header propagation and UI support-reference tests establish correlation behavior. Actual hosted CI, throughput and tracing export are not inferred. Revisit gateway, discovery, resilience or tracing tooling when deployment topology and operational objectives justify them.
