import { expect, test } from "@playwright/test";
import { auctionState, bidAs, seedRoom, startIn } from "./support/seed.mjs";
import { auctionUrl, contextFor, sleep, type Fixture } from "./support/helpers";

// 2026-11-02-08 fix loop 1 (AC-3, AC-5): bid limits against a real server on the stub pack.

async function goLive(fx: Fixture) {
  await startIn(fx, 2);
  for (let i = 0; i < 100; i++) {
    if ((await auctionState(fx)).phase === "live") return;
    await sleep(100);
  }
  throw new Error("auction did not start");
}

const sizes = [
  { name: "390x844", width: 390, height: 844 },
  { name: "1440x900", width: 1440, height: 900 },
];

test.describe("bid limits", () => {
  for (const size of sizes) {
    test(`an outbid leader sees the reserve lock at once, with no reread, at ${size.name}`, async ({ browser }) => {
      // Squad minimum 3 at 300 000 a player: winning lot 1 leaves 600 000 to keep back, so with a
      // purse of 1 000 000 the most this manager may bid is 400 000.
      const fx = await seedRoom({ managers: 2, startInSec: 600, purse: 1_000_000, squadMin: 3 });
      const page = await (await contextFor(browser, fx.managers[0].cookie, size)).newPage();
      await page.goto(auctionUrl(fx));
      await goLive(fx);
      const button = page.getByTestId("bid-button");
      await expect(button).toHaveAttribute("data-state", "ready");
      await button.click();
      await expect(page.getByTestId("leader")).toContainText("You");

      let reads = 0;
      page.on("request", (r) => r.method() === "GET" && /\/auction$/.test(new URL(r.url()).pathname) && reads++);
      expect((await bidAs(fx, fx.managers[1], 1, 400_000)).body.outcome).toBe("accepted");

      await expect(button).toHaveAttribute("data-state", "bid_locked", { timeout: 1000 });
      await expect(button).toBeDisabled();
      await expect(button).toContainText("Can't bid €0.5 M");
      await expect(button).toContainText("short on purse to fill your squad minimum at base price");
      expect(reads).toBe(0);
      await page.screenshot({ path: `test-results/eligibility-reserve-${size.name}.png` });

      // A new tab reads the same lock from the server.
      const tab = await page.context().newPage();
      await tab.goto(auctionUrl(fx));
      await expect(tab.getByTestId("bid-button")).toHaveAttribute("data-state", "bid_locked");
      await expect(tab.getByTestId("bid-button")).toContainText("short on purse");
    });
  }

  test("a lot that takes the only place a needed role must use is locked as no room", async ({ browser }) => {
    // Squad of exactly 1 that must be a Defender; lot 1 is a Goalkeeper.
    const fx = await seedRoom({ managers: 2, startInSec: 600, squadMin: 1, squadMax: 1, roleMinimums: { def: 1 } });
    const page = await (await contextFor(browser, fx.managers[0].cookie, sizes[1])).newPage();
    await page.goto(auctionUrl(fx));
    await goLive(fx);

    const button = page.getByTestId("bid-button");
    await expect(page.getByTestId("lot-card")).toContainText("Goalkeeper");
    await expect(button).toHaveAttribute("data-state", "bid_locked");
    await expect(button).toContainText("Winning this would leave no room for your Defender minimum.");
    await page.screenshot({ path: "test-results/eligibility-no-room-1440x900.png" });
    expect((await bidAs(fx, fx.managers[0], 1, 300_000)).body.reason).toBe("no_room");
  });
});
