---
feature: 2026-11-22-auction-panels
phase: done
status: approved
g1: none
g2: approved:Shanerex:2026-11-22
g3: none
bump: minor
next: open a PR; a human merges
---
# State: 2026-11-22-auction-panels

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-22 intake: quick lane, out-of-band UI spec; scope: auction Rivals and Bids chips and sheets, Bids feed card, Your needs card with Max bid; web only, no endpoint or schema change; takes the items deferred from 2026-11-02-10-squads and 2026-11-02-24-fit-engine
- 2026-11-22 intake: design refs Auction A Spotlight, AuctionFeed, AuctionRivals; AuctionRivals "/8 OS" is the room's restricted category label and cap
- 2026-11-22 plan: quick.md written; g2 approved by Shanerex; phase build
- 2026-11-22 build: T-1..T-7 done; make test and make lint green; e2e panels.spec.ts passes on alt ports; phase verify
- 2026-11-22 build: deferred to a later spec: a tab whose socket is down has no state at all (existing Reconnecting screen), so it shows no feed
- 2026-11-22 verify: deferred to a later spec: CR-5 start the feed cursor at the open lot's opening seq (needs the server to expose it); needs card outline is the global :focus-visible rule only
- 2026-11-22 verify: fix loops 1-2 (CR-1..4, CR-6..14) and round 3 review; human asked for loop 3 and the loading/failed states drawn in docs/design/source
- 2026-11-23 verify: loop 3 CR-15, CR-16; CR-17 drawn as AuctionPanelStates.dc.html, approved by Shanerex; loop 4 matched it
- 2026-11-23 verify: review rounds 4-6 and loops 5-7 with human approval: CR-18..27, CR-29..32 fixed, CR-21 new files staged; CR-28 kept as drawn (Spotlight feed card max-height 150)
- 2026-11-23 verify: final rx:qa PASS (vitest 620, e2e panels 2/2); review.md verdict pass; bump minor
- 2026-11-23 close: 1 decision, 2 learnings written; quick.md final; rx-check done run
- 2026-11-23 close: rx-check done green; phase done
