---
status: final
---
# Review: 2026-11-02-11-scoring-engine

## QA
Re-run after fix loop 1: `./mvnw -q verify` exit 0, 195 scoring tests, 0 failures. `make test` and `make lint` green per rx:dev.
- AC-1 to AC-18 pass, each with a named test. AC-14, AC-17, AC-18 re-checked after the fixes (FreezeIT, SecondContextIT, new RecomputeLockIT); no regressions.
- AC-14 note, non-blocking: a request that is both stale and frozen returns 409 `stale_version`, not `frozen`. The freeze is still recorded.
- AC-13 gap, non-blocking: manager 403 is not tested on a custom (edited) set before the freeze. Check runs before the state read.
- T-1 to T-34: all ticked, each has a matching test or artefact.

## Security
- S-1 (minor): `MatchStatsService` write methods and `ScoringService.recompute/recomputeRoom` are public with no authz, and `putProviderStats` skips the validation `putOverride` applies. No REST route reaches them. Deferred to 2026-11-02-13-provider-ingestion.
- Fix loop 1 checked: lock order (rule row then points rows) consistent, no deadlock cycle, Commissioner check before insert/lock/freeze write, new 400s leak nothing.
- Everything else checked, no findings (authn/authz, freeze bypass, SQL, deserialization, caps, sanitising, audit-log trigger, exposure, secrets).

## Code review (medium)
CR-1, CR-2, CR-9, CR-10 fixed in fix loop 1. CR-3 to CR-5 deferred to specs 13 and 12; CR-6 accepted at G1; CR-7, CR-8 non-blocking. Details in state.md.

## Verdict
pass. No blocking items. Open for the human: apply the architecture.md patch (plan.md "Architecture changes"); pre-existing `RoomConfigIT.aPurseBelowABasePriceInTheRoomsPoolIsRejectedAndNothingChanges` fails when run alone (same on base commit).
