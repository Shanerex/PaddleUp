import { describe, expect, it } from "vitest";
import { currencySymbol, formatMoney, formatMoneyShort, ladderRows } from "./money";

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

describe("formatMoneyShort", () => {
  const major = { label: "Crore", divisor: 100, short: "Cr", minor: { label: "Lakh", short: "L", divisor: 1 } };
  const football = { label: "Million", divisor: 1_000_000, short: "M", minor: { label: "Thousand", short: "K", divisor: 1_000 } };

  it("uses the major short unit, then the minor unit below one major (cricket-like)", () => {
    const c = { currencyCode: "INR", displayUnit: major };
    expect(formatMoneyShort(12000, c)).toBe("₹120 Cr");
    expect(formatMoneyShort(150, c)).toBe("₹1.5 Cr");
    expect(formatMoneyShort(100, c)).toBe("₹1 Cr");
    expect(formatMoneyShort(75, c)).toBe("₹75 L");
    expect(formatMoneyShort(5, c)).toBe("₹5 L");
    expect(formatMoneyShort(0, c)).toBe("₹0 L");
  });

  it("works for a football-like pack with no cricket word in the output", () => {
    const c = { currencyCode: "EUR", displayUnit: football };
    expect(formatMoneyShort(100_000_000, c)).toBe("€100 M");
    expect(formatMoneyShort(250_000, c)).toBe("€250 K");
    expect(formatMoneyShort(1_500_000, c)).toBe("€1.5 M");
    expect(formatMoneyShort(250_000, c)).not.toMatch(/cr|lakh|crore|₹/i);
  });

  it("falls back to the label and one decimal unit when the pack gives no short or minor", () => {
    const c = { currencyCode: "EUR", displayUnit: { label: "M", divisor: 1_000_000 } };
    expect(formatMoneyShort(100_000_000, c)).toBe("€100 M");
    expect(formatMoneyShort(250_000, c)).toBe("€0.25 M");
    expect(formatMoneyShort(250_000, { currencyCode: "EUR", displayUnit: { label: "Million", divisor: 1_000_000, short: "M" } })).toBe("€0.25 M");
  });
});
