import { defineConfig } from "@playwright/test";

/**
 * Browser specs for the auction screen. They run against `make dev` (web on :3000, server on :8080);
 * see e2e/support/seed.mjs for what the server needs and how test rooms are made.
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 90_000,
  workers: 1,
  retries: 0,
  reporter: "list",
  outputDir: "./test-results",
  use: { baseURL: process.env.E2E_WEB ?? "http://localhost:3000", trace: "off" },
});
