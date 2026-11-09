import { expect, test, type Page } from "@playwright/test";
import { auctionState, call, seedRoom } from "./support/seed.mjs";
import { auctionUrl, contextFor, countdownMs, fieldsIn, lobbyTimeIn, type Fixture } from "./support/helpers";

// T-25 (AC-18, AC-19, AC-20): the Commissioner's schedule sheet and lobby Change time against a
// real server. Times are picked as local date and time and must reach the server as one instant.

const KOLKATA = "Asia/Kolkata";
const AUCKLAND = "Pacific/Auckland";
const MIN = 60_000;
const roomUrl = (fx: Fixture) => `/rooms/${fx.roomId}`;

/** Fills the open sheet with a local date and time, read in `zone`. */
async function pick(page: Page, zone: string, at: Date) {
  const f = fieldsIn(zone, at);
  await page.getByLabel("Date", { exact: true }).fill(f.date);
  await page.getByLabel("Start time", { exact: true }).fill(f.time);
}

const roomState = async (fx: Fixture) => (await call(fx.commissioner, "GET", `/api/rooms/${fx.roomId}`)).body.state;
const auctionStatus = async (fx: Fixture) => (await call(fx.commissioner, "GET", `/api/rooms/${fx.roomId}/auction`)).status;

test.describe("schedule and change time", () => {
  test("the Commissioner schedules from the room and lands in the lobby; the server holds that one instant", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, schedule: false });
    // The browser clock is 5 min ahead: the preview must still follow the server's.
    const context = await contextFor(browser, fx.commissioner, { timezoneId: KOLKATA, skewMs: 5 * MIN });
    const page = await context.newPage();
    await page.goto(roomUrl(fx));
    expect(await roomState(fx)).toBe("setup");

    await page.getByRole("button", { name: "Schedule auction" }).click();
    const sheet = page.getByRole("dialog", { name: "Schedule the auction" });
    await expect(sheet).toBeVisible();
    // Defaults come from the server clock; replace them with a minute-aligned time 3h 31m on.
    const target = new Date(Math.ceil((Date.now() + 211 * MIN) / MIN) * MIN);
    await pick(page, KOLKATA, target);
    await expect(sheet.getByTestId("schedule-until")).toHaveText(/^in 3h 3[01]m$/);
    await expect(sheet.getByTestId("schedule-when")).toContainText("your time");
    await sheet.getByRole("button", { name: "Schedule auction" }).click();

    await page.waitForURL(`**${auctionUrl(fx)}`);
    await expect(page.getByTestId("lobby-countdown")).toHaveText(/^03:\d\d:\d\d$/);
    const server = await auctionState(fx);
    expect(Date.parse(server.startsAt)).toBe(target.getTime());
    expect(await roomState(fx)).toBe("auction_scheduled");
    expect(Math.abs(countdownMs((await page.getByTestId("lobby-countdown").textContent()) ?? "") - (target.getTime() - Date.now()))).toBeLessThan(2000);
    await context.close();
  });

  test("a manager in another zone sees the start in their own time", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, schedule: false });
    const commish = await (await contextFor(browser, fx.commissioner, { timezoneId: KOLKATA })).newPage();
    await commish.goto(roomUrl(fx));
    await commish.getByRole("button", { name: "Schedule auction" }).click();
    const target = new Date(Math.ceil((Date.now() + 5 * 24 * 60 * MIN) / MIN) * MIN);
    await pick(commish, KOLKATA, target);
    await commish.getByRole("dialog").getByRole("button", { name: "Schedule auction" }).click();
    await commish.waitForURL(`**${auctionUrl(fx)}`);
    await expect(commish.locator("body")).toContainText(`${lobbyTimeIn(KOLKATA, target)} your time`);

    const manager = await (await contextFor(browser, fx.managers[0].cookie, { timezoneId: AUCKLAND })).newPage();
    await manager.goto(auctionUrl(fx));
    await expect(manager.getByTestId("lobby-countdown")).toHaveText(/^\d+:\d\d:\d\d$/);
    await expect(manager.locator("body")).toContainText(`${lobbyTimeIn(AUCKLAND, target)} your time`);
    expect(lobbyTimeIn(AUCKLAND, target)).not.toBe(lobbyTimeIn(KOLKATA, target));
  });

  test("Change time moves another tab's countdown within 2 s and keeps ready taps", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 600 });
    const manager = await (await contextFor(browser, fx.managers[0].cookie, { timezoneId: AUCKLAND })).newPage();
    await manager.goto(auctionUrl(fx));
    await manager.getByTestId("ready-button").click();
    const row = manager.locator(`[data-testid=lobby-row][data-franchise="${fx.managers[0].franchiseId}"]`);
    await expect(row).toHaveAttribute("data-status", "READY");
    await expect(manager.getByTestId("lobby-countdown")).toHaveText(/^00:(09|10):\d\d$|^00:0\d:\d\d$/);

    const commish = await (await contextFor(browser, fx.commissioner, { timezoneId: KOLKATA })).newPage();
    await commish.goto(auctionUrl(fx));
    await commish.getByTestId("change-time").click();
    const sheet = commish.getByRole("dialog", { name: "Change the start time?" });
    await expect(sheet.getByTestId("schedule-until")).toHaveText(/^in (9|10)m$/);
    const target = new Date(Math.ceil((Date.now() + 3 * 60 * MIN) / MIN) * MIN);
    await pick(commish, KOLKATA, target);
    await sheet.getByRole("button", { name: "Change start time" }).click();
    await expect(sheet).toBeHidden();

    // The other tab's countdown jumps from about 10 minutes to about 3 hours within 2 s, no reload.
    await expect(manager.getByTestId("lobby-countdown")).toHaveText(/^0[23]:\d\d:\d\d$/, { timeout: 2000 });
    await expect(commish.getByTestId("lobby-countdown")).toHaveText(/^0[23]:\d\d:\d\d$/);
    expect(Date.parse((await auctionState(fx)).startsAt)).toBe(target.getTime());
    await expect(row).toHaveAttribute("data-status", "READY");
    await expect(manager.locator("body")).toContainText(`${lobbyTimeIn(AUCKLAND, target)} your time`);
  });

  test("a past time shows the server's field error and changes nothing", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, schedule: false });
    const page = await (await contextFor(browser, fx.commissioner, { timezoneId: KOLKATA })).newPage();
    await page.goto(roomUrl(fx));
    await page.getByRole("button", { name: "Schedule auction" }).click();
    const sheet = page.getByRole("dialog", { name: "Schedule the auction" });
    await pick(page, KOLKATA, new Date(Date.now() - 60 * MIN));
    await sheet.getByRole("button", { name: "Schedule auction" }).click();

    await expect(sheet.getByTestId("schedule-past")).toHaveText("That time has already gone. Pick a later one.");
    await expect(sheet.getByTestId("schedule-until")).toHaveText("—");
    expect(await roomState(fx)).toBe("setup");
    expect(await auctionStatus(fx)).toBe(404);
    expect(page.url()).toContain(roomUrl(fx));

    // Moving an existing start into the past is refused the same way and keeps the old one.
    const other = await seedRoom({ managers: 2, startInSec: 900 });
    const lobby = await (await contextFor(browser, other.commissioner, { timezoneId: KOLKATA })).newPage();
    await lobby.goto(auctionUrl(other));
    await lobby.getByTestId("change-time").click();
    const move = lobby.getByRole("dialog", { name: "Change the start time?" });
    await pick(lobby, KOLKATA, new Date(Date.now() - 5 * MIN));
    await move.getByRole("button", { name: "Change start time" }).click();
    await expect(move.getByTestId("schedule-past")).toBeVisible();
    expect(Date.parse((await auctionState(other)).startsAt)).toBe(Date.parse(other.startsAt!));
  });

  test("a pool that is too small shows the server's reason and nothing changes", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, schedule: false, squadMin: 30 }); // 2 x 30 = 60 needed, 40 in the pool
    const page = await (await contextFor(browser, fx.commissioner)).newPage();
    await page.goto(roomUrl(fx));
    await page.getByRole("button", { name: "Schedule auction" }).click();
    const sheet = page.getByRole("dialog", { name: "Schedule the auction" });
    await pick(page, "UTC", new Date(Date.now() + 2 * 24 * 60 * MIN));
    await sheet.getByRole("button", { name: "Schedule auction" }).click();

    const refused = sheet.getByTestId("schedule-refused");
    await expect(refused).toContainText("Can't schedule yet");
    await expect(refused).toContainText("The pool has 40 players but 60 are needed to fill every squad; add players first. Nothing was saved.");
    await expect(refused.getByRole("link", { name: "Open player pool" })).toHaveAttribute("href", `/rooms/${fx.roomId}/pool`);
    expect(await roomState(fx)).toBe("setup");
    expect(await auctionStatus(fx)).toBe(404);
  });

  test("a manager sees no Schedule or Change time control, and a direct call gets 403", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, schedule: false });
    const manager = await (await contextFor(browser, fx.managers[0].cookie)).newPage();
    await manager.goto(roomUrl(fx));
    await expect(manager.getByRole("heading", { level: 1 }).or(manager.getByTestId("room-state")).first()).toBeVisible();
    await expect(manager.getByTestId("room-state")).toBeVisible();
    await expect(manager.getByRole("button", { name: "Schedule auction" })).toHaveCount(0);

    const denied = await call(fx.managers[0].cookie, "PUT", `/api/rooms/${fx.roomId}/auction/schedule`, { startsAt: new Date(Date.now() + 3600_000).toISOString() });
    expect(denied.status).toBe(403);
    expect(await roomState(fx)).toBe("setup");

    const scheduled = await seedRoom({ managers: 2, startInSec: 600 });
    const lobby = await (await contextFor(browser, scheduled.managers[0].cookie)).newPage();
    await lobby.goto(auctionUrl(scheduled));
    await expect(lobby.getByTestId("lobby-countdown")).toBeVisible();
    await expect(lobby.getByTestId("change-time")).toHaveCount(0);
    const deniedMove = await call(scheduled.managers[0].cookie, "PUT", `/api/rooms/${scheduled.roomId}/auction/schedule`, { startsAt: new Date(Date.now() + 7200_000).toISOString() });
    expect(deniedMove.status).toBe(403);
  });
});
