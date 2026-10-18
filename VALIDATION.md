# Validation

Validated The Ark Salon & Spa changes against the complete container stack:

- All Maven modules built. Eight backend tests passed (three booking/outbox tests and five operations tests), with no failures or skips.
- All four application images built: booking-service, notification-service, operations-service and gateway. Cloud-only Maven proxy settings and trusted CA stores were supplied through private BuildKit secrets; TLS verification remained enabled.
- Flyway upgraded both existing appointment and notification databases. All three pre-upgrade rows in each database were preserved; subsequent test bookings increased those counts. Active appointment responses contain `contactNumber` and omit `customerEmail`. Historical email values remain private in legacy database columns rather than being destroyed by the migration.
- The normal Compose deployment runs all three APIs and PostgreSQL databases, Kafka, Grafana, Loki, Zipkin and Fluent Bit. Only the gateway publishes host port 8080.
- The gateway smoke test passed: branding/UI served, all three service health endpoints UP, booking/read/conflict handling, Kafka notification persistence, a cross-service consumer trace and application logs in Loki.
- Operations smoke checks passed against PostgreSQL: attendance hours, update-in-place without duplicate daily attendance, invalid shift rejection, exact UPI/cash/card totals, repeated sales requests without duplicate entries, and invalid amounts rejected.
- Four Chromium browser tests passed: booking/contact/confirmation/conflict; partial notification failure and safe text rendering; mobile layout and keyboard dialog controls; staff creation, attendance updates and persisted daily client entries using all payment methods.
- Desktop and mobile screenshots of attendance and daily sales were inspected. No horizontal page overflow was observed at 390px; wide register tables scroll within their panels.
- JavaScript syntax, Compose configuration, and whitespace checks passed. CI runs Maven, gateway and operations smoke checks, then browser tests.

Attendance times and business dates default to Asia/Kolkata. Appointment times use the browser's local time zone. Sales record payments already received; they do not collect payments or process refunds. Authentication, payroll, split payments and SMS delivery remain outside this development starter. See README.md for startup and upgrade instructions.
