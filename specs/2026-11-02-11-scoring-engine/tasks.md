---
status: final
---
# Tasks: 2026-11-02-11-scoring-engine
## Tasks
Design refs: none (server + REST only; UI deferred to a follow-up spec).
- [x] T-1 (AC-1, AC-12): Add an optional `label` to every `RuleDto` variant, keeping back-compat constructors. Change the `ThresholdBonus` Javadoc to stacking. Add a parser test showing that a label round-trips and that its absence is accepted.
- [x] T-2 (AC-4, AC-7): Edit packs/cricket-t20.json in place at 1.0.0: A label on every rule. Strike-rate +6 [170,100000), +4 [150,170). Economy -2 [10,12), -4 [12,1000). Fix the existing tests that pin the old edges.
- [x] T-3 (AC-10): Add a nullable `appearanceRule` to `PackLabelsDto` and set it in `RoomConfigFactory.snapshot`. Test that a new room carries it.
- [x] T-4 (AC-10, AC-11): `RoomService.saveConfig` keeps the stored `scoringRules`. Test that a client-sent change to the rules is ignored.
- [x] T-5 (AC-13): `rooms.service.ScoringRoomPort.access` and `requireCommissioner` return 404 for a non-member and 403 for a non-Commissioner. Unit test.
- [x] T-6 (AC-10, AC-9): `ScoringRoomPort.ruleInputs` (with datasetId), `players` (with source player id and role) and `roomsOnDataset`, plus their dtos. Test that they come from the snapshot and the pool, not the live pack.
- [x] T-7 (AC-15, AC-17): `V10__scoring.sql` with the six tables (dataset-keyed matches and provider values, room-keyed overrides and points). Test that `scoring_rule_log` rejects UPDATE, DELETE and TRUNCATE.
- [x] T-8 (AC-5, AC-8): `PointsEvaluator` per-unit and appearance-bonus, with line shape and label fallback (label, then stat label, then id). Test that the output is identical on two calls.
- [x] T-9 (AC-3): Evaluator threshold-bonus. Every reached tier adds; 49 gives no bonus, 50 gives +10, 100 gives +10 and +20.
- [x] T-10 (AC-4): Evaluator banded-rate with minVolume and [from, to). Cover every boundary listed in AC-4, plus 9 against 10 balls and 11 against 12 balls.
- [x] T-11 (AC-5, AC-6): Evaluator role-conditional and event-type-bonus. Cover the duck by role, the non-dismissed 0, 3 wickets = 101, direct/assisted run-outs, and the XI +4 against non-XI 0.
- [x] T-12 (AC-1): A stub football rule set using all six primitives evaluates to the expected total and lines, with zero core changes.
- [x] T-13 (AC-2): `ScoringAgnosticTest`, a source literal scan against pack and stub ids, with a self-test on a planted literal.
- [x] T-14 (AC-7): Cricket golden fixtures (century batter, five-wicket bowler, keeper with a stumping and PoM) against hand-computed totals.
- [x] T-15 (AC-12): `RuleSetValidator`: unknown stat/role/event, overlapping bands, negative minVolume, more than 100 rules, label over 60 chars, and the bounds and duplicates checks. One test per violation; every violation is reported.
- [x] T-16 (AC-17, AC-18): `RuleSetRepository`: insert-if-absent, lock, version CAS, `frozen_at`. Add `RuleLogRepository` (append, list). Repository ITs.
- [x] T-17 (AC-9): `MatchStatsRepository` and `MatchStatsService`: `upsertMatch` (per dataset). `putProviderStats`, which replaces the dataset match's provider rows. `putOverride`, which is room-scoped and sparse.
- [x] T-18 (AC-9): Effective stat line per room player: the override, else the provider value via `source_player_id`. A Commissioner-added player gets manual values only. Unit test.
- [x] T-19 (AC-8): `PointsRepository` and `ScoringService.recompute` and `recomputeRoom`. Recomputing twice gives one row per key with the same total.
- [x] T-20 (AC-9, AC-16): IT with two rooms on one dataset: A provider import recomputes both rooms. A manual override in room A beats the provider and still does after a re-import and recompute. Room B keeps the provider value.
- [x] T-21 (AC-14): `FreezePolicy.freezesAt` = the earliest `starts_at` of the room's dataset matches; a room with no pool or dataset has none. Unit test.
- [x] T-22 (AC-10, AC-13): `RuleSetService.read` returns the pack-default view when no row exists. Test the status, version 0 and freezesAt.
- [x] T-23 (AC-11, AC-14): `RuleSetService.edit`: commissioner check, lock, CAS, freeze check, save as custom, log, recompute. A test 1 s before the start is accepted; at the start it gives 409 `frozen`.
- [x] T-24 (AC-11): `RuleSetService.revert` restores the snapshot exactly (rules NULL, status pack-default) and is logged.
- [x] T-25 (AC-15): `changeAfterFreeze`: 422 without the reason (sanitised, 1-500 chars) or the attestation, and 409 `not_frozen` pre-freeze. With both it saves one log entry (actor, UTC time, reason, before/after diff).
- [x] T-26 (AC-16): A post-freeze change recomputes every scored match in that room with the new set. The old set stays in the log, and another room on the same dataset is unchanged.
- [x] T-27 (AC-12, AC-13, AC-14): scoring exceptions and `ScoringExceptionHandler` (403, 404, 409 codes, 422 field errors). The 409 `frozen` path commits `frozen_at`.
- [x] T-28 (AC-11, AC-13): `RuleSetController`: GET/PUT rules, POST revert, POST post-freeze-change, GET log. MockMvc tests: a non-Commissioner gets 403 in each state; a member reads; a non-member gets 404.
- [x] T-29 (AC-13): `PointsController` GET `matches/{matchId}/points`. A member reads the breakdowns; a non-member gets 404.
- [x] T-30 (AC-14): IT with a stale tab: a version read before the start is rejected after it with 409 `frozen`.
- [x] T-31 (AC-18): Concurrent ITs: Two edits on the same version: one succeeds and one gets 409 `stale_version`. An edit racing the clock crossing the start is either fully applied or rejected.
- [x] T-32 (AC-17): A restart IT (fresh context on the same DB): the rule set and the frozen state are identical, and an edit still gives 409.
- [x] T-33 (AC-10): IT: after a pack change in the registry, the room's rules and recomputed points are unchanged.
- [x] T-34 (AC-11): Regenerate `web/src/api/generated.ts` from springdoc and fix any web type errors. No UI changes.
