---
status: final
---
# Review: 2026-11-22-auction-panels

## QA
Final run after fix loop 6: web vitest 616 pass, tsc, eslint and check-agnostic clean; e2e panels.spec.ts 2/2 on alt ports (web 3001, server 8081, NEXT_PUBLIC_WS_URL=ws://localhost:8081/api/ws).
- AC-1 pass: chip order in auction-view.test.tsx and e2e; 390/1440 chip row matches Auction A Spotlight.
- AC-2 pass: feed cut at the open lot's latest lot_opened, re-opened-lot and dedupe tests, bounded store with no lot (CR-23), amount guard (CR-25); e2e two managers, new tab, stale tab.
- AC-3 pass: rivals tests; LEADING only while a lot is open (CR-18); OFFLINE and presence in e2e.
- AC-4 pass: needsView never shows 0 for pool-only counts (CR-15); failed card keeps Max bid and opens the squad sheet (CR-27).
- AC-5 pass: watcher and no-franchise tests, stub pack, check-agnostic ok.
- T-1..T-7 pass.
- CR-17 pass: loading and failed states match docs/design/source/AuctionPanelStates.dc.html (approved 2026-11-23).
- Fixed in loops 1-6: CR-1..4, CR-6..27 except CR-5 and CR-28, plus CR-29.
- Gap (non-blocking): loading and failed states have component tests only; no e2e forces a pool failure, no screenshot pair beside AuctionPanelStates.
- Deferred: CR-5 (lot-opening seq needs a server change). Kept as drawn: CR-28 (feed card clips the "Lot n opened at" line, max-height 150 in the mock).
- Round 6 code review nits CR-30 (no auto-retry on permanent 4xx), CR-31 (needs card aria-busy), CR-32 (pending counts labelled "Loading") fixed in loop 7; vitest 620.
- Tooling note: `next dev` reports a known security issue in Next 16.4.3 (suggests `next upgrade`); not acted on.

## Security
Not run, quick lane.

## Verdict
pass. No blocking AC-n or T-n.
