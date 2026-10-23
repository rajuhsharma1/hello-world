import { useEffect, useState, useRef } from "react";
import {
  api,
  ApiError,
  restorePending,
  savePending,
  terminalOrder,
} from "./api";
import type { Order, PendingCheckout, Product } from "./api";
import { money } from "./format";
import { ProductCard } from "./components/ProductCard";
import { OrderResult } from "./components/OrderResult";
import { CatalogImport } from "./components/CatalogImport";
import { Reporting } from "./components/Reporting";
import { OrderHistory } from "./components/OrderHistory";

/** Owns one buyer journey; server data and transient form state do not yet warrant a global store. */
export default function App() {
  const events = import.meta.env.VITE_ECAL_MODE === "events";
  const distributed =
    events || import.meta.env.VITE_ECAL_MODE === "distributed";
  const [products, setProducts] = useState<Product[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [selected, setSelected] = useState("");
  const [quantity, setQuantity] = useState(1);
  const [outcome, setOutcome] = useState<"APPROVE" | "DECLINE">("APPROVE");
  const [pending, setPending] = useState<PendingCheckout | null>(
    restorePending,
  );
  const [result, setResult] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);
  const [catalogError, setCatalogError] = useState("");
  const [historyError, setHistoryError] = useState("");
  const [checkoutError, setCheckoutError] = useState("");
  const [busy, setBusy] = useState(false);
  const [recovered, setRecovered] = useState(false);
  const [acceptedId, setAcceptedId] = useState<string | null>(null);

  const [catalogCursors, setCatalogCursors] = useState<(string | null)[]>([
    null,
  ]);
  const [catalogNext, setCatalogNext] = useState<string | null>(null);
  const [orderCursors, setOrderCursors] = useState<(string | null)[]>([null]);
  const [orderNext, setOrderNext] = useState<string | null>(null);
  const [status, setStatus] = useState("ALL");
  const [catalogBusy, setCatalogBusy] = useState(false),
    [historyBusy, setHistoryBusy] = useState(false);
  const catalogVersion = useRef(0),
    orderVersion = useRef(0);
  async function loadCatalog(cursors: (string | null)[] = [null]) {
    const version = ++catalogVersion.current;
    setCatalogBusy(true);
    try {
      const page = await api.productsPage(cursors.at(-1) ?? null);
      if (version !== catalogVersion.current) return;
      setProducts(page.items);
      setCatalogNext(page.nextCursor);
      setCatalogCursors(cursors);
      setCatalogError("");
    } catch {
      if (version !== catalogVersion.current) return;
      setCatalogError(
        "Catalog unavailable. Check that the backend is running, then refresh.",
      );
    } finally {
      if (version === catalogVersion.current) setCatalogBusy(false);
    }
  }
  async function loadOrders(
    cursors: (string | null)[] = [null],
    filter = status,
  ) {
    const version = ++orderVersion.current;
    setHistoryBusy(true);
    try {
      const page = await api.ordersPage(filter, cursors.at(-1) ?? null);
      if (version !== orderVersion.current) return;
      setOrders(page.items);
      setOrderNext(page.nextCursor);
      setOrderCursors(cursors);
      setStatus(filter);
      setHistoryError("");
    } catch {
      if (version !== orderVersion.current) return;
      setHistoryError(
        "Order history is temporarily unavailable. Your saved orders remain in the database.",
      );
    } finally {
      if (version === orderVersion.current) setHistoryBusy(false);
    }
  }
  useEffect(() => {
    // Reads may run twice under development StrictMode. Commands are only sent by an explicit user action.
    let active = true;
    Promise.all([loadCatalog(), loadOrders()]).finally(() => {
      if (active) setLoading(false);
    });
    return () => {
      active = false;
    };
  }, []);

  const product = products.find(
    (p) => p.id === (pending?.command.productId ?? selected),
  );
  const effectiveQuantity = pending?.command.quantity ?? quantity;
  const effectiveOutcome = pending?.command.paymentOutcome ?? outcome;

  async function checkout(event: React.SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || (!product && !pending)) return;
    const operation = pending ?? {
      key: crypto.randomUUID(),
      command: { productId: selected, quantity, paymentOutcome: outcome },
    };
    setBusy(true);
    setCheckoutError("");
    setResult(null);
    let requestStarted = false;
    try {
      // Both payload and key are frozen after an uncertain outcome; editing either would break safe replay.
      setPending(operation);
      savePending(operation);
      requestStarted = true;
      const order = await api.checkout(operation);
      setResult(order);
      setRecovered(Boolean(pending));
      if (events && !terminalOrder(order)) {
        setAcceptedId(order.id);
      } else {
        savePending(null);
        setPending(null);
        setAcceptedId(null);
      }
      await Promise.all([loadCatalog(), loadOrders()]);
    } catch (error) {
      if (error instanceof ApiError && error.isTerminal) {
        savePending(null);
        setPending(null);
        setCheckoutError(error.message);
        await loadCatalog();
      } else if (!requestStarted) {
        setCheckoutError(
          "No checkout was sent: this browser could not save its retry identity. Allow session storage and retry.",
        );
      } else {
        setCheckoutError(
          "We could not verify the checkout outcome. Retry the same checkout safely; it may already have committed." +
            (error instanceof ApiError && error.correlationId
              ? " Support reference: " + error.correlationId
              : ""),
        );
      }
    } finally {
      setBusy(false);
    }
  }

  // Poll only acknowledged order IDs. A lost POST response still needs its original key on replay.
  useEffect(() => {
    if (!events || !acceptedId || !pending) return;
    let active = true;
    const timer = window.setInterval(() => {
      void api
        .observe(acceptedId, pending)
        .then((order) => {
          if (!active) return;
          setResult(order);
          if (terminalOrder(order)) {
            savePending(null);
            setPending(null);
            setAcceptedId(null);
            void Promise.all([loadCatalog(), loadOrders()]);
          }
        })
        .catch(() => {
          /* Keep the durable identity during a read outage. */
        });
    }, 1500);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [events, acceptedId, pending]);

  async function viewOrder(id: string) {
    try {
      setResult(await api.order(id));
      setRecovered(false);
    } catch {
      setHistoryError(
        "Unable to load that order. Refresh history and try again.",
      );
    }
  }

  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="masthead">
        <a className="brand" href="#main" aria-label="ECAL home">
          <span className="brand-mark">E</span>
          <span>
            ECAL<small>COMMERCE ARCHITECTURE LAB</small>
          </span>
        </a>
        <nav aria-label="Main navigation">
          <a href="#catalog">Catalog</a>
          <a href="#checkout">Checkout</a>
          <a href="#orders">Order history</a>
        </nav>
        <span className="phase-label">
          <span />{" "}
          {events
            ? "PHASE 04 · EVENT-DRIVEN"
            : distributed
              ? "PHASE 03 · THREE SERVICES"
              : "PHASE 02 · LOCAL LAB"}
        </span>
      </header>
      <main id="main">
        <section className="hero">
          <div>
            <p className="eyebrow">A SMALL APPLICATION. REAL ARCHITECTURE.</p>
            <h1>
              Every order has
              <br />a story behind it.
            </h1>
            <p className="hero-description">
              {distributed
                ? "Explore a checkout across Orders, Inventory and Payments, with durable progress and recovery across independent databases."
                : "Explore a commerce flow where inventory, payment simulation and order state agree—in one local transaction."}
            </p>
            <a className="primary-link" href="#catalog">
              Explore the catalog <span aria-hidden="true">→</span>
            </a>
          </div>
          <aside
            className="architecture-card"
            aria-label="Checkout transaction overview"
          >
            <div className="architecture-heading">
              <span className="status-dot" />{" "}
              {events
                ? "KAFKA + DURABLE SAGA"
                : distributed
                  ? "DISTRIBUTED CHECKOUT"
                  : "MODULAR MONOLITH"}
            </div>
            <ol>
              <li>
                <span>01</span>
                <div>
                  <strong>Choose a product</strong>
                  <small>Read a server-owned price</small>
                </div>
              </li>
              <li>
                <span>02</span>
                <div>
                  <strong>Reserve inventory</strong>
                  <small>Atomic stock checks prevent overselling</small>
                </div>
              </li>
              <li>
                <span>03</span>
                <div>
                  <strong>Simulate a payment</strong>
                  <small>Approve or decline—no real money</small>
                </div>
              </li>
              <li>
                <span>04</span>
                <div>
                  <strong>Persist the outcome</strong>
                  <small>
                    {distributed
                      ? "Local commits, recoverable progress"
                      : "One transaction, a durable order"}
                  </small>
                </div>
              </li>
            </ol>
            <div className="transaction-caption">
              {distributed ? (
                <>
                  {events
                    ? "FOUR SERVICES · FOUR DATABASES · KAFKA"
                    : "THREE SERVICES · THREE DATABASES"}
                </>
              ) : (
                <>
                  ONE PROCESS <span>·</span> ONE POSTGRESQL DATABASE
                </>
              )}
            </div>
          </aside>
        </section>
        <div className="lab-notice">
          <span aria-hidden="true">ⓘ</span>
          <p>
            <strong>A learning environment, deliberately.</strong> One shared
            demo buyer, no sign-in and simulated payments only. Run on loopback;
            do not expose this phase publicly.
          </p>
        </div>
        <section
          id="catalog"
          className="section-block"
          aria-labelledby="catalog-title"
        >
          <div className="section-heading">
            <div>
              <p className="eyebrow">01 / DISCOVER</p>
              <h2 id="catalog-title">A considered collection.</h2>
            </div>
            <button
              className="text-button"
              onClick={() => void loadCatalog()}
              disabled={loading || catalogBusy}
            >
              Refresh catalog <span aria-hidden="true">↻</span>
            </button>
          </div>
          {catalogError && (
            <p className="notice error" role="alert">
              {catalogError}
            </p>
          )}
          {loading ? (
            <p role="status" className="empty-state">
              Loading products…
            </p>
          ) : !products.length && !catalogError ? (
            <p className="empty-state">
              No products available. Seed the local database to begin.
            </p>
          ) : null}
          <div className="pagination" aria-label="Catalog pages">
            <button
              disabled={catalogBusy || catalogCursors.length === 1}
              onClick={() => void loadCatalog(catalogCursors.slice(0, -1))}
            >
              Previous products
            </button>
            <span>Page {catalogCursors.length}</span>
            <button
              disabled={catalogBusy || !catalogNext}
              onClick={() => void loadCatalog([...catalogCursors, catalogNext])}
            >
              Next products
            </button>
          </div>
          <div className="product-grid">
            {products.map((p, i) => (
              <ProductCard
                key={p.id}
                product={p}
                index={i}
                selected={product?.id === p.id}
                locked={Boolean(pending)}
                onChoose={(id) => {
                  setSelected(id);
                  setQuantity(1);
                  setResult(null);
                  setCheckoutError("");
                  document.getElementById("checkout")?.scrollIntoView({
                    behavior: matchMedia("(prefers-reduced-motion: reduce)")
                      .matches
                      ? "instant"
                      : "smooth",
                    block: "start",
                  });
                }}
              />
            ))}
          </div>
        </section>
        <section
          id="checkout"
          className="section-block checkout-layout"
          aria-labelledby="checkout-title"
        >
          <div className="checkout-intro">
            <p className="eyebrow">02 / CHECKOUT</p>
            <h2 id="checkout-title">
              Make the invariants
              <br />
              do the work.
            </h2>
            <p>
              {events
                ? "Orders are accepted durably, then completed in the background. Kafka starts a recoverable Saga; reporting follows asynchronously. You can close the browser after acceptance. Refresh history to see completion."
                : distributed
                  ? "Try approval and decline across three services. A decline is only shown as cancelled after stock release is acknowledged. A timeout keeps durable progress; replay the same checkout to resume without another stock or payment effect."
                  : "Try an approved payment, then a declined one. A decline cancels the order and returns its stock. Replay an uncertain request with the same key to avoid a duplicate effect."}
            </p>
            <div className="boundary-note">
              <strong>
                {distributed
                  ? "Independent transaction boundaries"
                  : "The transaction boundary"}
              </strong>
              <p>
                {distributed
                  ? "Orders records intent, Inventory owns reservations, and Payments records an idempotent simulation. Each owns a private database. HTTP timeouts leave recoverable state; no cross-service ACID transaction is claimed."
                  : "Order + inventory + simulated payment + notification record. No network payment call or Kafka publish is hidden inside this transaction."}
              </p>
            </div>
          </div>
          <div className="checkout-panel">
            <form onSubmit={checkout} aria-label="Checkout">
              <div className="checkout-product">
                <span className="eyebrow">YOUR SELECTION</span>
                <h3>
                  {product?.name ??
                    (pending
                      ? "Recovering checkout"
                      : "Choose a product above")}
                </h3>
                <p>
                  {product
                    ? `${money(product.price, product.currency)} per item`
                    : pending
                      ? "The original product and request are preserved for safe retry."
                      : "A single-product order keeps this first slice focused."}
                </p>
              </div>
              <label htmlFor="quantity">Quantity</label>
              <input
                id="quantity"
                type="number"
                min="1"
                max="10"
                required
                value={effectiveQuantity}
                disabled={Boolean(pending) || busy || !product}
                onChange={(e) => setQuantity(Number(e.target.value))}
              />
              <label htmlFor="payment">Simulated payment outcome</label>
              <select
                id="payment"
                value={effectiveOutcome}
                disabled={Boolean(pending) || busy}
                onChange={(e) =>
                  setOutcome(e.target.value as "APPROVE" | "DECLINE")
                }
              >
                <option value="APPROVE">Approve · confirm the order</option>
                <option value="DECLINE">
                  Decline · cancel and release stock
                </option>
              </select>
              <p className="field-hint">
                No card, bank account or payment credentials are used.
              </p>
              {product && (
                <div className="order-summary">
                  <span>Estimated total</span>
                  <strong>
                    {money(
                      (Number(product.price) * effectiveQuantity).toFixed(2),
                      product.currency,
                    )}
                  </strong>
                  <small>The backend calculates the authoritative total.</small>
                </div>
              )}
              {pending && (
                <p className="notice pending" role="status">
                  Unresolved checkout retained. Its product, quantity and
                  simulation are locked until the same request is acknowledged.
                </p>
              )}
              {checkoutError && (
                <p className="notice error" role="alert">
                  {checkoutError}
                </p>
              )}
              <button
                className="primary-button"
                type="submit"
                disabled={busy || Boolean(acceptedId) || (!product && !pending)}
              >
                {acceptedId
                  ? "Accepted · waiting for background completion"
                  : busy
                    ? "Processing checkout…"
                    : pending
                      ? "Retry same checkout"
                      : "Place simulated order"}
                <span aria-hidden="true">→</span>
              </button>
            </form>
          </div>
        </section>
        {result && <OrderResult result={result} recovered={recovered} />}
        {events && <Reporting refreshKey={result?.status ?? ""} />}
        <OrderHistory
          orders={orders}
          controls={
            <div className="pagination">
              <label htmlFor="history-status">Order status</label>
              <select
                id="history-status"
                value={status}
                disabled={historyBusy}
                onChange={(e) => void loadOrders([null], e.target.value)}
              >
                <option value="ALL">All outcomes</option>
                <option value="CONFIRMED">Confirmed</option>
                <option value="CANCELLED">Cancelled</option>
              </select>
              <button
                disabled={historyBusy || orderCursors.length === 1}
                onClick={() => void loadOrders(orderCursors.slice(0, -1))}
              >
                Newer orders
              </button>
              <span>Page {orderCursors.length}</span>
              <button
                disabled={historyBusy || !orderNext}
                onClick={() => void loadOrders([...orderCursors, orderNext])}
              >
                Older orders
              </button>
            </div>
          }
          loading={loading || historyBusy}
          historyError={historyError}
          onRefresh={() => void loadOrders()}
          onView={(id) => void viewOrder(id)}
        />
        {!distributed && (
          <CatalogImport onImported={() => void loadCatalog()} />
        )}
      </main>
      <footer>
        <a className="brand footer-brand" href="#main">
          <span className="brand-mark">E</span>
          <span>
            ECAL<small>BUILD SMALL. UNDERSTAND DEEPLY.</small>
          </span>
        </a>
        <p>
          {events
            ? "Phase 4 · transactional events and autonomous recovery."
            : distributed
              ? "Phase 3 · independent services and durable recovery."
              : "Phase 2 · bounded imports and indexed read paths."}
          <br />
          Architecture is a set of decisions you can explain and prove.
        </p>
        <span>SIMULATED COMMERCE · LOCAL FIRST</span>
      </footer>
    </>
  );
}
