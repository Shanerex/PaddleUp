import { expect, test, type Page } from "@playwright/test";
import { auctionState, bidAs, seedRoom, startIn } from "./support/seed.mjs";
import { contextFor, sleep, type Fixture } from "./support/helpers";
import { MOCKS, ensureDir, pair } from "./support/mocks";

// 2026-11-22-auction-panels T-7 (AC-1 to AC-5): the Rivals and Bids chips and sheets, the Bids feed card
// and the Your needs card against a real server: bids from two managers reaching an open view with no
// reload, a new tab, a tab with no live socket, a Commissioner with no franchise, and 390 and 1440
// pictures beside Auction A Spotlight. Pictures land in test-results/panels as <size>-<name>.png,
// mock-<size>-<name>.png and pair-<size>-<name>.png (ours left, mock right).

const OUT = "test-results/panels";
const SIZES = [
  { name: "390", width: 390, height: 844 },
  { name: "1440", width: 1440, height: 900 },
];

const hideDevBadge = (page: Page) => page.addStyleTag({ content: "nextjs-portal { display: none !important; }" });

async function until(what: string, ok: () => Promise<boolean>, ms = 15000) {
  for (let i = 0; i < ms / 100; i++) {
    if (await ok()) return;
    await sleep(100);
  }
  throw new Error(`timed out: ${what}`);
}

async function goLive(fx: Fixture) {
  await startIn(fx, 2);
  await until("live", async () => (await auctionState(fx)).phase === "live");
  await until("lot 1 open", async () => {
    const s = await auctionState(fx);
    return s.lotOpen && s.lot?.no === 1;
  });
}

async function raise(fx: Fixture, who: number) {
  const s = await auctionState(fx);
  const res = await bidAs(fx, fx.managers[who], 1, s.nextBid);
  expect(res.body.outcome).toBe("accepted");
}

/** Auction A Spotlight as drawn, with its `open` chip pressed (none: the screen as it stands). */
async function mockShot(page: Page, size: (typeof SIZES)[number], file: string, open?: string) {
  if (!MOCKS) return false;
  const ctx = await page.context().browser()!.newContext({ viewport: { width: size.width, height: size.height } });
  const mock = await ctx.newPage();
  await mock.goto(`${MOCKS}/${encodeURIComponent("Auction A Spotlight.dc.html")}`);
  await mock.waitForTimeout(4000);
  if (open) {
    await mock.getByRole("button", { name: new RegExp(`${open}$`) }).first().click();
    await mock.waitForTimeout(600);
  }
  await mock.screenshot({ path: file });
  await ctx.close();
  return true;
}

async function shoot(page: Page, name: string, open?: string) {
  ensureDir(OUT);
  await hideDevBadge(page);
  for (const size of SIZES) {
    await page.setViewportSize({ width: size.width, height: size.height });
    await page.waitForTimeout(600);
    const ours = `${OUT}/${size.name}-${name}.png`;
    await page.screenshot({ path: ours });
    const mock = `${OUT}/mock-${size.name}-${name}.png`;
    if (await mockShot(page, size, mock, open)) await pair(page.context().browser()!, ours, mock, `${OUT}/pair-${size.name}-${name}.png`, size.width);
  }
}

const feedRows = (page: Page) => page.getByTestId("feed-card").getByTestId("feed-row");
/** The rows as "name amount", without the age, which differs per tab. */
const rowsText = async (page: Page) => (await feedRows(page).evaluateAll((els) => els.map((e) => (e.children[1].textContent ?? "") + " " + (e.children[2].textContent ?? ""))));

test.describe("auction panels", () => {
  test.setTimeout(240_000);
  test("chips, feed, rivals and needs follow two managers' bids with no reload; a new tab and a socket-less tab agree", async ({ browser }) => {
    const fx = await seedRoom({ managers: 3, startInSec: 600, open: 60, bid: 30, gap: 8, squadMin: 3, roleMinimums: { gk: 1, def: 2 } });
    const me = await (await contextFor(browser, fx.managers[0].cookie)).newPage();
    await me.goto(`/rooms/${fx.roomId}/auction`);
    await goLive(fx);

    // AC-1: the chip row, and AC-4: the Your needs card, with nothing bid yet.
    await expect(me.getByTestId("rivals-chip")).toBeVisible({ timeout: 10000 });
    const chips = await me.getByRole("button").evaluateAll((els) => els.map((e) => e.getAttribute("data-testid")).filter((id) => id?.endsWith("-chip")));
    expect(chips).toEqual(["rivals-chip", "feed-chip", "squad-chip"]);
    await expect(me.getByTestId("need-gk")).toContainText("0/1");
    await expect(me.getByTestId("need-def")).toContainText("0/2");
    await expect(me.getByTestId("needs-squad")).toContainText("0 / 3");
    await expect(me.getByTestId("max-bid")).toContainText("Max bid");
    await expect(feedRows(me)).toHaveCount(0);
    await expect(me.getByTestId("feed-opened")).toContainText("Lot 1 opened at");

    // AC-2: two managers bid; the open view shows each within one update, newest first.
    await raise(fx, 1);
    await expect(feedRows(me)).toHaveCount(1, { timeout: 6000 });
    await expect(feedRows(me).first()).toContainText("Club 2");
    await raise(fx, 2);
    await expect(feedRows(me)).toHaveCount(2, { timeout: 6000 });
    await expect(feedRows(me).first()).toContainText("Club 3");
    await raise(fx, 1);
    await expect(feedRows(me)).toHaveCount(3, { timeout: 6000 });
    const mine = await rowsText(me);
    expect(mine[0]).toContain("Club 2");

    // A new tab rebuilds the same rows from the events page.
    const second = await (await contextFor(browser, fx.managers[0].cookie)).newPage();
    await second.goto(`/rooms/${fx.roomId}/auction`);
    await expect(feedRows(second)).toHaveCount(3, { timeout: 10000 });
    expect(await rowsText(second)).toEqual(mine);

    // A tab that loses its connection keeps the rows it has, behind but not hidden, and catches up on reconnect.
    const staleContext = await contextFor(browser, fx.managers[1].cookie);
    const stale = await staleContext.newPage();
    await stale.goto(`/rooms/${fx.roomId}/auction`);
    await expect(feedRows(stale)).toHaveCount(3, { timeout: 10000 });
    expect(await rowsText(stale)).toEqual(mine.map((r) => r.replace("Club 2", "You")));
    await staleContext.setOffline(true);
    await raise(fx, 2);
    await expect(feedRows(me)).toHaveCount(4, { timeout: 6000 });
    await sleep(1500);
    await expect(feedRows(stale)).toHaveCount(3);
    await staleContext.setOffline(false);
    await expect(feedRows(stale)).toHaveCount(4, { timeout: 20000 });
    expect((await rowsText(stale))[0]).toContain("Club 3");

    // AC-3: the Rivals sheet lists all three franchises with the leader and presence, updating while open.
    await me.getByTestId("rivals-chip").click();
    const sheet = me.getByRole("dialog", { name: "The room" });
    await expect(sheet.getByTestId("rival-row")).toHaveCount(3);
    await expect(sheet.getByTestId("rival-row").filter({ hasText: "Club 3" })).toContainText("LEADING");
    await expect(sheet.getByTestId("rival-row").filter({ hasText: "Club 1" })).toContainText("needs 1 GK · 2 DEF");
    await expect(sheet.getByTestId("rival-row").filter({ hasText: "Club 1" })).not.toContainText("OFFLINE");
    await expect(sheet.getByTestId("rival-row").filter({ hasText: "Club 3" })).toContainText("OFFLINE");
    await raise(fx, 1);
    await expect(sheet.getByTestId("rival-row").filter({ hasText: "Club 2" })).toContainText("LEADING", { timeout: 6000 });
    await expect(sheet.getByTestId("rival-row").filter({ hasText: "Club 3" })).not.toContainText("LEADING");
    await shoot(me, "rivals-sheet", "Rivals");
    await me.keyboard.press("Escape");

    // The Bids sheet lists the same rows; the needs card opens the squad sheet.
    await me.setViewportSize({ width: 390, height: 844 });
    await me.getByTestId("feed-chip").click();
    const bids = me.getByRole("dialog", { name: "Bids on Lot 1" });
    await expect(bids.getByTestId("feed-row")).toHaveCount(5);
    await expect(bids.getByTestId("feed-opened")).toContainText("Lot 1 opened at");
    await shoot(me, "bids-sheet", "Bids");
    await me.keyboard.press("Escape");
    await me.getByTestId("needs-card").click();
    await expect(me.getByTestId("squad-sheet")).toBeVisible();
    await me.keyboard.press("Escape");

    const text = (await me.locator("body").innerText()).toLowerCase();
    for (const word of ["cricket", "wicket", "batter", "bowler", "overseas", "rupee", "₹"]) expect(text).not.toContain(word);
    await shoot(me, "screen");
  });

  test("a Commissioner with no franchise gets the chips and the feed, but no Squad chip or Your needs card", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 600, open: 60, bid: 30, gap: 8, squadMin: 3, roleMinimums: { gk: 1 } });
    await goLive(fx);
    await raise(fx, 0);
    const boss = await (await contextFor(browser, fx.commissioner)).newPage();
    await boss.goto(`/rooms/${fx.roomId}/auction`);
    await expect(boss.getByTestId("controls-chip")).toBeVisible({ timeout: 10000 });
    const chips = await boss.getByRole("button").evaluateAll((els) => els.map((e) => e.getAttribute("data-testid")).filter((id) => id?.endsWith("-chip")));
    expect(chips).toEqual(["controls-chip", "rivals-chip", "feed-chip"]);
    await expect(feedRows(boss)).toHaveCount(1, { timeout: 10000 });
    await expect(boss.getByTestId("needs-card")).toHaveCount(0);
    await shoot(boss, "commissioner-screen");
  });
});
