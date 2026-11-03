import { describe, expect, it } from "vitest";
import { fromDisplay, humanise, labelFor, suggestMonogram, toDisplay } from "./format";

describe("format", () => {
  it("makes ids readable", () => {
    expect(humanise("fit-check")).toBe("Fit check");
    expect(humanise("rival_scan")).toBe("Rival scan");
  });

  it("converts amounts between the smallest unit and the display unit", () => {
    expect(toDisplay(12000, 100)).toBe(120);
    expect(fromDisplay(120, 100)).toBe(12000);
    expect(fromDisplay(0.35, 100)).toBe(35);
    expect(toDisplay(5, 0)).toBe(5);
  });

  it("labels an id from the snapshot, falling back to the id", () => {
    const roles = [{ id: "A", label: "Alpha" }];
    expect(labelFor(roles, "A")).toBe("Alpha");
    expect(labelFor(roles, "some-id")).toBe("Some id");
  });

  it("suggests a monogram from the name", () => {
    expect(suggestMonogram("Super Kings")).toBe("SK");
    expect(suggestMonogram("Titans")).toBe("TI");
    expect(suggestMonogram("  ")).toBe("");
    expect(suggestMonogram("Ben <b>XI</b>")).toBe("BX");
    expect(suggestMonogram("!!")).toBe("");
  });
});
