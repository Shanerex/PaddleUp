import { mkdirSync, readFileSync } from "node:fs";
import { expect, test, type Browser, type Page } from "@playwright/test";
import { call, PACK, seedRoom, signIn } from "./support/seed.mjs";
import { contextFor, sleep, WEB } from "./support/helpers";

// T-21 (AC-5, AC-9, AC-12, AC-13): the home-redesign screens in a real browser. Needs `make dev`
// (see e2e/support/seed.mjs) and the design source served with `python3 -m http.server 8099` in
// docs/design/source (E2E_MOCKS). Pictures land in test-results/home: <size>-<name>.png (ours),
// mock-<size>-<name>.png and pair-<size>-<name>.png (ours left, mock right).

const MOCKS = process.env.E2E_MOCKS ?? "http://localhost:8099";
const OUT = process.env.E2E_SHOTS ?? "test-results/home";
const ok = (res: { status: number; body: unknown }, what: string) => {
  if (res.status >= 300) throw new Error(`${what}: ${res.status} ${JSON.stringify(res.body)}`);
  return res.body as Record<string, any>; // eslint-disable-line @typescript-eslint/no-explicit-any
};

/** The App Screens frame that draws each of our screens. */
const MOCK: Record<string, string> = {
  hubMany: "8a",
  hubScheduled: "8c",
  hubOne: "8g",
  hubLive: "8b",
  hubNoStart: "8d",
  hubRetention: "8e",
  hubSetupNoFranchise: "8h",
  hubSetupManager: "8i",
  hubSeasonLive: "8j",
  hubSingle: "8k",
  hubSetupFranchise: "8f",
  hubPast: "8l",
  hubLoneCompleted: "8m",
  lobby: "3a",
  lobbyLate: "3b1",
  lobbyOffline: "3b2",
  lobbyAdmin: "3b3",
  importFilePicked: "7l",
  importDenied: "7o",
  hubEmpty: "8n",
  hubError: "8o",
  hubLoading: "8p",
  settingsBasics: "8q",
  settingsSquad: "8r",
  settingsScoring: "8s",
  settingsAi: "8t",
  settingsInvite: "8u",
  settingsLocked: "8v",
  settingsConflict: "8w",
  settingsNote: "8x",
  setupScheduled: "8y",
  profile: "8ab",
  signIn: "8ac",
  join: "8ad",
  joinCode: "8ae",
  joinFull: "8af",
  roomMissing: "8ag",
};

const mockPages = new Map<string, Page>();
async function mockPage(browser: Browser): Promise<Page> {
  let page = mockPages.get("App Screens");
  if (!page) {
    page = await (await browser.newContext({ viewport: { width: 1700, height: 1200 } })).newPage();
    await page.goto(`${MOCKS}/${encodeURIComponent("App Screens.dc.html")}`);
    await page.waitForTimeout(8000);
    mockPages.set("App Screens", page);
  }
  return page;
}

async function mockShot(browser: Browser, name: string, w: number, file: string): Promise<boolean> {
  const id = MOCK[name];
  if (!id) return false;
  const page = await mockPage(browser);
  const el = page.locator(`[id="${id}"] dc-import[style*="width:${w}px"], [id="${id}"] .sc-host[style*="width: ${w}px"]`).first();
  if ((await el.count()) === 0) return false;
  await el.scrollIntoViewIfNeeded();
  await page.waitForTimeout(400);
  await el.screenshot({ path: file });
  return true;
}

async function pair(browser: Browser, ours: string, mock: string, out: string, width: number) {
  const img = (f: string) => `data:image/png;base64,${readFileSync(f).toString("base64")}`;
  const page = await browser.newPage({ viewport: { width: width === 390 ? 840 : 1500, height: width === 390 ? 900 : 480 } });
  const zoom = width === 390 ? 1 : 0.5;
  await page.setContent(
    `<body style="margin:0;display:flex;gap:16px;background:#999;font:12px sans-serif"><div><div>ours</div><img style="zoom:${zoom}" src="${img(ours)}"></div><div><div>mock</div><img style="zoom:${zoom}" src="${img(mock)}"></div></body>`,
  );
  await page.screenshot({ path: out, fullPage: true });
  await page.close();
}

const inviteCode = async (cookie: string, roomId: string) => ok(await call(cookie, "POST", `/api/rooms/${roomId}/invite`, {}), "invite").code as string;
const joinAs = async (cookie: string, code: string, name: string, monogram: string, colour: string) =>
  ok(await call(cookie, "POST", "/api/join", { code, franchise: { name, monogram, colour } }), `join ${name}`);

/** A person with a room in each hub state, signed in; `empty` is read before any of them exist. */
async function world(tag: string) {
  const viewer = await signIn(`e2e-hub-${tag}@example.test`);
  const build = async () => {
    // Setup, Commissioner with a franchise.
    const gang = ok(await call(viewer, "POST", "/api/rooms", { packId: PACK, presetId: "standard", name: "College Gang", competition: "IPL 2027" }), "create gang");
    ok(await call(viewer, "POST", `/api/rooms/${gang.id}/franchises`, { name: "Shane's Raiders", monogram: "SR", colour: "crimson" }), "own franchise");
    // Setup, Commissioner without a franchise.
    const office = ok(await call(viewer, "POST", "/api/rooms", { packId: PACK, presetId: "standard", name: "Office League" }), "create office");
    // Scheduled with a start two days out, as a manager.
    const sched = await seedRoom({ managers: 2, startInSec: 2 * 86400 + 4 * 3600 + 12 * 60 + 30, tag: `${tag}s` });
    await joinAs(viewer, await inviteCode(sched.commissioner, sched.roomId), "Dad's Army", "DA", "emerald");
    // Retention, as a manager.
    const ret = await seedRoom({ managers: 2, schedule: false, tag: `${tag}r` });
    await joinAs(viewer, await inviteCode(ret.commissioner, ret.roomId), "Hostel Bombers", "HB", "iris");
    ok(await call(ret.commissioner, "POST", `/api/rooms/${ret.roomId}/transitions`, { to: "retention" }), "retention");
    return { gang, office, sched, ret };
  };
  return { viewer, build };
}

for (const vp of [
  { width: 390, height: 844 },
  { width: 1440, height: 900 },
]) {
  test(`home-redesign screens at ${vp.width}`, async ({ browser }) => {
    mkdirSync(OUT, { recursive: true });
    test.setTimeout(420_000);
    const tag = Date.now().toString(36);
    const { viewer, build } = await world(tag);
    await mockPage(browser);

    const shot = async (page: Page, name: string) => {
      await page.waitForTimeout(500);
      const base = `${OUT}/${vp.width}-${name}`;
      await page.screenshot({ path: base + ".png" });
      const mock = `${OUT}/mock-${vp.width}-${name}.png`;
      if (await mockShot(browser, name, vp.width, mock)) await pair(browser, base + ".png", mock, `${OUT}/pair-${vp.width}-${name}.png`, vp.width);
    };
    const ctx = await contextFor(browser, viewer, vp);
    const page = await ctx.newPage();

    // Empty wall: nobody has a room yet.
    await page.goto("/");
    await expect(page.getByRole("heading", { name: "No rooms yet" })).toBeVisible();
    await shot(page, "hubEmpty");

    const w = await build();

    // Many rooms: hero is the scheduled auction, tiles follow in priority order.
    await page.goto("/");
    await expect(page.getByTestId("hero")).toBeVisible();
    await expect(page.getByTestId("hero-figure")).toHaveText(/^2d \d\d:\d\d$/);
    expect(await page.getByTestId("tile").count()).toBe(3);
    await shot(page, "hubMany");
    await shot(page, "hubScheduled");

    // AC-5: a stale tab follows the server on focus, and a new tab shows the same hero.
    const heroText = await page.getByTestId("hero").innerText();
    expect(heroText.length).toBeGreaterThan(0);
    const other = await ctx.newPage();
    await other.goto("/");
    await expect(other.getByTestId("hero")).toBeVisible();
    expect(await other.getByTestId("hero").innerText()).toContain(heroText.split("\n")[0]);
    expect((await other.getByTestId("hero").getAttribute("aria-label")) ?? "").toBe((await page.getByTestId("hero").getAttribute("aria-label")) ?? "");
    await other.close();
    const soon = await seedRoom({ managers: 2, startInSec: 3600, tag: `${tag}n` });
    await joinAs(viewer, await inviteCode(soon.commissioner, soon.roomId), "Night Owls", "NO", "teal");
    const started = Date.now();
    await page.evaluate(() => {
      window.dispatchEvent(new Event("focus"));
      document.dispatchEvent(new Event("visibilitychange"));
    });
    await expect(page.getByTestId("hero-figure")).toHaveText(/^\d\d:\d\d:\d\d$/, { timeout: 2000 });
    expect(Date.now() - started).toBeLessThan(2500);
    await expect(page.getByTestId("hero").getByRole("link", { name: "Open lobby" })).toHaveAttribute("href", `/rooms/${soon.roomId}/auction`);

    // Load error: a Note with Retry and no stale rooms.
    await page.route("**/api/rooms", (route) => (route.request().method() === "GET" ? route.fulfill({ status: 500, json: { error: "boom" } }) : route.continue()));
    await page.goto("/");
    await expect(page.getByText("Couldn't load your rooms.")).toBeVisible();
    await expect(page.getByTestId("hero")).toHaveCount(0);
    await shot(page, "hubError");
    await page.unroute("**/api/rooms");
    await page.getByRole("button", { name: "Retry" }).click();
    await expect(page.getByTestId("hero")).toBeVisible();

    // Loading skeleton.
    await page.route("**/api/rooms", async (route) => {
      if (route.request().method() !== "GET") return route.continue();
      await sleep(3000);
      await route.continue().catch(() => undefined);
    });
    await page.goto("/", { waitUntil: "commit" });
    await expect(page.locator("[aria-busy='true']")).toBeVisible();
    await shot(page, "hubLoading");
    await sleep(3000);
    await page.unroute("**/api/rooms");

    // Room settings: every section, then the locked and conflict variants.
    const gangUrl = (section: string) => `/rooms/${w.gang.id}/setup?section=${section}`;
    ok(await call(viewer, "POST", `/api/rooms/${w.gang.id}/invite`, {}), "invite code for the Invite section");
    for (const [name, section] of [
      ["settingsBasics", "basics"],
      ["settingsSquad", "squad"],
      ["settingsScoring", "scoring"],
      ["settingsAi", "ai"],
      ["settingsInvite", "invite"],
    ] as const) {
      await page.goto(gangUrl(section));
      await expect(page.getByText("ROOM SETTINGS", { exact: true })).toBeVisible();
      await expect(page.getByText("NEW ROOM")).toHaveCount(0);
      // Back to room is always reachable: the header link on a phone, the footer button on desktop.
      await expect(page.getByRole("button", { name: vp.width < 640 ? "College Gang" : "Back to room" })).toBeVisible();
      await shot(page, name);
    }

    // AC-9: header and footer keep their boxes while the body scrolls, in settings and in create.
    for (const url of [gangUrl("squad"), "/rooms/new"]) {
      await page.goto(url);
      const title = page.getByRole("heading", { level: 1 });
      await expect(title).toBeVisible();
      const body = page.locator("form [class*='body']").first();
      const footer = page.getByRole("button", { name: /^(Next|Continue)$/ });
      const before = { t: await title.boundingBox(), f: await footer.boundingBox() };
      const scrolled = await body.evaluate((el) => {
        el.scrollTop = 400;
        return { top: el.scrollTop, scrollable: el.scrollHeight > el.clientHeight };
      });
      const after = { t: await title.boundingBox(), f: await footer.boundingBox() };
      expect(after.t, `${url}: header box`).toEqual(before.t);
      expect(after.f, `${url}: footer box`).toEqual(before.f);
      expect(await page.evaluate(() => document.documentElement.scrollHeight <= window.innerHeight + 1), `${url}: the page itself does not scroll`).toBe(true);
      if (scrolled.scrollable) expect(scrolled.top).toBeGreaterThan(0);
    }

    // Next saves the section and moves on; a stale save keeps the server value.
    await page.goto(gangUrl("basics"));
    const nameField = page.getByLabel("Room name");
    await nameField.fill("College Gang 2");
    await page.getByRole("button", { name: "Next", exact: true }).click();
    await expect(page.getByRole("heading", { level: 1, name: "Squad rules" })).toBeVisible();
    await expect(page).toHaveURL(/section=squad/);
    await page.goto(gangUrl("basics"));
    await expect(page.getByLabel("Room name")).toHaveValue("College Gang 2");
    const stale = await contextFor(browser, viewer, vp);
    const stalePage = await stale.newPage();
    await stalePage.goto(gangUrl("basics"));
    await expect(stalePage.getByLabel("Room name")).toHaveValue("College Gang 2");
    // Someone else saves first.
    const fresh = ok(await call(viewer, "GET", `/api/rooms/${w.gang.id}`), "read");
    ok(await call(viewer, "PUT", `/api/rooms/${w.gang.id}/config`, { version: fresh.configVersion, config: { ...fresh.config, name: "Theirs" } }), "other save");
    await stalePage.getByLabel("Room name").fill("Mine");
    await stalePage.getByRole("button", { name: "Next", exact: true }).click();
    await expect(stalePage.getByText("Someone saved changes while you were editing.")).toBeVisible();
    await expect(stalePage.getByLabel("Room name")).toHaveValue("Theirs");
    await expect(stalePage.getByRole("heading", { level: 1, name: "Basics" })).toBeVisible();
    await shot(stalePage, "settingsConflict");
    expect((ok(await call(viewer, "GET", `/api/rooms/${w.gang.id}`), "after")).config.name).toBe("Theirs");
    await stale.close();

    // Locked: the room reads as live, so the fields are read-only with the reason.
    await page.route(`**/api/rooms/${w.gang.id}`, async (route) => {
      const res = await route.fetch();
      const body = await res.json();
      body.state = "auction_live";
      await route.fulfill({ response: res, json: body });
    });
    await page.goto(gangUrl("squad"));
    await expect(page.getByText("Settings are locked.")).toBeVisible();
    await shot(page, "settingsLocked");
    await page.unroute(`**/api/rooms/${w.gang.id}`);

    // A manager is sent back to the room with the Note.
    const mgr = await contextFor(browser, w.sched.managers[0].cookie, vp);
    const mgrPage = await mgr.newPage();
    await mgrPage.goto(`/rooms/${w.sched.roomId}/setup`);
    await expect(mgrPage).toHaveURL(new RegExp(`/rooms/${w.sched.roomId}\\?note=settings`));
    await expect(mgrPage.getByText("Only the Commissioner changes settings.")).toBeVisible();
    await expect(mgrPage.getByText("ROOM SETTINGS", { exact: true })).toHaveCount(0);
    await shot(mgrPage, "settingsNote");
    await mgr.close();

    // Scheduled room as its Commissioner: "Open the lobby".
    const commish = await contextFor(browser, w.sched.commissioner, vp);
    const commishPage = await commish.newPage();
    await commishPage.goto(`/rooms/${w.sched.roomId}`);
    await expect(commishPage.getByRole("link", { name: "Open the lobby" })).toBeVisible();
    await shot(commishPage, "setupScheduled");
    await commish.close();

    // Outside-room screens.
    await page.goto("/profile");
    await expect(page.getByRole("heading", { name: "Your profile" })).toBeVisible();
    await shot(page, "profile");
    const out = await browser.newContext({ viewport: vp });
    const outPage = await out.newPage();
    await outPage.goto("/sign-in");
    await expect(outPage.getByRole("link", { name: /continue with google/i })).toBeVisible();
    await expect(outPage.getByText(/apple/i)).toHaveCount(0);
    await shot(outPage, "signIn");
    await out.close();
    await page.goto("/join");
    await expect(page.getByRole("heading", { name: "Join a room" })).toBeVisible();
    await shot(page, "joinCode");
    const code = await inviteCode(w.sched.commissioner, w.sched.roomId);
    const newcomer = await contextFor(browser, await signIn(`e2e-new-${tag}@example.test`), vp);
    const newPage = await newcomer.newPage();
    await newPage.goto(`/join/${code}`);
    await expect(newPage.getByLabel("Franchise name")).toBeVisible();
    await shot(newPage, "join");
    await newPage.route("**/api/join/**", (route) => route.fulfill({ json: { roomName: "Monsoon League", takenColours: [], full: true, alreadyJoined: false, packName: "Cricket T20", competition: "IPL 2027", franchiseCount: 10, maxManagers: 10 } }));
    await newPage.goto(`/join/${code}`);
    await expect(newPage.getByText(/All 10 seats are taken/)).toBeVisible();
    await shot(newPage, "joinFull");
    await newcomer.close();
    await page.goto("/rooms/00000000-0000-4000-8000-000000000000");
    await expect(page.getByRole("heading", { name: "Can't find that room" })).toBeVisible();
    await shot(page, "roomMissing");

    // One room: the hero alone.
    const solo = await contextFor(browser, await signIn(`e2e-solo-${tag}@example.test`), vp);
    const soloPage = await solo.newPage();
    ok(await call(await signIn(`e2e-solo-${tag}@example.test`), "POST", "/api/rooms", { packId: PACK, presetId: "standard", name: "College Gang", competition: "IPL 2027" }), "solo");
    await soloPage.goto("/");
    await expect(soloPage.getByTestId("hero")).toBeVisible();
    await expect(soloPage.getByTestId("tile")).toHaveCount(0);
    await shot(soloPage, "hubOne");
    await solo.close();

    // Other hub states, drawn from the same person's rooms (the list is narrowed in the browser).
    const only = async (ids: string[], name: string, extra: (list: Record<string, any>[]) => void = () => undefined) => { // eslint-disable-line @typescript-eslint/no-explicit-any
      await page.route("**/api/rooms", async (route) => {
        if (route.request().method() !== "GET") return route.continue();
        const res = await route.fetch();
        const body = (await res.json()) as Record<string, any>[]; // eslint-disable-line @typescript-eslint/no-explicit-any
        const list = body.filter((r) => ids.includes(r.id));
        extra(list);
        await route.fulfill({ response: res, json: list });
      });
      await page.goto("/");
      await expect(page.getByRole("heading", { name: "Your rooms" })).toBeVisible();
      await page.waitForTimeout(300);
      await shot(page, name);
      await page.unroute("**/api/rooms");
    };
    await only([w.ret.roomId], "hubRetention");
    await only([w.ret.roomId], "hubLive", (list) => {
      list[0] = { ...list[0], state: "auction_live" };
    });
    await only([w.ret.roomId], "hubSeasonLive", (list) => {
      list[0] = { ...list[0], state: "season_live" };
    });
    await only([w.sched.roomId], "hubNoStart", (list) => {
      list[0] = { ...list[0], auctionStartsAt: null };
    });
    await only([w.gang.id], "hubSetupManager", (list) => {
      list[0] = { ...list[0], commissioner: false };
    });
    await only([w.gang.id], "hubSetupNoFranchise", (list) => {
      list[0] = { ...list[0], commissioner: false, myFranchise: null };
    });
    await only([w.gang.id], "hubSingle");
    await only([w.gang.id], "hubSetupFranchise");
    const completed = (list: Record<string, any>[], id: string, name: string) => list.push({ ...list[0], id, name, state: "completed", auctionStartsAt: null }); // eslint-disable-line @typescript-eslint/no-explicit-any
    await only([w.gang.id, w.ret.roomId], "hubPast", (list) => completed(list, "11111111-1111-4111-8111-111111111111", "Season 1 Cup"));
    await only([w.gang.id], "hubLoneCompleted", (list) => {
      list[0] = { ...list[0], state: "completed" };
    });

    await ctx.close();
  });
}

for (const vp of [
  { width: 390, height: 844 },
  { width: 1440, height: 900 },
]) {
  test(`lobby and season data import at ${vp.width}`, async ({ browser }) => {
    mkdirSync(OUT, { recursive: true });
    test.setTimeout(300_000);
    const fx = await seedRoom({ managers: 3, startInSec: 600, open: 20, bid: 60, gap: 8 });
    const shot = async (page: Page, name: string) => {
      await page.waitForTimeout(500);
      const base = `${OUT}/${vp.width}-${name}`;
      await page.screenshot({ path: base + ".png" });
      const mock = `${OUT}/mock-${vp.width}-${name}.png`;
      if (await mockShot(browser, name, vp.width, mock)) await pair(browser, base + ".png", mock, `${OUT}/pair-${vp.width}-${name}.png`, vp.width);
    };
    await mockPage(browser);

    const ctx = await contextFor(browser, fx.managers[0].cookie, vp);
    const page = await ctx.newPage();
    await page.goto(`/rooms/${fx.roomId}/auction`);
    await expect(page.getByTestId("lobby-countdown")).toHaveText(/\d\d:\d\d:\d\d/);
    await shot(page, "lobby");
    await ctx.setOffline(true);
    await expect(page.getByTestId("lobby-offline")).toBeVisible({ timeout: 3000 });
    await shot(page, "lobbyOffline");
    await ctx.setOffline(false);
    await ctx.close();

    const lateCtx = await contextFor(browser, fx.managers[1].cookie, vp);
    const late = await lateCtx.newPage();
    await late.route("**/api/rooms/*/auction", async (route) => {
      if (route.request().method() !== "GET") return route.continue();
      const res = await route.fetch();
      const body = await res.json();
      body.startsAt = new Date(Date.now() - 372_000).toISOString();
      await route.fulfill({ response: res, json: body });
    });
    await late.goto(`/rooms/${fx.roomId}/auction`);
    await expect(late.getByTestId("lobby-late")).toBeVisible();
    await shot(late, "lobbyLate");
    await lateCtx.close();

    const admin = await contextFor(browser, fx.commissioner, vp);
    const adminPage = await admin.newPage();
    await adminPage.goto(`/rooms/${fx.roomId}/auction`);
    await expect(adminPage.getByTestId("lobby-admin")).toBeVisible();
    await shot(adminPage, "lobbyAdmin");
    await admin.close();

    // Season data import: an app admin with a file picked, and a person who is not an admin.
    const appAdmin = await contextFor(browser, await signIn("e2e-admin@example.test"), vp);
    const importPage = await appAdmin.newPage();
    await importPage.goto("/admin/import");
    await expect(importPage.getByRole("heading", { name: "Season data" })).toBeVisible();
    await importPage.locator("input[type=file]").setInputFiles({ name: "ipl-2026-pool-final.csv", mimeType: "text/csv", buffer: Buffer.from("name,club,role,tags,base_price,set,notes\nFake 1,Spain,GK,local,1000000,Set A,\n") });
    await shot(importPage, "importFilePicked");
    await appAdmin.close();
    const nobody = await contextFor(browser, await signIn(`e2e-nobody-${Date.now().toString(36)}@example.test`), vp);
    const nobodyPage = await nobody.newPage();
    await nobodyPage.goto("/admin/import");
    await expect(nobodyPage.getByText("This page is for app admins")).toBeVisible();
    await shot(nobodyPage, "importDenied");
    await nobody.close();
  });
}

test("auction header is as tall as its mock, h1 margin 0 (AC-12)", async ({ browser }) => {
  test.setTimeout(120_000);
  const fx = await seedRoom({ managers: 2, startInSec: 4, open: 20, bid: 60 });
  const context = await contextFor(browser, fx.managers[0].cookie, { width: 390, height: 844 });
  const page = await context.newPage();
  await page.goto(`/rooms/${fx.roomId}/auction`);
  await expect(page.getByTestId("bid-button")).toBeVisible({ timeout: 30_000 });
  const ours = await page.evaluate(() => {
    const h = document.querySelector("header");
    return { height: h?.getBoundingClientRect().height ?? 0, margin: getComputedStyle(document.querySelector("header h1")!).marginTop };
  });
  await context.close();
  const mockPage = await (await browser.newContext({ viewport: { width: 1700, height: 1200 } })).newPage();
  await mockPage.goto(`${MOCKS}/${encodeURIComponent("Auction Explorations.dc.html")}`);
  await mockPage.waitForTimeout(6000);
  const mock = await mockPage
    .locator(`[id="1a"] .sc-host[style*="width: 390px"]`)
    .first()
    .evaluate((host) => {
      const bar = [...host.querySelectorAll("div")].find((d) => /padding:\s*10px 16px/.test(d.getAttribute("style") ?? "") && /var\(--team\)/.test(d.getAttribute("style") ?? ""));
      return bar?.getBoundingClientRect().height ?? 0;
    });
  console.log(`auction header: ours ${ours.height}, mock ${mock}, h1 margin ${ours.margin}`);
  expect(ours.margin).toBe("0px");
  expect(Math.abs(ours.height - mock)).toBeLessThanOrEqual(1);
});

void WEB;
