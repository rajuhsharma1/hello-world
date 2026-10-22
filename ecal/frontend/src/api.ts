/** Public REST contracts. Prices are decimal strings; Java/PostgreSQL own money arithmetic. */
export interface Product {
  id: string;
  name: string;
  description: string;
  price: string;
  currency: "INR";
  availableStock: number;
}
export interface CheckoutCommand {
  productId: string;
  quantity: number;
  paymentOutcome: "APPROVE" | "DECLINE";
}
export interface Order {
  id: string;
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: string;
  totalAmount: string;
  currency: "INR";
  status: "CONFIRMED" | "CANCELLED";
  paymentStatus: "AUTHORIZED" | "DECLINED";
  createdAt: string;
}
export interface PendingCheckout {
  key: string;
  command: CheckoutCommand;
}

/** A 4xx business response differs from a lost response: the latter must retain the retry identity. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string,
  ) {
    super(message);
  }
  get isTerminal(): boolean {
    // A proxy's 408/429 (or arbitrary 4xx page) does not prove the database rolled back.
    return (
      [400, 404, 409].includes(this.status) &&
      [
        "INVALID_REQUEST",
        "PRODUCT_NOT_FOUND",
        "ORDER_NOT_FOUND",
        "INSUFFICIENT_STOCK",
        "IDEMPOTENCY_CONFLICT",
      ].includes(this.code)
    );
  }
}

async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(url, {
    ...options,
    signal: options.signal ?? AbortSignal.timeout(15000),
  });
  const body: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    const detail =
      body && typeof body === "object" && "detail" in body
        ? String(body.detail)
        : "The server could not complete this request.";
    const code =
      body && typeof body === "object" && "code" in body
        ? String(body.code)
        : "";
    throw new ApiError(detail, response.status, code);
  }
  if (body === null) throw new Error("The server response was incomplete.");
  return body as T;
}

export interface Page<T> {
  items: T[];
  nextCursor: string | null;
}
export interface ImportResult {
  totalRows: number;
  created: number;
  skipped: number;
  committedBatches: number;
  failedBatches: number;
  batches: {
    index: number;
    status: string;
    rows: number;
    created: number;
    skipped: number;
    error: string | null;
  }[];
}
export const api = {
  productsPage: (cursor: string | null) =>
    request<Page<Product>>(
      "/api/catalog/products/page?limit=20" +
        (cursor ? "&cursor=" + encodeURIComponent(cursor) : ""),
    ),
  ordersPage: (status: string, cursor: string | null) =>
    request<Page<Order>>(
      "/api/orders/page?limit=20&status=" +
        status +
        (cursor ? "&cursor=" + encodeURIComponent(cursor) : ""),
    ),
  importCatalog: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return request<ImportResult>("/api/catalog/import", {
      method: "POST",
      body: form,
      signal: AbortSignal.timeout(120000),
    });
  },
  products: () => request<Product[]>("/api/catalog/products"),
  orders: () => request<Order[]>("/api/orders"),
  order: (id: string) =>
    request<Order>(`/api/orders/${encodeURIComponent(id)}`),
  checkout: async (pending: PendingCheckout): Promise<Order> => {
    const order = await request<Order>("/api/orders", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": pending.key,
      },
      body: JSON.stringify(pending.command),
    });
    // HTTP 2xx alone is not an acknowledgement: retain the key if an intermediary returns bad JSON.
    const approved = pending.command.paymentOutcome === "APPROVE";
    if (
      !order ||
      !uuid.test(order.id ?? "") ||
      order.productId !== pending.command.productId ||
      order.quantity !== pending.command.quantity ||
      typeof order.productName !== "string" ||
      !order.productName.trim() ||
      order.currency !== "INR" ||
      typeof order.unitPrice !== "string" ||
      !/^\d+\.\d{2}$/.test(order.unitPrice) ||
      typeof order.totalAmount !== "string" ||
      !/^\d+\.\d{2}$/.test(order.totalAmount) ||
      Number.isNaN(Date.parse(order.createdAt)) ||
      order.status !== (approved ? "CONFIRMED" : "CANCELLED") ||
      order.paymentStatus !== (approved ? "AUTHORIZED" : "DECLINED")
    ) {
      throw new Error(
        "The checkout acknowledgement was incomplete or did not match this request.",
      );
    }
    return order;
  },
};

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const storageKey = "ecal.pendingCheckout.v1";
export function restorePending(): PendingCheckout | null {
  try {
    const raw = sessionStorage.getItem(storageKey);
    if (!raw) return null;
    const value: unknown = JSON.parse(raw);
    if (!value || typeof value !== "object") return null;
    const p = value as PendingCheckout;
    if (
      typeof p.key !== "string" ||
      !uuid.test(p.key) ||
      typeof p.command?.productId !== "string" ||
      !uuid.test(p.command.productId) ||
      !Number.isInteger(p.command.quantity) ||
      p.command.quantity < 1 ||
      p.command.quantity > 10 ||
      !["APPROVE", "DECLINE"].includes(p.command.paymentOutcome)
    )
      return null;
    return p;
  } catch {
    return null;
  }
}

/** Persist before sending so a reload cannot silently turn a response timeout into a new order. */
export function savePending(pending: PendingCheckout | null): void {
  if (pending) sessionStorage.setItem(storageKey, JSON.stringify(pending));
  else sessionStorage.removeItem(storageKey);
}
