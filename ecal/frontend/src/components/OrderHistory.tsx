import type { Order } from "../api";
import { money, when } from "../format";
/** A read-only projection of REST results; callbacks keep network behavior in the journey coordinator. */
export function OrderHistory({
  controls,
  orders,
  loading,
  historyError,
  onRefresh,
  onView,
}: {
  controls: React.ReactNode;
  orders: Order[];
  loading: boolean;
  historyError: string;
  onRefresh: () => void;
  onView: (id: string) => void;
}) {
  return (
    <section
      id="orders"
      className="section-block"
      aria-labelledby="orders-title"
    >
      <div className="section-heading">
        <div>
          <p className="eyebrow">03 / FOLLOW THE OUTCOME</p>
          <h2 id="orders-title">Orders, with a memory.</h2>
        </div>
        <button className="text-button" onClick={() => onRefresh()}>
          Refresh history <span aria-hidden="true">↻</span>
        </button>
      </div>
      <p className="section-description">
        Twenty orders per page for the shared demo buyer, read from PostgreSQL.
        Refreshing the page does not erase them. Intermediate states represent
        paused work, not a declined payment.
      </p>
      {controls}
      {historyError && (
        <p className="notice error" role="alert">
          {historyError}
        </p>
      )}
      {!orders.length && !loading && !historyError && (
        <p className="empty-state">
          Your first order starts the story. Choose a product to begin.
        </p>
      )}
      {orders.length > 0 && (
        <div className="table-scroll">
          <table>
            <caption className="sr-only">Persisted order history</caption>
            <thead>
              <tr>
                <th>Product / reference</th>
                <th>Placed</th>
                <th>Quantity</th>
                <th>Total</th>
                <th>Status</th>
                <th>
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => (
                <tr key={o.id}>
                  <td>
                    <strong>{o.productName}</strong>
                    <small>{o.id}</small>
                  </td>
                  <td>{when(o.createdAt)}</td>
                  <td>{o.quantity}</td>
                  <td>{money(o.totalAmount, o.currency)}</td>
                  <td>
                    <span className={`status-badge ${o.status.toLowerCase()}`}>
                      {o.status}
                    </span>
                  </td>
                  <td>
                    <button
                      className="text-button"
                      onClick={() => onView(o.id)}
                      aria-label={`View order ${o.id}`}
                    >
                      View <span aria-hidden="true">↗</span>
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
