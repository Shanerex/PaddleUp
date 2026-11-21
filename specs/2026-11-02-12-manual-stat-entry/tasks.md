---
status: final
---
# Tasks: 2026-11-02-12-manual-stat-entry
## Tasks
<!-- Each task cites at least one existing AC. Tick to - [x] when done. -->
Server: packs and schema
- [x] T-1 (AC-1, AC-6): `StatDto` gets optional `min`/`max`. `PackValidator` checks 0 <= min <= max <= 1,000,000 and whole bounds for integer stats; old snapshots without them still load. No pack file changes. Tests: cricket and stub packs load unchanged; bad bounds are rejected.
- [x] T-2 (AC-4, AC-11, AC-12): `V12__manual_stat_entry.sql`: `scoring_override_log` with append-only triggers, plus nullable `scoring_matches.label`. `upsertMatch` takes the optional label. Tests: UPDATE, DELETE and TRUNCATE are refused; label round-trips.
Server: scoring core
- [x] T-3 (AC-6, AC-7): `scoring.util.StatValues`: normalise and range check (limits or default, 4 decimals, whole for integer). Unit tests: 52 / 52.0 / 52.0000, negative, over max, decimal on an integer stat, 5 decimals.
- [x] T-4 (AC-1): `scoring.util.SheetFields` builds fields from the room's stats: per event type, and yes/no for an integer stat with max 1. Tests on the cricket pack, the stub football pack and a fixture pack with a max-1 stat.
- [x] T-5 (AC-9, AC-12, AC-20): ports. `ScoringRoomPort.players` adds name; new `franchises(roomId)`; new `auction.service.AuctionResultsPort.owners(roomId)` from log squads and `auction_lots`. Tests: sold, unsold, retained, before go-live; `ModuleBoundariesTest` passes.
- [x] T-6 (AC-2, AC-12): `MatchStatsRepository` adds `overridesFor`, `deleteOverrides`, `matchStart` and `matchSummaries`; new `OverrideLogRepository` (append, page). Repository tests.
- [x] T-7 (AC-14, AC-16): `ScoringService.lockRoom` and a pure provider-only `evaluate`; `putProviderStats` normalises values. Test: provider-only points equal an evaluation with no overrides.
- [x] T-8 (AC-2, AC-6, AC-7, AC-8, AC-9, AC-13, AC-15): `ManualStatsService.save`, a multi-field per-field upsert: lock, then validate every named field ; refusals: unknown match, no dataset, not started (start == now accepted), not in pool, no filled field ; skip unchanged values; upsert only the filled fields (blank and unnamed overrides untouched) ; log, recompute, return before/after/provider-only/franchise delta ; remove `putOverride` and move its tests. Test: a save naming field A keeps an existing override on field B.
- [x] T-9 (AC-11, AC-12): `ManualStatsService.clear` removes only the named fields' overrides, logs old values and recomputes. In S0/S1 it is a no-op with no log row. Clearing the last override with no provider data removes the points row.
- [x] T-10 (AC-3, AC-4, AC-5): matrix tests: re-ingest after a save keeps the manual field ; a double save and a double ingest give identical totals, one row per player and one log row per changed field ; `recomputeRoom` keeps overrides
- [x] T-11 (AC-15, AC-16): rollback test with a failing recompute (override and log unchanged); two concurrent saves end equal to a fresh recompute.
- [x] T-12 (AC-12, AC-17): reads: `matches`: "Match n · label" and chips (PROVIDER, PROVIDER + n MANUAL, MANUAL, NO DATA, UPCOMING) ; `matchPlayers`: whole pool, valued first, with owner, chip, points and the "n of N" count; constant query count on a 300-player pool ; `sheet`: provider and manual value per field
Server: REST
- [x] T-13 (AC-6, AC-8, AC-10, AC-13): `ManualStatsController` routes and dtos; `InvalidStatSheetException` (422 with fields) and `MatchRefusedException` (404/409) in `ScoringExceptionHandler`. MockMvc: Commissioner 200, member 403, non-member 404, app admin who is not the Commissioner 403/404.
- [x] T-14 (AC-4, AC-11): `GET /scoring/override-log` for any member, paged by seq; non-member gets 404.
- [x] T-15 (AC-14, AC-20): API test. Returned before/after equal the stored rows. The franchise delta equals the change in the owning franchise's sum. An unowned player has no owner and no delta.
Web
- [x] T-16 (AC-1, AC-6, AC-18): `web/src/rooms/stat-sheet.ts`: field model, hints, request building (filled fields only) and 422 mapping that keeps typed values. Vitest. Regenerate `api/generated.ts`.
- [x] T-17 (AC-17, AC-18): stats route: match list ("Match n · label", chips) and the whole-pool player list with owner chips and the "n of N players" count. UPCOMING cannot be opened. Design refs: App Screens 9f (Room `statEntry`), 9h (Room `statEntryNoData`).
- [x] T-18 (AC-6, AC-13, AC-18): sheet form with number, yes/no and per-type counts from the pack. Error banner "1 field needs fixing. Nothing was saved." with field hints; an empty save points to clear. Design refs: App Screens 9f (Room `statEntry`), 9g (Room `statEntryError`).
- [x] T-19 (AC-14, AC-18): save and recompute, with the saved toast showing before/after and the franchise delta. Design refs: App Screens 9i (Room `statEntrySaved`).
- [x] T-20 (AC-11, AC-12, AC-18): the Clear override confirm clears every overridden field of the player. Design refs: App Screens 9j (Room `statEntryClear`).
- [x] T-21 (AC-17): MANUAL STAT ENTRY card on the hub for the Commissioner when the room has a dataset and is auction_live or later. It shows recent matches with chips and opens the stats route. Design refs: Room `commishLive` (9a), stat entry card.
Verify
- [x] T-22 (AC-18, AC-19): Playwright with make dev: 9f-9j beside their mocks at 390 and 1440 ; a second tab after provider data arrives still saves and shows the new provider values ; a stale tab refreshes
- [x] T-23 (AC-1): `scripts/check-agnostic.sh` clean on new files; a stub football pack end-to-end sheet save with zero core changes; decision record for the override log, match label and pack min/max in docs/knowledge/decisions.
