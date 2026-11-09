import { describe, expect, it, vi } from "vitest";
import { ApiError, type IntentResult } from "../api/client";
import { createBidController } from "./useBid";

const ok = (lot = 3): IntentResult => ({ outcome: "accepted", lot, serverNow: "2030-03-01T10:00:00Z", seq: 9 });

function setup(bid = vi.fn(async () => ok())) {
  let t = 0;
  let n = 0;
  const c = createBidController({ bid, newIntentId: () => `id-${++n}`, mono: () => t });
  return { c, bid, tick: (ms: number) => (t += ms) };
}

describe("createBidController", () => {
  it("sends the server's next bid for the lot, once", async () => {
    const { c, bid } = setup();
    await c.press(3, 25);
    expect(bid).toHaveBeenCalledTimes(1);
    expect(bid).toHaveBeenCalledWith({ intentId: "id-1", lot: 3, amount: 25 });
    expect(c.snapshot()).toMatchObject({ sending: false, bidLot: 3, outcome: { kind: "accepted", lot: 3 } });
  });

  it("sends one request for a double tap, in flight or within 300 ms", async () => {
    let release: (r: IntentResult) => void = () => {};
    const bid = vi.fn(() => new Promise<IntentResult>((r) => (release = r)));
    const { c, tick } = setup(bid);

    const first = c.press(3, 25);
    expect(c.snapshot().sending).toBe(true);
    await c.press(3, 25); // in flight
    tick(100);
    await c.press(3, 25);
    release(ok());
    await first;
    tick(100); // 200 ms after the first tap: still the same tap
    await c.press(3, 30);
    expect(bid).toHaveBeenCalledTimes(1);

    tick(400);
    bid.mockImplementationOnce(() => Promise.resolve(ok()));
    await c.press(3, 30);
    expect(bid).toHaveBeenCalledTimes(2);
  });

  it("shows a refusal from the server's reply with the mock copy and does not mark a bid", async () => {
    const { c } = setup(vi.fn(async () => ({ outcome: "rejected", reason: "purse", lot: 3, serverNow: "x" })));
    await c.press(3, 25);
    const s = c.snapshot();
    expect(s.outcome).toMatchObject({ kind: "rejected", reason: "purse", notice: { title: "Bid bounced" } });
    expect(s.bidLot).toBeNull();
    expect(s.sending).toBe(false);
  });

  it("maps a 409 phase error to a refusal and a new tap is a new intent", async () => {
    const bid = vi.fn().mockRejectedValueOnce(new ApiError(409, { error: "no_open_lot" })).mockResolvedValue(ok());
    const { c, tick } = setup(bid);
    await c.press(3, 25);
    expect(c.snapshot().outcome).toMatchObject({ kind: "rejected", reason: "no_open_lot" });
    tick(500);
    await c.press(3, 25);
    expect(bid.mock.calls.map((x) => (x as unknown[][])[0])).toEqual([
      { intentId: "id-1", lot: 3, amount: 25 },
      { intentId: "id-2", lot: 3, amount: 25 },
    ]);
  });

  it("keeps the same intent id when the answer never came, and a new one for another amount", async () => {
    const bid = vi.fn().mockRejectedValueOnce(new Error("network")).mockRejectedValueOnce(new ApiError(503, null)).mockResolvedValue(ok());
    const { c, tick } = setup(bid);

    await c.press(3, 25);
    expect(c.snapshot().outcome).toMatchObject({ kind: "unreached" });
    tick(500);
    await c.press(3, 25);
    tick(500);
    await c.press(3, 25);
    tick(500);
    await c.press(3, 30);

    const ids = bid.mock.calls.map((x) => (x as { intentId: string }[])[0].intentId);
    expect(ids.slice(0, 3)).toEqual(["id-1", "id-1", "id-1"]);
    expect(ids[3]).toBe("id-2");
  });

  it("clears the last outcome when dismissed", async () => {
    const { c } = setup();
    await c.press(3, 25);
    c.dismiss();
    expect(c.snapshot().outcome).toBeNull();
    expect(c.snapshot().bidLot).toBe(3);
  });

  it("never computes an amount itself: it sends exactly what it is given", async () => {
    const { c, bid } = setup();
    await c.press(7, 1234);
    expect(bid).toHaveBeenCalledWith(expect.objectContaining({ lot: 7, amount: 1234 }));
  });
});
