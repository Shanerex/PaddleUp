import { describe, expect, it } from "vitest";
import type { Room } from "../api/client";
import { scoringGroups } from "./scoring";

const labels = {
  packId: "p",
  packVersion: "1",
  roles: [{ id: "A", label: "Attacker" }],
  tags: [],
  restrictedCategories: [],
  stats: [
    { id: "goals", label: "Goals" },
    { id: "rate", label: "Rate" },
    { id: "mins", label: "Minutes" },
  ],
  terminology: { lineup: "Starting XI" },
} as unknown as Room["packLabels"];

describe("scoringGroups", () => {
  it("groups every rule type under its stat with labels from the pack", () => {
    const groups = scoringGroups(
      [
        { type: "per-unit", id: "g", stat: "goals", points: 5 },
        { type: "threshold-bonus", id: "h", stat: "goals", tiers: [{ atLeast: 3, points: 10 }] },
        { type: "event-type-bonus", id: "p", stat: "goals", eventType: "penalty", points: -1 },
        { type: "role-conditional", id: "r", roles: ["A"], rule: { type: "per-unit", id: "x", stat: "goals", points: 1 } },
        { type: "banded-rate", id: "b", numerator: "rate", minVolume: { stat: "mins", atLeast: 10 }, bands: [{ from: 0, to: 5, points: 2 }, { from: 9.01, to: 100000, points: -2 }] },
        { type: "appearance-bonus", id: "a", points: 4 },
      ],
      labels,
    );
    expect(groups).toEqual([
      {
        title: "Points per stat",
        note: "",
        rows: [
          { label: "Goals", points: "+5" },
          { label: "Goals (Attacker)", points: "+1" },
        ],
      },
      {
        title: "Bonuses",
        note: "",
        rows: [
          { label: "Goals 3+", points: "+10" },
          { label: "Goals: penalty", points: "−1" },
          { label: "In the Starting XI", points: "+4" },
        ],
      },
      { title: "Rate", note: "min 10 minutes", rows: [{ label: "Below 5", points: "+2" }, { label: "Above 9", points: "−2" }] },
    ]);
  });
});

const rules = [
  { type: "per-unit", id: "g", stat: "goals", points: 5 },
  { type: "threshold-bonus", id: "h", stat: "goals", tiers: [{ atLeast: 3, points: 10 }] },
  { type: "per-unit", id: "s", stat: "saves", points: 1 },
  { type: "banded-rate", id: "b", numerator: "rate", minVolume: { stat: "mins", atLeast: 10 }, bands: [{ from: 0, to: 5, points: 2 }] },
  { type: "role-conditional", id: "r", roles: ["A"], rule: { type: "per-unit", id: "x", stat: "saves", points: -1 } },
  { type: "appearance-bonus", id: "a", points: 4 },
];

describe("scoringGroups with stat groups", () => {
  const grouped = {
    ...labels,
    stats: [
      { id: "goals", label: "Goals", group: "Attack" },
      { id: "saves", label: "Saves", group: "Defence" },
      { id: "rate", label: "Rate", group: "Attack" },
      { id: "mins", label: "Minutes" },
    ],
  } as unknown as Room["packLabels"];

  it("groups rows by the pack's stat group in first-appearance order", () => {
    const groups = scoringGroups(rules, grouped);
    expect(groups.map((g) => g.title)).toEqual(["Attack", "Defence", "Bonuses", "Rate"]);
    expect(groups[0].rows).toEqual([
      { label: "Goals", points: "+5" },
      { label: "Goals 3+", points: "+10" },
    ]);
    expect(groups[1].rows).toEqual([
      { label: "Saves", points: "+1" },
      { label: "Saves (Attacker)", points: "−1" },
    ]);
    expect(groups[2].rows).toEqual([{ label: "In the Starting XI", points: "+4" }]);
  });

  it("keeps today's groups when no stat has a group", () => {
    expect(scoringGroups(rules, labels).map((g) => g.title)).toEqual(["Points per stat", "Bonuses", "Rate"]);
  });

  it("puts a rule whose stat has no group in the fallback cards", () => {
    const some = { ...grouped, stats: [{ id: "goals", label: "Goals", group: "Attack" }, { id: "saves", label: "Saves" }, { id: "rate", label: "Rate" }, { id: "mins", label: "Minutes" }] } as unknown as Room["packLabels"];
    const titles = scoringGroups(rules, some).map((g) => g.title);
    expect(titles).toEqual(["Attack", "Points per stat", "Bonuses", "Rate"]);
  });
});
