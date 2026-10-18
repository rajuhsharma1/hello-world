const { test, expect } = require("@playwright/test");
const { randomUUID } = require("node:crypto");

const authCredentials = () => {
  if (process.env.ARK_TEST_USERNAME && process.env.ARK_TEST_PASSWORD)
    return {
      username: process.env.ARK_TEST_USERNAME,
      password: process.env.ARK_TEST_PASSWORD,
    };
  return JSON.parse(
    require("node:fs").readFileSync(
      require("node:path").resolve(__dirname, "../../.runtime/test-auth.json"),
      "utf8",
    ),
  );
};
test.beforeEach(async ({ request, context }) => {
  const origin = process.env.SPA_BASE_URL || "http://localhost:8080";
  const session = await (await request.get("/api/auth/session")).json();
  const login = await request.post("/api/auth/login", {
    data: authCredentials(),
    headers: { Origin: origin, "X-CSRF-Token": session.csrfToken },
  });
  expect(login.ok()).toBeTruthy();
  const current = await login.json();
  // API fixture requests use the same protected session as the page.
  request._arkCsrf = current.csrfToken;
  await context.addCookies((await request.storageState()).cookies);
});

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

test("staff details, Punjab contract drafts and salary PDF downloads", async ({
  page,
  request,
}, testInfo) => {
  const staffName = "HR Browser " + randomUUID().slice(0, 8);
  const staff = await (
    await request.post("/api/staff", {
      headers: {
        Origin: process.env.SPA_BASE_URL || "http://localhost:8080",
        "X-CSRF-Token": request._arkCsrf,
      },
      data: { name: staffName, role: "Stylist" },
    })
  ).json();
  await page.goto("/#hr");
  await page
    .getByLabel("Choose staff member", { exact: true })
    .selectOption(staff.id);
  await expect(page.locator("#hr-person")).toContainText(staffName);
  await page
    .getByRole("button", { name: "Edit staff details", exact: true })
    .click();
  await page
    .getByLabel("Staff full name", { exact: true })
    .fill("Updated " + staffName);
  await page
    .getByLabel("Role / designation", { exact: true })
    .fill("Senior stylist");
  await page
    .getByLabel("Staff contact number", { exact: true })
    .fill("+919876543225");
  await page
    .getByLabel("Staff address", { exact: true })
    .fill("Employee address, Punjab");
  await page.getByLabel("Joining date", { exact: true }).fill("2020-01-01");
  await page.getByRole("button", { name: "Save record", exact: true }).click();
  await expect(page.locator("#admin-dialog")).toBeHidden();
  await expect(page.locator("#hr-person")).toContainText(
    "Updated " + staffName,
  );
  await page.getByRole("button", { name: /Add salary structure/ }).click();
  for (const [label, value] of [
    ["Annual CTC (INR)", "360000"],
    ["Monthly basic (INR)", "20000"],
    ["Monthly HRA (INR)", "5000"],
    ["Monthly allowances (INR)", "5000"],
    ["Monthly employee deductions (INR)", "1000"],
  ])
    await page.getByLabel(label, { exact: true }).fill(value);
  await expect(page.locator("#salary-preview")).toContainText("₹29,000.00");
  await page.getByRole("button", { name: "Save record", exact: true }).click();
  await expect(page.locator("#admin-dialog")).toBeHidden();
  await expect(page.locator("#salary-list")).toContainText("₹3,60,000.00");
  const salaryDownload = page.waitForEvent("download");
  await page
    .locator("#salary-list")
    .getByRole("link", { name: "Download PDF", exact: true })
    .first()
    .click();
  const salary = await salaryDownload;
  await salary.saveAs(testInfo.outputPath("salary.pdf"));
  expect(
    require("node:fs")
      .readFileSync(testInfo.outputPath("salary.pdf"))
      .subarray(0, 5)
      .toString(),
  ).toBe("%PDF-");
  const printPath = await page
    .locator("#salary-list")
    .getByRole("link", { name: "Open / print PDF", exact: true })
    .first()
    .getAttribute("href");
  expect(
    (await request.get(printPath)).headers()["content-disposition"],
  ).toContain("inline");
  await page.getByRole("button", { name: /Add contract/ }).click();
  for (const [label, value] of [
    ["Employer legal name", "The Ark Salon & Spa"],
    ["Notice days", "30"],
    ["Establishment registration / licence reference", "BROWSER-TEST"],
    ["Registered workplace address", "Workplace address, Punjab"],
    ["Employee residential address", "Employee address, Punjab"],
    ["Employee contact number", "+919876543225"],
    [
      "Working hours, breaks & overtime terms",
      "Draft working terms for local legal review.",
    ],
    ["Weekly rest day / terms", "Subject to verified applicable entitlement."],
    ["Leave and holiday entitlement", "To be confirmed by local adviser."],
    [
      "Pay, payment date & remuneration terms",
      "As per reviewed salary structure.",
    ],
    [
      "Applicable statutory benefits (EPF/ESI etc.)",
      "Applicability to be verified locally.",
    ],
    [
      "Termination, notice & grievance terms",
      "Subject to mandatory law and local review.",
    ],
    [
      "Confidentiality & workplace conduct terms",
      "Reasonable confidentiality subject to law.",
    ],
  ])
    await page.getByLabel(label, { exact: true }).fill(value);
  await expect(page.getByLabel("Jurisdiction", { exact: true })).toHaveValue(
    "Punjab, India",
  );
  await page.getByRole("button", { name: "Save record", exact: true }).click();
  await expect(page.locator("#admin-dialog")).toBeHidden();
  await expect(page.locator("#contract-list")).toContainText("Draft contract");
  const contractDownload = page.waitForEvent("download");
  await page
    .locator("#contract-list")
    .getByRole("link", { name: "Download PDF", exact: true })
    .click();
  expect((await contractDownload).suggestedFilename()).toMatch(
    /^ark-contract-.*\.pdf$/,
  );
});

test("daily expenses, monthly P&L PDF, CSV periods and emergency contacts", async ({
  page,
  request,
}) => {
  const tag = randomUUID().slice(0, 8),
    day = "2004-05-01";
  const before = await (
    await request.get("/api/reports/profit-loss?date=" + day)
  ).json();
  await page.goto("/#expenses");
  await page.getByRole("button", { name: /Add expense/ }).click();
  await page.getByLabel("Date paid", { exact: true }).fill(day);
  await page
    .getByLabel("Expense category", { exact: true })
    .selectOption("SUPPLIES");
  await page
    .getByLabel("Expense description", { exact: true })
    .fill("Browser supplies " + tag);
  await page.getByLabel("Amount paid (INR)", { exact: true }).fill("150.25");
  await page.getByRole("button", { name: "Save record", exact: true }).click();
  await expect(page.locator("#admin-dialog")).toBeHidden();
  await expect(page.locator("#expense-body")).toContainText(
    "Browser supplies " + tag,
  );
  await page.getByRole("link", { name: /Reports & exports/ }).click();
  await page.getByLabel("Report month", { exact: true }).fill("2004-05");
  await page.getByLabel("Report month", { exact: true }).press("Tab");
  const after = await (
    await request.get("/api/reports/profit-loss?date=" + day)
  ).json();
  expect(after.expenses).toBeCloseTo(before.expenses + 150.25, 2);
  await expect(page.locator("#profit-summary")).toContainText(
    new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
    }).format(after.expenses),
  );
  const pdf = page.waitForEvent("download");
  await page
    .getByRole("link", { name: "Download P&L PDF", exact: true })
    .click();
  expect((await pdf).suggestedFilename()).toContain("ark-profit-loss");
  for (const period of ["MONTH", "YEAR"]) {
    await page
      .getByLabel("Sales export period", { exact: true })
      .selectOption(period);
    await page
      .getByLabel("Sales export date", { exact: true })
      .fill(period === "MONTH" ? "2004-05" : "2004");
    const csv = page.waitForEvent("download");
    await page
      .getByRole("link", { name: "Download sales CSV", exact: true })
      .click();
    expect((await csv).suggestedFilename()).toBe(
      "ark-sales-" + (period === "MONTH" ? "2004-05" : "2004") + ".csv",
    );
  }
  await page.getByRole("link", { name: /Emergency contacts/ }).click();
  await page.getByRole("button", { name: /Add contact/ }).click();
  await page
    .getByLabel("Contact person / business", { exact: true })
    .fill("Plumber " + tag);
  await page.getByLabel("Service / trade", { exact: true }).fill("Plumber");
  await page.getByLabel("Primary phone", { exact: true }).fill("112");
  await page.getByRole("button", { name: "Save record", exact: true }).click();
  await expect(page.locator("#admin-dialog")).toBeHidden();
  const card = page
    .locator(".contact-card")
    .filter({ hasText: "Plumber " + tag });
  await expect(
    card.getByRole("link", { name: "112", exact: true }),
  ).toHaveAttribute("href", "tel:112");
  await card.getByRole("button", { name: "Edit contact", exact: true }).click();
  await page
    .getByLabel("Service / trade", { exact: true })
    .fill("Plumber / maintenance");
  await page.getByRole("button", { name: "Save record", exact: true }).click();
  await expect(page.locator("#admin-dialog")).toBeHidden();
  await expect(card).toContainText("Plumber / maintenance");
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/#reports");
  await expect(page.locator("#profit-summary .stat")).toHaveCount(3);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});
