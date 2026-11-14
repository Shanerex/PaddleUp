---
status: final
---
# Requirements: 2026-11-02-11-scoring-engine

## Problem
Season-long play needs points per player per match. The Commissioner must tune the pack's default rules before the season; everyone must trust the rules cannot shift mid-season without a trace (FR-70, FR-71, FR-72; module scoring).

## Requirements
- R-1: A rule set is declarative data (stat -> points) over the room's Sport Pack stat schema; core code names no sport, role, stat or currency.
- R-2: The engine supports six primitives: per-unit, threshold bonus, banded rate with minimum volume, role-conditional, event-type bonus, appearance bonus.
- R-3: Threshold bonuses stack: reaching a higher threshold also earns every lower one (100 runs = the 50 bonus plus the 100 bonus).
- R-4: Bands are mutually exclusive, apply only when the minimum volume is met, and are lower-inclusive, upper-exclusive (a boundary value takes the higher band).
- R-5: Evaluating one player in one match returns a total plus one line per rule that fired (rule, label, points); same inputs give the same output.
- R-6: Recompute replaces the stored result per (room, match, player); it never duplicates or drifts.
- R-7: Where a stat has both a manual and a provider value, the manual value is evaluated.
- R-8: Each pack ships a default rule set; a new room gets a snapshot of it pinned to the pack version, marked pack-default.
- R-9: Before the freeze the Commissioner can clone-and-edit (values, thresholds, bands, add/remove rules) or revert to the snapshot; sets are validated against the schema and capped.
- R-10: The rule set freezes at the scheduled start instant of the room's first match, checked on every edit; frozen is derived server-side from the server clock, persisted, never from the client.
- R-11: After the freeze the only change path is a Commissioner action with a reason and a unanimity attestation; it is audit-logged, keeps the old set in the log and recomputes all the room's scored matches.
- R-12: Any room member reads the rule set and per-match breakdowns; only the Commissioner writes.
- R-13: The Cricket T20 default evaluates per PRD 7.4 and is pinned by golden tests.

## Rule-set lifecycle matrix
Lives from room creation with no expiry and no sliding; ends only at room deletion. No framework default is relied on.

| Entry point | Pack default | Custom (edited) | Frozen (either) |
|---|---|---|---|
| Commissioner edit / clone / revert | AC-11 -> Custom | AC-11, AC-12 | AC-14; change path AC-15, AC-16 |
| Non-Commissioner write, member read, non-member | AC-13 | AC-13 | AC-13 |
| First match start passes, incl. stale open tab | AC-14 -> Frozen | AC-14 -> Frozen | already frozen |
| Two concurrent edits, edit racing the freeze | AC-18 | AC-18 | AC-18 |
| Server restart | AC-17 | AC-17 | AC-17 |
| Pack version bump | AC-10 no change | AC-10 no change | AC-10 no change |

## Acceptance criteria
- AC-1: A test rule set using all six primitives over the stub football pack schema evaluates a player-match to the expected total and lines, with zero core changes (R-1, R-2, R-5).
- AC-2: A build test fails if scoring main sources contain any pack stat, role or sport literal (R-1).
- AC-3: Runs 49 gives no milestone bonus, 50 gives +10, 100 gives +10 and +20 (R-3).
- AC-4: Min volume: SR bonus absent at 9 balls, +6 at 10 balls (SR 200); economy bonus absent at 1.5 overs, present at 2.0. Boundaries: SR 170 +6, 150 +4, 130 +2, 70 0, 60 -2; economy 5.0 +4, 7.0 0, 10.0 -2, 12.0 -4 (R-4).
- AC-5: Duck is -5 for BAT, WK, AR and 0 for BOWL; a non-dismissed 0 scores no duck (R-2).
- AC-6: 3 wickets (2 LBW/bowled, 1 caught) = 101; direct run-out +12, assisted +6; XI member with no other stats gets +4, a non-XI player 0 (R-2).
- AC-7: Cricket golden fixtures (century batter, five-wicket bowler, keeper with stumping and PoM) match hand-computed totals from PRD 7.4 under R-4 boundaries (R-13).
- AC-8: Evaluating twice gives identical output; recomputing twice leaves one stored row per (room, match, player) with the same total (R-5, R-6).
- AC-9: With a manual and a provider value for one stat, the manual value is scored, and still is after a provider re-import and recompute (R-7).
- AC-10: A new room holds a pack-default snapshot; a later pack version bump leaves its rules and points unchanged (R-8).
- AC-11: Pre-freeze Commissioner edit marks the set custom; revert restores the snapshot exactly (R-9).
- AC-12: Unknown stat/role/event, overlapping bands, negative min volume, over 100 rules or a label over 60 chars gives 422 with field errors and saves nothing (R-9).
- AC-13: Non-Commissioner write gives 403 in every state; any member reads the set and breakdowns; a non-member is denied (R-12).
- AC-14: An edit at or after the first match's scheduled start gives 409 frozen, even from a tab loaded before it; one second before is accepted (R-10).
- AC-15: Post-freeze change without reason and attestation gives 422; with both it saves and adds one audit entry (actor, UTC time, reason, before/after diff) (R-11).
- AC-16: After a post-freeze change every scored match in that room is recomputed with the new set and the old set stays in the log; other rooms are untouched (R-11).
- AC-17: After a server restart the rule set and frozen state are identical and edits are still rejected (R-10).
- AC-18: A stale-version edit gives 409; an edit racing the freeze is fully applied before it or rejected, never half-applied (R-9, R-10).

## Out of scope
- Stat ingestion, provider adapters, manual-entry UI and leaderboard (separate specs); captain multipliers (FR-73), live scoring, Player Intel (FR-120).
- In-app approval voting by managers (the Commissioner attests, R-11).
- Web UI: deferred to a follow-up UI spec, drawn in docs/design/source and approved first: rules editor (Customise), post-freeze change dialog, rule-change audit entry. This spec is server + REST only.
