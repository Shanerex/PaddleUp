import { describe, expect, it } from "vitest";
import { LIFELINE_ORDER, orderedLifelines } from "./lifelines";

describe("orderedLifelines", () => {
  it("lists the core lifelines in the core order whatever the stored key order", () => {
    expect(LIFELINE_ORDER).toEqual(["fit-check", "price-ceiling", "rival-scan"]);
    expect(orderedLifelines({ "rival-scan": 1, "fit-check": 2, "price-ceiling": 0 })).toEqual([
      ["fit-check", 2],
      ["price-ceiling", 0],
      ["rival-scan", 1],
    ]);
  });
  it("puts an unknown type after the known ones", () => {
    expect(orderedLifelines({ other: 1, "fit-check": 1 }).map(([t]) => t)).toEqual(["fit-check", "other"]);
  });
});
