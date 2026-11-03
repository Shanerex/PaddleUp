---
status: final
---
# Review: 2026-11-02-04-rooms

## QA
Ran (human-directed pass 4): `make test` (437 server tests: surefire 240 + failsafe 197, 0 failures; none lost; 103 web tests), `make lint` (ok). No AC regressed; packs use their own JsonMapper.
- T-3 fixed (was blocking, same root as S-9): stored jsonb read via rooms.util.StoredJson (no doc limit); config written NON_NULL and capped at 64 KB (400 invalid_config, version unchanged). RoomConfigIT large-config cases (100 x 50 bands of -1e6; role-conditional x 50 roles x 50 {} bands) save 200|400, then GET and repair PUT succeed, never 500. NON_NULL keeps semantics; old rows with nulls still read; AC-1 snapshot checks pass. Note: tests accept 200 or 400 on first save.
- AC-1 pass: RoomCreateIT, RoomConfigFactoryTest (config, pack id and version copied).
- AC-2 pass: RoomConfigValidatorTest (23 rules), RoomConfigIT 400 with field names.
- AC-3 pass: RoomConfigIT 403, 409 auction_live, 409 stale_version with no change.
- AC-4 pass: InviteIT (12-char code, link, revoke keeps members, regenerate kills old), InviteCodesTest.
- AC-5 pass: InviteExpiryIT with MutableClock at 6d23h and 7d; expired same error as revoked; client clock ignored.
- AC-6 pass: JoinIT re-redeem returns same franchise.
- AC-7 pass: FranchiseRulesTest, FranchiseEditIT (duplicate name/colour 409, markup stripped, lock at auction_live).
- AC-8 pass: JoinIT full room and rejoin after removal; RemoveManagerIT frees slot.
- AC-9 pass: JoinThrottleIT 429 per user and per IP, window lifts.
- AC-10 pass: CommissionerFranchiseIT (counts toward min/max, admin-only flows).
- AC-11 pass: RemoveManagerIT (retention ok, auction_live 409, self 400, manager 403).
- AC-12 pass: HandoverIT (all four outcomes, admin-only old Commissioner 404 after).
- AC-13 pass: TransitionIT min-franchise gate with and without Commissioner franchise; retention skip.
- AC-14 pass: TransitionIT, RoomStateMachineTest; REST cannot start the auction.
- AC-15 pass: TransitionIT log readable by members; RoomsSchemaIT trigger blocks UPDATE/DELETE.
- AC-16 pass: MembershipIT every endpoint (404 non-member, 403 manager on Commissioner endpoints).
- AC-17 pass: check-agnostic ok, StubPackRoomIT, RoomsSchemaIT timestamptz.
- AC-18 pass (unit): room-wizard.test, room-view.test; real-browser run reported by dev (T-25), not re-run by QA.
- AC-19 pass (unit): join-form, redirect, continue-redirect, stale-tab tests; real-browser run reported by dev, not re-run by QA.
- T-18 deviation accepted: ModuleBoundariesTest is a source scan with a self-test, not ArchUnit (pom.xml protected); record in decision.
- Deviation accepted: RoomState in rooms.dto (other modules need it; model is private).
- Deviation accepted: GET /api/rooms/packs, profile "Create a room" link, ClientAddress.limitKey, MutableClock, max_connections=400.
- Deviation accepted: GET invite Commissioner-only (stricter than plan; R-12/AC-16 hold).
- T-18 fixed: JSON limits moved to app-level com.paddleup.config.JsonLimitsConfig; ModuleBoundariesTest exempts only top-level `config` and still flags it reaching module-private packages; JsonLimitsIT covers accounts and rooms.
- Follow-up (non-AC, before ship): docs/knowledge/architecture.md lacks the Rooms section and still says enforcement deferred; plan Architecture changes 1-4 unapplied (human-owned file).

## Security
Scope: uncommitted working tree against main (no commits on branch yet).
- S-1 minor, fixed: currencyCode, displayUnit.label, rule id capped 10/20/60 and must equal sanitised form (RoomConfigValidator.java:97-118, :300-305).
- S-2 minor, fixed: top-level lists and maps capped at 50, rules at 100, echoed keys cut to 40.
- S-3 minor, fixed: POST invite consumes=application/json (InviteController.java:35); web client sends `{}`.
- S-4 minor, fixed: RoomsProperties rejects codeLength < 10 at startup (RoomsProperties.java:18-25).
- S-6 minor, fixed: roles/tiers/bands capped at 50, RoleConditional nesting at 3 (RoomConfigValidator.java:316-333); Jackson maxNestingDepth(64) app-wide (RoomsConfig.java:18-28) turns a real StackOverflowError 500 into 400.
- S-7 minor, fixed: JsonLimitsConfig.java:19-38 depth 64, document 256 KB, string 16 KB; deep/long/oversize bodies give 400 on accounts and rooms (~8 KB buffer slack, fine).
- S-8 minor, fixed: scoring decimals bounded to ±1,000,000, ≤4 decimals, value not echoed (RoomConfigValidator.java:392-406).
- S-9 major, fixed (was persistent room DoS): StoredJson (RoomRepository.java:31, RoomLogRepository.java:27) reads only server-written rows, never request bodies; 64 KB size check on both config write paths (RoomRepository.java:65 create, :92 save) rolls back with 400. Uncapped DB reads safe (config capped pre-write, log/pack trusted, read depth 200 > request 64). Polymorphic typing unchanged: closed named subtypes, no default typing.
- S-10 minor, fixed: scale floor -6 (RoomConfigValidator.java:57, :402); `0E+2000000000` gives 400.
- Other request paths: all new bodies JSON-only; no form/multipart/raw bodies added.
- S-5 minor, accepted in plan: invite codes stored in plain text (V5__rooms.sql:44).
- Checked clean: SecureRandom codes, throttle before lookup, ClientAddress hop trust, 404 before 403, Commissioner-only actions, row locks plus version CAS, append-only room_log, parameterised JDBC, name sanitising, safe `next` redirect.

## Verdict
pass (after human-directed pass 4). Blocking: none. Open: S-5 accepted; AC-18/19 real-browser run dev-reported only; architecture.md follow-up before ship (human-owned).
