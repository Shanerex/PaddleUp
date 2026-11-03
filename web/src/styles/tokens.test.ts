import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

/**
 * Drift guard: every token in tokens.css must equal docs/design/source/paddle.js, and no token may
 * be missing or extra. See docs/knowledge/decisions/2026-11-03-design-source-fidelity.md.
 */
const paddle = readFileSync("../docs/design/source/paddle.js", "utf8");
const css = readFileSync("src/styles/tokens.css", "utf8");

type Tokens = Record<string, string>;

function designObject(name: string): Tokens {
  const body = new RegExp(`const ${name} = \\{([\\s\\S]*?)\\n  \\};`).exec(paddle)?.[1];
  if (!body) throw new Error(`paddle.js has no ${name}`);
  return Object.fromEntries([...body.matchAll(/'(--[a-z0-9-]+)':\s*'([^']*)'/g)].map((m) => [m[1], m[2]]));
}

function designTeam(name: string): Tokens {
  const body = new RegExp(`const ${name} = \\(h\\) => \\(\\{([\\s\\S]*?)\\}\\);`).exec(paddle)?.[1];
  if (!body) throw new Error(`paddle.js has no ${name}(h)`);
  return Object.fromEntries(
    [...body.matchAll(/'(--[a-z0-9-]+)':\s*`([^`]*)`/g)].map((m) => [m[1], m[2].replace("${h}", "var(--h)")]),
  );
}

function cssBlock(name: string): Tokens {
  const body = new RegExp(`/\\* @tokens ${name} \\*/([\\s\\S]*?)/\\* @end \\*/`).exec(css)?.[1];
  if (!body) throw new Error(`tokens.css has no @tokens ${name} block`);
  const pairs = [...body.matchAll(/(--[a-z0-9-]+):\s*([^;]+);/g)].map((m) => [m[1], m[2].trim()]);
  return Object.fromEntries(pairs.filter(([k]) => k !== "--h"));
}

describe("design tokens", () => {
  it.each([
    ["light", () => designObject("LIGHT")],
    ["dark", () => designObject("DARK")],
    ["team-light", () => designTeam("L")],
    ["team-dark", () => designTeam("D")],
  ])("%s block equals paddle.js", (block, design) => {
    expect(cssBlock(block)).toEqual(design());
  });

  it("defaults --h to the brand hue so a room without a franchise uses brand colours", () => {
    expect(css).toMatch(/--h:\s*38;/);
  });
});
