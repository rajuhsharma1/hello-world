import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  render,
  screen,
  waitFor,
  within,
  fireEvent,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "./App";
import type { Order, Product } from "./api";

const product: Product = {
  id: "10000000-0000-0000-0000-000000000001",
  name: "Field notebook",
  description: "A thoughtful everyday notebook.",
  price: "499.00",
  currency: "INR",
  availableStock: 5,
};
const confirmed: Order = {
  id: "20000000-0000-0000-0000-000000000001",
  productId: product.id,
  productName: product.name,
  quantity: 1,
  unitPrice: "499.00",
  totalAmount: "499.00",
  currency: "INR",
  status: "CONFIRMED",
  paymentStatus: "AUTHORIZED",
  createdAt: "2026-01-01T10:00:00Z",
};
const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json" },
  });
let orders: Order[];
let post: (init: RequestInit) => Promise<Response>;
let fetchMock: ReturnType<typeof vi.fn>;
beforeEach(() => {
  orders = [];
  post = async () => {
    orders = [confirmed];
    return json(confirmed, 201);
  };
  fetchMock = vi.fn(async (url: string, init: RequestInit = {}) => {
    if (url.startsWith("/api/catalog/products/page"))
      return json({ items: [product], nextCursor: null });
    if (url === "/api/orders" && init.method === "POST") return post(init);
    if (url.startsWith("/api/orders/page"))
      return json({ items: orders, nextCursor: null });
    if (url.startsWith("/api/orders/")) return json(confirmed);
    throw new Error("Unexpected request " + url);
  });
  vi.stubGlobal("fetch", fetchMock);
});

async function choose() {
  const user = userEvent.setup();
  render(<App />);
  await user.click(
    await screen.findByRole("button", { name: /Choose Field notebook/ }),
  );
  return user;
}

describe("buyer journey", () => {
  it("places an order, sends only server-priced product/quantity, and displays persisted status", async () => {
    const user = await choose();
    await user.click(
      screen.getByRole("button", { name: /Place simulated order/ }),
    );
    await screen.findByRole("heading", { name: "Your order is confirmed." });
    const mutation = fetchMock.mock.calls.find(
      ([, init]) => init?.method === "POST",
    )!;
    expect(JSON.parse(String(mutation[1].body))).toEqual({
      productId: product.id,
      quantity: 1,
      paymentOutcome: "APPROVE",
    });
    expect(mutation[1].headers).toHaveProperty("Idempotency-Key");
    await waitFor(() =>
      expect(
        within(screen.getByRole("table")).getByText(confirmed.id),
      ).toBeVisible(),
    );
    expect(sessionStorage.getItem("ecal.pendingCheckout.v1")).toBeNull();
  });
  it("preserves the exact key and payload on a lost response, locks edits, then safely retries", async () => {
    let original: RequestInit | undefined;
    post = async (init) => {
      if (!original) {
        original = init;
        throw new TypeError("Network response lost");
      }
      orders = [confirmed];
      return json(confirmed);
    };
    const user = await choose();
    await user.click(
      screen.getByRole("button", { name: /Place simulated order/ }),
    );
    await screen.findByRole("alert");
    expect(screen.getByLabelText("Quantity")).toBeDisabled();
    expect(screen.getByLabelText("Simulated payment outcome")).toBeDisabled();
    const retained = JSON.parse(
      sessionStorage.getItem("ecal.pendingCheckout.v1")!,
    );
    expect(retained.command.quantity).toBe(1);
    await user.click(
      screen.getByRole("button", { name: /Retry same checkout/ }),
    );
    await screen.findByRole("heading", { name: "Your order is confirmed." });
    const calls = fetchMock.mock.calls.filter(
      ([, init]) => init?.method === "POST",
    );
    expect(calls[0][1].headers).toEqual(calls[1][1].headers);
    expect(calls[0][1].body).toEqual(calls[1][1].body);
  });
  it("restores an unresolved checkout after reload instead of minting a new identity", async () => {
    const retained = {
      key: "30000000-0000-0000-0000-000000000001",
      command: {
        productId: product.id,
        quantity: 2,
        paymentOutcome: "APPROVE",
      },
    };
    post = async () =>
      json({ ...confirmed, quantity: 2, totalAmount: "998.00" });
    sessionStorage.setItem("ecal.pendingCheckout.v1", JSON.stringify(retained));
    render(<App />);
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: /Retry same checkout/ }),
    );
    await screen.findByRole("heading", { name: "Your order is confirmed." });
    const mutation = fetchMock.mock.calls.find(
      ([, init]) => init?.method === "POST",
    )!;
    expect(mutation[1].headers).toHaveProperty("Idempotency-Key", retained.key);
    expect(JSON.parse(String(mutation[1].body))).toEqual(retained.command);
  });
  it("makes payment decline explicit without presenting it as a network failure", async () => {
    post = async () =>
      json(
        { ...confirmed, status: "CANCELLED", paymentStatus: "DECLINED" },
        201,
      );
    const user = await choose();
    await user.selectOptions(
      screen.getByLabelText("Simulated payment outcome"),
      "DECLINE",
    );
    await user.click(
      screen.getByRole("button", { name: /Place simulated order/ }),
    );
    await screen.findByRole("heading", {
      name: "Payment declined. Stock released.",
    });
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(sessionStorage.getItem("ecal.pendingCheckout.v1")).toBeNull();
  });
  it("shows authoritative insufficient-stock errors and allows a revised checkout", async () => {
    post = async () =>
      json({ detail: "Insufficient stock.", code: "INSUFFICIENT_STOCK" }, 409);
    const user = await choose();
    await user.click(
      screen.getByRole("button", { name: /Place simulated order/ }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Insufficient stock.",
    );
    expect(screen.getByLabelText("Quantity")).toBeEnabled();
    expect(sessionStorage.getItem("ecal.pendingCheckout.v1")).toBeNull();
  });
  it("retains the retry identity on malformed 2xx acknowledgements", async () => {
    post = async () => json({}, 200);
    const user = await choose();
    await user.click(
      screen.getByRole("button", { name: /Place simulated order/ }),
    );
    await screen.findByRole("alert");
    expect(
      screen.queryByRole("heading", { name: "Your order is confirmed." }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Retry same checkout/ }),
    ).toBeEnabled();
    expect(sessionStorage.getItem("ecal.pendingCheckout.v1")).not.toBeNull();
  });
  it("does not treat a proxy timeout as a definitive business rejection", async () => {
    post = async () => json({ detail: "Proxy timeout" }, 408);
    const user = await choose();
    await user.click(
      screen.getByRole("button", { name: /Place simulated order/ }),
    );
    await screen.findByRole("alert");
    expect(screen.getByLabelText("Quantity")).toBeDisabled();
    expect(sessionStorage.getItem("ecal.pendingCheckout.v1")).not.toBeNull();
  });
  it("does not turn a server 500 into a fresh checkout key", async () => {
    post = async () => json({ detail: "Unavailable" }, 500);
    const user = await choose();
    await user.click(
      screen.getByRole("button", { name: /Place simulated order/ }),
    );
    await screen.findByRole("alert");
    expect(
      screen.getByRole("button", { name: /Retry same checkout/ }),
    ).toBeEnabled();
    expect(sessionStorage.getItem("ecal.pendingCheckout.v1")).not.toBeNull();
  });
});

describe("Phase 2 read and import controls", () => {
  it("navigates catalog cursors and resets after refresh", async () => {
    const original = fetchMock.getMockImplementation() as (
      url: string,
      init?: RequestInit,
    ) => Promise<Response>;
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
      if (url.startsWith("/api/catalog/products/page"))
        return url.includes("cursor=")
          ? json({
              items: [
                {
                  ...product,
                  id: "10000000-0000-0000-0000-000000000002",
                  name: "Second page lamp",
                },
              ],
              nextCursor: null,
            })
          : json({ items: [product], nextCursor: "opaque-next-cursor" });
      return original(url, init);
    });
    const user = userEvent.setup();
    render(<App />);
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "Next products" }),
      ).toBeEnabled(),
    );
    await user.click(screen.getByRole("button", { name: "Next products" }));
    await screen.findByRole("button", { name: /Choose Second page lamp/ });
    expect(
      screen.getByRole("button", { name: "Next products" }),
    ).toBeDisabled();
    await user.click(screen.getByRole("button", { name: /Refresh catalog/ }));
    await screen.findByRole("button", { name: /Choose Field notebook/ });
    expect(
      screen.getByRole("button", { name: "Previous products" }),
    ).toBeDisabled();
  });
  it("passes the selected order filter to the server", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByRole("button", { name: /Choose Field notebook/ });
    await user.selectOptions(
      screen.getByLabelText("Order status"),
      "CANCELLED",
    );
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(
          ([url]) => url === "/api/orders/page?limit=20&status=CANCELLED",
        ),
      ).toBe(true),
    );
  });
  it("shows partial import outcomes and a safe replay instruction", async () => {
    const original = fetchMock.getMockImplementation() as (
      url: string,
      init?: RequestInit,
    ) => Promise<Response>;
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) =>
      url === "/api/catalog/import"
        ? json({
            totalRows: 51,
            created: 50,
            skipped: 0,
            committedBatches: 1,
            failedBatches: 1,
            batches: [
              {
                index: 2,
                status: "ROLLED_BACK",
                rows: 1,
                created: 0,
                skipped: 0,
                error: "Check fields.",
              },
            ],
          })
        : original(url, init),
    );
    const user = userEvent.setup();
    render(<App />);
    await user.upload(
      screen.getByLabelText("Catalog CSV"),
      new File(["csv"], "catalog.csv", { type: "text/csv" }),
    );
    // jsdom's required file validity does not observe user-event's uploaded FileList.
    // Real browser E2E covers native submit validation; exercise the handler here.
    fireEvent.submit(
      screen.getByRole("button", { name: "Import catalog" }).closest("form")!,
    );
    await screen.findByText("Import partially completed");
    expect(
      screen.getByText(/50 created · 0 skipped · 1 failed batches/),
    ).toBeVisible();
    expect(screen.getByText(/Correct failed rows and replay/)).toBeVisible();
  });
});
