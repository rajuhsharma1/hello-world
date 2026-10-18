const { test, expect } = require("@playwright/test");
const { randomUUID } = require("node:crypto");

test("guest booking, search, Kafka confirmation, and duplicate error through the gateway", async ({
  page,
}) => {
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  const guest = `Browser Guest ${randomUUID().slice(0, 8)}`;
  const therapist = `Therapist ${randomUUID().slice(0, 8)}`;
  const start = new Date(Date.now() + 2 * 86400000).toISOString().slice(0, 16);
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "A calmer way to manage your day." }),
  ).toBeVisible();
  await expect(page.locator("#service-message")).toBeHidden();
  async function fillBooking() {
    await page
      .getByRole("button", { name: "New appointment", exact: false })
      .click();
    await page.getByLabel("Guest name", { exact: true }).fill(guest);
    await page
      .getByLabel("Email address", { exact: true })
      .fill("browser@example.com");
    await page
      .getByLabel("Treatment", { exact: true })
      .selectOption("Restorative facial");
    await page.getByLabel("Therapist", { exact: true }).fill(therapist);
    await page.getByLabel("Date & time", { exact: true }).fill(start);
  }
  await fillBooking();
  await page
    .getByRole("button", { name: "Create appointment", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(
    page.getByRole("status").filter({ hasText: "Appointment created." }),
  ).toBeVisible();
  await page
    .getByRole("link", { name: "Appointments", exact: false })
    .first()
    .click();
  await page.getByRole("searchbox").fill(guest);
  const row = page.getByRole("row").filter({ hasText: guest });
  await expect(row).toHaveCount(1);
  await expect(row).toContainText("Restorative facial");
  await expect(row).toContainText(therapist);
  await page.getByRole("link", { name: /Confirmations/ }).click();
  await expect(page.locator("#all-confirmations")).toContainText(
    "browser@example.com",
    { timeout: 45000 },
  );
  await fillBooking();
  await page
    .getByRole("button", { name: "Create appointment", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "already has an appointment",
  );
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("button", { name: "Cancel", exact: true }).click();
  expect(errors).toEqual([]);
});

test("notification outage leaves the schedule usable and renders unsafe guest text literally", async ({
  page,
}) => {
  const unsafeName = "<img src=x onerror=alert(1)>";
  await page.route("**/api/appointments?*", (route) =>
    route.fulfill({
      json: [
        {
          id: randomUUID(),
          customerName: unsafeName,
          customerEmail: "guest@example.com",
          therapist: "Sam",
          treatment: "Massage",
          startsAt: new Date(Date.now() + 86400000).toISOString(),
        },
      ],
    }),
  );
  await page.route("**/api/notifications", (route) =>
    route.fulfill({ status: 503, body: "offline" }),
  );
  await page.goto("/#appointments");
  await expect(page.locator("#service-message")).toContainText(
    "Confirmations:",
  );
  await expect(page.getByRole("cell", { name: /<img src=x/ })).toBeVisible();
  await expect(page.locator("#appointments-body img")).toHaveCount(0);
  await page.getByRole("searchbox").fill("no matching guest");
  await expect(page.locator("#appointments-empty")).toHaveText(
    "No matching appointments on this page.",
  );
});

test("mobile layout and keyboard booking dialog work", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(
    page.getByRole("button", { name: "New appointment", exact: false }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page
    .getByRole("button", { name: "New appointment", exact: false })
    .click();
  await expect(page.getByLabel("Guest name", { exact: true })).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(
    page.getByRole("button", { name: "New appointment", exact: false }),
  ).toBeFocused();
});
