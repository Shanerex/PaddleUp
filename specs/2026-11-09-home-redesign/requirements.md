---
status: final
---
# Requirements: 2026-11-09-home-redesign
## Problem
Home is a neutral grid of white cards with no franchise identity and a weak desktop layout; outside-room screens clash with room and auction. Room settings reopens the NEW ROOM wizard at "2 of 6", and visual drift remains. Touches FR-1, FR-2, FR-10..FR-13, FR-15.
## Requirements
- R-1: Home hub (FR-2) is a Franchise wall: one hero band full width, below it a wall of tiles, each painted in that room's own franchise colour; no-franchise rooms use the inverse surface.
- R-2: The hero is chosen by a pure, deterministic priority: auction_live, auction_scheduled (soonest start), retention, setup where the viewer is Commissioner and an action is possible (or a member with no franchise), season_live, setup waiting on others; completed is never hero. Ties go to the latest `updatedAt`.
- R-3: Hero shows monogram, room name, franchise name, phase stepper (Setup, Retention, Auction, Season), one 56px DM Mono figure with a facts-first status line, and one CTA labelled by state. Tiles show state chip, one mono figure and a foot line.
- R-4: A single room shows the hero only; completed rooms collapse into a Past seasons strip (room name, your franchise monogram, "Completed"); `+ Create a room` is the last tile. Empty state is a blank wall of ghost tiles in the 12 franchise hues with Create and Join, copy unchanged.
- R-5: The hub list carries each scheduled auction's start (UTC Instant, rendered local) via additive data; `rooms` must not depend on `auction`. No Flyway change expected.
- R-6: The outside-room world (sign-in, join, joinCode, joinError, roomMissing, wizard, Room settings, profile, admin import) follows the same shell and language; profile gets its first mock; the mock's Apple button is dropped (FR-1).
- R-7: Room settings is its own mode of `/rooms/[roomId]/setup`: rail titled with the room name, sections Basics, Squad rules, Scoring, AI, Invite, the sport as a locked fact, no step counter, "Back to room" always visible; entry from "Room settings" and "Edit rules".
- R-8: Settings save per section with the config `version` conflict check (FR-12). Editable through auction_scheduled (the pack is locked from scheduled, FR-15), read-only from auction_live. A non-commissioner is redirected to the room with a Note that only the Commissioner changes settings; the server still refuses their saves.
- R-9: Wizard and settings header stay pinned and only the body scrolls, in create mode too.
- R-10: Wizard role cards (short key, pack order), lifelines, quotas, scoring groups and money units come from optional Sport Pack fields that fall back to the existing label; cricket and stub football JSON both populate them; no sport string in core.
- R-11: Fix visual drift: auction header h1 margin, Monogram admin tile, lobby, room view, join "full", sign-in and admin import copy and sizes, wrong mock line refs in CSS comments.
- R-12: Feature-shaped gaps are logged in the owning specs' state.md, not built.
Design refs: docs/design/README.md; Home.dc.html `hubNow`, `empty`, `join`, `joinCode`, `joinError`, `roomMissing`, `wizard:1-6` plus new scenes to draw: hub per state (hero and wall, 390 and 1440), Past seasons, `settings` mode, profile, sign-in wall; Room.dc.html (lobby, "Open the lobby"); AdminImport.dc.html; App Screens.dc.html.
## State x entry-point matrix
Every cell ends in server truth, never local state. Hero tier is the R-2 order (1 first).
| Case | Result |
|---|---|
| setup, Commissioner | tier 4: CTA "Invite managers"; joined count `4/6` |
| setup, manager with franchise | tier 6: tile "Waiting for the Commissioner", CTA Open room |
| setup, no franchise | tier 4: CTA "Take your seat" |
| retention | tier 3: CTA Open room |
| auction_scheduled | tier 2: local start, countdown, CTA "Open lobby"; no start yet shows "Time not set", ranks after dated ones |
| auction_live | tier 1: live status, CTA to the auction screen |
| season_live | tier 5: CTA Open room |
| completed | Past seasons strip only |
| 0 rooms | empty wall |
| 1 room | hero only (a lone completed room: strip plus empty wall) |
| many rooms | hero plus tiles in R-2 order |
| load error | Note with Retry; no stale rooms shown as current |
| stale tab | refetch on focus; a CTA tapped on an outdated state lands where the server says |
| new tab | same as first load, same hero |
| settings editable (setup to auction_scheduled) | per section save; sport locked; version conflict shows the server's message, nothing overwritten |
| settings auction_live or later | read-only with the reason; server refuses saves |
| settings non-commissioner | redirect to room with Note; server answers 403 |
## Acceptance criteria
- AC-1: Unit test over fixtures covering every state pair and tie: the hero follows R-2, same input gives same output, completed is never hero.
- AC-2: With 0, 1 and many rooms the hub renders as R-4 and the matrix rows; completed rooms appear only in Past seasons, and the strip is absent without any.
- AC-3: Each state renders the chip, CTA label and figure of its matrix row; a tile's hue equals that room's franchise colour, and a no-franchise room uses the inverse surface.
- AC-4: The scheduled hero countdown stays within 1s of the server deadline with the browser clock skewed 5 minutes; with no start it shows "Time not set".
- AC-5: A failed load shows the Note and Retry; a stale tab updates within 2s of focus; a new tab shows the same hero as the first.
- AC-6: Room settings and Edit rules open settings mode: room-name rail, five sections, locked sport, no "NEW ROOM" or "n of 6", Back to room visible at 390 and 1440.
- AC-7: A save with an outdated `version` shows the conflict, keeps the server value and changes nothing; from auction_live the fields are read-only and a save call is refused with the server's reason.
- AC-8: A non-commissioner opening `/rooms/[roomId]/setup` lands on the room with the Note and sees no settings; their save call gets 403.
- AC-9: At 390x844 and 1440x900 the wizard and settings header keeps its bounding box while the body scrolls (Playwright), in both modes.
- AC-10: IT: the room list returns the start Instant for a scheduled auction and null otherwise; `generated.ts` is regenerated; module dependency checks pass.
- AC-11: With the stub football pack the wizard shows no cricket role, key, stat, group or currency string; with cricket, role order and units match the pack; a pack without the new fields still renders its old labels.
- AC-12: Auction header height equals its mock and h1 margin is 0; the admin Monogram renders its tile on hub and wizard.
- AC-13: Screenshots at 390 and 1440 of every changed screen sit beside their mock with no visible deviation; sign-in has no Apple button.
- AC-14: Each R-12 gap appears in its owning spec's state.md; `make test` and `make lint` pass.
## Prerequisites
- P-1: Before any UI code, draw the new and changed scenes of the Design refs in docs/design/source and record human approval in state.md; R-1, R-3, R-4, R-6, R-7 are not buildable until approved.
## Out of scope
- Season, standings and report screens; final place in Past seasons (deferred to standings spec 14); rivals, bids, squad chips, Table tile, locked-bid reason, Start now and the other gaps of R-12; new fonts; multi-season (FR-14); changing the pack after scheduling (FR-15).
