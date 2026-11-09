import { expect, test, type Page } from "@playwright/test";
import { auctionState, bidAs, seedRoom, startIn } from "./support/seed.mjs";
import { auctionUrl, contextFor, sleep, type Fixture } from "./support/helpers";

// T-18 (AC-7 to AC-12) and T-16 (AC-5): bidding against a real server.

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

test.describe("bidding", () => {
  test("a double tap sends one request, and the lot shows the pack's words and money only", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 600 });
    const page = await (await contextFor(browser, fx.managers[0].cookie)).newPage();
    const bids: string[] = [];
    page.on("request", (r) => r.method() === "POST" && r.url().endsWith("/auction/bids") && bids.push(r.postData() ?? ""));
    await page.goto(auctionUrl(fx));
    await goLive(fx);
    await expect(page.getByTestId("bid-button")).toHaveAttribute("data-state", "ready");

    const text = (await page.locator("body").innerText()).toLowerCase();
    for (const word of ["cricket", "wicket", "batter", "bowler", "overseas", "rupee", "₹"]) expect(text).not.toContain(word);
    await expect(page.getByTestId("lot-card")).toContainText(/Goalkeeper|Defender|Midfielder|Forward/);

    await page.getByTestId("bid-button").dblclick();
    await expect(page.getByTestId("leader")).toContainText("You", { timeout: 2000 });
    await sleep(600);
    expect(bids).toHaveLength(1);
    expect(JSON.parse(bids[0]).amount).toBe((await auctionState(fx)).currentBid);
  });

  test("a bid the purse cannot cover shows the server's reason and changes nothing", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 600, purse: 350_000 });
    const page = await (await contextFor(browser, fx.managers[0].cookie)).newPage();
    await page.goto(auctionUrl(fx));
    await goLive(fx);
    await expect(page.getByTestId("bid-button")).toHaveAttribute("data-state", "ready");
    const first = await bidAs(fx, fx.managers[1], 1, 300_000);
    expect(first.body.outcome).toBe("accepted");
    await expect(page.getByTestId("leader")).toContainText("Club 2");
    await expect(page.getByTestId("bid-button")).toContainText("€0.4 M");
    const before = { bid: await page.getByTestId("current-bid").innerText(), purse: await page.getByTestId("purse").innerText() };

    await page.getByTestId("bid-button").click();

    await expect(page.getByTestId("notice-rejected")).toContainText("more than your purse allows");
    expect(await page.getByTestId("current-bid").innerText()).toBe(before.bid);
    expect(await page.getByTestId("purse").innerText()).toBe(before.purse);
    await expect(page.getByTestId("leader")).toContainText("Club 2");
  });

  test("another franchise's bid shows outbid within 500 ms and the new next amount", async ({ browser }) => {
    const fx = await seedRoom({ managers: 2, startInSec: 600 });
    const page = await (await contextFor(browser, fx.managers[0].cookie)).newPage();
    await page.goto(auctionUrl(fx));
    await goLive(fx);
    await expect(page.getByTestId("bid-button")).toHaveAttribute("data-state", "ready");
    await page.getByTestId("bid-button").click();
    await expect(page.getByTestId("bid-button")).toHaveAttribute("data-state", "leading");
    const mine = (await auctionState(fx)).nextBid;

    const answered = await bidAs(fx, fx.managers[1], 1, mine);
    expect(answered.body.outcome).toBe("accepted");

    await expect(page.getByTestId("notice-outbid")).toBeVisible({ timeout: 500 });
    await expect(page.getByTestId("notice-outbid")).toContainText("Club 2");
    await expect(page.getByTestId("bid-button")).toContainText("€0.5 M");
    await expect(page.getByTestId("bid-button")).toHaveAttribute("data-state", "ready");
  });

  for (const size of sizes) {
    test(`boxes stay put over 20 bid broadcasts, and the dock sits in the thumb zone at ${size.name}`, async ({ browser }) => {
      const fx = await seedRoom({ managers: 3, startInSec: 600, open: 30, bid: 30 });
      const page = await (await contextFor(browser, fx.managers[0].cookie, size)).newPage();
      await page.goto(auctionUrl(fx));
      await goLive(fx);
      const button = page.getByTestId("bid-button");
      await expect(button).toHaveAttribute("data-state", "ready");

      const ids = ["bid-button", "current-bid", "timer", "purse"];
      const boxes = async (p: Page) => Object.fromEntries(await Promise.all(ids.map(async (id) => [id, await p.getByTestId(id).boundingBox()])));
      const before = await boxes(page);
      // AC-12: the current bid and the purse are in view without scrolling.
      for (const id of ["current-bid", "purse"]) {
        const box = before[id]!;
        expect(box.y + box.height).toBeLessThanOrEqual(size.height);
      }
      // AC-11: 72 px or more, in the bottom third on a phone, and the lifeline slot is its own box.
      const b = before["bid-button"]!;
      expect(b.height).toBeGreaterThanOrEqual(72);
      const slot = (await page.getByTestId("lifeline-slot").boundingBox())!;
      expect(slot.x + slot.width).toBeLessThanOrEqual(b.x);
      if (size.width === 390) expect(b.y).toBeGreaterThanOrEqual((size.height * 2) / 3);

      let next = (await auctionState(fx)).nextBid;
      for (let i = 0; i < 20; i++) {
        const who = fx.managers[1 + (i % 2)];
        const res = await bidAs(fx, who, 1, next);
        expect(res.body.outcome).toBe("accepted");
        next = (await auctionState(fx)).nextBid;
        await expect(page.getByTestId("bid-button")).toContainText(String(next / 1_000_000).replace(/0+$/, ""), { timeout: 1000 });
        expect(await boxes(page)).toEqual(before);
      }
    });
  }
});
