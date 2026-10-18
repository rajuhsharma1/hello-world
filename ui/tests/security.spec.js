const { test, expect } = require("@playwright/test");
const fs = require("node:fs");
const path = require("node:path");
const credentials = () =>
  process.env.ARK_TEST_USERNAME
    ? {
        username: process.env.ARK_TEST_USERNAME,
        password: process.env.ARK_TEST_PASSWORD,
      }
    : JSON.parse(
        fs.readFileSync(
          path.resolve(__dirname, "../../.runtime/test-auth.json"),
          "utf8",
        ),
      );
test("sign-in, protected logs, account controls and sign-out", async ({
  page,
}) => {
  await page.goto("/index.html");
  await expect(page).toHaveURL(/login[.]html/);
  await page.locator("#username").fill(credentials().username);
  await page.locator("#password").fill(credentials().password);
  await page.locator("#login-form button[type=submit]").click();
  await expect(page.locator("#sign-out")).toBeVisible();
  await page.goto("/index.html#logs");
  await expect(page.locator("#logs-body tr").first()).toBeVisible();
  await expect(page.locator("#log-error")).toBeHidden();
  await page.goto("/index.html#users");
  await expect(page.locator("#new-account")).toBeVisible();
  await page.locator("#sign-out").click();
  await expect(page).toHaveURL(/login[.]html/);
  const denied = await page.request.get("/api/staff");
  expect(denied.status()).toBe(401);
  await page.goto("/index.html#users");
  await expect(page).toHaveURL(/login[.]html/);
});

test("temporary staff password change and restricted navigation", async ({
  page,
  request,
}) => {
  const { randomUUID, randomBytes } = require("node:crypto");
  const origin = process.env.SPA_BASE_URL || "http://localhost:8080";
  const session = await (await request.get("/api/auth/session")).json();
  const login = await request.post("/api/auth/login", {
    data: credentials(),
    headers: { Origin: origin, "X-CSRF-Token": session.csrfToken },
  });
  expect(login.ok()).toBeTruthy();
  const owner = await login.json();
  const username = "browser-" + randomUUID().slice(0, 8),
    temporary = randomBytes(24).toString("hex"),
    permanent = randomBytes(24).toString("hex");
  const created = await request.post("/api/auth/users", {
    data: {
      username,
      displayName: "Browser Staff Test",
      password: temporary,
      role: "STAFF",
    },
    headers: { Origin: origin, "X-CSRF-Token": owner.csrfToken },
  });
  expect(created.ok()).toBeTruthy();
  const account = (await (await request.get("/api/auth/users")).json()).find(
    (u) => u.username === username,
  );
  try {
    await page.goto("/login.html");
    await page.locator("#username").fill(username);
    await page.locator("#password").fill(temporary);
    await page.locator("#login-form button[type=submit]").click();
    await expect(page.locator("#change-form")).toBeVisible();
    await page.locator("#current-password").fill(temporary);
    await page.locator("#new-password").fill(permanent);
    await page.locator("#confirm-password").fill(permanent);
    await page.locator("#change-form button[type=submit]").click();
    await expect(page.locator("#login-form")).toBeVisible();
    await page.locator("#username").fill(username);
    await page.locator("#password").fill(permanent);
    await page.locator("#login-form button[type=submit]").click();
    await expect(page.locator("#sign-out")).toBeVisible();
    for (const view of [
      "users",
      "logs",
      "hr",
      "expenses",
      "reports",
      "attendance",
    ])
      await expect(page.locator(`[data-view="${view}"]`)).toBeHidden();
    await expect(page.locator('[data-view="appointments"]')).toBeVisible();
    await expect(page.locator('[data-view="sales"]')).toBeVisible();
    expect((await page.request.get("/api/auth/logs")).status()).toBe(403);
    await page.goto("/index.html#users");
    await expect(page).toHaveURL(/#overview$/);
  } finally {
    await request.put("/api/auth/users/" + account.id, {
      data: { role: "STAFF", enabled: false },
      headers: { Origin: origin, "X-CSRF-Token": owner.csrfToken },
    });
  }
});
