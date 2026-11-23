---
status: final
---
# Quick: 2026-11-22-auction-panels

## Change
The auction screen gets the panels drawn in Auction A Spotlight but never built: the Rivals and Bids chips and their sheets, the Bids feed card beside the lot, and the Your needs card with Max bid. All of it is derived on the client from data the server already sends (auction events, `standings`, `connected`, `config`, `bidLimits`); no endpoint, no schema change. Pack labels only. Fit Engine content stays with 24-fit-engine.
Design refs: Auction A Spotlight.dc.html (chip row, feed card, Your needs card, `rivals` and `feed` sheets); AuctionFeed.dc.html; AuctionRivals.dc.html; AuctionPanelStates.dc.html (load, empty and failed states).

## Acceptance criteria
- AC-1: The chip row shows Controls (Commissioner), Rivals, Bids and Squad (franchise owners) in that order, matching the mock at 390 and 1440.
- AC-2: Bids feed (card and sheet) lists the open lot's accepted bids and passes newest first, each with franchise monogram in its colour, name, amount in the pack display unit and time ago, ending with "Lot n opened at <base>". A new bid shows within one live update; a reconnect or new tab rebuilds it from `GET /auction/events` with the same rows.
- AC-3: Rivals sheet lists every franchise with monogram, name, manager, purse left, squad count, restricted-category `count/cap` with the pack label and the room cap, "needs" from unmet role minimums, and LEADING / OFFLINE markers from the lot leader and `connected`; it updates on each hammer and presence change.
- AC-4: Your needs card (franchise owners only) shows each pack role with a minimum as have/min tiles, squad count against the range, the restricted count, and Max bid taken from the viewer's `bidLimits` (the lowest `above`; a limit without `above` shows that no bid is possible). It opens the squad sheet.
- AC-5: A Commissioner without a franchise and any member watching get the chips and feed but no Squad chip or Your needs card. The stub football pack renders AC-2 to AC-4 with zero core changes; `scripts/check-agnostic.sh` passes.

## Tasks
- [x] T-1 (AC-2): Pure `feedView(events, lot, franchises, unit)` in `web/src/auction/feed.ts` with unit tests: order, passes, lot change resets, rebuild from the events page equals the live-patched list.
- [x] T-2 (AC-3, AC-4): Pure `rivalsView` and `needsView` in `web/src/auction/rivals.ts` from standings, connected, config and bidLimits, with unit tests incl. max-bid edge cases and both packs.
- [x] T-3 (AC-1): Chip row and sheet shells on the auction screen built to Auction A Spotlight.
- [x] T-4 (AC-2): Feed card and Bids sheet wired to `useLive` (live events, fetch on reconnect); component tests.
- [x] T-5 (AC-3): Rivals sheet built to AuctionRivals.dc.html; component tests for leading, offline and needs.
- [x] T-6 (AC-4, AC-5): Your needs card built to the Spotlight card, opens the squad sheet; watcher and no-franchise variants; component tests.
- [x] T-7 (AC-1, AC-2, AC-3, AC-4, AC-5): Playwright on `make dev` (alt ports): bids from two managers, new tab, stale state; 390 and 1440 shots beside the mocks; run check-agnostic.
