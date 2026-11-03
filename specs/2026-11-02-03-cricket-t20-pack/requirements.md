---
status: final
---
# Requirements: 2026-11-02-03-cricket-t20-pack

## Problem
v1 ships exactly one real pack and it must be pure data: Commissioners creating cricket rooms need roles, presets, stats, default scoring and AI context from a pack, with no sport in core.
Scope: PRD 7.1-7.4, 7.6, FR-72; module packs. Framework contract is spec 02; live provider adapter is spec 13.
The spec 02 dtos reject unknown keys, so R-1 adds optional sport-neutral fields the data needs.

## Pack lifetime matrix
The pack file lives for the process (spec 02 matrix). Entry points: S = startup load, L = registry lookup, R = restart after edit.
- File absent from `packs/`: S starts with zero packs (spec 02 R-3), L not-found; the shipped file must exist (AC-1).
- Valid shipped file: S registers `cricket-t20` (AC-1); L returns every section unchanged (AC-2..AC-14).
- Valid edit (e.g. one rule value): R serves the edited value (spec 02 AC-12); rooms that already cloned the rule set keep their copy (rooms specs, FR-72).
- Edited into invalid: S aborts naming file, pack id, every violation (AC-16); L and R n/a.
- Pack id renamed or version bumped: a rename is a new pack and the old id is not-found; one version per id (spec 02 R-4); room references are rooms' concern.

## Requirements
- R-1: Add `packs/cricket-t20.json`, pack id `cricket-t20`, in the spec 02 R-1 schema. Also add optional, sport-neutral fields to the packs dto and validation: tag-limited retention caps, retention slabs, RTM start season, final counter-raise flag, short-pool size, stat unit. Nothing else changes under server/src/main; no migrations or web; no sport name in core.
- R-2: Roles BAT, BOWL, AR, WK with labels; tags capped, uncapped, overseas; restricted category overseas, default max 8 (7.1).
- R-3: Presets: purse 120 Cr, currency INR, display unit Crore, squad 18-25, role minimums WK 2, BAT 5, BOWL 5, AR 3 (7.2). Money is stored as integer lakh, 1 Cr = 100.
- R-4: Increment ladder: +5L below 1 Cr, +10L below 2 Cr, +20L below 5 Cr, +25L from 5 Cr up; a threshold belongs to the higher step.
- R-5: Retention: maxRetentions 6, at most 5 capped and 2 uncapped; slabs capped picks 1-3 18/14/11 Cr, picks 4-5 18/14 Cr (IPL 2025, 7.7), uncapped 4 Cr. rtmCards 6 (retentions plus RTMs total, IPL 2025). RTM from season 2 with the final counter-raise (FR-49). Short-pool preset 80 players, optional.
- R-6: Stat schema covers every 7.3 stat with id, type and unit; overs held as integer legal balls bowled (2 overs = 12 balls); strike rate and economy are ordinary decimal stats supplied by adapter or manual entry, not derived.
- R-7: Appearance rule names the in-playing-XI stat; an impact-player substitute who bats, bowls or fields counts.
- R-8: Default rule set per 7.4 built only from spec 02 R-7 primitives; milestone and "additional" bonuses stack (100 earns +10 and +20; 5 wickets earns +10 and +20); the 3+ catch bonus is once per match.
- R-9: Rate bands are exclusive: each value falls in one band, and the most extreme matching band wins and replaces, not adds to, the milder one. Strike rate: above 170 is >170; 150-170 inclusive; 130-150 is >=130 and <150; below 70 and below 60 are strict. Economy: below 5, 5-6 and 6-7 are lower-inclusive; above 10 and above 12 are strict.
- R-10: Band encoding: rates are held to 2 dp; bands are from-inclusive, to-exclusive; a strict ">x" is lower bound x+0.01. Band rules read the supplied rate stat divided by in-playing-XI (always 1 when playing), scale 1.
- R-11: UI terminology: lineup "Playing XI", match unit "Match", competition "League" (assumed wording, pack data only).
- R-12: AI domain context has Head Coach knowledge (7.6 topics), a stat glossary with one plain-language entry per R-6 stat, and fit heuristics (slot scarcity, overseas-slot opportunity cost, keeper-batter scarcity); concepts only, no real player names (FR-123).
- R-13: No adapter key, so the pack is manual-entry only (spec 02 R-8); the live adapter is spec 13.
- R-14: The pack loads from the default dir and passes FR-143 validation; existing packs, including the stub football pack, load unchanged; check-agnostic stays green.

## Acceptance criteria
- AC-1: Starting the app with the default dir registers `cricket-t20`; the registry lists its id and version.
- AC-2: Roles are exactly BAT, BOWL, AR, WK, each with a label; tags are exactly capped, uncapped, overseas.
- AC-3: The only restricted category is overseas with default max 8, naming the overseas tag.
- AC-4: Presets read purse 120 Cr, INR, Crore, squad 18-25, role minimums WK 2, BAT 5, BOWL 5, AR 3.
- AC-5: The next increment at 90L, 100L, 190L, 200L, 490L, 500L is 5L, 10L, 10L, 20L, 20L, 25L.
- AC-6: Retention reads maxRetentions 6, caps 5 capped and 2 uncapped, slabs 18/14/11/18/14 Cr capped and 4 Cr uncapped, rtmCards 6, RTM from season 2 with counter-raise, short pool 80.
- AC-7: Every 7.3 stat id is in the stat schema with a type and unit; balls bowled is an integer; the appearance rule names a stat in it.
- AC-8: Batting rules: +1/run, +2/four, +4/six, +10 at 50, +20 more at 100, -5 duck for BAT, WK, AR only.
- AC-9: Strike-rate rule (min 10 balls) bands: from 170.01 +6, 150 to 170.01 +4, 130 to 150 +2, 60 to 70 -2, below 60 -4 only (not -6).
- AC-10: Bowling rules: +25/wicket, +8 per LBW or bowled, +10 at 3 wickets, +20 more at 5, +12/maiden.
- AC-11: Economy rule (min 12 balls) bands: below 5 +6, 5 to 6 +4, 6 to 7 +2, 10.01 to 12.01 -2, from 12.01 -4 only (not -6).
- AC-12: Fielding and other rules: +8/catch, +4 at 3+ catches, +12/stumping, +12 direct run-out, +6 assisted run-out, +25 Player of the Match, +4 appearance.
- AC-13: Every rule parses to a typed rule of one of the six spec 02 R-7 primitives (spec 02 AC-17); band rules use the R-10 encoding.
- AC-14: The AI context has all three parts non-empty, a glossary entry for every stat id, and none of the names in a test list of 20 well-known players.
- AC-15: The registry reports the pack as manual-entry only.
- AC-16: A copy of the file with the WK minimum raised so role minimums exceed squad min fails startup naming file, pack id and both numbers.
- AC-17: With cricket and the stub football pack in one dir, both register under distinct ids.
- AC-18: `git diff main` under server/src/main touches only packs dto and validation files for the R-1 fields; no migrations or web files; make lint passes including scripts/check-agnostic.sh.
- AC-19: The stub football pack, which lacks every new R-1 field, still loads and its section values are unchanged.

## Out of scope
- Rule evaluation and scoring, room cloning or editing of the rule set, rooms referencing a pack (other specs).
- The live provider adapter and its choice (7.5, spec 13); player pool import and the short-pool import flow itself.
- Other framework changes (derived stats, new rule primitives, required new fields); prompt assembly from the AI context.
