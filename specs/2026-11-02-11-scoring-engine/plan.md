---
status: final
---
# Plan: 2026-11-02-11-scoring-engine

## Approach
- A pure evaluator in `scoring.service` turns (rule set, appearance rule, role, effective stat line) into a total plus lines (R-1, R-2, R-5). It is tested first against the stub football pack and the cricket golden fixtures (AC-1, AC-3..AC-7).
- The room's pack-default snapshot already exists: `RoomConfigDto.scoringRules`, copied at creation and pinned to `rooms.pack_version` (R-8). It becomes read-only in rooms. Scoring keeps the room's custom set, its version, the persisted freeze and an append-only change log in its own tables (R-9..R-11).
- Matches and provider stats are stored once per season dataset and shared by every room on that dataset. Manual stats are sparse per-room overrides. The effective value is the room override if present, else the shared provider value (R-7).
- Freeze = the server `Clock` at or after the earliest `starts_at` among the room's dataset matches. It is checked under the room's rule-set row lock on every write and persisted per room as `frozen_at` once seen (R-10, AC-14, AC-17, AC-18).
- Recompute replaces `scoring_points` rows per (room, match, player) (R-6). Every successful rule change recomputes that room's scored matches in the same transaction (AC-16).
- Server and REST only; the fixture and stat writes are a service API (no REST) for the ingestion spec to build on.

## Options considered
1. **Chosen:** scoring owns the edited set, freeze, log and the shared match/stat data. The rooms config `scoringRules` stays as the immutable pack-default snapshot. Trade-off: two homes for rules, but rooms never needs to know about fixtures or the freeze.
2. Rules stay editable in the rooms config and rooms enforces the freeze and audit. Trade-off: rooms would need fixture data from scoring, which makes a module cycle, and `saveConfig` would get freeze/attestation paths.
3. Event-sourced rule set: the log is the truth and the current set is replayed. Trade-off: a stronger history, but more code than a CAS row plus an append-only log needs; no replay consumer exists yet.

## Design
- packs (dto only):
  - Every `RuleDto` variant gets an optional `label`, with back-compat constructors like `PackDto`. Label fallback: the stat label, then the rule id.
  - Change the `ThresholdBonus` Javadoc to "every tier reached adds" (R-3; the cricket single-tier rules are unaffected).
- packs/cricket-t20.json, edited in place at 1.0.0 (no version bump):
  - Labels on every rule.
  - Strike-rate +6 band [170,100000), +4 band [150,170).
  - Economy -2 band [10,12), -4 band [12,1000), per R-4/AC-4.
- rooms:
  - `PackLabelsDto` gains a nullable `appearanceRule`, set by `RoomConfigFactory.snapshot`.
  - `RoomService.saveConfig` keeps the stored `scoringRules` whatever the client sends.
  - New `rooms.service.ScoringRoomPort`:
    - `access(roomId, user)`: reuses `RoomAccessDto`; 404 for a non-member.
    - `requireCommissioner(roomId, user)`: 403.
    - `ruleInputs(roomId)` -> `rooms.dto.ScoringInputsDto` (packId, packVersion, roles, stats, default `RuleSetDto`, appearanceRule, datasetId nullable from `room_pools`).
    - `players(roomId)` -> `rooms.dto.ScoringPlayerDto` (pool player id, source player id nullable from `room_pool_players.source_player_id`, role).
    - `roomsOnDataset(datasetId)` -> room ids.
- `V10__scoring.sql`:
  - `scoring_rule_sets`: room_id PK, `rules` jsonb (NULL = pack default), version int, frozen_at, updated_at, updated_by.
  - `scoring_rule_log`: append-only by trigger. Columns: kind edit|revert|post_freeze_change, actor, occurred_at, reason, attested, before, after, diff.
  - `scoring_matches`: PK dataset_id (FK season_datasets) + match_id, starts_at.
  - `scoring_stat_values` (provider): PK dataset_id, match_id, season_player_id (FK season_players), stat, event_type ('' = none); value numeric.
  - `scoring_stat_overrides` (manual): PK room_id, match_id, pool player id, stat, event_type; value numeric. Sparse.
  - `scoring_points`: PK room, match, pool player; total numeric, lines jsonb, rule_set_version, computed_at.
- Player mapping: a room player with a source player id reads the shared provider values plus its own overrides. A Commissioner-added player (no `source_player_id`) can only take manual stats.
- scoring.service:
  - `PointsEvaluator`: pure, no Spring state. Line = (ruleId, label, points), emitted only when the rule fires with non-zero points. A role-conditional reports its outer id and label. Missing stats count as 0. A missing rate gives no band.
  - `RuleSetValidator`: every violation, never just the first. Checks unknown stat/role/event; overlapping or from>=to bands; negative minVolume; more than 100 rules; label or id over 60 chars (sanitised); duplicate ids; nesting over 3; tiers/bands over 50; the same number bounds as the rooms validator.
  - `FreezePolicy`: `freezesAt(datasetId)` = min `starts_at`. A room with no pool or dataset has no freeze instant.
  - `RuleSetService`:
    - `read`: any member.
    - `edit(version, rules)`, `revert(version)`: pre-freeze only.
    - `changeAfterFreeze(version, rules, reason, attested)`.
    - Each write: commissioner check, then insert-if-absent and lock the row, then the stale-version check, then the freeze check, then save, log, recompute the room.
  - `MatchStatsService`: service API only.
    - `upsertMatch(datasetId, matchId, startsAt)`.
    - `putProviderStats(datasetId, matchId, lines)`: replaces that match's provider rows, then recomputes the match for every room on the dataset (`roomsOnDataset`).
    - `putOverride(room, matchId, poolPlayerId, stat, eventType, value)`: then recomputes that room and match.
  - `ScoringService`: `recompute(room, match)`, `recomputeRoom(room)` and `points(room, match)`.
- scoring.controller (all under `/api/rooms/{roomId}/scoring`):
  - `GET rules` -> `RuleSetViewDto` (rules, status pack-default|custom, version, packVersion, frozen, freezesAt).
  - `PUT rules`
  - `POST rules/revert`
  - `POST rules/post-freeze-change`
  - `GET rules/log`
  - `GET matches/{matchId}/points`
- scoring.exception:
  - `RuleSetConflictException` (409 `frozen` | `stale_version` | `not_frozen`) and `InvalidRuleSetException` (422 field errors in the rooms `Violation` shape).
  - `ScoringExceptionHandler` scoped to scoring.controller. It also maps the rooms 403 and 404.
  - The 409 `frozen` path commits `frozen_at` (`noRollbackFor`).
- Tests:
  - `ScoringAgnosticTest` (AC-2) scans `scoring/**` main sources for string literals equal to any pack, stat, role or event id from `packs/*.json` and the stub pack. It has a self-test.
  - Testcontainers ITs cover AC-8..AC-18 with a mutable `Clock`.
- web: only regenerate `web/src/api/generated.ts` (the optional `label`). No UI.

## Architecture changes
Patch to docs/knowledge/architecture.md (human approves):
- Source line: add "Scoring from specs/2026-11-02-11-scoring-engine/plan.md".
- Rooms: add "Config `scoringRules` is the immutable pack-default snapshot taken at creation (with `appearanceRule` in the pack snapshot); `saveConfig` never changes it. Scoring reads a room only through `rooms.service.ScoringRoomPort`."
- New section "## Scoring":
  - The room's edited rule set, version (CAS, 409 `stale_version`), `frozen_at` and the append-only `scoring_rule_log` live in scoring; no row means pack default.
  - Matches and provider stats are stored once per season dataset and shared across rooms. Manual stats are sparse per-room overrides (override beats provider). Room players map to dataset players through `source_player_id`; added players take manual stats only.
  - Freeze = server Clock >= the earliest `starts_at` of the room's dataset matches. It is checked under the room row lock on every write and persisted per room once seen. After the freeze the only path is a reasoned, attested, logged change that recomputes the room.
  - `PointsEvaluator` is pure. `scoring_points` is replaced per (room, match, player).
- Decision record at ship: supersede the 2026-11-03 cricket-pack "> x is x + 0.01" convention with "boundary takes the higher band" (R-4).

## Risks
- The band-edge fix in pack 1.0.0 breaks existing pack/room tests that pin 170.01 or 10.01/12.01. Mitigation: update them in T-2. The app is not live, so no stored rooms need migrating.
- A provider import fans out to every room on the dataset, synchronously and in one transaction (10k+ rooms is slow). Mitigation: deferred to the provider-ingestion spec (batch/async fan-out); this spec exposes only per-room and per-match recompute.
- Legacy rooms have no `appearanceRule` in their snapshot. Mitigation: fall back to the live pack's rule. This is logged once at WARN and documented.
- A pool reset can change a room's dataset before the auction. Mitigation: the freeze and the matches are read through the port on every call, and once persisted, `frozen_at` holds.
- Rules are validated in two places (rooms validator, scoring validator). Mitigation: rooms' copy is now only for the immutable default; extracting a shared checker is deferred.
- Fixture rescheduling after the freeze: `frozen_at` is persisted, so the room stays frozen.
