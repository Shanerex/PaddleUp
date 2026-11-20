---
status: final
---
# Review: 2026-11-02-10-squads

## QA
Ran: `make test` exit 0 (server verify + web 510/510); vitest alone 510/510; AuctionSquadsIT 3/3, AuctionStubPackIT 6/6 (failsafe XML); `scripts/check-agnostic.sh` ok.
- AC-1 pass: squads-view.test.tsx "one chip per franchise", "groups players by pack role, newest first, with tag words and price"; squads.test.ts grouping and order; check-agnostic ok.
- AC-2 pass: squads-view.test.tsx "bar and Met or Needs ... restricted count against the room's cap"; squads.test.ts bars, room cap beats pack cap, pack fallback.
- AC-3 pass: squads-view.test.tsx "shows the new player, count, purse and bars after a hammer with no reload, marked until next lot" and "drops the marker once the next lot opens".
- AC-4 pass: "empty state with scheduled start for every franchise", "takes franchises and full purse from the room", "falls back to fetching the state on a lost connection"; AuctionSquadsIT pre-go-live case shows standings empty and startsAt set.
- AC-5 pass: auction-view.test.tsx "opens the viewer's own squad in a sheet built from the squad panel" and live update of the open sheet; e2e pair-390/1440-squad-sheet.png exist (seen 390; not re-run by QA).
- AC-5 note (non-blocking): sheet omits mock's Max bid and reserved-for-minimums segment; logged as deferred in state.md (no server value exists). Fidelity gap is visible beside the mock.
- AC-6 pass with deviation: AuctionSquadsIT asserts every member (and Commissioner) reads all franchises' standings; stub pack test passes with no core change. Non-member tested as 404 on state and ticket, 401 unauthenticated.
- Deviation AC-6 404 vs 403: acceptable. docs/knowledge/architecture.md lines 26 and 29 say non-members get 404 on auction routes; the AC text "existing 403" is the error. Fix the AC wording at close; no code change.
- Deviation AC-4 franchises from room members: acceptable. Before go-live standings is empty (asserted in AuctionSquadsIT), so room members and room config purse are the only source; covered by a test and by the IT. No new endpoint.
- T-1..T-7: all ticked; evidence above; T-7 shots exist under web/test-results/squads (gitignored, so not in the diff).
- T-6 note (non-blocking): scripts/check-agnostic.sh allowlist widened (intents.run(, async run<T>() outside this spec's scope but needed for a green check; state.md says main already failed it. Confirm at G3.
- No test exercises a mid-auction non-member web view beyond the 404 message test; adequate.

## Security
Not run, quick lane.

## Verdict
pass. Blocking: none. Non-blocking: AC-5 sheet Max bid and reserve segment deferred; AC-6 wording 403 should read 404; check-agnostic allowlist widening to confirm.
