---
feature: 2026-11-09-home-redesign
phase: close
status: blocked
g1: approved:Shanerex:2026-11-10
g2: approved:Shanerex:2026-11-10
g3: approved:Shanerex:2026-11-10
bump: minor
next: human closes: rx-check done is red only for the 7 intentional T-24 edits to other specs; accept, set phase: done, open the PR
---
# State: 2026-11-09-home-redesign

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-09 intake: feature created
- 2026-11-09 intake: why: Home hub is a neutral grid of white cards with no franchise identity and a weak desktop layout, clashing with room/auction screens; Room settings reopens the NEW ROOM wizard at "2 of 6"; visual drift elsewhere (auction header h1 margin, Monogram admin tile bug, wizard order/units/scoring groups, lobby and sign-in copy, profile with no mock). Agreed direction: Franchise wall (hero band full width + wall of tiles, auto hero priority, Past seasons strip, keep Plus Jakarta + DM Mono), whole outside-room world restyled, dedicated Room settings mode, small additive auction-start data, visual bugs now and feature-shaped gaps deferred. Full plan: ~/.claude/plans/before-starting-any-other-squishy-quiche.md
- 2026-11-09 intake: who: every signed-in manager and Commissioner on outside-room screens (hub, empty, sign-in, join, joinCode/joinError, roomMissing, wizard, Room settings, profile, admin import)
- 2026-11-09 intake: done-when: redesigned and fixed screens drawn in docs/design/source and approved by the human before UI code; build matches them in 390 and 1440 screenshots; hero priority and pack-driven labels/units/groups tested (stub football pack passes); deferred gaps logged in owning specs' state.md; make test and make lint green
- 2026-11-10 requirements: pm pass 2 done, requirements.md final (R-1..R-12, AC-1..AC-14, P-1); awaiting g1
- 2026-11-10 requirements: g1 approved by Shanerex; phase plan
- 2026-11-10 plan: architect pass 2 done, plan.md + tasks.md final (25 tasks); awaiting g2
- 2026-11-10 plan: g2 approved by Shanerex; phase build
- 2026-11-10 build: T-2..T-8 done and green (make test); T-1 scenes drawn (App Screens turn 8, 8a-8ag), P-1 awaiting human approval, T-1 not ticked
- 2026-11-10 build: P-1 approved:Shanerex:2026-11-10 (mocks in Home/Room/App Screens .dc.html; settings uses Prev/Next, Next saves the section, last says Done, form column centred)
- 2026-11-10 build: T-9..T-25 done; Playwright run at 390 and 1440 (hub, settings, outside-room screens beside App Screens 8a-8ag) green; make test and make lint green; check-agnostic allowlists the mock line "You run this one,"
- 2026-11-10 build: all 25 tasks ticked; phase verify
- 2026-11-10 verify: qa fail (AC-13), security pass; fix loop 1 started
- 2026-11-10 verify: fix loop 1: T-21 every changed screen shot at 390 and 1440 beside its mock (web/test-results/home, list in specs/2026-11-09-home-redesign/README.md), deviations fixed; T-20 lobby "120-player short pool" line not buildable from lobby props (no pool size), logged as deferred in spec 08 state.md like T-24
- 2026-11-10 verify: fix loop 1 done; qa pass, security pass; awaiting g3 and bump
- 2026-11-10 verify: g3 approved by Shanerex, bump minor; phase close
- 2026-11-10 close: knowledge files written, [UI] task-line format fixed; rx-check done blocked on T-24 edits to 7 other specs' state.md
- 2026-11-10 close: human chose to keep the T-24 edits and close the gate themselves; phase left at close
