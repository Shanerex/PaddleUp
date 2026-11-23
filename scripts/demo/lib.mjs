// Shared HTTP helpers for the demo scripts. Signs people in through the dev console email: the
// server's stdout goes to DEMO_LOG and the 6-digit code is read from it.
import { existsSync, readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
export const RUN_DIR = join(HERE, ".run");
mkdirSync(RUN_DIR, { recursive: true });

export const SERVER = process.env.DEMO_SERVER ?? "http://localhost:8080";
export const WEB = process.env.DEMO_WEB ?? "http://localhost:3000";
export const LOG = process.env.DEMO_LOG ?? join(RUN_DIR, "server.log");
export const DB = process.env.DEMO_DB ?? "paddleup_demo";
export const PG_CONTAINER = process.env.DEMO_PG_CONTAINER ?? "paddleup-postgres-1";
const SESSIONS = join(RUN_DIR, "sessions.json");
export const STATE_FILE = join(RUN_DIR, "demo.json");

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const cache = existsSync(SESSIONS) ? JSON.parse(readFileSync(SESSIONS, "utf8")) : {};

export async function call(cookie, method, path, body) {
  const headers = { ...(cookie ? { Cookie: cookie } : {}), ...(body && !(body instanceof FormData) ? { "Content-Type": "application/json" } : {}) };
  const res = await fetch(`${SERVER}${path}`, { method, headers, body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { json = text; }
  return { status: res.status, body: json, headers: res.headers };
}

export function must(res, what) {
  if (res.status >= 300) throw new Error(`${what}: ${res.status} ${JSON.stringify(res.body)}`);
  return res.body;
}

function logText() {
  return existsSync(LOG) ? readFileSync(LOG, "utf8") : "";
}

async function codeFor(email, since) {
  for (let i = 0; i < 60; i++) {
    const log = logText();
    const at = log.lastIndexOf(`to=${email}`);
    if (at > since) {
      const code = /\b(\d{6})\b/.exec(log.slice(at, at + 800));
      if (code) return code[1];
    }
    await sleep(250);
  }
  throw new Error(`no sign-in code for ${email} in ${LOG}`);
}

/** Cookie header value for this email (cached session, or a fresh sign-in through the log). */
export async function signIn(email) {
  if (cache[email]) {
    const me = await call(cache[email], "GET", "/api/me");
    if (me.status === 200) return cache[email];
  }
  const since = logText().length;
  must(await call(null, "POST", "/api/auth/email/start", { email }), `sign-in start ${email}`);
  const code = await codeFor(email, since);
  const verify = await call(null, "POST", "/api/auth/email/verify", { email, code });
  if (verify.status !== 200) throw new Error(`verify ${email}: ${verify.status} ${JSON.stringify(verify.body)}`);
  const cookie = verify.headers.get("set-cookie").split(";")[0];
  cache[email] = cookie;
  writeFileSync(SESSIONS, JSON.stringify(cache, null, 1));
  return cookie;
}

/** Runs one SQL statement in the demo database (fixtures and provider stats have no REST route). */
export function sql(statement) {
  return execFileSync("docker", ["exec", PG_CONTAINER, "psql", "-U", "paddleup", "-d", DB, "-v", "ON_ERROR_STOP=1", "-At", "-c", statement], { encoding: "utf8" }).trim();
}

/** Lakh amounts as the pack shows them: 150 -> "1.5 Cr", 75 -> "75 L". */
export function money(lakh) {
  return lakh >= 100 ? `${(lakh / 100).toFixed(2).replace(/\.?0+$/, "")} Cr` : `${lakh} L`;
}

export function loadState() {
  return existsSync(STATE_FILE) ? JSON.parse(readFileSync(STATE_FILE, "utf8")) : {};
}

export function saveState(state) {
  writeFileSync(STATE_FILE, JSON.stringify(state, null, 1));
}

export const intent = () => crypto.randomUUID();

export async function control(cookie, roomId, action, extra = {}) {
  return call(cookie, "POST", `/api/rooms/${roomId}/auction/controls`, { intentId: intent(), action, ...extra });
}
