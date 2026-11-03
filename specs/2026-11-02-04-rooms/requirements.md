---
status: final
---
# Requirements: 2026-11-02-04-rooms

## Problem
Friend groups need a private league with its own rules, prefilled from a Sport Pack, that others join by invite and where the Commissioner stays in control. Nothing models a room, membership or franchise yet. Scope: FR-10, FR-11, FR-12, FR-13, FR-15, NFR-7 (invites), PRD 4.2.

## Requirements
- R-1: A signed-in user creates a room by choosing a Sport Pack (from the packs service) and automatically becomes its Commissioner; the room starts in `setup`; the pack is fixed after creation (v1 stricter than FR-15).
- R-2: At creation the room copies the pack's presets (recording pack id and version) into room-owned editable config: name, competition/season, min/max managers, purse, currency and display unit, increment ladder, squad min/max, role minimums, restricted-category quotas, retention rules, scoring rule set, AI settings (`ai_enabled` default on, lifelines per type default 1, report card visibility default room-wide). Later pack changes never alter an existing room.
- R-3: Config is validated on create and every save, reporting all violations: role minimums sum <= squad min <= squad max; 2 <= min managers <= max managers and max >= current franchise count; purse > 0; ladder non-empty with positive steps; quotas and lifeline counts integers >= 0; role, tag and stat ids exist in the pack.
- R-4: Only the Commissioner can change config, and only while state is `setup`, `retention` or `auction_scheduled`; saves carry a config version and a stale version is rejected.
- R-5: The Commissioner gets one active invite code (unguessable, >= 50 bits) and a join link; it expires 7 days after issue by the server clock (Instant UTC); they can revoke it or regenerate it (new 7-day code, old one revoked); an expired code is rejected like a revoked one; joined members are unaffected.
- R-6: A signed-in user redeeming a valid code joins and creates a franchise: name 2-30 chars sanitised and unique per room (case-insensitive), colour from the 12 design hues unique per room, monogram 1-3 chars. One franchise per user per room; re-redeeming returns the existing one.
- R-7: Joining is refused when the room is full (franchise count == max managers), the code is revoked or expired, or the state is `auction_live` or later; removing a manager frees the slot, and a removed user may rejoin with a valid active code. Failed code attempts are rate-limited per user and per IP.
- R-8: A franchise owner can edit their franchise name, colour and monogram under R-6 rules until state `auction_live`.
- R-9: Owning a franchise is optional for the Commissioner: they may create one for themselves (same R-6 rules, no code, subject to R-7 full and state limits) and play, or stay admin-only. Only franchises count toward min/max managers.
- R-10: The Commissioner can remove another manager (franchise deleted) before `auction_live`, and hand commissionership to a manager with a franchise in any state except `completed`. Handover is immediate and a room always has exactly one Commissioner; the old Commissioner keeps their franchise if they had one, otherwise they leave the room.
- R-11: Room state moves only forward through `setup, retention, auction_scheduled, auction_live, season_live, completed` per the matrix (`retention` may be skipped); every transition is explicit, actor-attributed and rejected when illegal. Leaving `setup` needs franchise count >= min managers, whether or not the Commissioner has one. The Commissioner triggers pre-auction transitions over REST; later ones are called by other modules through the rooms service.
- R-12: Room, members, franchises, invite and state are visible only to members (Commissioner and franchise owners); non-members get 404. An append-only room log records transitions, removals, handovers, invite revoke/regenerate and config saves (actor, Instant UTC); members can read transitions.
- R-13: Rooms code, schema and UI strings name no sport, role, stat or currency; labels come from the room's pack, and the stub football pack runs a room with zero core changes (NFR-12). Rooms is a new module (Flyway, Instant UTC) exposing only service, dto and exception packages; membership references the accounts user id.
- R-14: Web: 6-step create wizard (Sport, Basics, Squad rules, Scoring, AI, Invite), join screen, and a room header showing the current state to every member, with Commissioner controls; a signed-out visitor to a join link signs in, then returns to it.

## State x entry point (room lifetime)
Join = redeem code or Commissioner creates own franchise; Franchise = owner edits own; Advance = next state; "no" = 409. Join also needs room not full and code active.
| State | Join | Config | Franchise | Remove | Handover | Advance |
|---|---|---|---|---|---|---|
| setup | ok | ok | ok | ok | ok | retention or auction_scheduled |
| retention | ok | ok | ok | ok | ok | auction_scheduled |
| auction_scheduled | ok | ok | ok | ok | ok | auction_live |
| auction_live | no | no | no | no | ok | season_live |
| season_live | no | no | no | no | ok | completed |
| completed | no | no | no | no | no | none |
Other cells: code revoked, expired or room full = join refused, state unchanged; non-member = 404 on every column; non-Commissioner = 403 on Config, Remove, Handover, Advance; the code lives until revoked, regenerated, 7 days or `auction_live`; an admin-only Commissioner has no Franchise cell.

## Acceptance criteria
- AC-1: Creating a room from the Cricket T20 pack returns `setup`, caller as Commissioner and config equal to the pack presets plus R-2 AI defaults; after the pack presets change in a test, the room's config and recorded pack id and version are unchanged (R-1, R-2).
- AC-2: Each R-3 violation (role minimums sum > squad min, squad min > max, max managers < franchises, purse 0, empty ladder, negative quota, unknown role id) returns 400 naming that field; a valid save persists (R-3).
- AC-3: A non-Commissioner config save returns 403; the Commissioner's save in `auction_live` returns 409; a stale-version save returns 409 and changes nothing (R-4).
- AC-4: The invite endpoint returns a code of >= 10 base32 characters and a link; revoke makes redeem fail without removing members; regenerate gives a new working code and the old one fails (R-5).
- AC-5: With a test clock, a code redeems at 6 days 23 hours after issue and is rejected (same error as revoked) at 7 days; regenerate restarts the 7 days; the client clock is not read (R-5).
- AC-6: Redeeming a valid code creates one member with a franchise; repeating it returns the same franchise and the member count is unchanged (R-6).
- AC-7: A name of 1 or 31 chars, a duplicate differing only by case, a taken colour or a monogram over 3 chars is rejected (400 or 409), markup in a name is stored sanitised; the owner renames in `setup` (200) and in `auction_live` (409), another member gets 403 (R-6, R-8).
- AC-8: With franchises == max managers a redeem is refused and creates no franchise; after a removal the same redeem succeeds, including for the removed user (R-7).
- AC-9: More than the configured failed redeem attempts per minute from one user returns 429, including for a valid code (R-7).
- AC-10: Commissioner with a franchise: creating it follows R-6 rules, counts toward min and max, and a second one returns 409; admin-only Commissioner: has no franchise, does not count toward max, and every other Commissioner action still works (R-9).
- AC-11: The Commissioner removes a manager in `retention` (franchise gone, slot freed); in `auction_live` it returns 409; removing self returns 400; a manager attempting it gets 403 (R-10).
- AC-12: Handover to a franchise owner makes them the sole Commissioner; an old Commissioner with a franchise stays a manager, an admin-only one is no longer a member (404 afterwards); to a non-member or franchise-less user returns 400; in `completed` returns 409 (R-10).
- AC-13: Advancing `setup` with franchises < min managers returns 409 and state stays, for a Commissioner with or without a franchise; with enough franchises it succeeds to `retention` or directly to `auction_scheduled` (R-11, R-9).
- AC-14: Skipping forward past `auction_scheduled` (`setup` to `auction_live`), moving backward, or advancing from `completed` returns 409 for every caller; the rooms service moves `auction_scheduled` through `auction_live` and `season_live` to `completed` (R-11).
- AC-15: Each transition appears in the room log with actor and Instant UTC and is readable by any member; no update or delete path exists on the log (R-11, R-12).
- AC-16: A non-member gets 404 (not 403) on room read, members, invite and transitions; removals, handovers, revoke/regenerate and config saves each add a log row (R-12).
- AC-17: A grep of rooms code, migrations and UI strings finds no sport, role, stat or currency term; a test creates a room and joins from the stub football pack; Flyway migrates an empty Postgres with Instant UTC columns (R-13).
- AC-18: Web: the wizard creates a room end to end and shows the invite code, link and expiry; a second member's tab shows the new state after a transition and reload (R-14).
- AC-19: Web: a signed-out visitor opening a join link signs in and lands back on it, where a valid code creates the franchise and a revoked or expired code shows an error; a stale tab acting after `auction_live` shows a clear error, not silent success (R-14, R-4).

## Out of scope
- Auction scheduling time, lobby and entry into `auction_live` (auction spec); retention/RTM content (retention spec); scoring-rule editing (scoring spec).
- Multiple seasons per room (FR-14), room deletion or archive, a manager leaving on their own, deleting one's own franchise, multiple concurrent codes, email invitations.
