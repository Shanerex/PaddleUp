import { expect, test } from "@playwright/test";
import { auctionState, bidAs, seedRoom, startIn } from "./support/seed.mjs";
import { auctionUrl, contextFor, sleep, type Fixture } from "./support/helpers";

// T-20 (AC-16, AC-17): sold, unsold and a Commissioner without a franchise. Paused and adjourned
// are covered by component fixtures; their e2e waits for the Commissioner controls spec (FR-47).

async function goLive(fx: Fixture) {
  await startIn(fx, 2);
  for (let i = 0; i < 100; i++) {
    if ((await auctionState(fx)).phase === "live") return;
    await sleep(100);
  }
  throw new Error("auction did not start");
}

test.describe("results and the observer", () => {
  test("sold and unsold match the server's events, and the purse is the server's", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 600, open: 5, bid: 3, gap: 2 });
    const page = await (await contextFor(browser, fx.managers[1].cookie)).newPage();
    await page.goto(auctionUrl(fx));
    await goLive(fx);
    await expect(page.getByTestId("bid-button")).toHaveAttribute("data-state", "ready");

    // Lot 1: Club 2 (this tab) bids and nobody answers, so it is sold to Club 2.
    await page.getByTestId("bid-button").click();
    await expect(page.getByTestId("stamp-sold")).toBeVisible({ timeout: 8000 });
    await expect(page.getByTestId("stamp-sold")).toContainText("You");
    await expect(page.getByTestId("bid-button")).toContainText("Next lot coming up…");
    const server = await auctionState(fx);
    expect(server.lastResult).toMatchObject({ lot: 1, sold: true, franchiseId: fx.managers[1].franchiseId, amount: 300_000 });
    const mine = server.standings.find((s: { franchiseId: string }) => s.franchiseId === fx.managers[1].franchiseId);
    expect(mine.purse).toBe(100_000_000 - 300_000);
    await expect(page.getByTestId("purse")).toHaveText("€99.7 M");

    // Lot 2: nobody bids, so it goes unsold.
    await expect(page.getByTestId("lot-name")).toHaveText("Fake 2", { timeout: 6000 });
    await expect(page.getByTestId("stamp-unsold")).toBeVisible({ timeout: 9000 });
    expect((await auctionState(fx)).lastResult).toMatchObject({ lot: 2, sold: false });
    await expect(page.getByTestId("stamp-unsold")).toContainText("Nobody blinked");
  });

  test("a Commissioner without a franchise sees live bids, no bid button and the stated cause", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 600, open: 20, bid: 10 });
    const page = await (await contextFor(browser, fx.commissioner)).newPage();
    await page.goto(auctionUrl(fx));
    await expect(page.getByTestId("lobby-admin")).toBeVisible();
    await expect(page.getByTestId("ready-button")).toHaveCount(0);
    await goLive(fx);

    await expect(page.getByTestId("lot-card")).toBeVisible({ timeout: 3000 });
    await bidAs(fx, fx.managers[0], 1, (await auctionState(fx)).nextBid);
    await expect(page.getByTestId("leader")).toContainText("Club 1", { timeout: 2000 });
    await expect(page.getByTestId("current-bid")).toHaveText("€0.3 M");
    await expect(page.getByTestId("bid-button")).toHaveCount(0);
    await expect(page.getByTestId("bid-observer")).toContainText("You lead this room without a franchise");
    await expect(page.getByText("WATCHING AS")).toBeVisible();
  });
});
