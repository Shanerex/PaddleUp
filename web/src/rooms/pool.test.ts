import { describe, expect, it } from "vitest";
import { ApiError, type Pool, type PoolPlayer } from "../api/client";
import {
  classifyPoolError,
  formatPrice,
  hasNewerData,
  unfitReason,
  originFlag,
  parsePrice,
  roleCounts,
  roleLabel,
  roleSlot,
  sourceLine,
  tagText,
  type PackLabels,
} from "./pool";

const labels = {
  packId: "p",
  packVersion: "1",
  packName: "Pack Name",
  roles: [
    { id: "R1", label: "First" },
    { id: "R2", label: "Second" },
    { id: "R3", label: "Third" },
    { id: "R4", label: "Fourth" },
    { id: "R5", label: "Fifth" },
  ],
  tags: [
    { id: "a", label: "Alpha" },
    { id: "b", label: "Beta" },
  ],
  restrictedCategories: [],
  stats: [],
  terminology: {},
} as unknown as PackLabels;

const money = { currencyCode: "INR", displayUnit: { label: "Crore", divisor: 100 } };

function player(role: string, origin: PoolPlayer["origin"] = "source"): PoolPlayer {
  return { id: role + origin, name: role, country: "C", role, tags: [], basePrice: 20, set: "S", origin } as PoolPlayer;
}

function pool(over: Partial<Pool> = {}): Pool {
  return {
    hasPool: true,
    version: 3,
    datasetVersion: 3,
    latestDatasetVersion: 3,
    latestFit: "fits",
    editCount: 5,
    canEdit: true,
    players: [player("R1"), player("R1"), player("R2")],
    sets: [],
    ...over,
  } as Pool;
}

describe("pack labels", () => {
  it("reads role and tag labels from the room's pack snapshot and falls back to the key", () => {
    expect(roleLabel(labels, "R2")).toBe("Second");
    expect(roleLabel(labels, "GONE")).toBe("GONE");
    expect(tagText(labels, ["a", "b", "zz"])).toBe("Alpha, Beta, zz");
  });

  it("gives each role a colour slot by pack position, wrapping after four", () => {
    expect([1, 2, 3, 4, 5].map((n) => roleSlot(labels, `R${n}`))).toEqual([1, 2, 3, 4, 1]);
  });

  it("counts players per role in pack order, including roles with none", () => {
    const counts = roleCounts(labels, pool().players);
    expect(counts.map((c) => [c.key, c.count])).toEqual([["R1", 2], ["R2", 1], ["R3", 0], ["R4", 0], ["R5", 0]]);
  });
});

describe("flags and source line", () => {
  it("flags edited and added players only", () => {
    expect(originFlag("source")).toBeNull();
    expect(originFlag("edited")).toBe("EDITED");
    expect(originFlag("added")).toBe("ADDED");
  });

  it("names the pack, data version, players and edits for the Commissioner", () => {
    expect(sourceLine(pool(), labels, { commish: true, locked: false })).toBe("Pack Name · season data v3 · 3 players · 5 edits");
    expect(sourceLine(pool({ editCount: 1 }), labels, { commish: true, locked: false })).toContain("1 edit");
    expect(sourceLine(pool(), labels, { commish: false, locked: false })).toBe("Pack Name · season data v3 · 3 players");
    expect(sourceLine(pool(), labels, { commish: false, locked: true })).toBe("Pack Name · season data v3 · 3 players · locked");
    expect(sourceLine(pool({ hasPool: false }), labels, { commish: true, locked: false })).toBe("Pack Name · no season data yet");
  });

  it("notices a newer dataset only when the copy is older", () => {
    expect(hasNewerData(pool())).toBe(false);
    expect(hasNewerData(pool({ latestDatasetVersion: 4 }))).toBe(true);
    expect(hasNewerData(pool({ hasPool: false, latestDatasetVersion: 4, datasetVersion: null }))).toBe(false);
  });

  it("does not offer newer data this room cannot load, and says why", () => {
    expect(hasNewerData(pool({ latestDatasetVersion: 4, latestFit: "price" }))).toBe(false);
    expect(hasNewerData(pool({ latestDatasetVersion: 4, latestFit: "pack" }))).toBe(false);
    expect(unfitReason(pool({ latestDatasetVersion: 4, latestFit: "price" }))).toBe("price");
    expect(unfitReason(pool({ latestDatasetVersion: 4, latestFit: "pack" }))).toBe("pack");
    expect(unfitReason(pool({ latestDatasetVersion: null, latestFit: "none" }))).toBeNull();
    expect(unfitReason(pool({ latestDatasetVersion: 4, latestFit: "fits" }))).toBeNull();
    expect(unfitReason(pool({ latestDatasetVersion: 4, latestFit: null }))).toBeNull();
  });
});

describe("money", () => {
  it("shows a price in the room's currency and unit", () => {
    expect(formatPrice(20, money)).toBe("₹0.2 Crore");
  });

  it("reads a typed price back to whole stored units", () => {
    expect(parsePrice("₹0.2 Crore", money)).toBe(20);
    expect(parsePrice("1.53", money)).toBe(153);
    expect(parsePrice(" ₹ 1.5 crore ", money)).toBe(150);
    expect(parsePrice("0.005", money)).toBeNull();
    expect(parsePrice("abc", money)).toBeNull();
    expect(parsePrice("", money)).toBeNull();
    expect(parsePrice("-1", money)).toBeNull();
  });
});

describe("classifyPoolError", () => {
  const e = (status: number, body: unknown) => new ApiError(status, body);

  it("maps the two 409 codes to the stale and locked banners", () => {
    expect(classifyPoolError(e(409, { error: "stale_version" }))).toEqual({ kind: "stale" });
    expect(classifyPoolError(e(409, { error: "room_locked" }))).toEqual({ kind: "locked" });
  });

  it("maps a 422 to field errors keyed by sheet field, with the generic price copy", () => {
    const failure = classifyPoolError(
      e(422, {
        error: "invalid_player",
        violations: [
          { field: "base_price", message: "That base price isn't allowed in Pack Name." },
          { field: "role", message: "Not a Pack Name role." },
          { field: "provider_id", message: "Bad id." },
        ],
      }),
    );
    expect(failure).toEqual({
      kind: "fields",
      fields: {
        price: "That base price isn't allowed in Pack Name. Nothing was saved.",
        role: "Not a Pack Name role.",
        providerId: "Bad id.",
      },
    });
  });

  it("treats a missing room as gone and other failures as a message", () => {
    expect(classifyPoolError(e(404, { error: "room_not_found" }))).toEqual({ kind: "gone" });
    expect(classifyPoolError(e(403, { error: "forbidden" }))).toMatchObject({ kind: "other", refetch: true });
    expect(classifyPoolError(e(409, { error: "pack_mismatch" }))).toMatchObject({ kind: "other", refetch: true, message: expect.stringMatching(/roles or tags this room's pack doesn't have/) });
    expect(classifyPoolError(e(409, { error: "price_mismatch" }))).toMatchObject({ kind: "other", refetch: true, message: expect.stringMatching(/purse or price steps don't allow/) });
    expect(classifyPoolError(e(500, null))).toMatchObject({ kind: "other", refetch: false });
    expect(classifyPoolError(new Error("offline"))).toMatchObject({ kind: "other", refetch: false });
  });
});
