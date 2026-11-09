import { expect, test } from "@playwright/test";
import { auctionState, seedRoom, startIn } from "./support/seed.mjs";
import { auctionUrl, contextFor, countdownMs, sleep } from "./support/helpers";

// T-17 (AC-1, AC-2, AC-3, AC-6) and T-16 (AC-5): the lobby against a real server.

test.describe("lobby", () => {
  for (const skew of [5 * 60_000, -5 * 60_000]) {
    test(`the countdown stays within 1 s of the server with the browser clock ${skew > 0 ? "ahead" : "behind"} by 5 min`, async ({ browser }) => {
      const fx = await seedRoom({ managers: 2, startInSec: 600 });
      const context = await contextFor(browser, fx.managers[0].cookie, { skewMs: skew });
      const page = await context.newPage();
      await page.goto(auctionUrl(fx));
      const countdown = page.getByTestId("lobby-countdown");
      await expect(countdown).toHaveText(/^\d\d:\d\d:\d\d$/);
      await sleep(1100);
      const shown = countdownMs((await countdown.textContent()) ?? "");
      const truth = Date.parse(fx.startsAt!) - Date.now();
      expect(Math.abs(shown - truth)).toBeLessThan(1000); // AC-1: within 1 s
      await expect(page.getByText(/your time/)).toBeVisible();
      await context.close();
    });
  }

  test("ready in one tab shows in another within 2 s, and undoing it does too; the pack's words only", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 600 });
    const a = await (await contextFor(browser, fx.managers[0].cookie)).newPage();
    const b = await (await contextFor(browser, fx.managers[1].cookie)).newPage();
    await a.goto(auctionUrl(fx));
    await b.goto(auctionUrl(fx));
    const rowOfA = (p: typeof a) => p.locator(`[data-testid=lobby-row][data-franchise="${fx.managers[0].franchiseId}"]`);
    await expect(rowOfA(b)).toHaveAttribute("data-status", "IN LOBBY", { timeout: 4000 });

    await a.getByTestId("ready-button").click();
    await expect(rowOfA(b)).toHaveAttribute("data-status", "READY", { timeout: 2000 });
    await expect(a.getByTestId("ready-button")).toContainText("You're ready. Paddle up.");

    await a.getByTestId("ready-button").click();
    await expect(rowOfA(b)).toHaveAttribute("data-status", "IN LOBBY", { timeout: 2000 });

    const text = (await a.locator("body").innerText()).toLowerCase();
    for (const word of ["cricket", "wicket", "batter", "bowler", "overseas", "rupee", "₹"]) expect(text).not.toContain(word);
  });

  test("a server start moves the open lobby to the lot screen within 2 s, with no reload", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 600 });
    const page = await (await contextFor(browser, fx.managers[0].cookie)).newPage();
    await page.goto(auctionUrl(fx));
    await expect(page.getByTestId("lobby-countdown")).toBeVisible();
    await page.evaluate(() => ((window as unknown as { __kept: boolean }).__kept = true));

    await startIn(fx, 3);
    for (let i = 0; i < 80; i++) {
      if ((await auctionState(fx)).phase === "live") break;
      await sleep(100);
    }
    await expect(page.getByTestId("lot-card")).toBeVisible({ timeout: 2000 });
    expect(await page.evaluate(() => (window as unknown as { __kept?: boolean }).__kept)).toBe(true);
    await expect(page.getByTestId("lot-name")).toHaveText("Fake 1");
  });
});
