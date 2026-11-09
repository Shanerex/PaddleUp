/** The browser's monotonic clock, in ms. Date can be wrong or change; this cannot. */
const monotonic = () => (typeof performance !== "undefined" ? performance.now() : Date.now());

/** A sample older than this no longer protects the offset from drift. */
const SAMPLE_TTL_MS = 30_000;

type Sample = { rtt: number; serverAtReceive: number; receivedAt: number };

/**
 * An estimate of the server clock (R-6). The browser Date is never read: the server says what time
 * it is (`serverNow` on a state read or a bid reply), and the estimate advances on the
 * monotonic clock. Of several samples the one with the smallest round trip is the most exact, so it
 * wins until it is old; the estimate itself never goes backwards.
 */
export class ServerClock {
  private best: Sample | null = null;
  private last = 0;

  constructor(private readonly mono: () => number = monotonic) {}

  get ready(): boolean {
    return this.best !== null;
  }

  /**
   * Records one answer. `serverNow` is the server time (epoch ms) in the answer; `sentAt` and
   * `receivedAt` are `mono()` readings taken when the request left and the answer arrived.
   */
  observe(serverNow: number, sentAt: number, receivedAt: number): void {
    const rtt = Math.max(0, receivedAt - sentAt);
    const stale = this.best !== null && receivedAt - this.best.receivedAt > SAMPLE_TTL_MS;
    if (this.best === null || stale || rtt <= this.best.rtt) {
      // The server stamped the answer about half a round trip before it arrived.
      this.best = { rtt, serverAtReceive: serverNow + rtt / 2, receivedAt };
    }
  }

  /** The estimated server time, epoch ms. Zero until the first sample. */
  now(): number {
    if (this.best === null) return 0;
    const estimate = this.best.serverAtReceive + (this.mono() - this.best.receivedAt);
    this.last = Math.max(this.last, estimate);
    return this.last;
  }

  /** Ms until a server deadline (epoch ms), never below zero; null before the first sample. */
  timeLeft(deadline: number): number | null {
    return this.best === null ? null : Math.max(0, deadline - this.now());
  }
}

/**
 * The page-wide server clock for screens with no auction feed (the room page's schedule sheet).
 * `api/client.ts` feeds it from each response's `Date` header; the auction screen has its own,
 * fed from `serverNow`.
 */
export const pageClock = new ServerClock();

/** A lot clock in the mock's form: "0:22". Seconds round up, so it reads 0:00 only at the deadline. */
export function formatLotTime(ms: number): string {
  const secs = Math.ceil(Math.max(0, ms) / 1000);
  return `${Math.floor(secs / 60)}:${String(secs % 60).padStart(2, "0")}`;
}

const pad = (n: number) => String(n).padStart(2, "0");

/** The lobby countdown: "02:14:36" before the start, "+06:12" once it is past (Room `lobbyLate`). */
export function formatCountdown(ms: number): string {
  if (ms < 0) {
    const since = Math.floor(-ms / 1000);
    return `+${pad(Math.floor(since / 60))}:${pad(since % 60)}`;
  }
  const secs = Math.floor(ms / 1000);
  return `${pad(Math.floor(secs / 3600))}:${pad(Math.floor(secs / 60) % 60)}:${pad(secs % 60)}`;
}
