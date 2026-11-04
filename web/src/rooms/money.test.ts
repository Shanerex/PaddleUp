import { describe, expect, it } from "vitest";
import { currencySymbol, formatMoney, ladderRows } from "./money";

const cfg = { currencyCode: "INR", displayUnit: { label: "Crore", divisor: 100 } };

describe("money", () => {
  it("formats in the pack's currency and display unit", () => {
    expect(currencySymbol("INR")).toBe("₹");
    expect(formatMoney(12000, cfg)).toBe("₹120 Crore");
    expect(formatMoney(5, cfg)).toBe("₹0.05 Crore");
    expect(formatMoney(100_000_000, { currencyCode: "EUR", displayUnit: { label: "M", divisor: 1_000_000 } })).toBe("€100 M");
  });

  it("falls back to the code for an unknown currency", () => {
    expect(currencySymbol("XXZ")).toBe("XXZ");
  });

  it("describes the increment ladder as ranges", () => {
    const rows = ladderRows({ ...cfg, incrementLadder: [{ from: 100, step: 10 }, { from: 0, step: 5 }, { from: 500, step: 25 }] });
    expect(rows).toEqual([
      { range: "Up to ₹1 Crore", step: "+₹0.05 Crore" },
      { range: "₹1 Crore–₹5 Crore", step: "+₹0.1 Crore" },
      { range: "Above ₹5 Crore", step: "+₹0.25 Crore" },
    ]);
  });
});
