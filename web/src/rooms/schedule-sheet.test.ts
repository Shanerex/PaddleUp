import { describe, expect, it } from "vitest";
import { pickedInstant, untilLabel } from "./schedule-sheet";

describe("schedule sheet helpers", () => {
  it("reads the picked fields in the viewer's zone and returns one instant", () => {
    expect(pickedInstant("2031-02-03", "20:15")?.toISOString()).toBe(new Date(2031, 1, 3, 20, 15).toISOString());
  });

  it("returns null until both fields are valid", () => {
    expect(pickedInstant("", "20:15")).toBeNull();
    expect(pickedInstant("2031-02-03", "")).toBeNull();
    expect(pickedInstant("nope", "20:15")).toBeNull();
  });

  it("says how long until the start", () => {
    const min = 60_000;
    expect(untilLabel(12 * min)).toBe("in 12m");
    expect(untilLabel((3 * 60 + 14) * min)).toBe("in 3h 14m");
    expect(untilLabel((5 * 1440 + 22 * 60 + 5) * min)).toBe("in 5d 22h");
  });
});
