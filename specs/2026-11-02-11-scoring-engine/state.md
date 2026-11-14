---
feature: 2026-11-02-11-scoring-engine
phase: done
status: approved
g1: approved:Shanerex:2026-11-12
g2: approved:Shanerex:2026-11-12
g3: approved:Shanerex:2026-11-13
bump: minor
next: open a PR; a human merges
---
# State: 2026-11-02-11-scoring-engine

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02..12 intake/requirements/plan: pre-seeded from PRD (FR-70..72, module scoring); G1 and G2 approved by Shanerex 2026-11-12 (economy edges 10.0=-2, 12.0=-4 accepted; architecture.md patch approved); answers: attest+reason logged, freeze at first-match start, post-freeze change recomputes all, bands lower-inclusive, server+REST only, shared provider stats with room overrides
- 2026-11-13 build: rx:dev done 34/34; make test green on rerun (earlier hook failure was a race with target/ rebuild)
- 2026-11-13 verify: qa pass (18/18 AC), security pass (S-1 minor); review.md merged
- 2026-11-13 verify: deferred to 2026-11-02-13-provider-ingestion: add authz and vocabulary/range validation to MatchStatsService write methods before exposing them (S-1)
- 2026-11-13 verify: deferred to 2026-11-02-13-provider-ingestion: none else; AC-13 custom-state manager 403 test gap is non-blocking
- 2026-11-13 verify: G3 approved by Shanerex; bump minor
- 2026-11-13 verify: code-review medium: 10 findings (CR-1..CR-10). Fix loop now: CR-1 recompute vs edit lock race, CR-2 primitive int version, CR-9 freeze recorded before stale check, CR-10 earliestStart null row
- 2026-11-13 verify: deferred to 2026-11-02-13-provider-ingestion: CR-3 putProviderStats validation (same as S-1)
- 2026-11-13 verify: deferred to 2026-11-02-12-manual-stat-entry: CR-4 putOverride matchId check when room has no dataset; CR-5 override scale normalise and no way to clear an override
- 2026-11-13 verify: CR-6 pack band edges moved 170.01/10.01/12.01 to 170/10/12 is the G1-accepted lower-inclusive decision; no action
- 2026-11-13 verify: CR-7 repeated input loads per write (perf, non-blocking) and CR-8 stale points for matches with no remaining stat rows (not reachable until a delete path exists, see CR-5): no action now
- 2026-11-13 verify: fix loop 1 re-review qa pass, security pass; review.md final
- 2026-11-13 close: 3 decisions + 2 learnings written; rx-check done green; phase done
- 2026-11-14 verify: code-review high on PR #13: 10 findings (CR-11..CR-20). Fix loop 2: CR-11 freeze recorded by recompute too (FreezePolicy.recordIfDue), CR-12 freeze counts only matches at or after room creation (R-10 "room's first match"), CR-13 cricket pack 1.0.0 to 1.1.0 for the changed default set, CR-14 band rate no longer rounded to 2 dp, CR-15 legacy rooms get the appearance rule backfilled into the snapshot at startup (no live-pack read), CR-16 view reports the recorded freeze instant, CR-17 banded scale must be above zero, CR-18 keepRules replaced by RoomConfigDto.withScoringRules, CR-20 rules and inputs read once per write/recompute
- 2026-11-14 verify: deferred to 2026-11-02-16-agnostic-acceptance: CR-19 one shared rule validator for PackValidator, RoomConfigValidator.RuleChecker and RuleSetValidator; with it drop the config-side scoring check and mark config.scoringRules read-only (rest of CR-18)
