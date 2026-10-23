import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    // One browser origin keeps this local teaching slice free of unnecessary CORS policy.
    proxy: {
      "/api/v1/reports": {
        target: process.env.ECAL_REPORTING_TARGET ?? "http://127.0.0.1:8096",
      },
      "/api": {
        target: process.env.ECAL_API_TARGET ?? "http://127.0.0.1:8090",
      },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test-setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    restoreMocks: true,
  },
});
