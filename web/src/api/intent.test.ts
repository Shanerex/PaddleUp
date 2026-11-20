import { describe, expect, it } from "vitest";
import { IntentRetry } from "./intent";

describe("IntentRetry", () => {
  it("hands out the same id for the same action until it is settled", () => {
    const retry = new IntentRetry();
    const first = retry.id("pause");
    expect(retry.id("pause")).toBe(first);
    retry.settle();
    expect(retry.id("pause")).not.toBe(first);
  });

  it("a different action or different arguments is a new intent", () => {
    const retry = new IntentRetry();
    const pause = retry.id("pause");
    expect(retry.id("skip:7")).not.toBe(pause);
    expect(retry.id("pause")).not.toBe(pause);
  });

  it("run reuses the id after a failed send and takes a new one after any answer", async () => {
    const retry = new IntentRetry();
    const seen: string[] = [];
    await expect(retry.run("pause", async (id) => { seen.push(id); throw new Error("offline"); })).rejects.toThrow("offline");
    await retry.run("pause", async (id) => { seen.push(id); return "rejected"; });
    await retry.run("pause", async (id) => { seen.push(id); return "accepted"; });
    expect(seen[1]).toBe(seen[0]);
    expect(seen[2]).not.toBe(seen[0]);
  });

  it("forgets an id after the window so a stale retry is a fresh action", () => {
    let t = 0;
    const retry = new IntentRetry(30_000, () => t);
    const first = retry.id("pause");
    t = 30_000;
    expect(retry.id("pause")).toBe(first);
    t = 30_001;
    expect(retry.id("pause")).not.toBe(first);
  });
});
