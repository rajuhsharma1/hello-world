import { expect, test } from "../../frontend/node_modules/@playwright/test";

test("a buyer confirms an order, reloads persisted history, and sees a declined payment outcome", async ({
  page,
}) => {
  const pageErrors: string[] = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "A considered collection." }),
  ).toBeVisible();
  const choose = page.getByRole("button", { name: /^Choose / }).first();
  await choose.click();
  await page.getByRole("button", { name: /Place simulated order/ }).click();
  await expect(
    page.getByRole("heading", { name: "Your order is confirmed." }),
  ).toBeVisible();
  const result = page.getByRole("status").filter({
    has: page.getByRole("heading", { name: "Your order is confirmed." }),
  });
  const orderId = await result.locator("dd").first().innerText();
  await page.reload();
  await expect(
    page.getByRole("table").getByText(orderId, { exact: true }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: /^Choose / })
    .first()
    .click();
  await page.getByLabel("Simulated payment outcome").selectOption("DECLINE");
  await page.getByRole("button", { name: /Place simulated order/ }).click();
  await expect(
    page.getByRole("heading", { name: "Payment declined. Stock released." }),
  ).toBeVisible();
  expect(pageErrors).toEqual([]);
});

test("mobile catalog has no horizontal overflow and the checkout is keyboard accessible", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(
    page.getByRole("button", { name: /^Choose / }).first(),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  ).toBeTruthy();
  await page
    .getByRole("button", { name: /^Choose / })
    .first()
    .focus();
  await page.keyboard.press("Enter");
  await expect(page.getByLabel("Quantity")).toBeEnabled();
});

test("a committed checkout with a lost response survives reload and replays one business effect", async ({
  page,
}) => {
  const before = await (await page.request.get("/api/catalog/products")).json();
  const chosen = before.find(
    (p: { availableStock: number }) => p.availableStock > 0,
  );
  expect(chosen).toBeTruthy();
  let committedId = "";
  let dropped = false;
  const commands: { key: string | undefined; body: string | null }[] = [];
  await page.route("**/api/orders", async (route) => {
    if (route.request().method() !== "POST") return route.continue();
    commands.push({
      key: route.request().headers()["idempotency-key"],
      body: route.request().postData(),
    });
    if (!dropped) {
      // Let the real backend commit, then drop only its response to reproduce the dangerous gap.
      const response = await route.fetch();
      expect(response.status()).toBe(
        process.env.ECAL_MODE === "events" ? 202 : 201,
      );
      committedId = (await response.json()).id;
      dropped = true;
      return route.abort("failed");
    }
    return route.continue();
  });
  await page.goto("/");
  await page
    .getByRole("button", { name: `Choose ${chosen.name}`, exact: true })
    .click();
  await page.getByRole("button", { name: /Place simulated order/ }).click();
  await expect(page.getByRole("alert")).toContainText("could not verify");
  await page.reload();
  await expect(page.getByLabel("Quantity")).toBeDisabled();
  await page.getByRole("button", { name: /Retry same checkout/ }).click();
  await expect(
    page.getByRole("heading", { name: "Your order is confirmed." }),
  ).toBeVisible();
  const result = page.getByRole("status").filter({
    has: page.getByRole("heading", { name: "Your order is confirmed." }),
  });
  await expect(result.locator("dd").first()).toHaveText(committedId);
  expect(commands).toHaveLength(2);
  expect(commands[0]).toEqual(commands[1]);
  const after = await (await page.request.get("/api/catalog/products")).json();
  expect(
    after.find((p: { id: string }) => p.id === chosen.id).availableStock,
  ).toBe(chosen.availableStock - 1);
  const history = await (await page.request.get("/api/orders")).json();
  expect(
    history.filter((o: { id: string }) => o.id === committedId),
  ).toHaveLength(1);
});

test("operator uploads real CSV, pages products, replays safely and filters history", async ({
  page,
}) => {
  test.skip(
    ["distributed", "events"].includes(process.env.ECAL_MODE ?? ""),
    "CSV import is a Phase 2 local-transaction feature.",
  );
  const { randomUUID } = await import("node:crypto");
  const rows = Array.from(
    { length: 25 },
    (_, index) =>
      `${randomUUID()},ZZ browser import ${String(index).padStart(2, "0")},Browser fixture,1.25,INR,3`,
  );
  const file = {
    name: "browser-import.csv",
    mimeType: "text/csv",
    buffer: Buffer.from(
      "productId,name,description,price,currency,stock\r\n" +
        rows.join("\r\n") +
        "\r\n",
    ),
  };
  await page.goto("/");
  await page.getByLabel("Catalog CSV").setInputFiles(file);
  await page
    .getByRole("button", { name: "Import catalog", exact: true })
    .click();
  await expect(
    page.getByText("25 created · 0 skipped · 0 failed batches."),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Next products", exact: true }),
  ).toBeEnabled();
  await page
    .getByRole("button", { name: "Next products", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Previous products", exact: true }),
  ).toBeEnabled();
  await expect(
    page.getByRole("button", { name: /Choose ZZ browser import/ }).first(),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Import catalog", exact: true })
    .click();
  await expect(
    page.getByText("0 created · 25 skipped · 0 failed batches."),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Previous products", exact: true }),
  ).toBeDisabled();
  await page.getByLabel("Order status").selectOption("CANCELLED");
  await expect(
    page.getByRole("table").getByText("CANCELLED", { exact: true }).first(),
  ).toBeVisible();
  await expect(
    page.getByRole("table").getByText("CONFIRMED", { exact: true }),
  ).toHaveCount(0);
  expect(
    (await page.request.get("/examples/catalog-import.csv")).status(),
  ).toBe(200);
});

test("Phase 3 exposes versioned reads and explains independent transaction boundaries", async ({
  page,
}) => {
  test.skip(
    process.env.ECAL_MODE !== "distributed",
    "Only the distributed build has this boundary.",
  );
  await page.goto("/");
  await expect(page.getByText("PHASE 03 · THREE SERVICES")).toBeVisible();
  await expect(
    page.getByText("Independent transaction boundaries"),
  ).toBeVisible();
  await expect(page.getByLabel("Catalog CSV")).toHaveCount(0);
  const response = await page.request.get(
    "/api/v1/catalog/products/page?limit=1",
  );
  expect(response.status()).toBe(200);
  expect((await response.json()).items).toHaveLength(1);
  expect(response.headers()["x-correlation-id"]).toMatch(/^[0-9a-f-]{36}$/);
  expect(response.headers()["x-trace-id"]).toMatch(/^[0-9a-f]{32}$/);
});
