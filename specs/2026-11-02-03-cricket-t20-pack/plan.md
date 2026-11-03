---
status: final
---
# Plan: 2026-11-02-03-cricket-t20-pack

## Approach
Add `packs/cricket-t20.json` (R-1) in the spec 02 schemaVersion 1 shape. The spec also adds a small set of optional, sport-neutral fields to the framework (Q1=a), because the existing dtos have nowhere to hold the R-5/AC-6 retention data or the R-6/AC-7 stat unit, and the parser enables `FAIL_ON_UNKNOWN_PROPERTIES`. Packs without the new fields, the stub football pack among them, still load unchanged.
The rate rules and band edges are data only (Q2=a, Q3=a). Tests in `server/src/test` check every AC through `PackService`.

## Options considered
Each question lists its options in order; the chosen one is marked.
- Q1, missing fields:
  - a (chosen): add optional, sport-neutral fields in this spec. They are additive and old packs load unchanged.
  - b: a separate framework spec adds them first. This spec would be blocked until it merges.
  - c: stay data-only and move the rest of AC-6 and the stat unit to a later spec. The pack would be incomplete.
- Q2, band edges:
  - a (chosen): bands are half-open `[from, to)`, and a strict ">x" edge is written as x + 0.01 (rates are held to 2 dp).
  - b: per-band inclusivity flags. This is another framework change.
  - c: accept drift at the boundary values. This breaks R-9.
- Q3, rate source:
  - a (chosen): the supplied rate stat divided by `in-playing-xi` (always 1), with scale 1. This keeps R-6 "not derived".
  - b: derive the rate from runs/balls. That reverses the R-6 answer.
  - c: a single-stat banded rule, which is another framework change.

## Decisions (pass 2)
- Q1=a: the pm amends R-1, R-13, AC-18 and Out of scope, and adds a new AC: the stub football pack, which has none of the new fields, still loads. This is AC-19.
- Q2=a, Q3=a as above.
- rtmCards = 6 is accepted (IPL 2025 counts the 6 as retentions plus RTMs) unless PRD 7.7 says otherwise; T-7 checks this.

## Design
Framework (server/src/main/java/com/paddleup/packs/). All new fields are optional: a missing key reads as empty or null, and the existing JSON shapes and required keys do not change.
- dto/PresetDto:
  - `Retention` gains `Map<String,Integer> tagLimits` (empty when missing), `List<Slab> slabs` (empty when missing), `Integer rtmFromSeason` and `Boolean rtmCounterRaise`.
  - New record `PresetDto.Slab(String tag, List<Long> prices)`.
  - `PresetDto` gains `Integer poolSize`.
- dto/StatDto gains `String unit`, which may be null.
- Each changed record keeps a constructor with the old number of arguments, so existing callers and tests compile unchanged.
- util/PackParser: no shape change. The `PRESET`, `APPEARANCE` and stat `Shape`s keep their current required keys; Jackson binds the new optional keys.
- service/PackValidator gets two new checks:
  - `retention-unknown-tag`: every key of tagLimits and every slab tag must be in the tag list. Skipped when tags is partial.
  - `pool-size`: poolSize, when present, must be at least squadMax.
- Untouched: the stub football pack, the alt pack and every existing fixture (AC-17, AC-19).

Pack file `packs/cricket-t20.json`: schemaVersion 1, id `cricket-t20`, version `1.0.0`, no `adapterKey` (R-12, AC-15). Money is in integer lakh (R-3).
- roles: BAT Batter, BOWL Bowler, AR All-rounder, WK Wicket-keeper. tags: capped, uncapped, overseas (R-2, AC-2).
- restrictedCategories: `[{id: overseas-cap, label: Overseas players, tag: overseas, max: 8}]` (AC-3).
- presets[standard] (R-3, R-4, R-5, AC-4..AC-6):
  - Money: purse 12000, currencyCode INR, displayUnit `{label: Crore, divisor: 100}`.
  - Squad: squadMin 18, squadMax 25, roleMinimums `{WK:2, BAT:5, BOWL:5, AR:3}`.
  - incrementLadder: `[{0,5},{100,10},{200,20},{500,25}]`.
  - retention: `{maxRetentions 6, rtmCards 6, tagLimits {capped:5, uncapped:2}, slabs [{capped,[1800,1400,1100,1800,1400]},{uncapped,[400]}], rtmFromSeason 2, rtmCounterRaise true}`.
- presets[short-pool]: the same values plus poolSize 80, optional for the room.
- statSchema `{id, label, kind integer|decimal, unit, eventTypes?}` (R-6, AC-7). T-7 cross-checks this list against PRD 7.3:
  - in-playing-xi (flag)
  - Batting: runs, balls-faced, fours, sixes, duck (flag), strike-rate (decimal, runs per 100 balls)
  - Bowling: wickets (eventTypes bowled, lbw, caught, stumped, hit-wicket), balls-bowled (integer, balls), runs-conceded, maidens, economy (decimal, runs per over)
  - Fielding and other: catches, stumpings, run-outs (eventTypes direct, assisted), player-of-match (flag)
- appearanceRule `{stat: in-playing-xi, atLeast: 1}`. The glossary notes that an impact-player substitute who bats, bowls or fields counts (R-7).
- defaultRuleSet (R-8, R-9, AC-8..AC-13):
  - Batting: per-unit runs 1, fours 2, sixes 4. Separate threshold rules runs 50:10 and runs 100:20, so they stack. role-conditional [BAT, WK, AR] wrapping per-unit duck -5.
  - Strike rate: banded-rate strike-rate / in-playing-xi x1, minVolume balls-faced 10. Bands: [170.01,100000) +6, [150,170.01) +4, [130,150) +2, [60,70) -2, [0,60) -4.
  - Bowling: per-unit wickets 25, event-type-bonus wickets/lbw 8 and wickets/bowled 8. Separate threshold rules wickets 3:10 and wickets 5:20. Per-unit maidens 12.
  - Economy: banded-rate economy / in-playing-xi x1, minVolume balls-bowled 12. Bands: [0,5) +6, [5,6) +4, [6,7) +2, [10.01,12.01) -2, [12.01,1000) -4.
  - Fielding: per-unit catches 8; threshold catches 3:4 (applied once per match); per-unit stumpings 12; event-type-bonus run-outs/direct 12 and run-outs/assisted 6.
  - Other: per-unit player-of-match 25, appearance-bonus 4.
- terminology `{lineup: Playing XI, match: Match, competition: League}` (R-10).
- aiDomainContext (R-11, AC-14): one string in three parts, `## Head Coach knowledge`, `## Stat glossary` (one `- <stat-id>: ...` line per stat, rates stated at 2 dp) and `## Fit heuristics`. Concepts only, no player names.

Tests:
- server/src/test/java/com/paddleup/packs/CricketPackTest.java, an `ApplicationContextRunner` on `PacksConfig`. AC-1 uses the default dir `../packs`. AC-16 and AC-17 use a `@TempDir` copy; AC-16 raises WK to 6, so the minimums sum to 19 against squad min 18.
- Test-only helpers: the ladder lookup (last step with `from` <= bid) and the band lookup (from <= v < to).
- Framework tests: fixtures for `retention-unknown-tag` and `pool-size` under `server/src/test/resources/packs-invalid/`. The existing stub tests run unchanged (AC-19).

Docs: decision record docs/knowledge/decisions/2026-11-03-cricket-pack-conventions.md (at most 6 lines). It covers money in lakh, half-open bands with x + 0.01 strict edges, the rate-over-appearance denominator, stacked milestones as separate rules, and the optional retention and unit fields.

## Architecture changes
For human approval; I have not edited docs/knowledge/architecture.md. Add one line to the "Sport Packs" section:
- "Presets may carry optional per-tag retention limits and slabs, the RTM start season and counter-raise flag, and a pool size; stats may carry a unit. All are optional, opaque pack data, and packs without them load unchanged."

## Risks
- The framework change goes beyond the original data-only scope. Mitigation: the fields are optional and sport-neutral, the old-arity constructors stay, the stub pack is untouched, and the change is covered by AC-19 plus the pm's amendment.
- The scoring spec may read bands differently than `[from, to)`. Mitigation: the decision record and the test helper pin the convention, and the scoring spec cites it.
- The 2 dp strict edges need rates entered at 2 dp. Mitigation: the glossary says so, and FR-81 and spec 13 round to 2 dp.
- The stat list and rtmCards were set without reading PRD 7.3 and 7.7. Mitigation: T-7 checks both before the rules are written.
- `in-playing-xi` = 0 with the min volume met gives a division by zero. Mitigation: that data is inconsistent, and the scoring spec should guard against it.
