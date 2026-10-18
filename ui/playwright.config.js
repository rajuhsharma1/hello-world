const { defineConfig } = require("@playwright/test");
module.exports = defineConfig({
  testDir: "./tests",
  workers: 1,
  timeout: 60000,
  expect: { timeout: 15000 },
  use: {
    baseURL: process.env.SPA_BASE_URL || "http://localhost:8080",
    browserName: "chromium",
    timezoneId: "UTC",
    launchOptions: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
      ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH }
      : {},
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
});
