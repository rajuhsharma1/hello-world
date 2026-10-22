import type { Order } from "../api";
import { money } from "../format";
/** Displays a server-acknowledged order; it never derives business status from client-side timers. */
export function OrderResult({
  result,
  recovered,
}: {
  result: Order;
  recovered: boolean;
}) {
  return (
    <section
      className={`result-panel ${result.status === "CANCELLED" ? "cancelled" : ""}`}
      aria-labelledby="result-title"
      role="status"
    >
      <div>
        <p className="eyebrow">
          {recovered ? "SAFE REPLAY ACKNOWLEDGED" : "PERSISTED ORDER"}
        </p>
        <h2 id="result-title">
          {result.status === "CONFIRMED"
            ? "Your order is confirmed."
            : "Payment declined. Stock released."}
        </h2>
        <p>
          {result.productName} · {result.quantity}{" "}
          {result.quantity === 1 ? "item" : "items"} ·{" "}
          {money(result.totalAmount, result.currency)}
        </p>
      </div>
      <dl>
        <div>
          <dt>Order reference</dt>
          <dd>{result.id}</dd>
        </div>
        <div>
          <dt>Status</dt>
          <dd>{result.status}</dd>
        </div>
        <div>
          <dt>Simulated payment</dt>
          <dd>{result.paymentStatus}</dd>
        </div>
      </dl>
    </section>
  );
}
