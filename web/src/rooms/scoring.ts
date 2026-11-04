import type { Room } from "../api/client";
import { humanise, labelFor } from "./format";

type Labels = Room["packLabels"];
type Rule = Record<string, unknown> & { type?: string; id?: string };

export interface ScoreRow {
  label: string;
  points: string;
}

export interface ScoreGroup {
  title: string;
  note: string;
  rows: ScoreRow[];
}

const kind = (t: unknown) => String(t ?? "").toLowerCase().replace(/[-_]/g, "");
const signed = (n: unknown) => {
  const v = Number(n ?? 0);
  return v < 0 ? `−${Math.abs(v)}` : `+${v}`;
};
const OPEN_TOP = 1000;

function band(from: number, to: number): string {
  if (from <= 0) return `Below ${to}`;
  if (to >= OPEN_TOP) return `Above ${Math.floor(from)}`;
  return `${from}–${to}`;
}

/**
 * The pack's scoring rules as read-only tables (Home `wizard:4`): one card of points per stat,
 * one of bonuses, and one per rate band. Every label comes from the room's pack snapshot, so no
 * sport words live in the UI.
 */
export function scoringGroups(rules: Rule[], labels: Labels): ScoreGroup[] {
  const stat = (id: unknown) => labelFor(labels.stats, String(id ?? ""));
  const lineup = labels.terminology?.lineup ?? "lineup";
  const perStat: ScoreGroup = { title: "Points per stat", note: "", rows: [] };
  const bonuses: ScoreGroup = { title: "Bonuses", note: "", rows: [] };
  const bands: ScoreGroup[] = [];

  function add(rule: Rule, suffix = "") {
    switch (kind(rule.type)) {
      case "perunit":
        perStat.rows.push({ label: `${stat(rule.stat)}${suffix}`, points: signed(rule.points) });
        break;
      case "thresholdbonus":
        for (const t of (rule.tiers as { atLeast?: number; points?: number }[]) ?? []) {
          bonuses.rows.push({ label: `${stat(rule.stat)} ${t.atLeast}+${suffix}`, points: signed(t.points) });
        }
        break;
      case "eventtypebonus":
        bonuses.rows.push({ label: `${stat(rule.stat)}: ${humanise(String(rule.eventType ?? "")).toLowerCase()}${suffix}`, points: signed(rule.points) });
        break;
      case "bandedrate": {
        const min = rule.minVolume as { stat?: string; atLeast?: number } | undefined;
        bands.push({
          title: `${stat(rule.numerator)}${suffix}`,
          note: min ? `min ${min.atLeast} ${stat(min.stat).toLowerCase()}` : "",
          rows: ((rule.bands as { from?: number; to?: number; points?: number }[]) ?? []).map((b) => ({
            label: band(b.from ?? 0, b.to ?? OPEN_TOP),
            points: signed(b.points),
          })),
        });
        break;
      }
      case "roleconditional": {
        const roles = ((rule.roles as string[]) ?? []).map((r) => labelFor(labels.roles, r)).join(", ");
        if (rule.rule) add(rule.rule as Rule, ` (${roles})`);
        break;
      }
      case "appearancebonus":
        bonuses.rows.push({ label: `In the ${lineup}`, points: signed(rule.points) });
        break;
      default:
        bonuses.rows.push({ label: humanise(rule.id ?? "rule"), points: signed(rule.points) });
    }
  }

  rules.forEach((r) => add(r));
  return [perStat, bonuses, ...bands].filter((g) => g.rows.length > 0);
}
