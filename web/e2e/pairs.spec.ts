import { expect, test, type Page } from "@playwright/test";
import { auctionState, bidAs, call, seedRoom } from "./support/seed.mjs";
import { auctionUrl, contextFor, sleep, type Fixture } from "./support/helpers";
import { MOCKS, ensureDir, mockShot, pair, type Frame } from "./support/mocks";

// F-2 (AC-14): the screens of the auction-controls spec beside their design frames at 390 and 1440:
// the lobby with Start now, the Room page's AUCTION CONTROLS card, the audit log (with entries and
// empty), the Controls sheets (skip, re-open, adjourn), the paused and adjourned views and the
// resume lobby. Pictures land in test-results/controls as <size>-<screen>.png, mock-<size>-<screen>.png
// and pair-<size>-<screen>.png (ours left, mock right). Needs E2E_MOCKS (see support/mocks.ts) for
// the mock side; without it only our pictures are taken.

const OUT = "test-results/controls";
const SIZES = [
  { name: "390", width: 390, height: 844 },
  { name: "1440", width: 1440, height: 900 },
];

/** Where each picture is drawn in the design source. The mocks draw no resume lobby: 3e is nearest. */
const FRAME: Record<string, Frame> = {
  "lobby-start-now": { page: "App Screens", id: "3b3" },
  "room-commish-card": { page: "App Screens", id: "9a" },
  audit: { page: "App Screens", id: "9b" },
  "audit-empty": { page: "App Screens", id: "9c" },
  "controls-sheet": { page: "Auction Explorations", id: "3n" },
  "paused-commish": { page: "Auction Explorations", id: "3o" },
  "skip-confirm": { page: "Auction Explorations", id: "3p" },
  "reopen-sheet": { page: "Auction Explorations", id: "3r" },
  "adjourn-sheet": { page: "Auction Explorations", id: "3u" },
  "adjourned-commissioner": { page: "Auction Explorations", id: "3v" },
  adjourned: { page: "Auction Explorations", id: "3e" },
  "resume-lobby": { page: "Auction Explorations", id: "3e" },
};

/** The Next dev badge sits over the bottom-left of a phone screen: out of the pictures and the clicks. */
async function hideDevBadge(page: Page) {
  await page.addStyleTag({ content: "nextjs-portal { display: none !important; }" });
}

async function shoot(page: Page, size: string, name: string) {
  ensureDir(OUT);
  await hideDevBadge(page);
  await page.waitForTimeout(600); // sheets and notices fade in
  const ours = `${OUT}/${size}-${name}.png`;
  await page.screenshot({ path: ours });
  const frame = FRAME[name];
  if (MOCKS && frame) {
    const mock = `${OUT}/mock-${size}-${name}.png`;
    if (await mockShot(page.context().browser()!, frame, Number(size), mock)) await pair(page.context().browser()!, ours, mock, `${OUT}/pair-${size}-${name}.png`, Number(size));
  }
}

const control = (fx: Fixture, action: string, extra: Record<string, unknown> = {}) =>
  call(fx.commissioner, "POST", `/api/rooms/${fx.roomId}/auction/controls`, { intentId: crypto.randomUUID(), action, ...extra });

async function waitPhase(fx: Fixture, phase: string, ms = 8000) {
  for (let i = 0; i < ms / 100; i++) {
    if ((await auctionState(fx)).phase === phase) return;
    await sleep(100);
  }
  throw new Error(`auction did not reach ${phase}`);
}

for (const size of SIZES) {
  const vp = { width: size.width, height: size.height };

  test(`the lobby with Start now at ${size.name}`, async ({ browser }) => {
    const fx = await seedRoom({ managers: 3, startInSec: 3600, open: 30, bid: 10, gap: 2 });
    const boss = await (await contextFor(browser, fx.commissioner, vp)).newPage();
    await boss.goto(auctionUrl(fx));
    await expect(boss.getByTestId("start-now")).toBeVisible();
    await shoot(boss, size.name, "lobby-start-now");
  });

  test(`the Room commish card and the audit log at ${size.name}`, async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 3600, open: 60, bid: 3, gap: 4 });
    await control(fx, "start_now");
    await waitPhase(fx, "live");
    await bidAs(fx, fx.managers[0], 1, (await auctionState(fx)).nextBid);
    await call(fx.managers[1].cookie, "POST", `/api/rooms/${fx.roomId}/auction/passes`, { intentId: crypto.randomUUID(), lot: 1 });
    for (let i = 0; i < 100 && !(await auctionState(fx)).lastResult; i++) await sleep(100);
    await control(fx, "pause");
    await control(fx, "resume");
    await waitPhase(fx, "live");
    // The live strip shows the lot on the block, so wait for the next lot to open.
    for (let i = 0; i < 100 && !(await auctionState(fx)).lotOpen; i++) await sleep(100);
    const boss = await (await contextFor(browser, fx.commissioner, vp)).newPage();

    await boss.goto(`/rooms/${fx.roomId}`);
    await expect(boss.getByTestId("commish-controls")).toBeVisible();
    await expect(boss.getByTestId("audit-link")).toBeVisible();
    await shoot(boss, size.name, "room-commish-card");

    await boss.getByTestId("audit-link").click();
    await expect(boss.getByTestId("audit")).toBeVisible();
    await expect(boss.getByTestId("audit-row").first()).toBeVisible();
    await shoot(boss, size.name, "audit");
  });

  test(`the audit log with nothing logged at ${size.name}`, async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, schedule: false });
    const boss = await (await contextFor(browser, fx.commissioner, vp)).newPage();
    await boss.goto(`/rooms/${fx.roomId}/audit`);
    await expect(boss.getByTestId("audit-empty")).toBeVisible();
    await shoot(boss, size.name, "audit-empty");
  });

  test(`the Controls sheets, paused, adjourned and the resume lobby at ${size.name}`, async ({ browser }) => {
    test.setTimeout(240_000); // the first pictures load the design source page
    const fx = await seedRoom({ managers: 2, startInSec: 3600, open: 60, bid: 3, gap: 30 });
    await control(fx, "start_now");
    await waitPhase(fx, "live");
    const boss = await (await contextFor(browser, fx.commissioner, vp)).newPage();
    const manager = await (await contextFor(browser, fx.managers[0].cookie, vp)).newPage();
    await boss.goto(auctionUrl(fx));
    await manager.goto(auctionUrl(fx));
    await hideDevBadge(boss);
    await hideDevBadge(manager);
    await expect(boss.getByTestId("controls-chip")).toBeVisible();
    await expect(manager.getByTestId("lot-card")).toBeVisible();

    // Skip confirm (Keep it returns to the list), then the adjourn time picker (Escape returns to the list).
    await boss.getByTestId("controls-chip").click();
    await shoot(boss, size.name, "controls-sheet");
    await boss.getByTestId("control-skip").click();
    await expect(boss.getByTestId("skip-sheet")).toBeVisible();
    await shoot(boss, size.name, "skip-confirm");
    await boss.getByRole("button", { name: "Keep it" }).click();
    await boss.getByTestId("control-adjourn").click();
    await expect(boss.getByRole("dialog").last()).toBeVisible();
    await shoot(boss, size.name, "adjourn-sheet");
    // Closing the time picker goes back to the list of controls.
    await boss.keyboard.press("Escape");
    await expect(boss.getByTestId("controls-sheet")).toBeVisible();

    // Paused, as the Commissioner sees it (Resume is in the notice).
    await boss.getByTestId("control-pause").click();
    await expect(boss.getByTestId("notice-paused")).toContainText("You paused the auction");
    await shoot(boss, size.name, "paused-commish");
    await boss.getByTestId("notice-resume").click();
    await expect(boss.getByTestId("notice-paused")).toHaveCount(0, { timeout: 4000 });

    // A closed lot to re-open: a bid, then the hammer, then the sheet.
    await bidAs(fx, fx.managers[1], 1, (await auctionState(fx)).nextBid);
    await expect(boss.getByTestId("stamp-sold")).toBeVisible({ timeout: 8000 });
    await boss.getByTestId("controls-chip").click();
    await boss.getByTestId("control-reopen").click();
    await boss.getByTestId("reopen-reason").fill("Timer bug on Zoya's phone");
    await shoot(boss, size.name, "reopen-sheet");
    await boss.keyboard.press("Escape");

    // Adjourned, for the Commissioner and a manager; then Resume now opens the lobby.
    await control(fx, "adjourn", { resumeAt: new Date(Date.now() + 3 * 3600_000).toISOString() });
    await expect(boss.getByTestId("resume-now")).toBeVisible();
    await expect(manager.getByTestId("adjourned-card")).toBeVisible();
    await shoot(boss, size.name, "adjourned-commissioner");
    await shoot(manager, size.name, "adjourned");
    await boss.getByTestId("resume-now").click();
    await expect(manager.getByTestId("notice-resume-lobby")).toContainText("goes live in");
    await shoot(manager, size.name, "resume-lobby");
  });
}
