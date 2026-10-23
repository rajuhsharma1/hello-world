import { expect, test } from "../../frontend/node_modules/@playwright/test";

test("Phase 4 completes an accepted operation after the page closes and serves eventual reports", async ({
  page,
  request,
}) => {
  test.skip(
    process.env.ECAL_MODE !== "events",
    "Only the Phase 4 build supports asynchronous acceptance.",
  );
  await page.goto("/");
  await expect(page.getByText("PHASE 04 · EVENT-DRIVEN")).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Revenue, after the events arrive." }),
  ).toBeVisible();
  const products = await (await request.get("/api/v1/catalog/products")).json();
  const product = products.find(
    (p: { availableStock: number }) => p.availableStock > 0,
  );
  expect(product).toBeTruthy();
  const key = crypto.randomUUID();
  const payload = {
    productId: product.id,
    quantity: 1,
    paymentOutcome: "APPROVE",
  };
  const response = await request.post("/api/v1/orders", {
    headers: { "Idempotency-Key": key },
    data: payload,
  });
  expect(response.status()).toBe(202);
  const accepted = await response.json();
  expect(response.headers().location).toBe("/api/v1/orders/" + accepted.id);
  await page.close();
  await expect
    .poll(
      async () =>
        (await (await request.get("/api/v1/orders/" + accepted.id)).json())
          .status,
      { timeout: 30000 },
    )
    .toBe("CONFIRMED");
  const replay = await request.post("/api/v1/orders", {
    headers: { "Idempotency-Key": key },
    data: payload,
  });
  expect(replay.status()).toBe(200);
  expect((await replay.json()).id).toBe(accepted.id);
  await expect
    .poll(
      async () => {
        const report = await (
          await request.get("/api/v1/reports/summary")
        ).json();
        expect(report.consistency).toBe("EVENTUAL");
        return report.monthlyRevenue.length;
      },
      { timeout: 30000 },
    )
    .toBeGreaterThan(0);
});
