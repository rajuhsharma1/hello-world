import { defineConfig } from "../../frontend/node_modules/@playwright/test";
export default defineConfig({
  testDir: ".",
  workers: 1,
  timeout: 30000,
  use: {
    baseURL: process.env.ECAL_WEB_URL ?? "http://127.0.0.1:5173",
    browserName: "chromium",
    launchOptions: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
      ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH }
      : {},
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  outputDir: "../../frontend/test-results",
});
