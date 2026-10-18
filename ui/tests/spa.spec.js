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
      .getByLabel("Contact number", { exact: true })
      .fill("+919876543211");
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
    "+919876543211",
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
          contactNumber: "+919876543212",
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

test("staff attendance and daily client sales split UPI, cash and card", async ({
  page,
  request,
}) => {
  const staffName = `Ark Staff ${randomUUID().slice(0, 8)}`;
  const businessDay = "2001-03-07";
  const before = await (
    await request.get(`/api/sales?date=${businessDay}`)
  ).json();
  await page.goto("/#attendance");
  await expect(
    page.getByRole("link", { name: "The Ark Salon & Spa home" }),
  ).toBeVisible();
  await page.getByRole("button", { name: /Add staff$/, exact: false }).click();
  await page.getByLabel("Staff name", { exact: true }).fill(staffName);
  await page.getByLabel("Staff role", { exact: true }).fill("Stylist");
  await page
    .getByRole("button", { name: "Add staff member", exact: true })
    .click();
  await expect(page.locator("#staff-dialog")).toBeHidden();
  const staffRow = page
    .locator("#attendance-body tr")
    .filter({ hasText: staffName });
  await expect(staffRow).toContainText("Not marked");
  await page
    .getByRole("button", {
      name: `Mark attendance for ${staffName}`,
      exact: true,
    })
    .click();
  await page.getByLabel("Check-in", { exact: true }).fill("09:15");
  await page.getByLabel("Check-out", { exact: true }).fill("17:45");
  await page
    .getByRole("button", { name: "Save attendance", exact: true })
    .click();
  await expect(page.locator("#attendance-dialog")).toBeHidden();
  await expect(staffRow).toContainText("8h 30m");
  await expect(staffRow).toContainText("Present");
  await page
    .getByRole("button", {
      name: `Update attendance for ${staffName}`,
      exact: true,
    })
    .click();
  await page
    .getByLabel("Attendance status", { exact: true })
    .selectOption("LEAVE");
  await expect(page.getByLabel("Check-in", { exact: true })).toBeDisabled();
  await page
    .getByRole("button", { name: "Save attendance", exact: true })
    .click();
  await expect(page.locator("#attendance-dialog")).toBeHidden();
  await expect(staffRow).toContainText("Leave");
  await expect(staffRow).not.toContainText("8h 30m");

  await page.getByRole("link", { name: /Daily sales/ }).click();
  await expect(page.locator("#sales-date")).not.toHaveValue("");
  await page.locator("#sales-date").fill(businessDay);
  await page.locator("#sales-date").press("Tab");
  const methods = [
    ["UPI", "1250.50"],
    ["CASH", "200.00"],
    ["CARD", "300.00"],
  ];
  const people = await (await request.get("/api/staff")).json();
  const person = people.find((p) => p.name === staffName);
  for (const [method, amount] of methods) {
    await page.getByRole("button", { name: /Add client entry/ }).click();
    await page
      .getByLabel("Client name", { exact: true })
      .fill(`Client ${method} ${staffName}`);
    await page
      .getByLabel("Client contact number", { exact: true })
      .fill("+919876543215");
    await page.getByLabel("Visit date", { exact: true }).fill(businessDay);
    await page.getByLabel("Service attended", { exact: true }).fill("Haircut");
    await page
      .getByLabel("Staff member", { exact: true })
      .selectOption(person.id);
    await page.getByLabel("Amount collected (₹)", { exact: true }).fill(amount);
    await page
      .getByLabel("Payment method", { exact: true })
      .selectOption(method);
    await page
      .getByRole("button", { name: "Save client entry", exact: true })
      .click();
    await expect(page.locator("#sale-dialog")).toBeHidden();
    const row = page
      .locator("#sales-body tr")
      .filter({ hasText: `Client ${method} ${staffName}` });
    await expect(row).toContainText(
      method === "UPI" ? "UPI" : method === "CASH" ? "Cash" : "Card",
    );
    await expect(row).toContainText("+919876543215");
  }
  const after = await (
    await request.get(`/api/sales?date=${businessDay}`)
  ).json();
  expect(after.summary.clients).toBe(before.summary.clients + 3);
  expect(after.summary.total).toBeCloseTo(before.summary.total + 1750.5, 2);
  expect(after.summary.upi).toBeCloseTo(before.summary.upi + 1250.5, 2);
  expect(after.summary.cash).toBeCloseTo(before.summary.cash + 200, 2);
  expect(after.summary.card).toBeCloseTo(before.summary.card + 300, 2);
  await expect(page.locator("#sales-stats")).toContainText(
    new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
    }).format(after.summary.total),
  );
  await page.reload();
  await page.locator("#sales-date").fill(businessDay);
  await page.locator("#sales-date").press("Tab");
  await expect(page.locator("#sales-body")).toContainText(
    `Client CARD ${staffName}`,
  );
});
