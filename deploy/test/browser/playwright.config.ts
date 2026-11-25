import { defineConfig } from "@playwright/test";

// Optional: runs against a live seeded pod. Nothing here runs on push.
export default defineConfig({
  testDir: ".",
  timeout: 60_000,
  retries: 0,
  use: { baseURL: process.env.PYLON_URL ?? "http://127.0.0.1:19119" },
});
