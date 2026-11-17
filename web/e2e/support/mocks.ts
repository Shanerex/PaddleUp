import { mkdirSync, readFileSync } from "node:fs";
import type { Browser, Page } from "@playwright/test";

/**
 * The design source frames, to put beside our screens. Set E2E_MOCKS to where `docs/design/source`
 * is served (`python3 -m http.server 8099` in that folder). Unset, nothing is fetched and no pair
 * is made, so a spec still runs without the mocks.
 */
export const MOCKS = process.env.E2E_MOCKS ?? null;

export type Frame = { page: "Auction Explorations" | "App Screens"; id: string };

const pages = new Map<string, Page>();
async function mockPage(browser: Browser, name: string): Promise<Page> {
  let page = pages.get(name);
  if (!page) {
    page = await (await browser.newContext({ viewport: { width: 1700, height: 1200 } })).newPage();
    await page.goto(`${MOCKS}/${encodeURIComponent(name + ".dc.html")}`);
    await page.waitForTimeout(6000);
    pages.set(name, page);
  }
  return page;
}

/** The frame as drawn at `width` (390 or 1440), saved to `file`; false when it is not drawn at that width. */
export async function mockShot(browser: Browser, frame: Frame, width: number, file: string): Promise<boolean> {
  const page = await mockPage(browser, frame.page);
  const el = page.locator(`[id="${frame.id}"] .sc-host[style*="width: ${width}px"]`).first();
  if ((await el.count()) === 0) return false;
  await el.scrollIntoViewIfNeeded();
  await page.waitForTimeout(400);
  await el.screenshot({ path: file });
  return true;
}

/** Ours on the left, the mock on the right, in one picture. */
export async function pair(browser: Browser, ours: string, mock: string, out: string, width: number) {
  const img = (f: string) => `data:image/png;base64,${readFileSync(f).toString("base64")}`;
  const page = await browser.newPage({ viewport: { width: width === 390 ? 840 : 1500, height: width === 390 ? 900 : 480 } });
  const zoom = width === 390 ? 1 : 0.5;
  await page.setContent(
    `<body style="margin:0;display:flex;gap:16px;background:#999;font:12px sans-serif"><div><div>ours</div><img style="zoom:${zoom}" src="${img(ours)}"></div><div><div>mock</div><img style="zoom:${zoom}" src="${img(mock)}"></div></body>`,
  );
  await page.screenshot({ path: out, fullPage: true });
  await page.close();
}

export const ensureDir = (dir: string) => mkdirSync(dir, { recursive: true });
