---
status: final
---
# Plan: 2026-11-09-home-redesign

## Approach
P-1 comes first: T-1 draws every new or changed scene and records approval, and no UI task starts before that. Server work is small and additive. The hub list carries each room's auction start and a server clock sample (R-5, AC-4, AC-10), read through a rooms-owned port that `auction` implements, so `rooms` never imports `auction`. Packs gain three optional display fields: role short key, stat group, and short and minor money units. Each falls back to today's label (R-10, AC-11). Web: a pure `pickHero` function (R-2, AC-1) drives a new Franchise wall hub (R-1, R-3, R-4). `RoomWizard` gets a `settings` mode (R-7, R-8). One pinned-header layout serves both modes (R-9). The listed drift fixes follow (R-11). Docs and deferred-gap tasks close the spec (R-12).
Pass 2 answers (2026-11-10, all defaults): Q1 port A; Q2 `serverNow` on each summary; Q3 a bare `/setup` URL or `?section=` opens settings, and `?step=N` is create; Q4 lifeline order is a core constant, not a pack field; Q5 pack fields as listed, validated on load and on config save.

## Options considered
- A (chosen): `rooms.service.AuctionStartSource` is an interface owned by rooms and implemented in `auction.service`. `RoomService.listMine` fills `auctionStartsAt`. Trade-off: one request and one failure mode, the hero is right on first paint, and there is no compile-time cycle; the cost is one cross-module bean wired at runtime.
- B: a separate `GET /api/auctions/mine` that the hub merges client-side. Trade-off: `rooms` stays untouched, but two fetches can fail or go stale apart, and the R-2 order needs both responses before the first paint.
- C: `rooms` reads the `auctions` table with its own SQL. Trade-off: the simplest, but it breaks table ownership and gets past ModuleBoundariesTest in spirit only. Rejected.

## Design
- Server, rooms: `rooms/service/AuctionStartSource.java` (interface `Map<UUID, Instant> startsAt(Collection<UUID> roomIds)`). `RoomService` takes it through `ObjectProvider`, so it defaults to empty when no bean exists, as in rooms-only tests. `RoomSummaryDto` adds `@Schema(nullable) Instant auctionStartsAt`, set only when the state is auction_scheduled, plus `Instant serverNow` (the injected `Clock`, the same value on every item; it feeds the web `ServerClock`).
- Server, auction: `auction/service/AuctionStarts.java` implements the port. `AuctionRepository.startsAt(ids)` runs one `SELECT room_id, starts_at FROM auctions WHERE room_id = ANY(?)`. No Flyway change.
- Server, packs: `RoleDto(id, label, short)` (short <= 6 chars, plain text). `StatDto` adds `group` (<= 24, plain). `PresetDto.DisplayUnit(label, divisor, short, minor)` where `minor` = {label, short, divisor} and 0 < minor.divisor < divisor. Old constructors are kept. `PackParser` shapes and `PackValidator` enforce the limits; a bad value is a violation at load. Snapshots and old rooms read null and fall back. `RoomConfigValidator` also checks the new `displayUnit` fields, because the client sends config back on save.
- Packs JSON: `packs/cricket-t20.json` fills role `short`, stat `group`, and `displayUnit.short` and `minor`. The stub football pack test fixture fills its own values.
- Web, data: regenerate `web/src/api/generated.ts`. `web/src/rooms/money.ts` adds `formatMoneyShort` (major short unit, then the minor unit below one major). `web/src/rooms/scoring.ts` groups by `stat.group` in first-appearance order and falls back to today's grouping. The wizard uses `role.short ?? role.label`, `labels.restrictedCategories` order for quotas, and a core `LIFELINE_ORDER` constant for lifelines (lifeline types are core, not sport).
- Web, hub: `web/src/app/hub/hero.ts` holds the pure `pickHero(rooms) -> {hero, wall, past}` (R-2 tiers; ties on latest `updatedAt`; within tier 2 dated rooms come first, soonest first). It also exports `heroFacts(room)` (chip, CTA label and href, figure). The hub is made of `home-hub.tsx` (load, Retry, refetch on focus and visibilitychange), `hub/HeroBand.tsx`, `hub/WallTile.tsx`, `hub/PastSeasons.tsx`, `hub/EmptyWall.tsx` and `hub.module.css`. The countdown reuses `web/src/auction/clock.ts` fed by `serverNow`. Tiles are scoped with `teamStyle(colour)` from `web/src/rooms/hues.ts`; rooms without a franchise get the `--inverse` tokens and the admin `Monogram`.
- Web, settings: `/rooms/[roomId]/setup?section=basics|squad|scoring|ai|invite` (or no params) opens settings mode, and `?step=N` stays create mode. `RoomWizard` gets `mode` and `section` props. Settings mode has a rail titled with the room name, five sections, the sport as a locked fact, Save per section (whole config plus `version`, so the existing 409 `stale_version` is shown with the server value kept), read-only with the reason when `!isPreAuction(state)`, and Back to room. When `room.myRole !== "commissioner"` it does `router.replace('/rooms/{id}?note=settings')` and the room view shows the Note. Entry links: `room-frame.tsx:95` goes to `?section=basics` and `room-view.tsx:664` to `?section=squad`.
- Web, layout: `AppShell.module.css` bleed frame is `100dvh` minus the top bar. In `wizard.module.css` the header is fixed and only the body scrolls (`overflow:auto`).
- Drift (R-11): `auction.module.css` h1 margin 0; `kit.tsx` Monogram `admin` maps to `.adminTile`; `lobby.tsx`, `room-view.tsx`, `join-form.tsx:45`, `sign-in-form.tsx` (Apple button removed), `globals.css`, `import-view.tsx`, `import.module.css` and the CSS header comments with mock line refs.
- Server, settings authority: unchanged. `saveConfig` already enforces Commissioner-only (403), `preAuction` including auction_scheduled (409 `room_locked`), and the version check (409 `stale_version`). ITs cover AC-7 and AC-8.

## Architecture changes
- Rooms: add "The hub list reads auction start times through `rooms.service.AuctionStartSource`, implemented by `auction`; `rooms` never imports `auction`."
- Sport Packs: add "Optional display fields: `roles[].short`, `statSchema[].group`, `presets[].displayUnit.short` and `.minor {label, short, divisor}`, validated on load and on config save; a missing field falls back to the label."
- Web (new line): "Room settings is a mode of `/rooms/[roomId]/setup` (`?section=` or no params); `?step=` is the create flow."

## Risks
- The bare `/setup` URL changes meaning (create resume becomes settings). Mitigation: T-12 checks that every create redirect writes `?step=`, with a test.
- Wiring `AuctionStartSource` at runtime could fail if no bean exists. Mitigation: `ObjectProvider` fallback, plus an IT that runs with the auction bean.
- The new `displayUnit` fields arrive with every config save. Mitigation: `RoomConfigValidator` applies the same length, plain-text and divisor limits as the pack (T-5).
- The agnostic lint may miss the new short units in web. Mitigation: the units live only in pack JSON, and the AC-11 stub-pack test asserts that no cricket strings appear.
- Mock approval is slow and blocks the UI. Mitigation: server and pack tasks (T-2..T-7) need no P-1 and run in parallel.
