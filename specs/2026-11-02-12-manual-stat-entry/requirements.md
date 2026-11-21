---
status: final
---
# Requirements: 2026-11-02-12-manual-stat-entry

## Problem
A Commissioner has no way to enter or correct a match stat sheet when provider data is missing or wrong. Manual values must beat provider data, survive re-ingestion and keep totals idempotent (FR-81, NFR-5). Also closes scoring-engine CR-4 and CR-5. Design refs: App Screens 9f-9j (Room statEntry, statEntryError, statEntryNoData, statEntrySaved, statEntryClear), Room commishLive 9a.

## State x entry-point matrix
State of one (room, match, player field), by entry point. P = provider value, M = override. "Recompute" is the same-transaction recompute of that room and match.
| State | Save field | Clear field | Provider ingest | Rule edit / room recompute |
|---|---|---|---|---|
| S0 no P, no M | M set, recompute, S2 | no-op, no log | P set, S1 | no points row |
| S1 P only | M set, recompute, S3 | no-op, no log | P replaced, recompute | points unchanged |
| S2 M only | M replaced, recompute | M gone, S0; row removed if no value is left | P set, M wins, S3 | M kept, rescored |
| S3 P and M | M replaced, recompute | M gone, field from P, S1; other fields keep M | P replaced, M wins, M unchanged | M kept, rescored |
| Match not started | refused | refused | n/a | n/a |

## Requirements
- R-1: Save a player's stat sheet for a match as one atomic action; its fields come from the room's pack stat schema (stats, per-event-type counts), with no sport, stat or role name in core code or UI strings (FR-81, FR-141).
- R-2: A save upserts each filled field as an override for that stat and event type; a blank field is left as it is (an existing override stays, none is created).
- R-3: Validation is all-or-nothing: each field must be a known stat or event type, whole for an integer stat and within its limit; any bad field refuses the sheet and names each bad field with its allowed range. The limit is the stat's optional min and max in the pack schema (stub pack unchanged), else 0 to 1,000,000. An integer stat with max 1 renders yes/no; an event-typed stat renders one count per type.
- R-4: The match must exist in the room's season dataset and have started by the server clock; a room without a dataset has no matches (CR-4).
- R-5: The player must be in the room's pool. Commissioner only; a non-member gets 404, a member who is not the Commissioner 403.
- R-6: Per stat and event type a manual value beats the provider value, through every provider re-ingest and every rule recompute (NFR-5).
- R-7: Clear removes the named fields' overrides for a player and match, atomically, and rescores; other fields keep theirs. Clearing a field with no override is a no-op. The 9j "Clear override" clears every overridden field of the selected player.
- R-8: Values are normalised to one scale before they are stored or compared (CR-5).
- R-9: Save and clear recompute the room and match in the same transaction; a recompute leaves one row per scored player with the same total and lines for the same inputs.
- R-10: Save and clear return the player's points before and after and the owning franchise's change; reads also give the points the provider values alone would give.
- R-11: Each field change by save or clear appends an immutable entry to a scoring override log (actor, UTC instant, match, player, stat, event type, old, new), readable by any room member through a read endpoint; a change-free save writes none.
- R-12: Reads list the room's dataset matches with a source chip (PROVIDER, PROVIDER + n MANUAL, MANUAL, NO DATA, UPCOMING), a match's players with owner, chip and points, and a player's sheet showing provider and manual value per field.
- R-13: The screens are built to the Design refs, with owner chips and franchise deltas from a read-only port to the auction results; the match card on commishLive opens them.

## Acceptance criteria
- AC-1: For the cricket pack and the stub football pack, the sheet fields equal the pack's stat schema (id, label, kind, event types, min, max) with zero core changes.
- AC-2: Saving 52 over provider 46 stores one override; the player's stored total equals the evaluator's total on the effective stats.
- AC-3: After that save, re-ingesting changed provider values leaves the manual field and its points unchanged and updates only fields without an override.
- AC-4: Saving the same sheet twice, and ingesting the same provider stats twice, leave identical totals, one points row per player and one log entry per changed field.
- AC-5: A rule edit recompute (recomputeRoom) keeps every override in effect.
- AC-6: A sheet with one bad field (unknown stat or event type, negative, over the pack max, decimal on an integer stat, over 4 decimals) is refused with that field and its range named; overrides, points and log are unchanged.
- AC-7: Saving 52, 52.0 and 52.0000 stores the same value, and re-saving it writes no log entry.
- AC-8: An unknown match id, any id in a room with no dataset, and a match that has not started are each refused; a match starting at exactly now is accepted.
- AC-9: A player not in this room's pool is refused, including a pool player of another room.
- AC-10: Commissioner saves; a member who is not the Commissioner gets 403; a non-member gets 404; an allow-listed app admin who is not the Commissioner gets 404 or 403.
- AC-11: With overrides on two fields, clearing one leaves the other; the cleared field takes its provider value and the log holds the old value.
- AC-12: Clearing the last override of a player with no provider data removes the points row and the match chip reads NO DATA; clearing in S0 or S1 changes nothing and logs nothing.
- AC-13: A sheet with no values is refused and points the user to clear.
- AC-14: The before and after points returned equal the stored points before and after; the provider-only points equal an evaluation with no overrides.
- AC-15: If the recompute fails, the override and its log entry are rolled back.
- AC-16: Two simultaneous saves for one match end with stored points equal to a fresh recompute from the stored overrides.
- AC-17: Chips by state: a started match with no values shows NO DATA, an upcoming one UPCOMING and cannot be opened.
- AC-18: In a real browser at 390 and 1440 each of 9f-9j matches its mock; the error state shows "1 field needs fixing. Nothing was saved." and keeps typed values.
- AC-19: In a second tab, after provider data arrives, saving still works and the sheet shows the new provider values.
- AC-20: The returned franchise change equals the owning franchise's change in the sum of its players' points for the match; an unowned player has no owner chip and no change.

## Out of scope
- Provider ingestion and adapters (FR-80), unmapped-player flags (FR-83), leaderboard and drill-down (FR-82, spec 14), live scoring (FR-84).
- Deferred: app admin entry (rooms port change) and listing override-log entries in the Records audit screen; both go to a later spec. Also no bulk or CSV upload, approval workflow or players outside the pool.
