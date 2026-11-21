---
status: final
---
# Plan: 2026-11-02-12-manual-stat-entry

## Approach
- A new scoring service `ManualStatsService` replaces the single-field `MatchStatsService.putOverride`. It takes a multi-field save and a multi-field clear for one player and match (R-1, R-2, R-7) behind Commissioner-only REST routes (R-5, AC-10).
- Save is a per-field upsert, never a replace of the set: each filled field upserts its own override, a blank or omitted field stays as it is, and overrides not named in the request are untouched (R-2). Clear deletes only the named fields' overrides (R-7). "Atomic" means all named fields commit or none do.
- Each write takes the room's rule row lock first (existing order: rule row, then points). It then reads the player's current overrides, validates every named field (R-3, R-4, R-8), upserts or deletes field by field, appends one log row per changed field and recomputes the match in the same transaction (R-9, R-11, AC-15, AC-16).
- Manual beats provider stays in `EffectiveStats` (R-6). Re-ingest and `recomputeRoom` already read overrides, so tests cover AC-3 and AC-5 with no new code.
- Pack stat schema gains optional `min` and `max` (R-3, AC-1); no shipped pack uses them yet (Q-3 B). Owners come from a new read-only auction port (R-13, AC-20).
- Web: one new route `rooms/[roomId]/stats` built to 9f-9j, opened from the commishLive card (R-12, R-13).

## Options considered
- A (chosen): per-field override rows (existing table) plus a separate append-only `scoring_override_log`; one write path for save and clear. Small schema delta, log immutable by trigger.
- B: each saved sheet as one JSONB version row, overrides derived from the latest. Simple history, but breaks per-field upsert and clear (R-2, R-7) and the `EffectiveStats` merge.
- C: reuse `scoring_rule_log` with a new kind. One log, but its before/after columns are whole rule sets; mixing shapes worsens both reads.

## Design
Schema `V12__manual_stat_entry.sql` (new migration; V10 untouched):
- `scoring_override_log` (seq identity, room_id, actor, occurred_at, match_id, pool_player_id, stat, event_type, old_value numeric NULL, new_value numeric NULL); triggers reuse `scoring_log_reject_change()` for UPDATE, DELETE, TRUNCATE.
- `scoring_matches.label text NULL` (Q-1 A). `MatchStatsService.upsertMatch` takes an optional label; the UI shows "Match n · label", else "Match n" (n = rank by `starts_at`, then `match_id`).

Packs:
- `packs.dto.StatDto` gets `BigDecimal min, max` (nullable, optional in the JSON; old room snapshots read them as null). `PackValidator` checks 0 <= min <= max <= 1,000,000, with whole bounds for integer stats. No pack file changes and no version bump (Q-3 B). Cricket flag stats render as counts; yes/no renders only for an integer stat with max 1, tested with a fixture pack (AC-1).

Scoring (`com.paddleup.scoring`):
- `util/StatValues`: normalise (stripTrailingZeros, scale >= 0) and the range check against the stat's limits (default 0..1,000,000), at most 4 decimals, whole for integer stats (R-3, R-8, AC-6, AC-7).
- `util/SheetFields`: builds the field list from `ScoringInputsDto.stats()`, one field per stat or one per event type (R-1, AC-1).
- `service/ManualStatsService`: `sheet`, `save`, `clear`, `matches`, `matchPlayers`, `overrideLog`. Refusals:
  - no dataset or unknown match: 404 `unknown_match`
  - `starts_at > now`: 409 `match_not_started` (R-4, AC-8)
  - player not in pool: 404 `unknown_player` (AC-9)
  - no filled field: 422 `empty_sheet`, pointing to clear (AC-13)
  - any bad field: 422 `invalid_stat_sheet` with `fields[{stat, eventType, min, max, reason}]`; nothing is written (AC-6)

  Values equal to the stored override are skipped, so a change-free save writes no log row (AC-4, AC-7).
- `ScoringService`: package-private `lockRoom(roomId)` (rule row lock) and `evaluate(roomId, matchId, player, withOverrides)` for provider-only points (R-10, AC-14). `putProviderStats` normalises values (R-8).
- `MatchStatsService.putOverride` is removed and its tests move to `ManualStatsService` (closes CR-4, CR-5).
- `repository/MatchStatsRepository`: `overridesFor(room, match, player)`, `deleteOverrides(room, match, player, keys)`, `matchStart`, `matchSummaries(room, dataset)` (provider count and override player count per match, one query). New `repository/OverrideLogRepository` (append, page by seq).
- `matchPlayers` (Q-2 B) lists the whole pool. It sorts players with a value first and returns compact rows (id, name, role, owner, chip, total; no lines). It reads with set queries only: pool, owners, stored points, provider player ids and override player ids, each once with no per-player query. Header: "n of N players" (as in the mock). No search, because the mock has none.
- `controller/ManualStatsController` under `/api/rooms/{roomId}/stat-entry`:
  - `GET matches`
  - `GET matches/{matchId}/players`
  - `GET matches/{matchId}/players/{poolPlayerId}`
  - `PUT .../players/{poolPlayerId}` with `{fields:[{stat, eventType, value}]}` (upsert of the named fields)
  - `POST .../players/{poolPlayerId}/clear` with `{fields:[{stat, eventType}]}`

  Save and clear return `{before, after, providerOnly, franchise:{id, delta}|null, sheet}` (R-10, AC-14, AC-20). `GET /api/rooms/{roomId}/scoring/override-log?after&limit` is open to any member (R-11).
- Chips (R-12, AC-17), first match wins:
  - UPCOMING: `starts_at > now`
  - NO DATA: no provider row and no override
  - MANUAL: overrides only
  - PROVIDER + n MANUAL: provider rows plus overrides, n = players with an override
  - PROVIDER: otherwise
- dtos: `StatSheetViewDto`, `SheetFieldDto` (stat, eventType, label, kind, min, max, input number|yes_no, provider, manual), `SaveSheetRequest`, `ClearFieldsRequest`, `SheetResultDto`, `StatMatchDto` (n, label, startsAt, chip), `StatMatchPlayerDto`, `OverrideLogEntryDto`, `SheetFieldErrorDto`. Exceptions: `InvalidStatSheetException` (422) and `MatchRefusedException` (404/409), mapped in `ScoringExceptionHandler`.

Rooms and auction (ports only):
- `rooms.service.ScoringRoomPort.players` adds `name`. New `franchises(roomId)` returns `ScoringFranchiseDto(id, name, shortName, colour)`.
- New `auction.service.AuctionResultsPort.owners(roomId)` returns `Map<UUID poolPlayerId, UUID franchiseId>`, built from the replayed log's squads joined to `auction_lots.pool_player_id`; it is empty before go-live. Franchise delta = the player's delta when owned, since points are per player (AC-20).

Web (`web/src`):
- `rooms/stat-sheet.ts`: field model from the dto, local mirror of the range rules for hints, request building (filled fields only) and error mapping that keeps typed values (AC-18).
- `app/rooms/[roomId]/stats/page.tsx` with `stat-entry-view.tsx`, `match-list.tsx`, `player-list.tsx`, `sheet-form.tsx`, `clear-confirm.tsx` and `stats.module.css`; saved toast per 9i; numbers in DM Mono tabular. The player list renders the full pool without layout shift.
- `room-view.tsx`: MANUAL STAT ENTRY card (Room commishLive 9a) for the Commissioner when the room has a dataset and its state is auction_live or later (Q-4 A).
- `api/generated.ts` regenerated from springdoc.

## Architecture changes
Patch to docs/knowledge/architecture.md (human approves):
- Sport Packs, add: "A stat may declare optional `min` and `max`; absent means 0 to 1,000,000. Manual entry enforces them."
- Scoring, replace the last bullet with: "Fixture and provider writes are a service API (`MatchStatsService`) with no REST route; ingestion adds one. Matches carry an optional `label`. Manual entry (`ManualStatsService`, `/api/rooms/{id}/stat-entry`, Commissioner only) upserts or clears named fields per field in one transaction: rule row lock, overrides, `scoring_override_log` (append-only by trigger), recompute. Values are normalised before they are stored or compared."
- Rooms, append to the ScoringRoomPort list: "players (with name), franchises".
- Auction engine, add: "Other modules read owners only through `auction.service.AuctionResultsPort` (read-only, from the log)."

## Risks
- Retained players may be missing from the auction log squads. The `owners()` test covers retention, and the gap is fixed in the port if found.
- Old snapshots and the cricket pack have no min/max, so flag stats render as counts. Accepted (Q-3 B) and recorded in the decision record.
- If save or clear reads overrides before taking the rule row lock, AC-16 races. A two-thread test asserts the lock order.
- A large pool could slow the whole-pool list. Mitigated by set queries only, compact rows and a test with a 300-player pool that asserts a constant query count.
- Agnostic lint: all labels come from the pack, and `check-agnostic.sh` runs on the new files.
