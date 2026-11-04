import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { HUES, hueLabel, hueOf, isHueKey, teamStyle } from "./hues";

describe("hues", () => {
  it("has the 12 design hues at 30 degree steps", () => {
    expect(HUES).toHaveLength(12);
    expect(HUES.map((h) => h.hue)).toEqual([15, 45, 75, 105, 135, 165, 195, 225, 255, 285, 315, 345]);
    expect(new Set(HUES.map((h) => h.key)).size).toBe(12);
  });

  it("looks up a hue by key and falls back for an unknown one", () => {
    expect(hueOf("sky")).toBe(225);
    expect(hueOf("nope")).toBe(15);
    expect(hueLabel("orchid")).toBe("Orchid");
    expect(isHueKey("rose")).toBe(true);
    expect(isHueKey("mauve")).toBe(false);
  });

  it("feeds the team tokens through --h", () => {
    expect(teamStyle("teal")).toEqual({ "--h": 195 });
  });

  it("matches the design README", () => {
    const readme = readFileSync("../docs/design/README.md", "utf8");
    for (const { label, hue } of HUES) {
      expect(readme).toContain(`${label} ${hue}`);
    }
  });
});
