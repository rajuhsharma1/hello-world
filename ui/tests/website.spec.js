const { test, expect } = require("@playwright/test");
const { randomUUID } = require("node:crypto");
const credentials = () =>
  JSON.parse(
    require("node:fs").readFileSync(
      require("node:path").resolve(__dirname, "../../.runtime/test-auth.json"),
      "utf8",
    ),
  );
async function owner(request, context) {
  const origin = process.env.SPA_BASE_URL || "http://localhost:8080";
  const session = await (await request.get("/api/auth/session")).json();
  const r = await request.post("/api/auth/login", {
    data: process.env.ARK_TEST_USERNAME
      ? {
          username: process.env.ARK_TEST_USERNAME,
          password: process.env.ARK_TEST_PASSWORD,
        }
      : credentials(),
    headers: { Origin: origin, "X-CSRF-Token": session.csrfToken },
  });
  expect(r.ok()).toBeTruthy();
  await context.addCookies((await request.storageState()).cookies);
  return (await r.json()).csrfToken;
}
test("public homepage, careers questionnaire, complaint submission and mobile layout", async ({
  page,
}) => {
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "A little time. A deeper calm." }),
  ).toBeVisible();
  await expect(page.locator(".hero-photo")).toBeVisible();
  expect(
    await page
      .locator(".hero-photo")
      .evaluate((e) => e.complete && e.naturalWidth > 0),
  ).toBeTruthy();
  await expect(page.locator("#public-posts article").first()).toBeVisible();
  await page.getByRole("link", { name: "Careers", exact: true }).click();
  await expect(
    page.locator('[data-enquiry="CAREER"] input[name="resume"]'),
  ).toBeVisible();
  const f = page.locator('[data-enquiry="COMPLAINT"]');
  await page.goto("/#complaints");
  await f
    .locator('[name="name"]')
    .fill("Browser Complaint " + randomUUID().slice(0, 8));
  await f.locator('[name="email"]').fill("browser@example.test");
  await f.locator('[name="phone"]').fill("+919876543219");
  await f.locator('[name="subject"]').fill("Feedback from browser test");
  await f
    .locator('[name="message"]')
    .fill("<script>window.untrusted=true</script>");
  await f.locator('[name="consent"]').check();
  await f.getByRole("button", { name: "Send enquiry" }).click();
  await expect(f.locator(".form-status")).toContainText("Reference:");
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(page.locator(".public-header")).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  ).toBeTruthy();
  expect(errors).toEqual([]);
});
test("dashboard charts, staff period switches, inbox and blog drafts", async ({
  page,
  request,
  context,
}) => {
  await owner(request, context);
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("/index.html");
  await expect(page.locator("#overview-revenue-chart svg")).toBeVisible();
  await page.locator('[data-view="statistics"]').click();
  await expect(page.locator("#revenue-chart svg")).toBeVisible();
  await page.locator("#stats-year").fill("2005");
  await page.locator("#staff-stats-date").fill("2005-01-03");
  await page.locator("#refresh-statistics").click();
  await expect(page.locator("#staff-stats-range")).toContainText("2005-01-01");
  await page.locator("#staff-stats-period").selectOption("WEEK");
  await expect(page.locator("#staff-stats-range")).toContainText(
    "2005-01-03 to 2005-01-09",
  );
  await page.locator("#staff-stats-period").selectOption("YEAR");
  await expect(page.locator("#staff-stats-range")).toContainText(
    "2005-01-01 to 2005-12-31",
  );
  await page.locator('[data-view="website"]').click();
  await expect(
    page.locator('#website-settings input[name="recipientEmail"]'),
  ).not.toHaveValue("");
  await page.locator("#inbox-kind").selectOption("COMPLAINT");
  await expect(
    page.locator("#enquiry-cards .enquiry-card").first(),
  ).toBeVisible();
  expect(await page.evaluate(() => window.untrusted)).toBeUndefined();
  const title = "Browser draft " + randomUUID().slice(0, 8);
  await page.locator("#new-blog").click();
  const form = page.locator("#blog-editor");
  await form.locator('[name="title"]').fill(title);
  await form.locator('[name="excerpt"]').fill("A test introduction");
  await form.locator('[name="body"]').fill("<img src=x onerror=alert(1)>");
  await form.getByRole("button", { name: "Save post" }).click();
  await expect(page.locator("#blog-status")).toHaveText("Post saved.");
  await expect(page.locator("#blog-list")).toContainText(title);
  const publicPosts = await (await request.get("/api/public/blogs")).json();
  expect(publicPosts.some((p) => p.title === title)).toBeFalsy();
  expect(errors).toEqual([]);
});
