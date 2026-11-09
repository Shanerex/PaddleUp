import { afterEach, describe, expect, it, vi } from "vitest";
import { formatCountdown, formatLotTime, ServerClock } from "./clock";

/** A monotonic clock the test moves by hand, unrelated to Date. */
function mono(start = 1000) {
  let t = start;
  return { now: () => t, advance: (ms: number) => (t += ms) };
}

afterEach(() => vi.useRealTimers());

describe("ServerClock", () => {
  it("keeps time left within 1 s while the browser Date is skewed by plus and minus 5 min", () => {
    const server = Date.parse("2030-03-01T10:00:00Z");
    for (const skew of [5 * 60_000, -5 * 60_000]) {
      vi.useFakeTimers();
      vi.setSystemTime(server + skew);
      const m = mono();
      const clock = new ServerClock(m.now);
      // The server answered at `server`; the round trip took 80 ms.
      clock.observe(server, m.now(), m.now() + 80);
      m.advance(80);
      m.advance(3000);
      // The deadline is 10 s after the server's answer; 3 s have passed since.
      expect(Math.abs((clock.timeLeft(server + 10_000) ?? 0) - 7_000)).toBeLessThan(1000);
      vi.useRealTimers();
    }
  });

  it("applies a new deadline at once", () => {
    const m = mono();
    const clock = new ServerClock(m.now);
    clock.observe(1_000_000, m.now(), m.now());
    expect(clock.timeLeft(1_010_000)).toBe(10_000);
    m.advance(4000);
    expect(clock.timeLeft(1_010_000)).toBe(6000);
    // A bid pushes the deadline: the next read already shows it.
    expect(clock.timeLeft(1_014_000)).toBe(10_000);
  });

  it("never reports less than zero time left", () => {
    const m = mono();
    const clock = new ServerClock(m.now);
    clock.observe(5000, m.now(), m.now());
    m.advance(10_000);
    expect(clock.timeLeft(8000)).toBe(0);
  });

  it("prefers the sample with the smallest round trip", () => {
    const m = mono();
    const clock = new ServerClock(m.now);
    clock.observe(1_000_000, m.now(), m.now() + 400); // slow: the server time is uncertain
    m.advance(400);
    const slowEstimate = clock.now();
    m.advance(100);
    clock.observe(1_000_500, m.now() - 10, m.now()); // fast: 10 ms trip
    // Server time at this instant is 1_000_500 + 5 ms.
    expect(clock.now()).toBeGreaterThanOrEqual(slowEstimate);
    expect(clock.now()).toBe(1_000_505);
  });

  it("does not let a worse sample replace a better recent one", () => {
    const m = mono();
    const clock = new ServerClock(m.now);
    clock.observe(2_000_000, m.now() - 10, m.now()); // 10 ms trip
    m.advance(1000);
    clock.observe(2_001_900, m.now() - 800, m.now()); // 800 ms trip, off by far more than its worth
    expect(clock.now()).toBe(2_001_005);
  });

  it("takes a worse sample once the best one is old, so drift cannot build up", () => {
    const m = mono();
    const clock = new ServerClock(m.now);
    clock.observe(2_000_000, m.now() - 10, m.now());
    m.advance(60_000);
    clock.observe(2_060_020, m.now() - 100, m.now());
    expect(clock.now()).toBe(2_060_070);
  });

  it("never goes backwards when a better sample lands", () => {
    const m = mono();
    const clock = new ServerClock(m.now);
    clock.observe(3_000_000, m.now() - 200, m.now());
    m.advance(500);
    const before = clock.now();
    clock.observe(3_000_300, m.now() - 4, m.now());
    expect(clock.now()).toBeGreaterThanOrEqual(before);
  });

  it("has no estimate until it has seen the server", () => {
    const clock = new ServerClock(mono().now);
    expect(clock.ready).toBe(false);
    expect(clock.timeLeft(1)).toBeNull();
  });
});

describe("clock formatting", () => {
  it("shows a lot clock as m:ss, rounding up like the mock", () => {
    expect(formatLotTime(22_000)).toBe("0:22");
    expect(formatLotTime(21_001)).toBe("0:22");
    expect(formatLotTime(0)).toBe("0:00");
    expect(formatLotTime(65_000)).toBe("1:05");
  });

  it("shows the lobby countdown as hh:mm:ss and, past the start, as +mm:ss", () => {
    expect(formatCountdown((2 * 3600 + 14 * 60 + 36) * 1000)).toBe("02:14:36");
    expect(formatCountdown(0)).toBe("00:00:00");
    expect(formatCountdown(-(6 * 60 + 12) * 1000)).toBe("+06:12");
  });
});
