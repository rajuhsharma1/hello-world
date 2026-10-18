# Validation

Validated the complete The Ark Salon & Spa development stack, including HR, finance, reporting and updated visual assets:

- Maven `verify` passed: 12 backend tests, none failed or skipped. New coverage checks CSV quoting/formula neutralization, leap-year/calendar boundaries, CTC consistency and multi-page PDF content including INR symbols and long text.
- Operations-service and gateway images rebuilt successfully with the PDF library and licensed bundled font. Cloud builds used private BuildKit proxy settings and a trusted CA store; TLS verification remained enabled. Unused Docker build cache was cleared after the cloud disk filled; application volumes and data were retained.
- The operations Flyway migration added staff profile fields, versioned contracts/salary structures, expense records and emergency contacts without deleting existing records.
- The gateway and operations smoke suites passed: appointment/contact persistence, conflicts, Kafka notifications, tracing, logs, attendance updates/hours, exact daily collection totals and duplicate-request protection.
- Administration smoke checks passed against real PostgreSQL and the gateway: staff editing; contract/salary PDF inline and attachment responses; invalid CTC rejection; contact creation/update including short emergency numbers; expense replay protection; exact monthly revenue/expense/profit changes; complete day/month/year CSV exports beyond the 50-row page limit; next-month exclusion/month versus year inclusion; escaped formula text and phone preservation.
- All six Chromium browser tests passed. New workflows include staff profile editing, CTC preview/save, real salary/contract PDF downloads, daily expenses, monthly profit/loss PDF, CSV period selection and emergency contact updates. Existing booking, attendance, sales, mobile, keyboard and safe-text-rendering tests still pass.
- The user supplied the jurisdiction (Punjab, India), venue references and address. Contract outputs are explicit English drafts requiring local legal review; no current legal rates or statutory applicability were certified.
- Updated desktop overview and mobile reporting screenshots were inspected; no browser JavaScript errors or horizontal mobile page overflow were observed.
- Venue images are clearly documented as adapted assets based on supplied photos. Google access was blocked, and no scraped images/current ratings or opening hours were claimed.

The deployment continues to publish only gateway port 8080. PDFs and exports contain sensitive staff/client information; the starter has no authentication and is intended for isolated development. Reports reflect recorded cash-based operations, not audited or statutory accounts. Salary structures are budgets, separate from actual payroll expenses. See README.md for calculations, upgrade instructions and limitations.
