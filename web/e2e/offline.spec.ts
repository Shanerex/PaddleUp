import { expect, test } from "@playwright/test";
import { auctionState, bidAs, seedRoom, startIn } from "./support/seed.mjs";
import { auctionUrl, contextFor, sleep, type Fixture } from "./support/helpers";

// T-19 (AC-13 to AC-15) and AC-6: dropped network, new tab, reload and a stale tab.

async function goLive(fx: Fixture) {
  await startIn(fx, 2);
  for (let i = 0; i < 100; i++) {
    if ((await auctionState(fx)).phase === "live") return;
    await sleep(100);
  }
  throw new Error("auction did not start");
}

const timerMs = (text: string) => {
  const [m, s] = text.split(":").map(Number);
  return (m * 60 + s) * 1000;
};

test.describe("offline, new tab and stale state", () => {
  test("the lot clock matches the server deadline within 1 s with the browser clock 5 min ahead, and resets on a bid", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 600, open: 30, bid: 20 });
    const context = await contextFor(browser, fx.managers[0].cookie, { skewMs: 5 * 60_000 });
    const page = await context.newPage();
    await page.goto(auctionUrl(fx));
    await goLive(fx);
    await expect(page.getByTestId("bid-button")).toHaveAttribute("data-state", "ready");
    await sleep(1200);
    const open = await auctionState(fx);
    const truth = Date.parse(open.deadline) - Date.now();
    const shown = timerMs((await page.getByTestId("timer").textContent()) ?? "0:00");
    expect(Math.abs(shown - truth)).toBeLessThan(1000); // AC-6: within 1 s

    await bidAs(fx, fx.managers[1], 1, open.nextBid);
    await expect(page.getByTestId("timer")).toHaveText(/0:(19|20)/, { timeout: 500 }); // AC-6: reset within 500 ms
    await context.close();
  });

  test("going offline shows the notice within 3 s, locks the button and freezes the timer; coming back resyncs to the server's lot and purse", async ({ browser }) => {
    const fx = await seedRoom({ managers: 3, startInSec: 600, open: 8, bid: 4, gap: 2 });
    const context = await contextFor(browser, fx.managers[0].cookie);
    const page = await context.newPage();
    await page.goto(auctionUrl(fx));
    await goLive(fx);
    await expect(page.getByTestId("bid-button")).toHaveAttribute("data-state", "ready");

    await context.setOffline(true);
    await expect(page.getByTestId("notice-reconnecting")).toBeVisible({ timeout: 3000 });
    const button = page.getByTestId("bid-button");
    await expect(button).toBeDisabled();
    await expect(button).toContainText("Reconnecting…");
    await expect(page.getByTestId("timer-note")).toHaveText("WAITING");
    const frozen = await page.getByTestId("timer").textContent();
    await sleep(1500);
    expect(await page.getByTestId("timer").textContent()).toBe(frozen);
    expect(await page.getByTestId("timer").getAttribute("data-frozen")).toBe("true");

    // While the tab is cut off, a rival buys lot 1 and lot 2 opens.
    await bidAs(fx, fx.managers[1], 1, (await auctionState(fx)).nextBid);
    for (let i = 0; i < 100; i++) {
      const s = await auctionState(fx);
      if (s.lot?.no === 2 && s.lotOpen) break;
      await sleep(100);
    }

    await context.setOffline(false);
    await expect(page.getByTestId("notice-reconnecting")).toBeHidden({ timeout: 5000 });
    const server = await auctionState(fx, fx.managers[0].cookie);
    await expect(page.getByTestId("lot-name")).toHaveText(server.lot.name);
    const mine = server.standings.find((x: { franchiseId: string }) => x.franchiseId === fx.managers[0].franchiseId);
    await expect(page.getByTestId("purse")).toHaveText(/€/);
    expect(await page.getByTestId("purse").innerText()).toBe(`€${mine.purse / 1_000_000} M`);
    await expect(page.getByTestId("bid-button")).not.toBeDisabled();
    await context.close();
  });

  test("a reload and a new tab show the server's state within 2 s", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 600, open: 30, bid: 30 });
    const context = await contextFor(browser, fx.managers[0].cookie);
    const page = await context.newPage();
    await page.goto(auctionUrl(fx));
    await goLive(fx);
    await bidAs(fx, fx.managers[1], 1, (await auctionState(fx)).nextBid);
    await bidAs(fx, fx.managers[1], 1, (await auctionState(fx)).nextBid).catch(() => undefined);
    const server = await auctionState(fx);
    const money = `€${server.currentBid / 1_000_000} M`;

    await page.reload();
    await expect(page.getByTestId("current-bid")).toHaveText(money, { timeout: 2000 });
    await expect(page.getByTestId("leader")).toContainText("Club 2");

    const other = await context.newPage();
    await other.goto(auctionUrl(fx));
    await expect(other.getByTestId("current-bid")).toHaveText(money, { timeout: 2000 });
    await expect(other.getByTestId("leader")).toContainText("Club 2");
    await context.close();
  });

  test("a stale tab sends no bid before it has resynced", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 600, open: 30, bid: 30 });
    const context = await contextFor(browser, fx.managers[0].cookie);
    const page = await context.newPage();
    const bids: string[] = [];
    page.on("request", (r) => r.method() === "POST" && r.url().endsWith("/auction/bids") && bids.push(r.url()));
    await page.goto(auctionUrl(fx));
    await goLive(fx);
    await expect(page.getByTestId("bid-button")).toHaveAttribute("data-state", "ready");

    // The state read after foregrounding is slow, so the tab is stale for a while.
    await page.route("**/api/rooms/*/auction", async (route) => {
      if (route.request().method() === "GET") await sleep(1500);
      await route.continue();
    });
    await page.evaluate(() => {
      const set = (v: string) => Object.defineProperty(document, "visibilityState", { configurable: true, get: () => v });
      set("hidden");
      document.dispatchEvent(new Event("visibilitychange"));
      set("visible");
      document.dispatchEvent(new Event("visibilitychange"));
    });
    const button = page.getByTestId("bid-button");
    await expect(button).toBeDisabled();
    await expect(button).toContainText("Syncing…");
    await button.click({ force: true }).catch(() => undefined);
    expect(bids).toHaveLength(0);

    await expect(button).toHaveAttribute("data-state", "ready", { timeout: 4000 });
    await context.close();
  });
});
