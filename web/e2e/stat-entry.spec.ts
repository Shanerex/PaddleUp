import { mkdirSync } from "node:fs";
import { expect, test, type Page } from "@playwright/test";
import { auctionState, bidAs, call, seedRoom, startIn } from "./support/seed.mjs";
import { contextFor, sleep, type Fixture } from "./support/helpers";
import { mockShot, pair } from "./support/mock-pairs";
import { deliverProvider, seedMatches } from "./support/stat-seed.mjs";

// T-22 (AC-18, AC-19): manual stat entry against `make dev`, at 390 and 1440, each state beside its
// mock (App Screens 9f-9j). Pictures: test-results/stat/<size>-<name>.png (ours), mock-<size>-<name>.png
// and pair-<size>-<name>.png (ours left, mock right). Needs E2E_PG_DB when the server is not on the
// default database; see support/stat-seed.mjs.

const OUT = "test-results/stat";
const SCREENS = { entry: "9f", error: "9g", nodata: "9h", saved: "9i", clear: "9j" } as const;
const SIZES = [
  { width: 390, height: 844 },
  { width: 1440, height: 900 },
];

async function goLive(fx: Fixture) {
  await startIn(fx, 2);
  for (let i = 0; i < 100; i++) {
    if ((await auctionState(fx)).phase === "live") return;
    await sleep(100);
  }
  throw new Error("auction did not start");
}

/** A live auction in which Club 1 and Club 2 each bought a lot, so the pool has owners. */
async function roomWithOwners() {
  const fx = await seedRoom({ managers: 2, startInSec: 600, open: 5, bid: 3, gap: 1, squadMin: 1 });
  await goLive(fx);
  await bidAs(fx, fx.managers[0], 1, (await auctionState(fx)).nextBid);
  await expect.poll(async () => (await auctionState(fx)).lastResult?.lot, { timeout: 12_000 }).toBe(1);
  await expect.poll(async () => (await auctionState(fx)).lot?.no, { timeout: 12_000 }).toBe(2);
  await bidAs(fx, fx.managers[1], 2, (await auctionState(fx)).nextBid);
  await expect.poll(async () => (await auctionState(fx)).lastResult?.lot, { timeout: 12_000 }).toBe(2);
  const seeded = await seedMatches(fx);
  return { fx, ...seeded };
}

/** Opens a match from the list; on a phone only the open match shows, so Change comes first. */
async function pick(page: Page, text: string) {
  const change = page.getByRole("button", { name: "Change" });
  if (await change.isVisible()) await change.click();
  await page.getByTestId("stat-match").filter({ hasText: text }).click();
}

async function shot(page: Page, size: number, name: string) {
  mkdirSync(OUT, { recursive: true });
  await page.waitForTimeout(500); // let a sheet or toast finish its entrance
  const file = `${OUT}/${size}-${name}.png`;
  await page.screenshot({ path: file });
  return file;
}

async function pairWithMock(browser: Parameters<typeof mockShot>[0], ours: string, size: number, name: keyof typeof SCREENS) {
  const mock = `${OUT}/mock-${size}-${name}.png`;
  if (await mockShot(browser, "App Screens", SCREENS[name], size, mock)) await pair(browser, ours, mock, `${OUT}/pair-${size}-${name}.png`, size);
}

test.describe("manual stat entry", () => {
  test("the flows, and each screen beside its mock at 390 and 1440", async ({ browser }) => {
    test.setTimeout(240_000); // an auction to seed, ten pictures and the mock page to load
    const { fx, dataset, first, seasonIds } = await roomWithOwners();
    const url = `/rooms/${fx.roomId}/stats`;

    for (const vp of SIZES) {
      // Each size starts from the same state: one manual override on the Derby, for the first player.
      const put = await call(fx.commissioner, "PUT", `/api/rooms/${fx.roomId}/stat-entry/matches/e2e-m2/players/${first.poolPlayerId}`, { fields: [{ stat: "assists", value: 3 }] });
      expect(put.status).toBe(200);
      const context = await contextFor(browser, fx.commissioner, vp);
      const page = await context.newPage();
      await page.goto(url);
      await expect(page.getByTestId("stat-sheet")).toBeVisible();

      // The newest started match has no data and opens first; 9f is the match with data and one override.
      await expect(page.getByTestId("stat-sheet")).toContainText("NOT ENTERED");
      await pick(page, "Derby");
      await expect(page.getByTestId("stat-player-count")).toContainText("of 40 players");
      await expect(page.getByTestId("stat-sheet")).toContainText("MANUAL · BEATS PROVIDER");
      await expect(page.getByTestId("stat-player").first()).toHaveAttribute("aria-current", "true");
      await pairWithMock(browser, await shot(page, vp.width, "entry"), vp.width, "entry");

      // 9g: a value outside the range: the banner, the field's hint, and the typed values stay.
      const typed = page.getByTestId("stat-field").nth(2).locator("input");
      await typed.fill("2000000");
      await page.getByTestId("stat-save").click();
      await expect(page.getByTestId("stat-banner")).toContainText("1 field needs fixing. Nothing was saved.");
      await expect(typed).toHaveValue("2000000");
      await pairWithMock(browser, await shot(page, vp.width, "error"), vp.width, "error");
      await typed.fill("");

      // 9h: a started match with no provider data: every field is empty and entered by hand.
      await pick(page, "NO DATA");
      await expect(page.getByTestId("stat-sheet")).toContainText("NOT ENTERED");
      await expect(page.getByTestId("stat-clear")).toHaveCount(0);
      await pairWithMock(browser, await shot(page, vp.width, "nodata"), vp.width, "nodata");

      // The upcoming match cannot be opened.
      if (vp.width === 1440) await expect(page.getByTestId("stat-match").filter({ hasText: "UPCOMING" })).toBeDisabled();

      // 9i: save and recompute.
      await pick(page, "Derby");
      await expect(page.getByTestId("stat-sheet")).toContainText("MANUAL · BEATS PROVIDER");
      const goals = page.getByTestId("stat-field").nth(1).locator("input");
      await goals.fill(String(vp.width === 390 ? 2 : 3));
      await page.getByTestId("stat-save").click();
      await expect(page.getByRole("status")).toContainText("recomputed");
      await pairWithMock(browser, await shot(page, vp.width, "saved"), vp.width, "saved");

      // 9j: clear the override.
      await page.getByTestId("stat-clear").click();
      await expect(page.getByRole("dialog")).toContainText("goes back to provider data");
      await pairWithMock(browser, await shot(page, vp.width, "clear"), vp.width, "clear");
      await page.getByRole("dialog").getByRole("button", { name: "Clear override" }).click();
      await expect(page.getByRole("dialog")).toHaveCount(0);
      await expect(page.getByTestId("stat-clear")).toHaveCount(0);
      await context.close();
    }
    expect(first.poolPlayerId).toBeTruthy();

    // AC-19: a second tab, opened before the provider delivers, still saves and then shows the new values.
    expect((await call(fx.commissioner, "PUT", `/api/rooms/${fx.roomId}/stat-entry/matches/e2e-m2/players/${first.poolPlayerId}`, { fields: [{ stat: "assists", value: 3 }] })).status).toBe(200);
    const context = await contextFor(browser, fx.commissioner, SIZES[1]);
    const stale = await context.newPage();
    await stale.goto(url);
    await pick(stale, "Derby");
    await expect(stale.getByTestId("stat-sheet")).toContainText("Fake 1");
    await expect(stale.getByTestId("stat-sheet")).toContainText("MANUAL · BEATS PROVIDER");
    const before = await stale.getByTestId("stat-field").nth(0).locator("input").inputValue();
    deliverProvider(dataset, "e2e-m2", seasonIds[0], "minutes", 99);
    await stale.getByTestId("stat-field").nth(1).locator("input").fill("1");
    await stale.getByTestId("stat-save").click();
    await expect(stale.getByRole("status")).toContainText("recomputed");
    await expect(stale.getByTestId("stat-field").filter({ hasText: "Minutes" }).locator("input")).toHaveValue("99");
    expect(before).not.toBe("99");

    // A stale tab refreshes when it comes back to the front.
    deliverProvider(dataset, "e2e-m2", seasonIds[0], "minutes", 77);
    await stale.evaluate(() => document.dispatchEvent(new Event("visibilitychange")));
    await expect(stale.getByTestId("stat-field").filter({ hasText: "Minutes" }).locator("input")).toHaveValue("77");
    await context.close();

    // The hub card (Room commishLive, 9a) lists matches and opens the route.
    const hub = await (await contextFor(browser, fx.commissioner, SIZES[0])).newPage();
    await hub.goto(`/rooms/${fx.roomId}`);
    await expect(hub.getByTestId("stat-entry-card")).toBeVisible();
    await expect(hub.getByTestId("stat-card-match").first()).toBeVisible();
    mkdirSync(OUT, { recursive: true });
    await hub.getByTestId("stat-entry-card").screenshot({ path: `${OUT}/390-hub-card.png` });
    await hub.getByTestId("stat-entry-open").click();
    await expect(hub).toHaveURL(new RegExp(`/rooms/${fx.roomId}/stats`));
    expect((await call(fx.commissioner, "GET", `/api/rooms/${fx.roomId}/scoring/override-log`)).status).toBe(200);
  });
});
