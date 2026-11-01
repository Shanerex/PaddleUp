import { describe, expect, it } from "vitest";
import manifest from "./manifest";

describe("web app manifest", () => {
  const m = manifest();

  it("is installable-shaped", () => {
    expect(m.name).toBe("PaddleUp");
    expect(m.short_name).toBe("PaddleUp");
    expect(m.start_url).toBe("/");
    expect(m.display).toBe("standalone");
  });

  it("uses design token colours", () => {
    expect(m.theme_color).toMatch(/^#[0-9a-f]{6}$/i);
    expect(m.background_color).toMatch(/^#[0-9a-f]{6}$/i);
  });

  it("declares 192, 512 and maskable icons", () => {
    const icons = m.icons ?? [];
    const sizes = icons.map((i) => i.sizes);
    expect(sizes).toContain("192x192");
    expect(sizes).toContain("512x512");
    expect(icons.some((i) => i.purpose === "maskable")).toBe(true);
    for (const i of icons) expect(i.type).toBe("image/png");
  });
});
