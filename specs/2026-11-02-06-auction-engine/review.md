---
status: final
---
# Review: 2026-11-02-06-auction-engine

## QA
`./mvnw -q verify` in server/: exit 0, 0 failures, 0 skipped. `check-agnostic.sh`: ok. No UI in this spec, so no browser flow.
- AC-1 pass: AuctionScheduleIT
- AC-2 pass: AuctionLockIT
- AC-3 pass: AuctionLifecycleIT
- AC-4 pass: LadderTest, IntentRulesTest, AuctionBidIT
- AC-5 pass: IntentRulesTest, AuctionBidIT
- AC-6 pass: AuctionBidIT, IntentRulesTest
- AC-7 pass: AuctionBidIT (client clock ignored)
- AC-8 pass: AuctionLifecycleIT, TimerRulesTest, AuctionBidIT
- AC-9 pass: AuctionRunnerIT (20 concurrent rounds, one accepted)
- AC-10 pass: AuctionRunnerIT, AuctionBidIT (no cancel route)
- AC-11 pass: AuctionRunnerIT, AuctionSchemaIT, AuctionRepositoriesIT
- AC-12 pass: AuctionReadIT
- AC-13 pass: AuctionResyncIT, AuctionStompIT
- AC-14 pass: AuctionRecoveryIT
- AC-15 pass: AuctionLatencyIT, p95 11 ms, max 38 ms, n=1000
- AC-16 pass: AuctionStubPackIT (stub pack, zero core change)
- AC-17 pass: AuctionStartRefusedIT, AuctionRecoveryIT
- T-3 minor: tasks.md says out-of-range timers return 422; code returns 400 `invalid_config` like every other config rule (RoomConfigIT). No AC needs 422. Fix the T-3 wording.
- Deviations accepted: timer defaults on read, `AuctionRoomPort.markScheduled`, schedule-time readiness 409 `not_ready`, standings ordered by franchise id, AbstractPostgresIT tick=0/recovery=false.

## Security
- minor: StompTicketService.java:40. Live-ticket cap of 10,000 is shared by all users. One member can fill it for 30 s and block cross-origin connects; overflow returns 500. Fix: per-user cap or replace the user's older ticket.
- Checked, no issue: STOMP dual auth, SUBSCRIBE membership (non-member and missing room both 404), REST membership, client trust, log before broadcast, irrevocable bids, SQL, data exposure, input validation, secrets.

## Verdict
pass. Blocking items: none. Non-blocking: T-3 wording, StompTicketService ticket cap.
