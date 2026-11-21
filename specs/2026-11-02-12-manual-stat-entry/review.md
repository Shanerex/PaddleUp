---
status: final
---
# Review: 2026-11-02-12-manual-stat-entry

## QA
Rerun after fix loop 1: `make test` twice exit 0 (1004 failsafe tests, 0 failures; web vitest 512/512).
- AC-1 to AC-17, AC-20 pass, each mapped to a passing test (ManualStats*IT, StatValuesTest, SheetFieldsTest, StatBoundsTest, EffectiveStatsTest, AuctionResultsPortIT).
- AC-18 pass, caveat: e2e spec read, not re-run; 390/1440 fidelity to the mocks is a human check at G3.
- AC-19 pass, caveat: asserted in the e2e spec only; no unit or server test.
- T-3 / T-13 pass: new tests cover SEC-1 (1e999999999, -1e999999999, int-max exponent, refused under 100 ms) and SEC-2 (500 fields, 400 invalid_request).
- T-5 note: owners() has no retained-player case; deferred in state.md.

## Security
- SEC-1 major: fixed. StatValues.magnitudeRefusal runs before normalise; new tests fail without the fix (checked by disabling it: 4 errors).
- SEC-2 minor: fixed. requireWithinSheet runs before the room lock in save and clear.
- SEC-3 minor: override log shows actor user id to members; allowed by R-11. No action.
- SEC-4 minor, new: MatchStatsService.java:81,92 pass provider values through normalise without the magnitude check; no endpoint reaches it yet. Deferred in state.md.
- Checked clean: SQL parameterised, Commissioner/member access on every route, append-only log, no secrets or new dependencies.

## Verdict
pass. No blocking IDs. Human checks at G3: pixel fidelity at 390 and 1440 (AC-18), the stale-tab flow (AC-19).
