---
status: final
---
# Tasks: 2026-11-02-04-rooms

## Tasks
- [x] T-1 (AC-17, AC-15): V5__rooms.sql: rooms, franchises, room_invites, room_log (UPDATE/DELETE trigger), room_join_throttle_events; RoomsSchemaIT checks the migration on empty Postgres, timestamptz columns, unique constraints and the trigger
- [x] T-2 (AC-1): rooms.model (RoomState, FranchiseColour, config records) and RoomConfigFactory: chosen preset + pack vocabulary snapshot + pack id/version + R-2 AI defaults; unit test against both Cricket T20 presets
- [x] T-3 (AC-2): RoomConfigValidator returns every R-3 violation by field, checked against pack_snapshot (role/tag/stat ids, including scoring-rule stats); one unit test per rule
- [x] T-4 (AC-1): Room/RoomLog repositories (JSONB), RoomService.create (packId + presetId; unknown preset gets 400), POST /api/rooms and GET /api/rooms/{id}, RoomsExceptionHandler; IT asserts that config is unchanged after a spied PackService changes its presets
- [x] T-5 (AC-16): MembershipGuard: non-member gets 404 on room read, members, invite and transitions; non-Commissioner gets 403; IT over every endpoint
- [x] T-6 (AC-2, AC-3, AC-16): PUT /api/rooms/{id}/config with version: 400 lists the violations, 403 for non-Commissioner, 409 in auction_live, 409 stale_version with no change; a log row on save; IT
- [x] T-7 (AC-4, AC-16): InviteCodes util (12 Crockford base32 chars, SecureRandom) and InviteService issue/read/revoke/regenerate with log rows; GET/POST/DELETE /{id}/invite; IT
- [x] T-8 (AC-5): MutableClock test bean; IT: a code redeems at 6d23h and is rejected at 7d with the same error as revoked; regenerate restarts the 7 days; no client time is read
- [x] T-9 (AC-7): FranchiseRules: name sanitised to 2-30 chars, unique ignoring case; colour from the 12 hues and unique; monogram 1-3 chars; unit tests including markup stripping
- [x] T-10 (AC-6, AC-8): POST /api/join (room row lock): creates the franchise, a re-redeem returns the same one, room_full/room_locked/invalid_invite create nothing; POST /api/join/preview; IT
- [x] T-11 (AC-9): public accounts.service.ClientAddress (AuthController switches to it) and rooms JoinThrottle (5 failures/min per user, 20/min per IP, from RoomsProperties), checked before the code lookup; IT: 429 with Retry-After, including for a valid code
- [x] T-12 (AC-7): PATCH /{id}/franchises/{fid}: owner gets 200 in setup and 409 in auction_live, another member gets 403, duplicate name/colour gets 409; IT
- [x] T-13 (AC-10): POST /{id}/franchises (Commissioner's own franchise, no code): R-6/R-7 rules, counts toward min/max, a second one gets 409; admin-only Commissioner flows still work; IT
- [x] T-14 (AC-11, AC-8, AC-16): DELETE /{id}/franchises/{fid}: 200 in retention frees the slot and the removed user can rejoin; 409 in auction_live, 400 on self, 403 for a manager; log row; IT
- [x] T-15 (AC-12, AC-16): POST /{id}/commissioner: target must own a franchise, else 400; the old admin-only Commissioner gets 404 afterwards; 409 in completed; log row; IT
- [x] T-16 (AC-13, AC-14): RoomStateMachine (pure): the matrix, forward-only next states, the retention skip, the min-managers gate; table-driven unit tests
- [x] T-17 (AC-13, AC-14, AC-15): POST/GET /{id}/transitions (pre-auction only over REST) and the RoomLifecycle.advance service API for other modules; revoke the invite on auction_live; IT for every caller
- [x] T-18 (AC-17): ArchUnit ModuleBoundariesTest: no module touches another's controller/repository/model/config/util; stub-football IT creates a room and joins with zero core changes; make lint check-agnostic passes
- [x] T-19 (AC-18): web api client functions + regenerated generated.ts + rooms/hues.ts (12 hue keys to design tokens); Vitest for the client
- [x] T-20 (AC-18): app/rooms/new wizard (Sport step picks pack + preset, room created when Basics is submitted, later saves carry the version, invite step shows code/link/expiry); Vitest
- [x] T-21 (AC-18): app/rooms/[roomId] page + room-header (state for every member; Commissioner controls for advance/invite/remove/handover), refreshing on load and focus; Vitest
- [x] T-22 (AC-19): app/join/[code] page: franchise form, valid code redirects to the room, a revoked/expired code shows an error; Vitest
- [x] T-23 (AC-19): sign-in return: redirect.ts adds a safe `next` (stored in localStorage), honoured by sign-in-form/verify-link and the new /auth/continue; GoogleLoginHandlers success redirects to /auth/continue; Vitest including open-redirect rejection
- [x] T-24 (AC-19, AC-3): rooms/errors.ts maps 409 room_locked/stale_version/illegal_state to a clear message and a refetch; Vitest for a stale tab after auction_live
- [x] T-25 (AC-18, AC-19): browser check via make dev + Playwright: wizard end to end, a second member's tab after a transition and reload, the join link signed out, a stale tab
- [x] T-26 (AC-18, AC-19): gap screens drawn in docs/design/source and approved by the human before code. Design refs: Home joinCode/joinError/wizard:6 + hub cards, Room setup/sheets/editFranchise/notFound; copy matches spec limits
- [ ] T-27 (AC-18): web foundation: styles/tokens.css mirrors source/paddle.js (parity test), ui/ primitives (DS 07, 08, 11, 13), AppTopBar, WizardShell, RoomShell. Design refs: Design System.dc.html, Home 41-48 + 130-140 + 239-242, Room 18-42 + 341-346
- [ ] T-28 (AC-18): GET /api/rooms lists the caller's rooms (RoomSummaryDto, removed members excluded); IT; regenerate generated.ts. Design refs: Home hub
- [ ] T-29 (AC-18): / is the Home hub + empty state (HOME = /); profile from the avatar. Design refs: Home hub, empty
- [ ] T-30 (AC-18): wizard rebuilt, room id in the URL after create (reload never duplicates), Commissioner franchise optional. Design refs: Home wizard:1-6
- [ ] T-31 (AC-18, AC-19): room page and join rebuilt (typed code, error variants, confirm sheets, edit/own franchise, busy/stale fixes). Design refs: Room setup + sheets, Home join, joinCode, joinError
- [ ] T-32 (AC-18, AC-19): Playwright flows + side-by-side mock vs app screenshots at 390 and 1440 for every screen; deltas fixed; review.md fidelity table
