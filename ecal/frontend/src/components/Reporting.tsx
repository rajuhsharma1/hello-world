import { useEffect, useState } from "react";
import { api } from "../api";
import type { ReportSummary } from "../api";
import { money } from "../format";

/** Separate read model: never present its numbers as the transactional order acknowledgement. */
export function Reporting({ refreshKey }: { refreshKey: string }) {
  const [report, setReport] = useState<ReportSummary | null>(null);
  const [error, setError] = useState("");
  async function refresh() {
    try {
      setReport(await api.report());
      setError("");
    } catch {
      setError(
        "Reporting is unavailable. Order processing is independent; retained figures may be stale.",
      );
    }
  }
  useEffect(() => {
    void refresh();
  }, [refreshKey]);
  const max = Math.max(
    1,
    ...(report?.monthlyRevenue.map((r) => Number(r.revenue)) ?? []),
  );
  return (
    <section className="section-block" aria-labelledby="report-title">
      <p className="eyebrow">ASYNCHRONOUS READ MODEL</p>
      <h2 id="report-title">Revenue, after the events arrive.</h2>
      <p>
        Reporting can lag behind checkout. These figures come from a separate
        database.
      </p>
      <button className="text-button" onClick={() => void refresh()}>
        Refresh reporting
      </button>
      {error && <p role="alert">{error}</p>}
      {report && (
        <>
          <p>
            Last projection:{" "}
            {report.lastProjectedAt ?? "No events projected yet"} · Quarantined
            events: {report.quarantinedEvents}
          </p>
          <dl>
            {report.totals.map((t) => (
              <div key={t.status}>
                <dt>{t.status}</dt>
                <dd>
                  {t.orders} orders · {money(t.amount, "INR")}
                </dd>
              </div>
            ))}
          </dl>
          <h3>Monthly confirmed revenue</h3>
          {report.monthlyRevenue.length === 0 && (
            <p>No confirmed revenue projected yet.</p>
          )}
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Month (India)</th>
                  <th>Revenue</th>
                  <th>Relative revenue</th>
                </tr>
              </thead>
              <tbody>
                {report.monthlyRevenue.map((m) => (
                  <tr key={m.month}>
                    <td>{m.month}</td>
                    <td>{money(m.revenue, "INR")}</td>
                    <td>
                      <meter
                        aria-label={m.month + " revenue"}
                        min={0}
                        max={max}
                        value={Number(m.revenue)}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p>{report.freshnessNote}</p>
        </>
      )}
    </section>
  );
}
