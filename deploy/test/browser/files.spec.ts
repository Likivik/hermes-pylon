import { createHash } from "node:crypto";
import { expect, test } from "@playwright/test";

/**
 * Files transport contract, exercised the way the wasm client exercises it.
 *
 * Covered: sign-in, upload of raw bytes as a data URL, listing, download, sha256 match.
 * Not covered: the canvas UI. Compose draws into one canvas, so the picker and the row
 * actions have no selectors; that path stays a manual check.
 */
const USER = process.env.PYLON_USER ?? "seed";
const PASSWORD = process.env.PYLON_PASSWORD;
const PAYLOAD = Buffer.from("pylon browser check\n", "utf8");

test.skip(!PASSWORD, "PYLON_PASSWORD is required");

test("upload, list, download round-trips the bytes", async ({ request }) => {
  const login = await request.post("/auth/password-login", {
    data: { provider: "basic", username: USER, password: PASSWORD },
  });
  expect(login.status(), "sign-in").toBe(200);

  const name = `pw-${Date.now()}.txt`;
  const upload = await request.post("/api/files/upload", {
    data: {
      path: `/opt/data/seed/${name}`,
      data_url: `data:text/plain;base64,${PAYLOAD.toString("base64")}`,
      overwrite: true,
    },
  });
  expect(upload.status(), "upload").toBe(200);

  const listing = await request.get("/api/files?path=/opt/data/seed");
  expect(listing.status(), "list").toBe(200);
  expect(await listing.text(), "listing mentions the upload").toContain(name);

  const download = await request.get(`/api/files/download?path=/opt/data/seed/${name}`);
  expect(download.status(), "download").toBe(200);
  const sha = createHash("sha256").update(await download.body()).digest("hex");
  expect(sha, "downloaded bytes match the upload").toBe(
    createHash("sha256").update(PAYLOAD).digest("hex"),
  );
});

test("the app boots without a composition error", async ({ page }) => {
  test.skip(process.env.PYLON_SKIP_APP === "1", "app dist not built in this run");
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("/");
  await page.waitForTimeout(15_000);
  expect(errors.join("\n")).not.toContain("WsAuthConfig not installed");
});
