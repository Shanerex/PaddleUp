---
status: final
---
# Tasks: 2026-11-02-03-cricket-t20-pack

## Tasks
- [x] T-1 (AC-17, AC-19): Add the optional `Retention.tagLimits/slabs/rtmFromSeason/rtmCounterRaise`, `PresetDto.Slab`, `PresetDto.poolSize` and `StatDto.unit`, keeping the old-arity constructors. Test: the stub football pack, unchanged, still loads and reads empty or null for the new fields; existing pack tests stay green.
- [x] T-2 (AC-6): Add the PackValidator checks `retention-unknown-tag` and `pool-size`, each with one invalid fixture under `server/src/test/resources/packs-invalid/` and a test naming the check and the offending id.
- [x] T-3 (AC-1, AC-15): Create `packs/cricket-t20.json` with the header, no adapterKey, and the roles, tags and restricted category. Test: the app with the default dir lists `cricket-t20` 1.0.0, manual-entry only.
- [x] T-4 (AC-2, AC-3): Tests: roles are exactly BAT/BOWL/AR/WK, each with a label; tags are exactly capped/uncapped/overseas; the only category is overseas, max 8, on tag overseas.
- [x] T-5 (AC-4, AC-5): Add the standard preset: purse, currency, display unit, squad, minimums and the increment ladder. Tests: the preset values, and the next-increment table at 90/100/190/200/490/500L, using a test-only ladder helper.
- [x] T-6 (AC-6): Add retention to the standard preset and the short-pool preset (poolSize 80). Test: 6/5/2, slabs 1800/1400/1100/1800/1400 and 400 lakh, RTM from season 2 with counter-raise, short pool 80.
- [x] T-7 (AC-7): Cross-check the stat list against PRD 7.3 and rtmCards against PRD 7.7. Then add statSchema (each entry with a kind and a unit; balls-bowled is an integer) and appearanceRule in-playing-xi. Test: every 7.3 id is present with a kind and a unit, and the appearance stat is in the schema.
- [x] T-8 (AC-8, AC-13): Add the batting rules, including the stacked 50/100 rules and the role-conditional duck. Test: each rule's type and values, and that the duck applies to BAT/WK/AR only.
- [x] T-9 (AC-9): Add the strike-rate banded rule, min 10 balls. Test with a test-only `[from, to)` band helper: 170.01 gets +6, 170 gets +4, 150 gets +4, 149.99 gets +2, 69.99 gets -2, 59.99 gets -4 (never -6), and 9 balls gets no band.
- [x] T-10 (AC-10, AC-13): Add the bowling rules: wickets, the LBW and bowled event bonuses, the stacked 3 and 5 wicket rules, and maidens. Test the values and types.
- [x] T-11 (AC-11): Add the economy banded rule, min 12 balls. Test: 4.99 gets +6, 5 gets +4, 6 gets +2, 10 gets nothing, 10.01 gets -2, 12 gets -2, 12.01 gets -4 (never -6), and 11 balls gets no band.
- [x] T-12 (AC-12, AC-13): Add the fielding and other rules: catch, the 3+ catch tier, stumping, direct and assisted run-out, Player of the Match, and appearance. Test the values, and that every rule is one of the six primitives.
- [x] T-13 (AC-14): Add terminology and the aiDomainContext with three `##` parts and one glossary line per stat id. Test: the parts are non-empty, the glossary covers every stat, and none of 20 listed player names appears.
- [x] T-14 (AC-16): Test: a temp-dir copy with WK raised to 6 fails startup naming `cricket-t20.json`, `cricket-t20`, 19 and 18.
- [x] T-15 (AC-17): Test: cricket and stub-football in one temp dir both register under distinct ids.
- [x] T-16 (AC-18): Add the decision record `docs/knowledge/decisions/2026-11-03-cricket-pack-conventions.md`. Run `make lint`, including check-agnostic. Confirm `git diff main --stat` touches only the packs dto and validator files from T-1 and T-2 under server/src/main, as the amended AC-18 allows.
