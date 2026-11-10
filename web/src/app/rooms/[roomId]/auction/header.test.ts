import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/** The rule body of a class in the auction stylesheet. */
function rule(css: string, selector: string): string {
  const at = css.indexOf(`${selector} {`);
  return css.slice(at, css.indexOf("}", at));
}

describe("auction header (AC-12)", () => {
  const css = readFileSync(join(__dirname, "auction.module.css"), "utf8");

  it("takes the h1 out of the browser's heading margin, so the header is as tall as its mock", () => {
    expect(rule(css, ".roomName")).toMatch(/margin:\s*0;/);
  });

  it("keeps the mock's 10px 16px padding", () => {
    expect(rule(css, ".header")).toMatch(/padding:\s*10px 16px;/);
  });
});
