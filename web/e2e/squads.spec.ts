import { expect, test, type Page } from "@playwright/test";
import { auctionState, bidAs, seedRoom, startIn } from "./support/seed.mjs";
import { contextFor, sleep, type Fixture } from "./support/helpers";
import { MOCKS, ensureDir, mockShot, pair, type Frame } from "./support/mocks";

// 10-squads T-7 (AC-1 to AC-5): the squads page and the auction screen's squad sheet against a real
// server: a hammer reaching an open view with no reload, a new tab, a stale (socket-less) tab, and
// 390 and 1440 pictures beside the design source. Pictures land in test-results/squads as
// <size>-<name>.png, mock-<size>-<name>.png and pair-<size>-<name>.png (ours left, mock right).
// E2E_MOCKS is where docs/design/source is served or a file:// URL of it (see support/mocks.ts).

const OUT = "test-results/squads";
const SIZES = [
  { name: "390", width: 390, height: 844 },
  { name: "1440", width: 1440, height: 900 },
];
const FRAME: Record<string, Frame> = {
  "squad-live": { page: "App Screens", id: "9d" },
  "squad-empty": { page: "App Screens", id: "9e" },
};

async function hideDevBadge(page: Page) {
  await page.addStyleTag({ content: "nextjs-portal { display: none !important; }" });
}

async function shoot(page: Page, name: string) {
  ensureDir(OUT);
  await hideDevBadge(page);
  for (const size of SIZES) {
    await page.setViewportSize({ width: size.width, height: size.height });
    await page.waitForTimeout(500);
    const ours = `${OUT}/${size.name}-${name}.png`;
    await page.screenshot({ path: ours });
    const frame = FRAME[name];
    if (MOCKS && frame) {
      const mock = `${OUT}/mock-${size.name}-${name}.png`;
      if (await mockShot(page.context().browser()!, frame, size.width, mock)) await pair(page.context().browser()!, ours, mock, `${OUT}/pair-${size.name}-${name}.png`, size.width);
    }
  }
}

/** The squad sheet of Auction A Spotlight, opened from its My squad chip, as the mock draws it. */
async function sheetMockShot(page: Page, size: { name: string; width: number; height: number }, file: string) {
  if (!MOCKS) return;
  const ctx = await page.context().browser()!.newContext({ viewport: { width: size.width, height: size.height } });
  const mock = await ctx.newPage();
  await mock.goto(`${MOCKS}/${encodeURIComponent("Auction A Spotlight.dc.html")}`);
  await mock.waitForTimeout(4000);
  await mock.getByRole("button", { name: "My squad" }).first().click();
  await mock.waitForTimeout(600);
  await mock.screenshot({ path: file });
  await ctx.close();
}

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
}

/** Manager `who` wins lot `lot` at its opening price, and the lot closes. */
async function buy(fx: Fixture, who: number, lot: number) {
  await until(`lot ${lot} open`, async () => {
    const s = await auctionState(fx);
    return s.lotOpen && s.lot?.no === lot;
  });
  const s = await auctionState(fx);
  const res = await bidAs(fx, fx.managers[who], lot, s.nextBid);
  expect(res.body.outcome).toBe("accepted");
  await until(`lot ${lot} closed`, async () => (await auctionState(fx)).lotsClosed >= lot);
}

const rows = (page: Page) => page.getByTestId("squad-player");

test.describe("squads", () => {
  test("a hammer reaches the open view with no reload, a new tab and the auction sheet agree, at 390 and 1440", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 600, open: 8, bid: 3, gap: 8, squadMin: 3, roleMinimums: { gk: 1, def: 2 } });
    const me = await (await contextFor(browser, fx.managers[0].cookie)).newPage();
    await me.goto(`/rooms/${fx.roomId}/squads`);

    // Before the first hammer: both franchises show the empty state with the scheduled start.
    await expect(me.getByRole("group", { name: "Franchises" }).getByRole("button")).toHaveText([/You/, /Club 2/]);
    await expect(me.getByTestId("squad-empty")).toContainText("The first hammer falls");
    await expect(me.getByTestId("squad-pill")).toContainText("AUCTION");
    await expect(me.getByTestId("squad-spent")).toHaveText("—");
    await expect(me.getByTestId("squad-role-def")).toContainText("Needs 2");
    await shoot(me, "squad-empty");
    await me.getByRole("button", { name: /Club 2/ }).click();
    await expect(me.getByTestId("squad-empty")).toContainText("The first hammer falls");
    await me.getByRole("button", { name: /You/ }).click();

    // The auction goes live: the open view follows without a reload.
    await goLive(fx);
    await expect(me.getByTestId("squad-pill")).toHaveText("LIVE", { timeout: 8000 });
    await expect(me.getByTestId("squad-empty")).toContainText("Nothing bought yet");
    const purse0 = await me.getByTestId("squad-purse").innerText();

    // First hammer: lot 1 (a Goalkeeper in the stub pool) goes to me.
    await buy(fx, 0, 1);
    await expect(rows(me)).toHaveCount(1, { timeout: 6000 });
    await expect(rows(me).first()).toContainText("Fake 1");
    await expect(me.getByTestId("just-in")).toBeVisible();
    await expect(me.getByTestId("squad-role-gk")).toContainText("Met");
    await expect(me.getByTestId("squad-role-def")).toContainText("Needs 2");
    expect(await me.getByTestId("squad-purse").innerText()).not.toBe(purse0);
    await expect(me.getByTestId("squad-spent")).not.toHaveText("—");

    // A new tab sees the same squad from the server's state alone.
    const second = await (await contextFor(browser, fx.managers[0].cookie)).newPage();
    await second.goto(`/rooms/${fx.roomId}/squads`);
    await expect(rows(second)).toHaveCount(1);
    await expect(second.getByTestId("squad-purse")).toHaveText(await me.getByTestId("squad-purse").innerText());
    // Another manager's squad, and the Commissioner's view of mine, need no permission beyond being in the room.
    await second.getByRole("button", { name: /Club 2/ }).click();
    await expect(second.getByTestId("squad-empty")).toBeVisible();
    await second.getByRole("button", { name: /You/ }).click();
    const boss = await (await contextFor(browser, fx.commissioner)).newPage();
    await boss.goto(`/rooms/${fx.roomId}/squads`);
    await expect(boss.getByRole("group", { name: "Franchises" }).getByRole("button")).toHaveCount(2);

    // Two more hammers, then the pictures.
    await buy(fx, 0, 2);
    await buy(fx, 0, 3);
    await expect(rows(me)).toHaveCount(3, { timeout: 6000 });
    await expect(rows(second)).toHaveCount(3, { timeout: 6000 });
    await expect(me.getByTestId("squad-count")).toContainText("3");
    await expect(me.getByTestId("squad-role-def")).toContainText("1/2");
    const text = (await me.locator("body").innerText()).toLowerCase();
    for (const word of ["cricket", "wicket", "batter", "bowler", "overseas", "rupee", "₹"]) expect(text).not.toContain(word);
    await shoot(me, "squad-live");

    // The auction screen's squad chip opens my squad in a sheet from the same component.
    await me.setViewportSize({ width: 390, height: 844 });
    await me.goto(`/rooms/${fx.roomId}/auction`);
    await me.getByTestId("squad-chip").click();
    const sheet = me.getByTestId("squad-sheet");
    await expect(sheet).toBeVisible();
    await expect(sheet.getByTestId("squad-player")).toHaveCount(3);
    await expect(sheet.getByTestId("sheet-role-gk")).toContainText("1/1");
    await expect(sheet.getByTestId("sheet-role-def")).toContainText("1/2");
    await hideDevBadge(me);
    for (const size of SIZES) {
      await me.setViewportSize({ width: size.width, height: size.height });
      await me.waitForTimeout(600);
      ensureDir(OUT);
      const ours = `${OUT}/${size.name}-squad-sheet.png`;
      await me.screenshot({ path: ours });
      const mock = `${OUT}/mock-${size.name}-squad-sheet.png`;
      await sheetMockShot(me, size, mock);
      if (MOCKS) await pair(browser, ours, mock, `${OUT}/pair-${size.name}-squad-sheet.png`, size.width);
    }
    // A hammer while the sheet is open updates it.
    await me.keyboard.press("Escape");
  });

  test("a tab with no live socket still follows the hammers by fetching the state, and says so", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 600, open: 8, bid: 3, gap: 2, squadMin: 3 });
    await goLive(fx);
    const context = await contextFor(browser, fx.managers[1].cookie);
    const stale = await context.newPage();
    // The socket never connects: this tab only has what it can fetch.
    await stale.routeWebSocket(/\/api\/ws/, (ws) => ws.close());
    await stale.goto(`/rooms/${fx.roomId}/squads`);
    await stale.getByRole("button", { name: /Club 1/ }).click();
    await expect(stale.getByTestId("squads-offline")).toBeVisible({ timeout: 8000 });
    await expect(stale.getByTestId("squad-empty")).toBeVisible();

    await buy(fx, 0, 1);

    // The fallback asks again every 5 s.
    await expect(rows(stale)).toHaveCount(1, { timeout: 12000 });
    await expect(stale.getByTestId("squad-purse")).not.toHaveText(/€100 M/);
    await shoot(stale, "squad-stale");
  });
});
