import { expect, test, type Page } from "@playwright/test";
import { auctionState, bidAs, call, seedRoom } from "./support/seed.mjs";
import { auctionUrl, contextFor, sleep, type Fixture } from "./support/helpers";
import { MOCKS, ensureDir, mockShot, pair, type Frame } from "./support/mocks";

// T-30 (AC-14): the Commissioner controls in a real browser against `make dev`: Start now, the
// Controls sheet (pause, skip, re-open, adjourn), the adjourned card and the resume lobby, the paused
// and adjourned notices from the real phases, and a new tab or stale tab after each. Pictures land
// in test-results/controls at 390 and 1440, named <size>-<screen>.png, for the side-by-side with the
// design source frames (Explorations 3n, 3o, 3p, 3r, 3u, 3v; Room lobbyAdmin; AuctionNotices).
// The end-of-main choice and the accelerated round (approved T-17 frames 3w, 3x, 3y, 3z, 3aa) are at
// the end. With E2E_MOCKS set (see support/mocks.ts) each picture also gets a pair-<size>-<screen>.png
// beside its frame.

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

/** Where each picture is drawn in the design source (Explorations frames). */
const FRAME: Record<string, Frame> = {
  "controls-lot-has-bids": { page: "Auction Explorations", id: "3n" },
  "skip-confirm": { page: "Auction Explorations", id: "3p" },
  "reopen-sheet": { page: "Auction Explorations", id: "3r" },
  "adjourned-commissioner": { page: "Auction Explorations", id: "3v" },
  adjourned: { page: "Auction Explorations", id: "3e" },
  "paused-notice": { page: "Auction Explorations", id: "3d" },
  "main-done-commish": { page: "Auction Explorations", id: "3z" },
  "main-done": { page: "Auction Explorations", id: "3aa" },
  "accel-nominate": { page: "Auction Explorations", id: "3w" },
  "accel-commish": { page: "Auction Explorations", id: "3x" },
  "accel-live": { page: "Auction Explorations", id: "3y" },
};

async function shoot(page: Page, size: string, name: string) {
  ensureDir(OUT);
  await page.waitForTimeout(500); // sheets and notices fade in
  const ours = `${OUT}/${size}-${name}.png`;
  await page.screenshot({ path: ours });
  const frame = FRAME[name];
  if (MOCKS && frame) {
    const mock = `${OUT}/mock-${size}-${name}.png`;
    if (await mockShot(page.context().browser()!, frame, Number(size), mock)) await pair(page.context().browser()!, ours, mock, `${OUT}/pair-${size}-${name}.png`, Number(size));
  }
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

    // The lot comes back under a new number (queue head), and the notice names that one (CR3-4).
    const s = await auctionState(fx);
    expect(s.nextLot.no).toBeGreaterThan(1);
    await expect(manager.getByTestId("notice-admin")).toContainText(`Lot ${s.nextLot.no} is back`);
    await expect(manager.getByTestId("notice-admin")).toContainText("Timer bug on Zoya's phone");
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

    // The Commissioner can start the lot at once or move the time (CR2-2).
    await expect(boss.getByTestId("start-now")).toBeVisible();
    await expect(boss.getByTestId("change-resume-time")).toBeVisible();
    await expect(boss.getByTestId("adjourned-card")).toContainText("Start now opens Lot 1 at once.");
    await boss.getByTestId("start-now").click();
    await waitPhase(fx, "live");
    await expect(manager.getByTestId("lot-card")).toBeVisible();
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

// The end of the main pool and the accelerated round (AC-10, AC-11, AC-14, AC-15), at 390 and 1440.
/** Lot 1 sold to the first manager, every other lot skipped as it opens, until the main pool is done. */
async function toMainDone(fx: Fixture) {
  let bid = false;
  for (let i = 0; i < 600; i++) {
    const s = await auctionState(fx);
    if (s.phase === "main_done") return;
    if (s.phase === "live" && s.lotOpen) {
      if (s.lot.no === 1) {
        if (!bid) {
          await bidAs(fx, fx.managers[0], 1, s.nextBid);
          bid = true;
        }
      } else if (s.currentBid === 0) {
        await control(fx, "skip");
      }
    }
    await sleep(100);
  }
  throw new Error("the main pool did not finish");
}

const eventCount = async (fx: Fixture) => (await call(fx.commissioner, "GET", `/api/rooms/${fx.roomId}/auction/events?after=0`)).body.events.length;

for (const size of SIZES) {
  test(`end of main, picks, End round and the accelerated lots at ${size.name}`, async ({ browser }) => {
    test.setTimeout(240_000);
    const fx = await seedRoom({ managers: 2, startInSec: 3600, open: 60, bid: 3, gap: 0 });
    await control(fx, "start_now");
    await waitPhase(fx, "live");
    await toMainDone(fx);
    const vp = { width: size.width, height: size.height };
    const boss = await (await contextFor(browser, fx.commissioner, vp)).newPage();
    const one = await (await contextFor(browser, fx.managers[0].cookie, vp)).newPage();
    const two = await (await contextFor(browser, fx.managers[1].cookie, vp)).newPage();
    for (const p of [boss, one, two]) await p.goto(auctionUrl(fx));

    // Main done: managers read it, only the Commissioner chooses.
    await expect(one.getByTestId("main-done-waiting")).toBeVisible();
    await expect(one.getByTestId("main-done-sold")).toHaveText("1");
    await expect(one.getByTestId("main-done-unsold")).toHaveText("39");
    await expect(one.getByTestId("open-round")).toHaveCount(0);
    await expect(one.getByTestId("bid-button")).toHaveCount(0);
    await expect(boss.getByTestId("open-round")).toBeVisible();
    await expect(boss.getByTestId("end-auction")).toBeVisible();
    await shoot(one, size.name, "main-done");
    await shoot(boss, size.name, "main-done-commish");
    // A new tab and a reload show the same card; a manager's own request is refused and writes nothing.
    const fresh = await (await contextFor(browser, fx.managers[1].cookie, vp)).newPage();
    await fresh.goto(auctionUrl(fx));
    await expect(fresh.getByTestId("main-done-waiting")).toBeVisible();
    const before = await eventCount(fx);
    expect((await call(fx.managers[0].cookie, "POST", `/api/rooms/${fx.roomId}/auction/controls`, { intentId: crypto.randomUUID(), action: "open_round" })).status).toBe(403);
    expect(await eventCount(fx)).toBe(before);

    // Open the round: every tab follows with no reload.
    await boss.getByTestId("open-round").click();
    await expect(one.getByTestId("nominating")).toBeVisible();
    await expect(two.getByTestId("nominating")).toBeVisible();
    await expect(fresh.getByTestId("nominating")).toBeVisible();
    await expect(one.getByTestId("nom-pool")).toHaveText("39");
    await expect(boss.getByTestId("end-round")).toContainText("End round · play 0 lots");
    await shoot(boss, size.name, "accel-commish");
    // Opening it again is refused: there is one accelerated round only.
    expect((await control(fx, "open_round")).body).toMatchObject({ outcome: "rejected" });

    // One picks the first two rows, the other the second and third; the counts reach every tab.
    const rowsOne = one.getByTestId("pick-row");
    await rowsOne.nth(0).click();
    await rowsOne.nth(1).click();
    await expect(one.getByTestId("nom-mine")).toHaveText("2 of 19");
    await shoot(one, size.name, "accel-nominate");
    await one.getByTestId("send-picks").click();
    await expect(one.getByTestId("picks-in")).toContainText("Waiting on 1 franchise.");
    const rowsTwo = two.getByTestId("pick-row");
    await expect(rowsTwo.nth(0).getByTestId("pick-count")).toHaveText("1 pick");
    await rowsTwo.nth(1).click();
    await rowsTwo.nth(2).click();
    await two.getByTestId("send-picks").click();
    await expect(two.getByTestId("picks-in")).toBeVisible();
    // Stale and new tabs: my picks come back from the server and the list is read-only.
    await one.reload();
    await expect(one.getByTestId("picks-in")).toBeVisible();
    await expect(one.getByTestId("nom-mine")).toHaveText("2 of 19");
    await expect(boss.getByTestId("end-round")).toContainText("End round · play 3 lots");
    const picked = await auctionState(fx);
    expect(picked.pickCounts.length).toBe(3);

    // End round: picked lots run most-picked first, as new lot numbers.
    await boss.getByTestId("end-round").click();
    await expect(one.getByTestId("lot-card")).toBeVisible({ timeout: 6000 });
    const first = await auctionState(fx);
    const top = [...picked.pickCounts].sort((a: { count: number; lot: number }, b: { count: number; lot: number }) => b.count - a.count || a.lot - b.lot)[0];
    expect(first.round).toBe("accelerated");
    expect(first.lot.no).toBeGreaterThan(40);
    expect(first.lot.name).toBe(`Fake ${top.lot}`);
    // Mock 3y: the header reads "n of m" and the lot card carries "Picked by N"; the same on every tab.
    expect(first).toMatchObject({ accelPosition: 1, accelSize: 3, lotPicks: top.count });
    for (const p of [one, two, boss]) {
      await expect(p.getByText("Accelerated round · 1 of 3")).toBeVisible();
      await expect(p.getByTestId("picked-by")).toHaveText(`Picked by ${top.count}`);
    }
    await expect(two.getByTestId("lot-name")).toHaveText(first.lot.name);
    await shoot(one, size.name, "accel-live");

    // Skip the three accelerated lots: the auction ends, never main-done again.
    for (let i = 0; i < 300 && (await auctionState(fx)).phase !== "ended"; i++) {
      const s = await auctionState(fx);
      if (s.phase === "live" && s.lotOpen && s.currentBid === 0) await control(fx, "skip");
      await sleep(100);
    }
    expect((await auctionState(fx)).phase).toBe("ended");
    await expect(one.getByTestId("closed-card")).toBeVisible();
    await expect(boss.getByTestId("closed-card")).toBeVisible();
  });
}

test("End the auction from main done ends it; End round with no picks ends it too", async ({ browser }) => {
  test.setTimeout(240_000);
  const a = await seedRoom({ managers: 2, startInSec: 3600, open: 60, bid: 3, gap: 0 });
  await control(a, "start_now");
  await waitPhase(a, "live");
  await toMainDone(a);
  const bossA = await (await contextFor(browser, a.commissioner)).newPage();
  const mgrA = await (await contextFor(browser, a.managers[1].cookie)).newPage();
  await bossA.goto(auctionUrl(a));
  await mgrA.goto(auctionUrl(a));
  await bossA.getByTestId("end-auction").click();
  await expect(mgrA.getByTestId("closed-card")).toBeVisible({ timeout: 6000 });
  expect((await auctionState(a)).phase).toBe("ended");

  const b = await seedRoom({ managers: 2, startInSec: 3600, open: 60, bid: 3, gap: 0 });
  await control(b, "start_now");
  await waitPhase(b, "live");
  await toMainDone(b);
  const bossB = await (await contextFor(browser, b.commissioner)).newPage();
  await bossB.goto(auctionUrl(b));
  await bossB.getByTestId("open-round").click();
  await expect(bossB.getByTestId("end-round")).toContainText("play 0 lots");
  await bossB.getByTestId("end-round").click();
  await expect(bossB.getByTestId("closed-card")).toBeVisible({ timeout: 6000 });
  expect((await auctionState(b)).phase).toBe("ended");
});
