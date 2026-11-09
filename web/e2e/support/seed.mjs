// Seeds a real auction room over HTTP for the Playwright specs, against `make dev`.
//
// Needs the server started with:
//   PADDLEUP_PACKS_DIR  a directory holding the stub football pack (server/src/test/resources/packs/stub)
//                       beside the cricket pack
//   PADDLEUP_ADMIN_EMAILS=e2e-admin@example.test   (the season data import is admin-only)
//   PADDLEUP_AUTH_THROTTLE_START_PER_IP=1000      (every test person signs in)
// and its console output in E2E_SERVER_LOG: dev sign-in emails are logged there, and the code is
// read from it. Session cookies are cached in E2E_SESSIONS (default .e2e-sessions.json).
import { existsSync, readFileSync, writeFileSync } from "node:fs";

export const SERVER = process.env.E2E_SERVER ?? "http://localhost:8080";
const LOG = process.env.E2E_SERVER_LOG;
const SESSIONS = process.env.E2E_SESSIONS ?? ".e2e-sessions.json";
export const PACK = "stub-football";
export const ADMIN_EMAIL = "e2e-admin@example.test";

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const cache = existsSync(SESSIONS) ? JSON.parse(readFileSync(SESSIONS, "utf8")) : {};

/** One HTTP call as a signed-in person. Returns { status, body }. */
export async function call(cookie, method, path, body, extra = {}) {
  const headers = { ...(cookie ? { Cookie: cookie } : {}), ...(body && !(body instanceof FormData) ? { "Content-Type": "application/json" } : {}) };
  const res = await fetch(`${SERVER}${path}`, { method, headers, body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined, ...extra });
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = text;
  }
  return { status: res.status, body: json, headers: res.headers };
}

async function codeFor(email, since) {
  for (let i = 0; i < 40; i++) {
    const log = LOG && existsSync(LOG) ? readFileSync(LOG, "utf8") : "";
    const at = log.lastIndexOf(`to=${email}`);
    if (at > since) {
      const code = /\b(\d{6})\b/.exec(log.slice(at, at + 600));
      if (code) return code[1];
    }
    await sleep(250);
  }
  throw new Error(`no sign-in code for ${email} in ${LOG}`);
}

/** A cookie header value for this email, signing in (or reusing a cached session). */
export async function signIn(email) {
  if (cache[email]) {
    const me = await call(cache[email], "GET", "/api/me");
    if (me.status === 200) return cache[email];
  }
  const since = LOG && existsSync(LOG) ? readFileSync(LOG, "utf8").length : 0;
  const start = await call(null, "POST", "/api/auth/email/start", { email });
  if (start.status >= 300) throw new Error(`start ${email}: ${start.status} ${JSON.stringify(start.body)}`);
  const code = await codeFor(email, since);
  const verify = await call(null, "POST", "/api/auth/email/verify", { email, code });
  if (verify.status !== 200) throw new Error(`verify ${email}: ${verify.status} ${JSON.stringify(verify.body)}`);
  const cookie = verify.headers.get("set-cookie").split(";")[0];
  cache[email] = cookie;
  writeFileSync(SESSIONS, JSON.stringify(cache, null, 1));
  return cookie;
}

function must(res, what) {
  if (res.status >= 300) throw new Error(`${what}: ${res.status} ${JSON.stringify(res.body)}`);
  return res.body;
}

const ROLES = ["gk", "def", "mid", "fwd"];
const SETS = ["Keepers", "Defence", "Midfield", "Attack"];
const COLOURS = ["crimson", "sky", "emerald", "orchid", "bronze", "teal"];

function csv(players) {
  const header = "name,country,role,tags,base_price,set,provider_id\n";
  const rows = [];
  for (let i = 0; i < players; i++) {
    const price = 300_000;
    rows.push(`Fake ${i + 1},Spain,${ROLES[i % 4]},${i % 2 === 0 ? "local" : "foreign"},${price},${SETS[Math.floor(i / (players / 4))] ?? SETS[3]},`);
  }
  return header + rows.join("\n") + "\n";
}

let seasonImported = false;

/** One season data version per run: 40 players, all at the same base price, in four sets. */
async function ensureSeason(players) {
  if (seasonImported) return;
  const admin = await signIn(ADMIN_EMAIL);
  const form = new FormData();
  form.append("packId", PACK);
  form.append("file", new Blob([csv(players)], { type: "text/csv" }), "season.csv");
  must(await call(admin, "POST", "/api/admin/datasets", form), "import season");
  seasonImported = true;
}

/**
 * A room on the stub football pack with `managers` franchises, a Commissioner with no franchise, a
 * pool of 40 and the auction scheduled `startInSec` from now. Everyone is a fresh person.
 */
export async function seedRoom({ managers = 2, startInSec = 60, open = 8, bid = 6, gap = 2, purse = 0, squadMin = 3, schedule = true, tag = Date.now().toString(36) } = {}) {
  await ensureSeason(40);
  const commissioner = await signIn(`e2e-commish-${tag}@example.test`);
  const room = must(await call(commissioner, "POST", "/api/rooms", { packId: PACK, presetId: "standard", name: `E2E ${tag}` }), "create room");
  const config = { ...room.config, ...(purse ? { purse } : {}), squadMin, squadMax: Math.max(20, squadMin), roleMinimums: {}, openSeconds: open, bidSeconds: bid, gapSeconds: gap };
  must(await call(commissioner, "PUT", `/api/rooms/${room.id}/config`, { version: room.configVersion, config }), "save config");
  const pool = must(await call(commissioner, "GET", `/api/rooms/${room.id}/pool`), "read pool");
  must(await call(commissioner, "POST", `/api/rooms/${room.id}/pool/reset`, { version: pool.version }), "reset pool");
  const invite = must(await call(commissioner, "POST", `/api/rooms/${room.id}/invite`, {}), "invite");
  const people = [];
  for (let i = 0; i < managers; i++) {
    const email = `e2e-mgr${i + 1}-${tag}@example.test`;
    const cookie = await signIn(email);
    const joined = must(await call(cookie, "POST", "/api/join", { code: invite.code, franchise: { name: `Club ${i + 1}`, monogram: `C${i + 1}`, colour: COLOURS[i] } }), "join");
    people.push({ email, cookie, franchiseId: joined.franchise?.id ?? joined.franchiseId ?? null });
  }
  // `schedule: false` leaves the room in setup with no auction, for the schedule sheet itself.
  let startsAt = null;
  if (schedule) {
    must(await call(commissioner, "POST", `/api/rooms/${room.id}/transitions`, { to: "auction_scheduled" }), "schedule room");
    startsAt = new Date(Date.now() + startInSec * 1000).toISOString();
    must(await call(commissioner, "PUT", `/api/rooms/${room.id}/auction/schedule`, { startsAt }), "schedule auction");
  }
  const members = must(await call(commissioner, "GET", `/api/rooms/${room.id}/members`), "members");
  for (const p of people) {
    const m = members.find((x) => x.franchise && x.franchise.name === `Club ${people.indexOf(p) + 1}`);
    p.franchiseId = m?.franchise.id ?? p.franchiseId;
  }
  return { roomId: room.id, commissioner, managers: people, startsAt, tag };
}

/** Reschedules the start (Commissioner), for a test that needs the auction to start now. */
export async function startIn(fx, seconds) {
  const startsAt = new Date(Date.now() + seconds * 1000).toISOString();
  must(await call(fx.commissioner, "PUT", `/api/rooms/${fx.roomId}/auction/schedule`, { startsAt }), "reschedule");
  return startsAt;
}

export const auctionState = async (fx, cookie = fx.commissioner) => must(await call(cookie, "GET", `/api/rooms/${fx.roomId}/auction`), "state");

/** A bid as a manager, at the server's next bid. */
export async function bidAs(fx, who, lot, amount) {
  return call(who.cookie, "POST", `/api/rooms/${fx.roomId}/auction/bids`, { intentId: crypto.randomUUID(), lot, amount });
}
