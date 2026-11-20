---
feature: 2026-11-02-09-auction-controls
phase: done
status: approved
g1: approved:Shanerex:2026-11-17
g2: approved:Shanerex:2026-11-17
g3: approved:Shanerex:2026-11-19
bump: none
next: open a PR; a human merges
---
# State: 2026-11-02-09-auction-controls

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02..18 (collapsed): intake FR-46..48; g1/g2 approved by Shanerex 2026-11-17; End round plays picked lots most-picked first; AC-5/AC-12, RTM and lifeline audit kinds deferred to 17-retention-rtm and 26-advisor-lifelines; T-17 design edits (3w,3x,3z,3aa) approved; T-30 e2e and side-by-side done; T-32 added (3y n of m, Picked by); 3w/3x pool cap/search/filter deferred to a new spec; 32 tasks ticked; verify rounds 1-3 and fix loops 1-2 (CR-1..CR-10, CR2-2..CR2-10; CR2-7..9 deferred)
- 2026-11-18 verify: deferred to a new spec (not yet on roadmap): CR2-7 audit server-side kind filter and paging, CR2-8 shared sanitiser with scoring, CR2-9 dead ROUND_DONE branch
- 2026-11-18 fix-loop-2: CR2-2..CR2-6 and CR2-10 fixed test first, 4 commits: Start now and Change time in the resume lobby (adjourned card layout, 3v pair), next-lot labels use nextLot in a gap (re-open sheet says "before Lot N" there, since a re-opened lot goes to the queue head), state DTO sends lotsClosed (reducer count, re-open takes one off) and resumeLobbySeconds (TimerRules.RESUME_LOBBY and 2-arg due removed), late bid on a closed lot answers lot_closed in every phase with client copy for paused/adjourned/resume_lobby/main_done/nominating, DM Mono on the two counts; make test and make lint green, e2e pairs regenerated; CR2-1 accepted, CR2-7..9 deferred
- 2026-11-18 verify: fix loop 2 done; round 3 qa pass, security pass, /code-review 10 new findings (CR3-1..CR3-6 bugs); blocked after 2 loops
- 2026-11-18 verify: human approved fix loop 3 for CR3-1..CR3-6, re-verify with qa + security only; deferred to a new spec (not yet on roadmap): CR3-7 zero/changed picks, CR3-8 held-phase tick, CR3-9 shared sanitiser, CR3-10 Phase.timed()/held()
- 2026-11-19 fix-loop-3: CR3-1..CR3-6 fixed test first, one commit each; Start now the room cannot honour is refused not_ready with no event, a go-live write failure after start_now stays accepted and the tick retries, a gap pause logs no lot (payload gap) and reads "Paused between lots", re-open notice uses newLot, adjourned card says "after N lots", the skip and re-open notice has its own slot; unit and web tests, spotless, agnostic check green; ITs and make test NOT run: Docker image store broken (containerd meta.db input/output error, postgres:17 cannot be pulled); CR3-7..CR3-10 untouched (deferred)
- 2026-11-19 verify: fix loop 3 done (CR3-1..CR3-6, 7 commits); round 4 qa pass (ITs and e2e unrun, Docker broken), security pass; CR4-1 found (go-live retry blocked by startRefusal); blocked for human
- 2026-11-19 verify: human approved fix loop 4 for CR4-1 only; then qa + security
- 2026-11-19 fix-loop-4: CR4-1 fixed test first, one commit; START_NOW from SCHEDULED now clears startRefusal in AuctionReducer (replay-safe; state DTO derives from it so the refusal stops showing), so the tick retries a go-live that failed after an earlier start_refused; new reducer unit test, new IT case in AuctionControlsIT (compiles, NOT run: Docker broken); unit tests (431), spotless, check-agnostic green; web untouched; CR3-7..CR3-10 untouched
- 2026-11-19 verify: fix loop 4 done (CR4-1, c90bbd5); round 5 qa pass, security pass; ITs and e2e unrun (Docker); asking g3 and bump
- 2026-11-19 verify: g3 approved by Shanerex, bump none; phase close
- 2026-11-19 close: learning written (reducer-state-outlives-the-event); decisions already exist; rx-check done blocked: make test fails, HealthDownTest cannot pull postgres:17 (Docker image store broken), 432 run, 1 error; ITs never ran
- 2026-11-19 close: Docker Desktop restarted, postgres:17 pulled, dev DB restarted; rerunning rx-check done
- 2026-11-19 close: fixed mechanical rx-check findings: AC lines in requirements.md now '- AC-n: (R-..)' form, state.md log collapsed; rerunning rx-check done
- 2026-11-19 close: rx-check done green (make test incl. ITs, lint); phase done
- 2026-11-20 pr-review: PR 16 /code-review comments: pickCounts privacy not changed, S-3 stands (human, 2026-11-20); fixed test first: resumeLobbySeconds rounds up, re-open reason sanitised once, reopen uses AuctionState.closed, web keeps one intent id per tap across a lost answer (IntentRetry); already deferred: zero/changed picks (CR3-7), ROUND_DONE dead branch (CR2-9), shared ReasonText (CR2-8, CR3-9), Phase.timed() (CR3-10); deferred to a new spec: AuctionState builder double copy (optional efficiency); unit tests 434 and web 483, eslint, tsc, spotless green; ITs and e2e not run
