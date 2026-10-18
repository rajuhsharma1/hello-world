# Validation

Validated the complete The Ark Salon & Spa development stack, including HR, finance, reporting and updated visual assets:

- Maven `verify` passed: 16 backend tests, none failed or skipped. New coverage checks CSV quoting/formula neutralization, leap-year/calendar boundaries, CTC consistency and multi-page PDF content including INR symbols and long text.
- Operations-service and gateway images rebuilt successfully with the PDF library and licensed bundled font. Cloud builds used private BuildKit proxy settings and a trusted CA store; TLS verification remained enabled. Unused Docker build cache was cleared after the cloud disk filled; application volumes and data were retained.
- The operations Flyway migration added staff profile fields, versioned contracts/salary structures, expense records and emergency contacts without deleting existing records.
- The gateway and operations smoke suites passed: appointment/contact persistence, conflicts, Kafka notifications, tracing, logs, attendance updates/hours, exact daily collection totals and duplicate-request protection.
- Administration smoke checks passed against real PostgreSQL and the gateway: staff editing; contract/salary PDF inline and attachment responses; invalid CTC rejection; contact creation/update including short emergency numbers; expense replay protection; exact monthly revenue/expense/profit changes; complete day/month/year CSV exports beyond the 50-row page limit; next-month exclusion/month versus year inclusion; escaped formula text and phone preservation.
- All eight Chromium browser tests passed (seven in the main run and the additional staff-security workflow separately). New workflows include staff profile editing, CTC preview/save, real salary/contract PDF downloads, daily expenses, monthly profit/loss PDF, CSV period selection and emergency contact updates. Existing booking, attendance, sales, mobile, keyboard and safe-text-rendering tests still pass.
- The user supplied the jurisdiction (Punjab, India), venue references and address. Contract outputs are explicit English drafts requiring local legal review; no current legal rates or statutory applicability were certified.
- Updated desktop overview and mobile reporting screenshots were inspected; no browser JavaScript errors or horizontal mobile page overflow were observed.
- Venue images are clearly documented as adapted assets based on supplied photos. Google access was blocked, and no scraped images/current ratings or opening hours were claimed.

The deployment continues to publish only gateway port 8080. PDFs and exports contain sensitive staff/client information; accounts and backend role checks protect the APIs and monitoring routes. The default stack remains a local deployment; public hosting needs HTTPS and production configuration. Reports reflect recorded cash-based operations, not audited or statutory accounts. Salary structures are budgets, separate from actual payroll expenses. See README.md for calculations, upgrade instructions and limitations.

Security update validation:

- All four Java services and the gateway images built, and Nginx configuration validation passed. Existing salon database volumes were retained; a separate authentication database was added.
- Real gateway security checks passed for anonymous denial, forced temporary-password change, staff HR/report/log restrictions, roster redaction, CSRF rejection, immediate session revocation, failed-login lockout and last-owner protection.
- Application logs from the auth service and security audit entries were verified through the protected API. Fluent Bit now parses the structured JSON before sending it to Loki.
- Browser sign-in, protected logging and account views, sign-out, forced staff password changes and role-specific navigation passed. All previous six salon workflows passed with authenticated sessions. The new desktop login was visually inspected.
- Local secrets and disposable test credentials remain ignored. No default application account exists on a fresh deployment. The existing Grafana administrator password in this workspace was explicitly rotated to its generated secret.
- Public HTTPS hosting was not deployed. See SECURITY.md for configuration and remaining operational requirements.

## Website, analytics and document design update

- The complete Maven reactor passed **19 backend tests** (4 authentication, 3 booking, 12 operations). New checks cover staff calendar-week/year boundaries, resume type/size limits and long professional PDF content with page totals.
- All **10 Chromium browser tests** passed, including public homepage imagery, mobile layout, complaint submission, careers controls, dashboard/revenue charts, weekly/monthly/yearly staff selectors, manager inbox and private blog drafts. The prior authenticated salon and security workflows continue to pass.
- Real-stack website smoke checks passed: consent/origin/type rejection, private resume round trip, complaint status, queued notification delivery to local Mailpit, draft/published blog visibility, a complete 12-month revenue series and exact staff period counts across week/month/year boundaries. No external Gmail delivery was attempted; SMTP remains intentionally unconfigured.
- All previous booking/Kafka/tracing/logging, operations, administration and security smoke checks passed. The combined employment and salary PDF was also downloaded through the protected gateway.
- Public desktop/mobile screens, dashboard/statistics and a rendered salary PDF were inspected. The generated spa-room hero is labelled illustrative; no current venue imagery or footage is falsely claimed. Optional video playback is supported through a manager-configured HTTPS URL; no video was supplied or configured.
- The new Flyway migration preserves existing records and adds website settings, enquiry/resume storage, notification outbox and journal posts. All databases remain private; only gateway port 8080 is published.
- Full multi-stage Docker rebuilds hit the cloud disk limit. Unused build cache was cleared without removing application volumes. Java services were run from runtime images containing the locally verified Maven jars; the gateway built normally. The normal multi-stage rebuild was not completed in this constrained cloud instance.
