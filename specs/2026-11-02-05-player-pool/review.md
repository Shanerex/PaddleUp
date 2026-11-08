---
status: final
---
# Review: 2026-11-02-05-player-pool

## QA
`make test` (server verify + 184 web tests) green; `scripts/check-agnostic.sh` ok. No app code changed since.
- AC-1..AC-17 pass, each covered by a named IT or web test.
- AC-18 pass pending human visual sign-off: 54 files in `shots/` plus README pairing app and mock at 390 and 1440. Layout, tokens, rail and sets panel match in the pairs opened.
- D-1 cleared: AdminImport mock says "manages", matches import-view.tsx.
- D-2 cleared: mock version rows no importer email.
- D-3 cleared: pool mock shows "₹2 Crore", same as app. Minor leftover: edit-error frame hard-codes '₹1.53 Cr' (Room.dc.html:745).
- Gaps (listed in shots/README.md): no 390 shots for pool dialogs, empty, stale, live, import-* states; import-empty/report/failed/denied have no mock shot.
- Unlisted minor gaps, data-driven: pool-1440 lacks "newer data" banner (pool-newer-1440 shows it); role chip order differs from mock; import shot shows dimmed Import button and empty versions list.
- Extras accepted: `GET /api/admin/datasets/packs`, 413 on files over 1 MB, AppShell `badge`.
- Extra untested: pack_mismatch skip at room creation (PoolService.copyLatest) is silent and has no test; reset path tested.

## Security
- S-1 (minor, deferred): DatasetAdminController.java:81 multipart write relies on SameSite=Lax only; CSRF off. Not exploitable today.
- S-2 (minor, deferred): RoomService.java:163 copies up to 2,000 rows per new room; no per-user room cap.
- Mocks and 52 screenshots checked: test data only, no emails, secrets or local paths.
- Authz, import limits, SQL, sanitisation, commissioner-only writes, room_locked, stale_version, sport-agnostic rule: no findings.

## Verdict
pass. No blockers; only human visual sign-off for AC-18 (via g3). S-1, S-2 deferred, pack_mismatch test gap noted.
