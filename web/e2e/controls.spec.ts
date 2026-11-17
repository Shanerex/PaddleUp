import { mkdirSync } from "node:fs";
import { expect, test, type Page } from "@playwright/test";
import { auctionState, bidAs, call, seedRoom } from "./support/seed.mjs";
import { auctionUrl, contextFor, sleep, type Fixture } from "./support/helpers";

// T-30 (AC-14): the Commissioner controls in a real browser against `make dev`: Start now, the
// Controls sheet (pause, skip, re-open, adjourn), the adjourned card and the resume lobby, the paused
// and adjourned notices from the real phases, and a new tab or stale tab after each. Pictures land
// in test-results/controls at 390 and 1440, named <size>-<screen>.png, for the side-by-side with the
// design source frames (Explorations 3n, 3o, 3p, 3r, 3u, 3v; Room lobbyAdmin; AuctionNotices).
// The end-of-main choice and the accelerated round wait for the approved T-17 design edits.

const OUT = "test-results/controls";
const SIZES = [
  { name: "390", width: 390, height: 844 },
  { name: "1440", width: 1440, height: 900 },
];

const control = (fx: Fixture, action: string, extra: Record<string, unknown> = {}) =>
  call(fx.commissioner, "POST", `/api/rooms/${fx.roomId}/auction/controls`, { intentId: crypto.randomUUID(), action, ...extra });

async function waitPhase(fx: Fixture, phase: string, ms = 8000) {
  for (let i = 0; i < ms / 100; i++) {
    if ((await auctionState(fx)).phase === phase) return;
    await sleep(100);
  }
  throw new Error(`auction did not reach ${phase}`);
}

async function shoot(page: Page, size: string, name: string) {
  mkdirSync(OUT, { recursive: true });
  await page.waitForTimeout(500); // sheets and notices fade in
  await page.screenshot({ path: `${OUT}/${size}-${name}.png` });
}

test.describe("Commissioner controls", () => {
  test("Start now goes live, logged before the go-live, and a manager sees no button", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 3600, open: 30, bid: 10, gap: 2 });
    const boss = await (await contextFor(browser, fx.commissioner)).newPage();
    const manager = await (await contextFor(browser, fx.managers[0].cookie)).newPage();
    await boss.goto(auctionUrl(fx));
    await manager.goto(auctionUrl(fx));
    await expect(manager.getByTestId("start-now")).toHaveCount(0);
    await expect(boss.getByTestId("start-now")).toBeVisible();
    await shoot(boss, "390", "lobby-start-now");

    await boss.getByTestId("start-now").click();

    await expect(boss.getByTestId("lot-card")).toBeVisible({ timeout: 6000 });
    await expect(manager.getByTestId("lot-card")).toBeVisible({ timeout: 6000 });
    const log = await call(fx.commissioner, "GET", `/api/rooms/${fx.roomId}/auction/events?after=0`);
    const kinds = log.body.events.map((e: { kind: string }) => e.kind);
    expect(kinds.indexOf("start_now")).toBeGreaterThan(-1);
    expect(kinds.indexOf("start_now")).toBeLessThan(kinds.indexOf("auction_started"));
  });

  test("pause and resume from the sheet reach a manager's tab, a new tab and a reload", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 3600, open: 60, bid: 10, gap: 2 });
    await control(fx, "start_now");
    await waitPhase(fx, "live");
    const boss = await (await contextFor(browser, fx.commissioner)).newPage();
    const manager = await (await contextFor(browser, fx.managers[0].cookie)).newPage();
    await boss.goto(auctionUrl(fx));
    await manager.goto(auctionUrl(fx));
    await expect(manager.getByTestId("bid-button")).toHaveAttribute("data-state", "ready");

    await boss.getByTestId("controls-chip").click();
    await expect(boss.getByTestId("controls-sheet")).toBeVisible();
    await shoot(boss, "390", "controls-sheet");
    await boss.getByTestId("control-pause").click();

    await expect(manager.getByTestId("notice-paused")).toContainText("Commish hit pause");
    await expect(manager.getByTestId("bid-button")).toContainText("Auction paused");
    await expect(boss.getByTestId("notice-paused")).toContainText("You paused the auction");
    const frozen = (await auctionState(fx)).heldMs;
    expect(frozen).toBeGreaterThan(0);
    await shoot(manager, "390", "paused-notice");
    // The held time does not move while paused.
    await sleep(1500);
    expect((await auctionState(fx)).heldMs).toBe(frozen);

    // A new tab and a reload show the same pause: the server is the only source.
    const second = await (await contextFor(browser, fx.managers[0].cookie)).newPage();
    await second.goto(auctionUrl(fx));
    await expect(second.getByTestId("notice-paused")).toBeVisible();
    await manager.reload();
    await expect(manager.getByTestId("notice-paused")).toBeVisible();

    await boss.getByTestId("notice-resume").click();
    await expect(manager.getByTestId("notice-paused")).toHaveCount(0, { timeout: 4000 });
    await expect(manager.getByTestId("bid-button")).toHaveAttribute("data-state", "ready");
    await expect(second.getByTestId("notice-paused")).toHaveCount(0, { timeout: 4000 });
  });

  test("skip works before the first bid and is locked once a bid lands; a manager sees the skip", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 3600, open: 60, bid: 10, gap: 3 });
    await control(fx, "start_now");
    await waitPhase(fx, "live");
    const boss = await (await contextFor(browser, fx.commissioner)).newPage();
    const manager = await (await contextFor(browser, fx.managers[0].cookie)).newPage();
    await boss.goto(auctionUrl(fx));
    await manager.goto(auctionUrl(fx));
    await expect(manager.getByTestId("lot-card")).toBeVisible();

    await boss.getByTestId("controls-chip").click();
    await boss.getByTestId("control-skip").click();
    await expect(boss.getByTestId("skip-sheet")).toBeVisible();
    await shoot(boss, "390", "skip-confirm");
    await boss.getByRole("button", { name: "Skip Lot 1" }).click();

    await expect(manager.getByTestId("notice-admin")).toContainText("Lot 1 skipped");
    expect((await auctionState(fx)).unsold).toContain(1);

    // Lot 2 gets a bid: Skip is locked and the server would refuse it.
    await expect(manager.getByTestId("bid-button")).toHaveAttribute("data-state", "ready", { timeout: 8000 });
    await bidAs(fx, fx.managers[1], 2, (await auctionState(fx)).nextBid);
    await boss.getByTestId("controls-chip").click();
    await expect(boss.getByTestId("control-skip")).toBeDisabled();
    expect((await control(fx, "skip")).body).toMatchObject({ outcome: "rejected", reason: "has_bid" });
  });

  test("re-open needs a reason, and every manager sees it", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 3600, open: 60, bid: 3, gap: 20 });
    await control(fx, "start_now");
    await waitPhase(fx, "live");
    await bidAs(fx, fx.managers[1], 1, (await auctionState(fx)).nextBid);
    const boss = await (await contextFor(browser, fx.commissioner)).newPage();
    const manager = await (await contextFor(browser, fx.managers[0].cookie)).newPage();
    await boss.goto(auctionUrl(fx));
    await manager.goto(auctionUrl(fx));
    await expect(manager.getByTestId("stamp-sold")).toBeVisible({ timeout: 8000 });

    await boss.getByTestId("controls-chip").click();
    await boss.getByTestId("control-reopen").click();
    const submit = boss.getByRole("button", { name: "Re-open Lot 1" });
    await expect(submit).toBeDisabled();
    await shoot(boss, "390", "reopen-sheet");
    await boss.getByTestId("reopen-reason").fill("Timer bug on Zoya's phone");
    await submit.click();

    await expect(manager.getByTestId("notice-admin")).toContainText("Lot 1 is back");
    await expect(manager.getByTestId("notice-admin")).toContainText("Timer bug on Zoya's phone");
    const s = await auctionState(fx);
    expect(s.standings.every((x: { purse: number; squad: unknown[] }) => x.squad.length === 0)).toBe(true);
  });

  test("adjourn locks the auction across a reload; Resume now opens the lobby, then the lot goes live", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 3600, open: 60, bid: 10, gap: 2 });
    await control(fx, "start_now");
    await waitPhase(fx, "live");
    const boss = await (await contextFor(browser, fx.commissioner)).newPage();
    const manager = await (await contextFor(browser, fx.managers[0].cookie)).newPage();
    await boss.goto(auctionUrl(fx));
    await manager.goto(auctionUrl(fx));
    await expect(manager.getByTestId("lot-card")).toBeVisible();

    await control(fx, "adjourn", { resumeAt: new Date(Date.now() + 3 * 3600_000).toISOString() });

    await expect(manager.getByTestId("adjourned-card")).toContainText("Bidding resumes");
    await expect(manager.getByTestId("bid-button")).toContainText("Adjourned");
    await expect(boss.getByTestId("resume-now")).toBeVisible();
    await shoot(manager, "390", "adjourned");
    await shoot(boss, "390", "adjourned-commissioner");
    await manager.reload();
    await expect(manager.getByTestId("adjourned-card")).toBeVisible();

    await boss.getByTestId("resume-now").click();
    await expect(manager.getByTestId("notice-resume-lobby")).toContainText("goes live in");
    await expect(manager.getByTestId("bid-button")).toContainText("Resuming soon");
    expect((await auctionState(fx)).phase).toBe("resume_lobby");
    await shoot(manager, "390", "resume-lobby");
  });

  for (const size of SIZES) {
    test(`the Commissioner's controls sheet and the adjourned view at ${size.name}`, async ({ browser }) => {
      const fx = await seedRoom({ managers: 2, startInSec: 3600, open: 60, bid: 10, gap: 2 });
      await control(fx, "start_now");
      await waitPhase(fx, "live");
      await bidAs(fx, fx.managers[0], 1, (await auctionState(fx)).nextBid);
      const boss = await (await contextFor(browser, fx.commissioner, { width: size.width, height: size.height })).newPage();
      await boss.goto(auctionUrl(fx));
      await boss.getByTestId("controls-chip").click();
      await expect(boss.getByTestId("controls-sheet")).toBeVisible();
      await expect(boss.getByTestId("control-skip")).toBeDisabled();
      await shoot(boss, size.name, "controls-lot-has-bids");
    });
  }
});
