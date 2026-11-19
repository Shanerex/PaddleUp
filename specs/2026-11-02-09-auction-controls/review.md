---
status: final
---
# Review: 2026-11-02-09-auction-controls

## QA
Round 5 (after fix loop 4, c90bbd5). Docker image store broken on this machine: Testcontainers ITs, Playwright e2e and full make test did NOT run.
- Ran green: `./mvnw test -Dtest='*Test,!HealthDownTest'` 431/431, spotless:check, check-agnostic, web tests 478/478, web lint.
- CR4-1 fixed: AuctionReducer.java:91 clears startRefusal on START_NOW from SCHEDULED; doTick retries go-live; AuctionReducerTest.startNowFromTheLobbyClearsAnEarlierStartRefusal passes, live apply equals replay. The new IT case in AuctionControlsIT compiles, unrun.
- CR3-1..CR3-6 fixed in round 4 (code and unit/web tests green); their ITs (AuctionAdjournIT, AuctionControlsIT) and e2e/controls.spec.ts unrun.
- Unverified this round: all server ITs behind AC-1, AC-2, AC-3, AC-4, AC-6..AC-11, AC-13, AC-15 (passed in round 3); AC-14 e2e at 390/1440 (web unit tests green). AC-5, AC-12, R-10 deferred to 2026-11-02-26-advisor-lifelines.
- Accepted mock gaps unchanged (resume lobby, adjourned at 1440, re-open sale time, adjourn copy per Q-3).

## Security
- Round 5 pass, no findings. START_NOW logged only after the Commissioner 403 check; Start now readiness is checked live, never from the stored refusal; go-live re-checks readiness in the locking transaction; replay equal; intent resend returns the first answer.

## Code review
- Rounds 1 to 3 findings fixed; CR3-1..CR3-6 and CR4-1 fixed with tests.
- Deferred to a new spec by the human: CR2-7..CR2-9, CR3-7..CR3-10.

## Decisions (human)
- 2026-11-17: picks private; CR-9, CR-10 in scope.
- 2026-11-18: S-3/CR2-1 accepted; CR2-7..CR2-9 deferred; loop 3 approved; CR3-7..CR3-10 deferred.
- 2026-11-19: verify without ITs (Docker broken); loop 4 approved for CR4-1.

## Verdict
pass on everything that could run; no blocking AC, no open bugs. Run make test and e2e once Docker works before opening the PR (ITs and e2e unverified this round).
