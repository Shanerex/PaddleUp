---
status: final
---
# Review: 2026-11-02-07-auction-screen

## QA
Round 3 (R-13 scope added after round 2). Ran web Vitest (32 files, 307 green), tsc --noEmit and eslint (clean). Read e2e log: 28/28 green at 22:05, newer than the last source edit (21:54). Server make test not rerun by QA (dev's mvn verify green; AuctionStubPackIT green after the DevSeedRunner fix).
- AC-1 to AC-18 pass, no regression from R-13 (round 2 detail: clock skew under 1 s, two-tab ready within 2 s, lobby to lot within 2 s, one POST per double tap, outbid within 500 ms, stable boxes over 20 broadcasts at 390/1440, 72 px dock in thumb zone, reconnecting under 3 s with resync, stale tab sends no bid, paused/adjourned via fixtures per T-20, observer without bid button, adjourned purse on one line).
- AC-19 pass: schedule.spec asserts server startsAt equals the picked instant (Kolkata zone, clock skewed 5 min); a manager in Auckland sees their own local time; Change time moves another tab's countdown within 2 s, no reload, READY kept.
- AC-20 pass: past time shows the server field error; too-small pool shows the server reason; room stays setup and GET auction stays 404; manager sees no control and a direct PUT gets 403.
- AC-18 (R-13 screens): 390 and 1440 pairs for sheetSchedule, sheetScheduleError, sheetScheduleRefused, lobbyReschedule match layout, tokens and type.
- Accepted deviations: native date/time input format; zone label shows IANA city ("Calcutta") not country; refusal text is the server's own. Earlier copy deviations forced by check-agnostic (lobbyAdmin "You lead this one", closed "has ended") stand.
- Minor: sheetScheduleError shot has a darker primary button than the mock (likely hover state in capture). Mock "Start now" not built (FR-47, out of scope).
- Needs human OK: Schedule auction button on NoAuctionCard (Commissioner only) has no drawn mock; R-13 requires the action, CLAUDE.md requires a drawing.
- T-1 to T-25 ticked with matching tests.

## Security
Round 3, diff vs main.
- PUT /auction/schedule: membership check then 403 for non-Commissioner (AuctionService.schedule :75-79); past start rejected server-side (AuctionRunner.doSchedule). UI button gating is cosmetic only.
- PUT /auction/ready: franchise from server access record; SCHEDULED only; Commissioner without franchise refused.
- Client clock (pageClock) drives only preview and countdown; server decides past/live.
- Server error text rendered as React text; no innerHTML or dangerouslySetInnerHTML.
- Dev seed: DevSeedRunner needs @Profile("dev") and paddleup.dev-seed.enabled=true (set only in application-dev.yml); DevAccountSeeder @Profile("dev"); .test emails, no secrets.
- Earlier: ready race fixed (serial queue, AuctionReadyRaceIT); socket ticket-only, members only. Minor open: PUT /ready broadcasts once per real toggle (franchise members, SCHEDULED only).

## Verdict
pass. No blocking items. Open: human OK for the NoAuctionCard Schedule button (keep and draw, or drop). Minor open: toggle-rate broadcast on PUT /ready.
