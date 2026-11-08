---
status: final
---
# Tasks: 2026-11-02-05-player-pool

## Tasks
<!-- Each task cites at least one existing AC. Tick to - [x] when done. -->
Server: accounts and packs
- [x] T-1 (AC-2): Admin allow-list (`AdminProperties`, `AdminAllowList.isAdmin`), case-insensitive, empty admits nobody.
- [x] T-2 (AC-4, AC-5): `V6__season_datasets.sql`, unique `(pack_id, version)`, append-only triggers; schema IT.
- [x] T-3 (AC-3, AC-8): Pure validator `PlayerRowRules` + `PriceRule` + `FieldError`; unit tests incl. stub football pack.
- [x] T-4 (AC-1): `DatasetFileReader` for CSV and JSON, 1 MB and 2,000-row caps, typed errors; unit tests.
- [x] T-5 (AC-3, AC-4, AC-5): `SeasonDatasetService` + repository; valid rows stored as v+1, row/field report, `NoValidRowsException`.
- [x] T-6 (AC-1, AC-2, AC-3, AC-4): `DatasetAdminController` (POST/GET `/api/admin/datasets`), `PacksExceptionHandler`; 403/201/422 ITs.
Server: rooms
- [x] T-7 (AC-6, AC-10): `V7__room_pools.sql` (`room_pools`, `room_pool_players`, origin check); schema IT.
- [x] T-8 (AC-6): `PoolService.copyLatest` at room creation, `GET /api/rooms/{id}/pool`; copy, empty and non-member ITs.
- [x] T-9 (AC-7): `RoomLifecycle.refusal` blocks `auction_live` without a pool; 409 IT.
- [x] T-10 (AC-6, AC-8, AC-9): Player add, edit, remove with row lock, version CAS, rules, room_log, 1,000 cap; ITs.
- [x] T-11 (AC-9, AC-10): `POST /pool/reset` to latest dataset or first load, `pack_mismatch` check; ITs.
- [x] T-12 (AC-9, AC-13): `PUT /pool/sets` full order as a permutation; persist, 422 and 403 ITs.
- [x] T-13 (AC-11, AC-12): Conflict matrix IT: `stale_version`, `room_locked`, race with `advance`, fresh second read.
- [x] T-14 (AC-18): Stub football pack end to end with zero core changes; `check-agnostic.sh` passes.
Web
- [x] T-15 (AC-14, AC-17): Regenerate API types; `pool.ts` helpers (labels, flags, counts, money), 409 mapping; Vitest.
- [x] T-16 (AC-14, AC-15): Pool read screen (`pool-view.tsx`); members see no edit controls. Refs: Room pool*, 7a, 7b, 7g, 7i.
- [x] T-17 (AC-8, AC-15): Add and edit sheet with pack roles, tags, set picker, 422 field errors. Refs: poolEdit, poolAdd, 7c, 7d.
- [x] T-18 (AC-10, AC-15): Remove and reset confirm sheets, "Load season data" at version 0. Refs: poolRemove, poolReset, 7e-7g.
- [x] T-19 (AC-11, AC-12): 409 handling: refetch, stale banner, locked banner removes controls. Refs: poolStale, poolLive, 7h, 7i.
- [x] T-20 (AC-13, AC-16): Set order screen (`set-order-view.tsx`), move, Save, Undo, read-only for members. Refs: sets, 7j, 7k.
- [x] T-21 (AC-14, AC-16): Room overview Player pool card and rail links in `room-view.tsx`. Refs: setup*, 6h-6k.
- [x] T-22 (AC-17): Admin import screen (`import-view.tsx`): picker, report, version list, importDenied. Refs: AdminImport, 7l-7o.
Verify and docs
- [x] T-23 (AC-12, AC-14, AC-15, AC-16, AC-17): Playwright on `make dev`: import, edit flows, member view, stale and live tabs, 600-row timing.
- [x] T-24 (AC-18): Screenshot each changed screen at 390 and 1440 beside its mock; fix drift.
- [x] T-25 (AC-18): `make lint` and `make test` green, incl. `check-agnostic`, `ModuleBoundariesTest`, football stub IT.
- [x] T-26 (AC-2, AC-6): README admin and file format section; three decision records; architecture.md waits for approval.
