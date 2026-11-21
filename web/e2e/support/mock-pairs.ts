import { readFileSync } from "node:fs";
import type { Browser, Page } from "@playwright/test";

// Design source frames, served with `python3 -m http.server 8099` in docs/design/source (E2E_MOCKS).
const MOCKS = process.env.E2E_MOCKS ?? "http://localhost:8099";
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

/** Saves the mock frame `id` of a design source page at one width; false when the frame is absent. */
export async function mockShot(browser: Browser, source: string, id: string, width: number, file: string): Promise<boolean> {
  const page = await mockPage(browser, source);
  const el = page.locator(`[id="${id}"] .sc-host[style*="width: ${width}px"]`).first();
  if ((await el.count()) === 0) return false;
  await el.scrollIntoViewIfNeeded();
  await page.waitForTimeout(400);
  await el.screenshot({ path: file });
  return true;
}

/** One picture with ours on the left and the mock on the right. */
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
