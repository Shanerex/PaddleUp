import { describe, expect, it } from "vitest";
import type { SheetField, StatSheet } from "../api/client";
import {
  buildSave,
  fixBanner,
  hintFor,
  matchChip,
  matchSub,
  matchTitle,
  overriddenFields,
  playerChip,
  playerCount,
  rangeHint,
  refusalOf,
  savedLine,
  shown,
  signed,
  validateText,
} from "./stat-sheet";

const field = (over: Partial<SheetField> = {}): SheetField => ({
  stat: "runs",
  eventType: "",
  label: "Runs",
  kind: "integer",
  min: 0,
  max: 120,
  input: "number",
  provider: null,
  manual: null,
  ...over,
});

const sheet = (fields: SheetField[]): StatSheet =>
  ({ fields, name: "A", role: "BAT", match: { n: 1 } }) as unknown as StatSheet;

describe("shown", () => {
  it("prefers what was typed, then the manual value, then the provider value", () => {
    expect(shown(field({ provider: 46, manual: 52 }), {})).toBe("52");
    expect(shown(field({ provider: 46 }), {})).toBe("46");
    expect(shown(field(), {})).toBe("");
    expect(shown(field({ provider: 46, manual: 52 }), { "runs|": "60" })).toBe("60");
    expect(shown(field({ provider: 46 }), { "runs|": "" })).toBe("");
  });
});

describe("validateText", () => {
  it("mirrors the server rules", () => {
    const f = field();
    expect(validateText(f, "52")).toBeNull();
    expect(validateText(f, "52.0000")).toBeNull();
    expect(validateText(f, " 7 ")).toBeNull();
    expect(validateText(f, "412")).toBe("above_max");
    expect(validateText(f, "-1")).toBe("below_min");
    expect(validateText(f, "2.5")).toBe("not_whole");
    expect(validateText(f, "1.23456")).toBe("too_many_decimals");
    expect(validateText(f, "abc")).toBe("not_a_number");
    expect(validateText(f, "1e3")).toBe("not_a_number");
    expect(validateText(field({ kind: "decimal" }), "2.5")).toBeNull();
  });
  it("names the range in a hint", () => {
    expect(rangeHint(field())).toBe("Whole number, 0 to 120.");
    expect(rangeHint(field({ kind: "decimal", max: 10 }))).toBe("Number, 0 to 10.");
    expect(hintFor(field(), "above_max")).toBe("Whole number, 0 to 120.");
    expect(hintFor(field({ kind: "decimal" }), "too_many_decimals")).toContain("4 decimals");
  });
});

describe("buildSave", () => {
  const s = sheet([field({ provider: 46 }), field({ stat: "fours", label: "Fours" }), field({ stat: "sixes", label: "Sixes" })]);
  it("sends only fields that were typed into and are not blank", () => {
    const built = buildSave(s, { "fours|": "6", "sixes|": "  " });
    expect(built).toEqual({ ok: true, body: { fields: [{ stat: "fours", eventType: "", value: 6 }] } });
  });
  it("does not turn a shown provider value into a manual one", () => {
    expect(buildSave(s, {})).toEqual({ ok: false, reason: "empty" });
  });
  it("names a non-number and sends nothing", () => {
    const built = buildSave(s, { "fours|": "six" });
    expect(built.ok).toBe(false);
    if (!built.ok && built.reason === "invalid") expect(built.hints["fours|"]).toBe("Whole number, 0 to 120.");
  });
});

describe("refusalOf", () => {
  const s = sheet([field(), field({ stat: "wickets", eventType: "lbw", label: "Wickets", max: 10 })]);
  it("maps the server's fields to hints and keeps the banner wording", () => {
    const refusal = refusalOf(s, {
      error: "invalid_stat_sheet",
      fields: [{ stat: "wickets", eventType: "lbw", min: 0, max: 10, reason: "above_max" }],
    });
    expect(refusal).toEqual({ banner: "1 field needs fixing. Nothing was saved.", hints: { "wickets|lbw": "Whole number, 0 to 10." } });
    expect(fixBanner(3)).toBe("3 fields need fixing. Nothing was saved.");
  });
  it("points an empty sheet to clear and ignores other answers", () => {
    expect(refusalOf(s, { error: "empty_sheet" })?.banner).toContain("Clear override");
    expect(refusalOf(s, { error: "unknown_match" })).toBeNull();
    expect(refusalOf(s, null)).toBeNull();
  });
});

describe("overriddenFields", () => {
  it("lists every field with a manual value, zero included", () => {
    const s = sheet([field({ manual: 0 }), field({ stat: "fours", provider: 3 }), field({ stat: "sixes", manual: 2 })]);
    expect(overriddenFields(s)).toEqual([
      { stat: "runs", eventType: "" },
      { stat: "sixes", eventType: "" },
    ]);
  });
});

describe("chips and lines", () => {
  it("labels the match chips", () => {
    expect(matchChip({ chip: "PROVIDER_MANUAL", manualPlayers: 2 })).toEqual({ label: "PROVIDER + 2 MANUAL", tone: "warn" });
    expect(matchChip({ chip: "NO_DATA", manualPlayers: 0 }).label).toBe("NO DATA");
    expect(matchChip({ chip: "UPCOMING", manualPlayers: 0 }).tone).toBe("quiet");
    expect(matchChip({ chip: "PROVIDER", manualPlayers: 0 }).label).toBe("PROVIDER");
    expect(matchChip({ chip: "MANUAL", manualPlayers: 1 }).label).toBe("MANUAL");
  });
  it("shows a manual value over the provider's and NOT ENTERED for none", () => {
    expect(playerChip("PROVIDER_MANUAL").label).toBe("MANUAL");
    expect(playerChip("PROVIDER").label).toBe("PROVIDER");
    expect(playerChip(null).label).toBe("NOT ENTERED");
  });
  it("titles a match with its number and label", () => {
    expect(matchTitle({ n: 21, label: "Eliminator" })).toBe("Match 21 · Eliminator");
    expect(matchTitle({ n: 4, label: null })).toBe("Match 4");
    expect(playerCount(6, 22)).toBe("6 of 22 players");
  });
  it("words the sub line by source", () => {
    const m = { chip: "PROVIDER_MANUAL", manualPlayers: 1, startsAt: "2027-04-17T14:00:00Z" } as never;
    expect(matchSub(m, () => "Wed 17 Apr")).toBe("Wed 17 Apr · provider data, 1 manual override");
    expect(matchSub({ ...(m as object), chip: "NO_DATA" } as never, () => "Thu 18 Apr")).toBe("Thu 18 Apr · no provider data. Everything here is manual.");
  });
});

describe("the saved toast", () => {
  it("states the points and the owner's change", () => {
    expect(signed(6)).toBe("+6");
    expect(signed(-6)).toBe("−6");
    expect(signed(0)).toBe("0");
    expect(savedLine("Sid Rao", 46, 52, { name: "Kochi Kites", delta: 6 })).toBe("Sid Rao 46 to 52 pts. Kochi Kites +6. It's in the audit log.");
    expect(savedLine("Sid Rao", null, 52, null)).toBe("Sid Rao no points to 52 pts. It's in the audit log.");
  });
});
