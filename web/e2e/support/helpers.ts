import type { Browser, BrowserContext, Page } from "@playwright/test";
import { seedRoom } from "./seed.mjs";

export type Fixture = Awaited<ReturnType<typeof seedRoom>>;
export const WEB = process.env.E2E_WEB ?? "http://localhost:3000";

export const cookieParts = (cookie: string) => {
  const [name, ...rest] = cookie.split("=");
  return { name, value: rest.join("="), url: WEB };
};

/** A browser context signed in as one of the fixture's people. */
export async function contextFor(browser: Browser, cookie: string, opts: { width?: number; height?: number; skewMs?: number; colorScheme?: "light" | "dark"; reducedMotion?: "reduce" | "no-preference"; timezoneId?: string } = {}): Promise<BrowserContext> {
  const context = await browser.newContext({
    viewport: { width: opts.width ?? 390, height: opts.height ?? 844 },
    colorScheme: opts.colorScheme ?? "light",
    reducedMotion: opts.reducedMotion ?? "no-preference",
    ...(opts.timezoneId ? { timezoneId: opts.timezoneId } : {}),
  });
  await context.addCookies([cookieParts(cookie)]);
  if (opts.skewMs) {
    // The browser's Date is wrong by this much; the monotonic clock is untouched.
    await context.addInitScript((skew: number) => {
      const Real = Date;
      class Skewed extends Real {
        constructor(...args: unknown[]) {
          if (args.length === 0) super(Real.now() + skew);
          else super(...(args as [number]));
        }
        static now() {
          return Real.now() + skew;
        }
      }
      (window as unknown as { Date: unknown }).Date = Skewed;
    }, opts.skewMs);
  }
  return context;
}

export const auctionUrl = (fx: Fixture) => `/rooms/${fx.roomId}/auction`;

export async function open(page: Page, fx: Fixture) {
  await page.goto(auctionUrl(fx));
}

/** "00:59:33" or "+06:12" to ms. */
export function countdownMs(text: string): number {
  const late = text.startsWith("+");
  const parts = text.replace("+", "").split(":").map(Number);
  const secs = parts.length === 3 ? parts[0] * 3600 + parts[1] * 60 + parts[2] : parts[0] * 60 + parts[1];
  return (late ? -1 : 1) * secs * 1000;
}

export const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** A date and time as the native inputs hold them, read in the given zone. */
export function fieldsIn(timeZone: string, at: Date): { date: string; time: string } {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hourCycle: "h23" })
      .formatToParts(at)
      .map((p) => [p.type, p.value]),
  );
  return { date: `${parts.year}-${parts.month}-${parts.day}`, time: `${parts.hour}:${parts.minute}` };
}

/** The lobby's "<time> your time" for an instant in a zone, as en-US renders it. */
export function lobbyTimeIn(timeZone: string, at: Date): string {
  return at.toLocaleTimeString("en-US", { timeZone, hour: "numeric", minute: "2-digit" });
}
