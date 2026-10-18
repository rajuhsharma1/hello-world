# Validation

Validated the UI and one-port stack in the cloud workspace:

- Both Spring Boot services pass Maven `verify`: three tests passed, none failed or skipped.
- All three application Docker images built: booking service, notification service, and Nginx UI/gateway. Cloud builds used optional Maven proxy settings and a trusted Java CA store through BuildKit secrets, with no TLS bypass or credentials in image files.
- The full default Compose stack started with containerized applications, databases, Kafka and observability services. No host Java processes or development port overrides were used.
- Both resolved Compose configuration and actual Docker port bindings confirmed that only `127.0.0.1:8080` is published, by the gateway. Internal container ports have no host bindings.
- `python3 scripts/smoke.py` passed through that gateway: UI served, both backend health checks UP, appointment creation/read, HTTP 409 for duplicate bookings, Kafka notification persistence, a cross-service trace containing the Kafka consumer span, and the appointment log in Loki.
- Grafana `/grafana/api/health` reported database OK and `/grafana/login` returned its UI through the gateway.
- Three Playwright browser tests passed using Chromium: real UI booking/search/Kafka confirmation/duplicate handling; partial notification outage and safe literal rendering of HTML-like guest names; mobile layout and keyboard dialog focus/escape behavior.
- Desktop and mobile screenshots were inspected. JavaScript syntax, Compose configuration and whitespace checks passed.

CI runs the smoke test before browser tests to wait for readiness.

Default credentials, plaintext Kafka and unauthenticated APIs are intended for local development only. See README.md for feature boundaries and startup commands. Live containers must be restarted after a cloud environment restart; saved startup instructions use the built images when available.
