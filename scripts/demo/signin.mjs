// Prints a fresh sign-in code and link for a demo account (default Cara), read from the dev log.
//   node scripts/demo/signin.mjs [email]
import { readFileSync } from "node:fs";
import { call, LOG, sleep } from "./lib.mjs";

const email = process.argv[2] ?? "cara@paddleup.test";
const since = readFileSync(LOG, "utf8").length;
const r = await call(null, "POST", "/api/auth/email/start", { email });
if (r.status >= 300) throw new Error(`start: ${r.status} ${JSON.stringify(r.body)}`);
for (let i = 0; i < 40; i++) {
  const text = readFileSync(LOG, "utf8").slice(since);
  const at = text.indexOf(`to=${email}`);
  if (at >= 0) {
    const chunk = text.slice(at, at + 800);
    const code = /\b(\d{6})\b/.exec(chunk)?.[1];
    const link = /(https?:\/\/\S+)/.exec(chunk)?.[1];
    if (code) {
      console.log(`${email}\n  code: ${code}\n  link: ${link ?? "(none)"}`);
      process.exit(0);
    }
  }
  await sleep(250);
}
throw new Error("no sign-in email in the log");
