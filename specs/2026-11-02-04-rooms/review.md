---
status: final
---
# Review: 2026-11-02-04-rooms

## QA
Re-verify after the design-fidelity loop (T-26..T-32). Ran `make test` (449 server: surefire 246 + failsafe 203, 0 failures; 128 web in 20 files) and `make lint` (spotless, eslint, check-agnostic and its self-test): all green. Read the loop diff, the tests below and docs/design/README.md. Playwright / `make dev` was not re-run in this pass.
- AC-1..AC-17 pass: server suites green; the loop changes to PackDto/PackParser/RoomConfigFactory/JoinService did not regress RoomCreateIT, RoomConfigFactoryTest, JoinIT, StubPackRoomIT. Earlier passes (RoomConfigIT large-config cases, StoredJson jsonb readback, 64 KB config cap, JsonLimitsConfig/JsonLimitsIT) still hold.
- AC-18 pass: room-wizard.test.tsx (pack display name, room created on Basics with id in URL, resume without duplicate, version on later saves, stale edit kept, invite step shows code and expiry), room-view.test.tsx (new state after a transition on visibility), home-hub.test.tsx.
- AC-19 pass: continue-redirect, redirect and sign-in/verify-link tests (signed-out return, open-redirect rejected); join-form.test.tsx (valid, invalid/revoked/expired, revoked between preview and join, full, auction-live, Retry-After); stale tab after auction_live gives a clear message and reload (room-view.test.tsx:198, :309).
- T-28 pass: MyRoomsIT (6 tests: empty, admin-only Commissioner, manager's own franchise, removed manager excluded, newest first, 401 signed out).
- T-26/T-27 pass: tokens.test.ts proves light, dark and team token blocks equal docs/design/source/paddle.js.
- Pack display fields pass: PackParserDisplayTest, stub-pack fixture, JoinIT richer preview.
- Hard invariant pass: check-agnostic ok; grep of web/src and server/src/main for sport, role and currency words found nothing outside tests; money.ts reads symbol, unit and divisor from the room config.
- Non-blocking (AC-18): the invite link is offered only through Share link / copy, not shown as text, and no wizard test asserts it (room-wizard.tsx:610-614).
- Non-blocking (T-32): Playwright flows and the 390/1440 mock-vs-app screenshots live in gitignored `.playwright-mcp/`, so end-to-end AC-18/19 is not repeatable by `make test`. The CLAUDE.md real-browser check rests on the dev run recorded at T-32 and was not repeated here.
- Non-blocking (fidelity): 7 raw `#fff` values (ui/kit.module.css 5, join/join.module.css 2) bypass tokens. Data-only differences from the mocks: pack unit "Crore" vs mock "Cr", role labels instead of ids, pack role order.
- Accepted deviations: ModuleBoundariesTest is a source scan (not ArchUnit); RoomState in rooms.dto; GET /api/rooms/packs, ClientAddress.limitKey, MutableClock, max_connections=400; GET invite is Commissioner-only (stricter than plan).
- Follow-up before ship (human-owned): docs/knowledge/architecture.md lacks the Rooms section; plan Architecture changes 1-4 unapplied.

## Security
Scope: loop commits dcc14eb and c784d2e (tree clean). S-1..S-10 are closed as recorded in earlier passes (field and list caps, JSON-only bodies, codeLength >= 10, JSON depth/size limits, StoredJson readback, 64 KB config cap, decimal bounds). No new findings.
- GET /api/rooms: session required (MyRoomsIT 401), parameterised query (RoomRepository.java:74-84), capped at 100. Removed managers and the former Commissioner after handover drop out; only the caller's own franchise is returned.
- Join preview (JoinService.java:79-98): session, throttle and valid code still required; new fields show only what a code holder sees after joining.
- Pack display fields: trusted pack files, length-capped, icon limited to [a-z0-9_]{1,40} (PackParser.java:156-176), rendered as text.
- Web: no dangerouslySetInnerHTML/innerHTML/eval; user text escaped; inline styles from a fixed hue table; API paths use encodeURIComponent; join code reduced to [A-Z0-9] before navigation; safeNext rejects `//`, schemes, backslashes, control chars, /auth, /api; `pu_next` checked on save and read, 1 h expiry, read once.
- S-5 minor, accepted in plan: invite codes stored in plain text (V5__rooms.sql:44).

## Verdict
pass. Blocking: none. Open: S-5 accepted; non-blocking items above (AC-18 invite-link test, T-32 evidence not committed, raw #fff); architecture.md follow-up before ship (human-owned).
