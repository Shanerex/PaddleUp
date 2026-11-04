---
status: final
---
# Plan: 2026-11-02-04-rooms

## Approach
New `com.paddleup.rooms` module in MVCS layout, plus one Flyway migration (V5). At creation the room takes a snapshot of the chosen pack preset and the pack's vocabulary as JSONB (R-1, R-2, R-13). After that the room never reads the pack again, except for labels already in its snapshot.
Membership is derived, not stored: a user is a member if they are `rooms.commissioner_user_id` or they own a franchise (R-9, R-10, R-12). That keeps "exactly one Commissioner" true by construction.
All mutations lock the room row (`SELECT ... FOR UPDATE`) and are checked by one pure `RoomStateMachine` that mirrors the requirements matrix (R-4, R-7, R-8, R-10, R-11). Every change writes to the insert-only `room_log`.
Web: create wizard, join page and room page, all built on REST only. State refreshes on load/focus; there are no sockets (R-14, AC-18).

## Options considered
- A (chosen, approved): config and pack vocabulary as one JSONB snapshot with an int `config_version`. One read/write per save, validated in Java, and immune to later pack edits. Cost: no SQL-level constraints inside the config.
- B: normalised config tables (ladder, role minimums, quotas, rules). DB constraints apply, but that means about 6 tables, multi-row diffs on every save, and the rule set does not fit relations.
- C: store pack id/version plus overrides only. Smallest rows, but breaks R-2 (a pack change alters existing rooms), and a removed pack breaks the room.

## Design
Schema `server/src/main/resources/db/migration/V5__rooms.sql` (timestamptz everywhere):
- `rooms`(id uuid, state text CHECK in 6 states, commissioner_user_id uuid FK users, pack_id, pack_version, preset_id, pack_snapshot jsonb, config jsonb, config_version int, created_at, updated_at).
- `franchises`(id uuid, room_id FK, owner_user_id FK users, name, colour, monogram, created_at, updated_at). UNIQUE (room_id, owner_user_id), (room_id, lower(name)), (room_id, colour).
- `room_invites`(id, room_id, code UNIQUE, issued_by, issued_at, expires_at, revoked_at). Partial UNIQUE (room_id) WHERE revoked_at IS NULL.
- `room_log`(id bigserial, room_id, kind text, actor_user_id uuid NULL, detail jsonb, occurred_at). A trigger rejects UPDATE/DELETE (AC-15).
- `room_join_throttle_events`(scope, key_hash, occurred_at): rooms-owned, the same sliding-window pattern as V4.

Server `server/src/main/java/com/paddleup/rooms/`:
- model: `RoomState`, `Room`, `Franchise`, `Invite`, `RoomLogEntry`, `FranchiseColour` (12 hue keys crimson..rose per docs/design), `LogKind`.
- dto:
  - `CreateRoomRequest`(packId, presetId, name, competition); the creator picks the preset in the Sport step, and an unknown presetId gets 400.
  - `RoomDto`(id, state, myRole, commissioner, configVersion, config, packLabels), `RoomConfigDto`, `SaveConfigRequest`(version, config).
  - `FranchiseDto`, `FranchiseRequest`, `JoinRequest`(code, franchise), `InviteDto`(code, link, expiresAt), `HandoverRequest`(franchiseId), `TransitionRequest`(to), `TransitionDto`, `RoomRef`.
- service:
  - `RoomService`: create, get, saveConfig.
  - `RoomConfigFactory`: preset to config, plus the R-2 AI defaults.
  - `RoomConfigValidator`: returns every `Violation(field, message)`, checked against `pack_snapshot`.
  - `MembershipGuard`: 404 for non-members, 403 for non-Commissioners.
  - `InviteService` (uses `Clock`), `FranchiseService` (join, own, edit, remove), `CommissionerService` (handover).
  - `RoomStateMachine`: pure; the matrix table and next-state rules.
  - `RoomLifecycle`: the public API other modules use to call `advance(roomId, to, actor)`.
  - `JoinThrottle`: per user and per IP, checked before the code lookup (AC-9).
- repository: `RoomRepository`, `FranchiseRepository`, `InviteRepository`, `RoomLogRepository` (insert and select only), `JoinThrottleRepository`. All are Spring JDBC.
- controller (`/api/rooms/**`, `/api/join`):
  - `POST /api/rooms`; `GET /api/rooms/{id}`; `PUT /{id}/config`.
  - `GET|POST|DELETE /{id}/invite`.
  - `POST /{id}/franchises` (the Commissioner's own franchise); `PATCH|DELETE /{id}/franchises/{fid}`; `GET /{id}/members`.
  - `POST /{id}/commissioner`; `POST /{id}/transitions`; `GET /{id}/transitions`.
  - `POST /api/join/preview`, which returns the room name and taken colours and is throttled like a redeem; `POST /api/join`.
- exception:
  - Exceptions: `RoomNotFoundException`, `RoomForbiddenException`, `RoomConflictException(code)` (codes `illegal_state`, `stale_version`, `room_full`, `room_locked`, `duplicate`), `InvalidConfigException(violations)`, `InvalidInviteException` (one error for unknown/revoked/expired), `RoomsRequestException`.
  - `RoomsExceptionHandler` (scoped to rooms.controller) maps them to `{error, message?, violations?}` and to 429 with Retry-After.
- config: `RoomsProperties` (`paddleup.rooms`: invite-ttl 7d, code-length 12, default min/max managers 2/10, join-throttle failed-per-user 5/1m and failed-per-ip 20/1m), `RoomsConfig`. util: `InviteCodes` (SecureRandom, Crockford base32), `Sql`.
- Cross-module calls:
  - Uses `accounts.service.CurrentUser`, `accounts.dto.UserId`, `accounts.service.DisplayNameSanitiser.sanitise`, `packs.service.PackService`/`packs.dto`, and the app-wide `java.time.Clock` bean.
  - Client IP comes from the new public `accounts.service.ClientAddress`, which wraps `accounts.util.ClientIp` and the trusted-proxy-hops setting.
- On the move to `auction_live`, `RoomLifecycle` revokes the active invite (state matrix footnote).

Accounts (small changes):
- `ClientAddress` service; `AuthController` switches to it.
- `GoogleLoginHandlers` success redirect changes from `/profile` to `/auth/continue`.

Web `web/src/`:
- `api/client.ts` gets the room calls; regenerate `api/generated.ts`.
- `rooms/hues.ts`: the 12 hues, token-driven.
- `rooms/errors.ts`: maps 409 codes to clear messages and refetches.
- `app/rooms/new/`: wizard with steps sport (pack + preset picker), basics, squad, scoring, ai, invite. The room is created when Basics is submitted; later steps save config with its version.
- `app/rooms/[roomId]/`: page plus `room-header.tsx` (state badge for everyone; Commissioner controls for advance, invite, remove and handover).
- `app/join/[code]/`: name, monogram, colour picker.
- Sign-in return:
  - `auth/redirect.ts` adds a safe relative `next`, stored in localStorage so it survives a magic link opened in a new tab.
  - `sign-in-form.tsx` and `verify-link.tsx` honour it.
  - New `app/auth/continue/page.tsx` is where Google's success redirect lands.
- Every label comes from `packLabels`; UI copy names no sport terms (R-13).

Tests:
- Server: `server/src/test/java/com/paddleup/rooms/**`. ITs extend `AbstractApiIT`; a `MutableClock` @Primary test bean drives AC-5; a `@MockitoSpyBean PackService` drives AC-1. A context with `paddleup.packs.dir=src/test/resources/packs/stub` runs AC-17.
- Boundaries: an ArchUnit test at `server/src/test/java/com/paddleup/ModuleBoundariesTest.java`.
- Web: Vitest, co-located `*.test.tsx`.

Design refs (added 2026-11-03, design-fidelity loop): every web task cites its docs/design/source screen in tasks.md (T-26..T-32); gap screens are drawn there first.

## Architecture changes
Approved by the human in pass 2; to apply to docs/knowledge/architecture.md:
1. New "Rooms" section:
   - Config and pack vocabulary are snapshotted as JSONB at creation, and rooms never re-read packs.
   - Membership = Commissioner column + franchise owners.
   - `room_log` is append-only, enforced by a DB trigger.
   - Other modules change room state only through `rooms.service.RoomLifecycle`.
2. `accounts.service.ClientAddress` is the shared way to resolve the client IP. `java.time.Clock` (UTC) is an app-wide bean that every module injects by type, and tests replace it.
3. Module boundaries are enforced by an ArchUnit test. Other modules may touch only `service|dto|exception`. That replaces "Enforcement deferred", and a new decision record supersedes 2026-11-02-plain-packages-no-modulith-yet.
4. Web: signed-out page requests redirect to `/sign-in?next=<safe path>` (kept in localStorage), and every sign-in path returns to `next`. Google's success redirect goes to `/auth/continue`.

## Risks
- Join race overfills the room or duplicates a colour: lock the room row, and the unique constraints are the backstop that maps to 409.
- Invite codes are stored in plain text (the Commissioner must re-read them): 60 bits, 7-day TTL, throttled redeem, members-only read. Hash-only would need show-once UX.
- JSONB config drifts from its DTO: one Jackson mapper, plus a validator on every read path in tests.
- Pack removed from the registry after a room exists: the room keeps working from `pack_snapshot`, and only new rooms need the pack.
- The login return path could become an open redirect: accept only paths starting with a single `/`, with no scheme and no `//`.
- Stale tabs after `auction_live`: the server rejects with 409 `room_locked`, and the web shows the message and refetches (AC-19).
- Rooms are abandoned after Basics (created early): harmless rows in `setup`. Deletion is out of scope.
