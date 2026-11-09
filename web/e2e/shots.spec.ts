import { mkdirSync, readFileSync } from "node:fs";
import { expect, test, type Browser, type Page } from "@playwright/test";
import { auctionState, bidAs, seedRoom, startIn } from "./support/seed.mjs";
import { auctionUrl, contextFor, sleep, type Fixture } from "./support/helpers";

// T-21 (AC-18): every changed screen at 390 and 1440, light and dark, beside its mock. Mocks are
// the design source frames, served with `python3 -m http.server 8099` in docs/design/source
// (E2E_MOCKS). Pictures land in test-results/shots: <size>-<scheme>-<name>.png (ours),
// mock-<size>-<scheme>-<name>.png and pair-<size>-<scheme>-<name>.png (ours left, mock right).
// Also: a reduced-motion run shows no number roll and no stamp.

const MOCKS = process.env.E2E_MOCKS ?? "http://localhost:8099";
const OUT = "test-results/shots";

/** Where each of our screens is drawn: the design source page and the id of its frame. */
const MOCK: Record<string, { page: "Auction Explorations" | "App Screens"; id: string }> = {
  live: { page: "Auction Explorations", id: "1a" },
  outbid: { page: "Auction Explorations", id: "3a" },
  rejected: { page: "Auction Explorations", id: "3b" },
  paused: { page: "Auction Explorations", id: "3d" },
  adjourned: { page: "Auction Explorations", id: "3e" },
  reconnecting: { page: "Auction Explorations", id: "3f" },
  sold: { page: "Auction Explorations", id: "3g" },
  unsold: { page: "Auction Explorations", id: "3h" },
  observer: { page: "Auction Explorations", id: "3k" },
  closed: { page: "Auction Explorations", id: "3l" },
  noAuction: { page: "Auction Explorations", id: "3m" },
  lobby: { page: "App Screens", id: "3a" },
  lobbyLate: { page: "App Screens", id: "3b1" },
  lobbyOffline: { page: "App Screens", id: "3b2" },
  lobbyAdmin: { page: "App Screens", id: "3b3" },
  sheetSchedule: { page: "App Screens", id: "6o1" },
  sheetScheduleError: { page: "App Screens", id: "6o2" },
  sheetScheduleRefused: { page: "App Screens", id: "6o3" },
  lobbyReschedule: { page: "App Screens", id: "3b4" },
};

/**
 * AC-12: the current bid and the purse are in view without scrolling, in every R-11 state. Where a
 * state draws neither (the Commissioner has no purse; an adjourned auction has no lot) the mock's
 * own stand-in is checked: the purse-left tile.
 */
const IN_VIEW: Record<string, string[]> = {
  live: ["current-bid", "purse"],
  outbid: ["current-bid", "purse"],
  rejected: ["current-bid", "purse"],
  reconnecting: ["current-bid", "purse"],
  paused: ["current-bid", "purse"],
  sold: ["current-bid", "purse"],
  unsold: ["current-bid", "purse"],
  observer: ["current-bid"],
  adjourned: ["purse", "adjourned-purse"],
};

async function inView(page: Page, name: string, vp: { width: number; height: number }) {
  for (const id of IN_VIEW[name] ?? []) {
    const el = page.getByTestId(id);
    await expect(el, `${name}: ${id} shown`).toBeVisible();
    const box = (await el.boundingBox())!;
    expect(box.x, `${name}: ${id} left edge`).toBeGreaterThanOrEqual(0);
    expect(box.y, `${name}: ${id} top edge`).toBeGreaterThanOrEqual(0);
    expect(box.x + box.width, `${name}: ${id} right edge`).toBeLessThanOrEqual(vp.width);
    expect(box.y + box.height, `${name}: ${id} bottom edge`).toBeLessThanOrEqual(vp.height);
    // One line only: a money value never wraps (AC-18).
    expect(await el.evaluate((n) => n.scrollWidth <= n.clientWidth + 1 && n.getClientRects().length === 1), `${name}: ${id} does not overflow`).toBe(true);
  }
  if (name === "adjourned") {
    // AC-18: "€100 M" stays on one line in the tile (the mock's tile is one line high).
    const oneLine = await page.getByTestId("adjourned-purse").evaluate((n) => n.getBoundingClientRect().height < parseFloat(getComputedStyle(n).fontSize) * 1.8);
    expect(oneLine, "adjourned purse tile is one line").toBe(true);
  }
}

const mockPages = new Map<string, Page>();
async function mockPage(browser: Browser, name: string): Promise<Page> {
  let page = mockPages.get(name);
  if (!page) {
    page = await (await browser.newContext({ viewport: { width: 1700, height: 1200 } })).newPage();
    await page.goto(`${MOCKS}/${encodeURIComponent(name + ".dc.html")}`);
    await page.waitForTimeout(6000);
    mockPages.set(name, page);
  }
  return page;
}

async function mockShot(browser: Browser, name: string, w: number, scheme: string, file: string): Promise<boolean> {
  const spec = MOCK[name];
  if (!spec) return false;
  // The dark mock is 3j, the live screen on a phone; no other screen is drawn dark.
  const id = scheme === "dark" ? (name === "live" && w === 390 ? { page: "Auction Explorations" as const, id: "3j" } : null) : spec;
  if (!id) return false;
  const page = await mockPage(browser, id.page);
  const el = page.locator(`[id="${id.id}"] .sc-host[style*="width: ${w}px"]`).first();
  if ((await el.count()) === 0) return false;
  await el.scrollIntoViewIfNeeded();
  await page.waitForTimeout(400);
  await el.screenshot({ path: file });
  return true;
}

async function pair(browser: Browser, ours: string, mock: string, out: string, width: number) {
  const img = (f: string) => `data:image/png;base64,${readFileSync(f).toString("base64")}`;
  const page = await browser.newPage({ viewport: { width: width === 390 ? 840 : 1500, height: width === 390 ? 900 : 480 } });
  const zoom = width === 390 ? 1 : 0.5;
  await page.setContent(
    `<body style="margin:0;display:flex;gap:16px;background:#999;font:12px sans-serif"><div><div>ours</div><img style="zoom:${zoom}" src="${img(ours)}"></div><div><div>mock</div><img style="zoom:${zoom}" src="${img(mock)}"></div></body>`,
  );
  await page.screenshot({ path: out, fullPage: true });
  await page.close();
}

async function goLive(fx: Fixture) {
  await startIn(fx, 2);
  for (let i = 0; i < 100; i++) {
    if ((await auctionState(fx)).phase === "live") return;
    await sleep(100);
  }
  throw new Error("auction did not start");
}

/** A page whose state reads are rewritten, to show states the engine cannot produce yet. */
async function fixturePage(browser: Browser, fx: Fixture, cookie: string, vp: { width: number; height: number; colorScheme: "light" | "dark" }, change: (s: Record<string, unknown>) => void) {
  const context = await contextFor(browser, cookie, vp);
  const page = await context.newPage();
  await page.route("**/api/rooms/*/auction", async (route) => {
    if (route.request().method() !== "GET") return route.continue();
    const res = await route.fetch();
    const body = await res.json();
    change(body);
    await route.fulfill({ response: res, json: body });
  });
  await page.goto(auctionUrl(fx));
  return { page, context };
}

for (const vp of [
  { width: 390, height: 844 },
  { width: 1440, height: 900 },
]) {
  for (const colorScheme of ["light", "dark"] as const) {
    test(`screens at ${vp.width} ${colorScheme}`, async ({ browser }) => {
      mkdirSync(OUT, { recursive: true });
      test.setTimeout(300_000);
      const fx = await seedRoom({ managers: 3, startInSec: 600, open: 20, bid: 60, gap: 8 });
      const shot = async (page: Page, name: string) => {
        await inView(page, name, vp);
        await page.waitForTimeout(450); // let fades and the number roll settle
        const base = `${OUT}/${vp.width}-${colorScheme}-${name}`;
        await page.screenshot({ path: `${base}.png` });
        const mock = `${OUT}/mock-${vp.width}-${colorScheme}-${name}.png`;
        if (await mockShot(browser, name, vp.width, colorScheme, mock)) await pair(browser, `${base}.png`, mock, `${OUT}/pair-${vp.width}-${colorScheme}-${name}.png`, vp.width);
      };
      const ctx = { ...vp, colorScheme };
      for (const name of Object.keys(MOCK)) await mockPage(browser, MOCK[name].page);

      // Lobby and its variants.
      const mainContext = await contextFor(browser, fx.managers[0].cookie, ctx);
      const page = await mainContext.newPage();
      await page.goto(auctionUrl(fx));
      await expect(page.getByTestId("lobby-countdown")).toHaveText(/\d\d:\d\d:\d\d/);
      await page.waitForTimeout(600);
      await shot(page, "lobby");
      await mainContext.setOffline(true);
      await expect(page.getByTestId("lobby-offline")).toBeVisible({ timeout: 3000 });
      await shot(page, "lobbyOffline");
      await mainContext.setOffline(false);
      await expect(page.getByTestId("lobby-offline")).toBeHidden({ timeout: 6000 });

      const late = await fixturePage(browser, fx, fx.managers[1].cookie, ctx, (s) => {
        s.startsAt = new Date(Date.now() - 372_000).toISOString();
      });
      await expect(late.page.getByTestId("lobby-late")).toBeVisible();
      await shot(late.page, "lobbyLate");
      await late.context.close();

      const admin = await contextFor(browser, fx.commissioner, ctx);
      const adminPage = await admin.newPage();
      await adminPage.goto(auctionUrl(fx));
      await expect(adminPage.getByTestId("lobby-admin")).toBeVisible();
      await adminPage.waitForTimeout(400);
      await shot(adminPage, "lobbyAdmin");

      // The lot screen.
      await goLive(fx);
      await expect(page.getByTestId("bid-button")).toHaveAttribute("data-state", "ready", { timeout: 4000 });
      await page.waitForTimeout(300);
      await shot(page, "live");
      await bidAs(fx, fx.managers[1], 1, (await auctionState(fx)).nextBid);
      await expect(page.getByTestId("leader")).toContainText("Club 2");
      await page.getByTestId("bid-button").click();
      await expect(page.getByTestId("bid-button")).toHaveAttribute("data-state", "leading");
      await bidAs(fx, fx.managers[2], 1, (await auctionState(fx)).nextBid);
      await expect(page.getByTestId("notice-outbid")).toBeVisible();
      await shot(page, "outbid");
      await page.getByRole("button", { name: "Dismiss" }).click({ timeout: 2000 }).catch(() => undefined);
      await expect(page.getByTestId("notice-outbid")).toBeHidden();

      await sleep(400); // a tap inside 300 ms of the last one counts as the same tap
      await page.route("**/auction/bids", (route) => route.fulfill({ json: { outcome: "rejected", reason: "purse", lot: 1, serverNow: new Date().toISOString() } }));
      await page.getByTestId("bid-button").click();
      await expect(page.getByTestId("notice-rejected")).toBeVisible();
      await shot(page, "rejected");
      await page.getByRole("button", { name: "Dismiss" }).click({ timeout: 2000 }).catch(() => undefined);
      await page.unroute("**/auction/bids");

      await mainContext.setOffline(true);
      await expect(page.getByTestId("notice-reconnecting")).toBeVisible({ timeout: 3000 });
      await shot(page, "reconnecting");
      await mainContext.setOffline(false);
      await expect(page.getByTestId("notice-reconnecting")).toBeHidden({ timeout: 6000 });

      const paused = await fixturePage(browser, fx, fx.managers[0].cookie, ctx, (s) => (s.phase = "paused"));
      await expect(paused.page.getByTestId("notice-paused")).toBeVisible();
      await shot(paused.page, "paused");
      await paused.context.close();
      const adjourned = await fixturePage(browser, fx, fx.managers[0].cookie, ctx, (s) => {
        s.phase = "adjourned";
        s.lotOpen = false;
      });
      await expect(adjourned.page.getByTestId("adjourned-card")).toBeVisible();
      await shot(adjourned.page, "adjourned");
      await adjourned.context.close();
      const closed = await fixturePage(browser, fx, fx.managers[0].cookie, ctx, (s) => {
        s.phase = "ended";
        s.lotOpen = false;
        delete s.nextBid;
      });
      await expect(closed.page.getByTestId("closed-card")).toBeVisible();
      await shot(closed.page, "closed");
      await closed.context.close();
      // R-13: a scheduled room with no start; the Commissioner gets Schedule auction on the card.
      const none = await contextFor(browser, fx.commissioner, ctx);
      const nonePage = await none.newPage();
      await nonePage.route("**/api/rooms/*/auction", (route) =>
        route.request().method() === "GET" ? route.fulfill({ status: 404, json: { code: "no_auction", message: "No auction is scheduled" } }) : route.continue(),
      );
      await nonePage.goto(auctionUrl(fx));
      await expect(nonePage.getByTestId("schedule-from-card")).toBeVisible();
      await shot(nonePage, "noAuction");
      await none.close();

      await adminPage.reload();
      await expect(adminPage.getByTestId("bid-observer")).toBeVisible();
      await adminPage.waitForTimeout(500);
      await shot(adminPage, "observer");

      // Sold (to Club 3), then unsold on the next lot.
      await expect(page.getByTestId("stamp-sold")).toBeVisible({ timeout: 80_000 });
      await shot(page, "sold");
      await expect(page.getByTestId("stamp-unsold")).toBeVisible({ timeout: 60_000 });
      await shot(page, "unsold");
      await mainContext.close();
      await admin.close();
    });
  }
}

test("reduced motion: no number roll and no stamp, fades only; without it they animate", async ({ browser }) => {
  const fx = await seedRoom({ managers: 2, startInSec: 600, open: 10, bid: 3, gap: 3 });
  const animation = (page: Page, sel: string) => page.locator(sel).first().evaluate((el) => getComputedStyle(el).animationName);
  const reduced = await (await contextFor(browser, fx.managers[0].cookie, { reducedMotion: "reduce" })).newPage();
  const normal = await (await contextFor(browser, fx.managers[1].cookie, { reducedMotion: "no-preference" })).newPage();
  await reduced.goto(auctionUrl(fx));
  await normal.goto(auctionUrl(fx));
  await goLive(fx);
  await expect(reduced.getByTestId("bid-button")).toHaveAttribute("data-state", "ready");
  await bidAs(fx, fx.managers[1], 1, (await auctionState(fx)).nextBid);
  await expect(reduced.getByTestId("leader")).toContainText("Club 2");
  await expect(normal.getByTestId("leader")).toContainText("You");

  expect(await animation(reduced, '[data-testid="current-bid"] > span')).not.toMatch(/roll/);
  expect(await animation(normal, '[data-testid="current-bid"] > span')).toMatch(/roll/);

  await expect(reduced.getByTestId("stamp-sold")).toBeVisible({ timeout: 8000 });
  await expect(normal.getByTestId("stamp-sold")).toBeVisible({ timeout: 8000 });
  const stamp = (page: Page) => page.getByTestId("stamp-sold").locator("> div").first().evaluate((el) => getComputedStyle(el).animationName);
  expect(await stamp(reduced)).not.toMatch(/hammer/);
  expect(await stamp(reduced)).toMatch(/fade/);
  expect(await stamp(normal)).toMatch(/hammer/);
});

// T-25 (AC-18): the schedule sheet (new, past time, pool refused) and the lobby's Change time sheet,
// each at 390 and 1440 beside its mock (6o1, 6o2, 6o3, 3b4).
for (const vp of [
  { width: 390, height: 844 },
  { width: 1440, height: 900 },
]) {
  test(`schedule screens at ${vp.width}`, async ({ browser }) => {
    mkdirSync(OUT, { recursive: true });
    test.setTimeout(180_000);
    const shot = async (page: Page, name: string) => {
      await page.waitForTimeout(450);
      const base = `${OUT}/${vp.width}-light-${name}`;
      await page.screenshot({ path: `${base}.png` });
      const mock = `${OUT}/mock-${vp.width}-light-${name}.png`;
      if (await mockShot(browser, name, vp.width, "light", mock)) await pair(browser, `${base}.png`, mock, `${OUT}/pair-${vp.width}-light-${name}.png`, vp.width);
    };
    const ctx = { ...vp, colorScheme: "light" as const };
    const open = await seedRoom({ managers: 2, schedule: false });
    const small = await seedRoom({ managers: 2, schedule: false, squadMin: 30 });
    const fixed = await seedRoom({ managers: 2, startInSec: 3 * 3600 });

    const at = async (fx: Fixture) => {
      const page = await (await contextFor(browser, fx.commissioner, ctx)).newPage();
      await page.goto(`/rooms/${fx.roomId}`);
      await page.getByRole("button", { name: "Schedule auction" }).click();
      return page;
    };
    const day = (days: number) => {
      const d = new Date(Date.now() + days * 86_400_000);
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
    };

    const page = await at(open);
    await page.getByLabel("Date", { exact: true }).fill(day(6));
    await page.getByLabel("Start time", { exact: true }).fill("20:00");
    await shot(page, "sheetSchedule");
    await page.getByLabel("Date", { exact: true }).fill(day(-1));
    await page.getByRole("dialog").getByRole("button", { name: "Schedule auction" }).click();
    await expect(page.getByTestId("schedule-past")).toBeVisible();
    await shot(page, "sheetScheduleError");

    const refused = await at(small);
    await refused.getByLabel("Date", { exact: true }).fill(day(6));
    await refused.getByLabel("Start time", { exact: true }).fill("20:00");
    await refused.getByRole("dialog").getByRole("button", { name: "Schedule auction" }).click();
    await expect(refused.getByTestId("schedule-refused")).toBeVisible();
    await shot(refused, "sheetScheduleRefused");

    const lobby = await (await contextFor(browser, fixed.commissioner, ctx)).newPage();
    await lobby.goto(auctionUrl(fixed));
    await expect(lobby.getByTestId("change-time")).toBeVisible();
    await lobby.getByTestId("change-time").click();
    await expect(lobby.getByRole("dialog")).toBeVisible();
    await shot(lobby, "lobbyReschedule");
  });
}
