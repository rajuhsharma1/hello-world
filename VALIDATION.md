# Validation

Validated in the cloud workspace using Java 21 and Maven 3.9.9:

- Maven reactor `verify`: both services built successfully.
- Three tests passed: request validation, failed Kafka publication keeps the outbox pending, successful publication marks it sent.
- `scripts/smoke.py` passed against the application JARs running on the host, with real PostgreSQL, Kafka, Zipkin, Fluent Bit, Loki and Grafana containers.
- The smoke test verified appointment persistence, HTTP 409 for duplicate slots, asynchronous notification persistence, a shared trace including the notification Kafka consumer span, and the appointment log in Loki.
- Grafana health endpoint reported database OK.
- Docker Compose configuration validation passed.

Application Docker image builds and the complete all-container startup have not been run in this workspace. CI is configured to build those images and run the same smoke test. The current development runtime uses host Java processes and a local log-bind override; these live processes will not survive environment publication or restart. Use the README startup commands in a new environment.
