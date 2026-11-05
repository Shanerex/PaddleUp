---
status: final
---
# Plan: 2026-11-02-05-player-pool

## Approach
- Season datasets (R-1..R-4) live in the `packs` module as immutable, versioned DB rows. The import is admin-only (R-2) through a new allow-list in `accounts`. One pure row validator, `packs.service.PlayerRowRules`, is shared by the import and by room edits (R-3, R-6, AC-3, AC-8).
- The room copy and set order (R-5..R-9) live in `rooms` as relational rows plus one `room_pools` header row. Its `pool_version` int is the single compare-and-set version for pool edits, set order and reset (R-8, AC-11, AC-12), the same pattern as `config_version` in the rooms feature.
- Every pool write locks the room row (`RoomRepository.findForUpdate`), the same row `RoomLifecycle.advance` locks. A write can therefore never commit after the auction goes live (AC-11), and `RoomLifecycle` refuses `auction_live` while no copy exists (R-5, AC-7).
- The UI is built to the approved mocks: the pool and set order are views in Room.dc.html (`pool`/`poolMember`, `sets`/`setsMember`, R-13), and the import screen is AdminImport.dc.html (R-10..R-12, AC-14..AC-18). Every label comes from the room's pack snapshot (R-14).

## Options considered
- A (chosen, approved 2026-11-04): datasets in `packs` (DB-backed `SeasonDatasetService`), room copy in `rooms`. Datasets sit beside the pack rules they are checked against, and rooms only use `packs.service`/`packs.dto`. The cost is that `packs` is no longer file-only (architecture change).
- B: datasets and copies both in `rooms`. Nothing changes in `packs`, but an admin, room-less import would live in the rooms module, and the auction and scoring would have to reach into `rooms` for season data.
- C: a new top-level `pool` module. Cleanest ownership, but it adds a 7th module, changes `ModuleBoundariesTest` and CLAUDE.md, and the rooms state lock still has to cross back into `rooms`.

## Design
Server, packs module (season dataset, R-1..R-4)
- Migration `V6__season_datasets.sql` adds two tables:
  - `season_datasets(id uuid pk, pack_id, version int, pack_version, source_format, imported_by uuid -> users, imported_at, player_count, rejected_count, set_order text[])`, with unique `(pack_id, version)`.
  - `season_players(id uuid pk, dataset_id fk, position int, name, country, role_key, tag_keys text[], base_price bigint, set_name, provider_player_id null)`.
  - Triggers block UPDATE, DELETE and TRUNCATE on both tables (same shape as `room_log`).
  - The dataset set order is the order in which each set first appears in the file (R-9).
- `packs.util.DatasetFileReader` turns a CSV with header `name,country,role,tags,base_price,set,provider_id` (tags `;`-separated) or a JSON `{"players":[{same keys, tags as array}]}` into `RawRow(rowNumber, fields)` (AC-1).
  - Caps: 1 MB file, 2,000 rows. It uses its own Jackson reader so the app-wide 256 KB limit does not apply, and adds the dependency `jackson-dataformat-csv` (version from the Boot BOM).
- `packs.service.PlayerRowRules` is pure. It checks one row against role keys, tag keys and a `PriceRule` and returns `FieldError(field, value, message)`.
  - Caps: name 1..60, country 1..40, set 1..40, provider id up to 64 chars `[A-Za-z0-9._:-]`, up to 10 tags with no duplicates.
  - Name, country and set text go through `accounts.service.DisplayNameSanitiser`.
  - Price rule (approved): a whole number > 0 in the pack's smallest unit, no more than the purse, and on the increment ladder, i.e. `(price - from) % step == 0` for the band that contains it.
    - For an import, every preset of the pack must accept the price.
    - For a room edit, the room config's `purse` and `incrementLadder` must accept it.
    - The error message is the generic "That base price isn't allowed in {pack name}."
- `packs.service.SeasonDatasetService`:
  - `importFile(UserId, packId, format, bytes)` returns `ImportReportDto`, or throws 422 `NoValidRowsException` carrying the report (AC-3, AC-4).
  - `latest(packId)` returns `Optional<SeasonDatasetDto>` with players and set order; also `list(packId)` and `player(datasetId, playerId)`.
  - The new version is `max+1` inside the transaction, and the unique constraint catches a race (AC-5).
- `packs.repository.SeasonDatasetRepository` uses Spring JDBC.
- `packs.controller.DatasetAdminController`, all endpoints 403 unless `AdminAllowList` (AC-2):
  - `POST /api/admin/datasets` (multipart `packId`, `file`; format from the extension or content type) returns 201 or 422 `ImportReportDto`.
  - `GET /api/admin/datasets?packId=` returns the version list (7l..7o).
- `packs.exception.PacksExceptionHandler` maps 403, 422 and 400 (bad file).
- The report lists the first 100 rejected rows with row, field, value and problem, plus a total.

Server, accounts module (R-2)
- `accounts.config.AdminProperties` binds `paddleup.admin.emails` (env `PADDLEUP_ADMIN_EMAILS`, comma list, default empty = nobody).
- `accounts.service.AdminAllowList.isAdmin(UserId)` matches the account email case-insensitively.

Server, rooms module (R-5..R-9)
- Migration `V7__room_pools.sql` adds two tables:
  - `room_pools(room_id pk -> rooms, dataset_id -> season_datasets, dataset_version int, source_count int, pool_version int, set_order text[], created_at, updated_at)`.
  - `room_pool_players(id uuid pk, room_id -> room_pools, source_player_id uuid null, origin text check in (source, edited, added), name, country, role_key, tag_keys text[], base_price bigint, set_name, provider_player_id null, position int)`.
- `rooms.service.PoolService`:
  - `copyLatest(roomId)` is called from `RoomService.create` in the same transaction. When no dataset exists, no row is written (R-5, AC-6).
  - Read: `get(caller, roomId)`, for members only (non-member 404).
  - Writes, Commissioner only (403 otherwise, AC-9), each needing pre-auction state and a matching version (409 `room_locked` / `stale_version`):
    - `add`, `edit`, `remove` (R-6, AC-8).
    - `reset(version)`: with no copy it expects version 0 and creates the copy. This is the poolEmpty "Load season data" action (R-7, AC-10).
    - `saveSetOrder(version, order)`: the full order must be a permutation of the current sets (R-9, AC-13).
  - Edits are checked by `PlayerRowRules`:
    - Roles and tags come from the room `packSnapshot`; price comes from the room config's `purse` and `incrementLadder`.
    - Each failure is a 422 with field errors, and nothing changes (AC-8).
  - Set rule (approved): add and edit pick one of the room's existing sets. A set left empty stays in the order with 0 players, so each player is in exactly one set (R-9).
  - `origin` is set at write time by comparing the row with its source row (`SeasonDatasetService.player`). The edit count = edited + added + removed source rows (`source_count` minus remaining source rows).
  - Each write appends a `room_log` entry: `POOL_COPIED`, `POOL_PLAYER_ADDED`, `POOL_PLAYER_EDITED`, `POOL_PLAYER_REMOVED`, `POOL_RESET` or `SET_ORDER_SAVED`.
  - Cap: 1,000 players per room pool.
- `rooms.repository.RoomPoolRepository` uses Spring JDBC with batch insert for copy and reset.
- `rooms.controller.PoolController` endpoints:
  - `GET /api/rooms/{id}/pool` returns `PoolDto`: dataset version, latest dataset version, version, edit count, players with origin, sets with counts, `canEdit`.
  - `POST /api/rooms/{id}/pool/players`
  - `PUT /api/rooms/{id}/pool/players/{playerId}`
  - `DELETE /api/rooms/{id}/pool/players/{playerId}?version=`
  - `POST /api/rooms/{id}/pool/reset`
  - `PUT /api/rooms/{id}/pool/sets`
- `RoomLifecycle.refusal` adds: `to == AUCTION_LIVE` with no `room_pools` row gives 409 `illegal_state` "No player pool yet" (AC-7).

Web (R-10..R-12)
- Regenerate `web/src/api/generated.ts`.
- New `web/src/rooms/pool.ts`: role chips, tag labels and EDITED/ADDED flags from `packSnapshot`; money through `web/src/rooms/money.ts`.
- `web/src/app/rooms/[roomId]/pool/page.tsx` + `pool-view.tsx` cover pool, poolMember, poolLive, poolEmpty, poolEdit, poolAdd, poolRemove, poolReset and poolStale.
- `web/src/app/rooms/[roomId]/sets/page.tsx` + `set-order-view.tsx` cover sets and setsMember: one Save sends the full order plus the version.
- `web/src/app/admin/import/page.tsx` + `import-view.tsx` cover import, importEmpty, importReport, importFailed and importDenied (shown on a 403 from GET).
- `web/src/app/rooms/[roomId]/room-view.tsx` gets the Player pool card and the rail links (6h-6k).
- On 409, both views refetch: `stale_version` shows the poolStale banner, and `room_locked` shows the poolLive banner with controls removed (AC-11, AC-12).

## Architecture changes
- Sport Packs: the pack definition stays file-only. The new part is that `packs` also owns season datasets in Postgres (`season_datasets`, `season_players`), immutable through DB triggers, read by other modules only through `packs.service.SeasonDatasetService` and `packs.dto`. `packs.repository` is no longer read-only file access.
- Accounts: `paddleup.admin.emails` (env `PADDLEUP_ADMIN_EMAILS`) is the app-admin allow-list, checked only through `accounts.service.AdminAllowList`. Admin REST lives under `/api/admin/**`.
- Rooms: each room has at most one pool copy (`room_pools` + `room_pool_players`) taken from the latest dataset at creation or reset. It is never re-read from the dataset after that. One `pool_version` compare-and-set covers pool edits, set order and reset (409 `stale_version`). Pool writes lock the room row, and `RoomLifecycle` refuses `auction_live` without a copy.
- Dependencies: add `com.fasterxml.jackson.dataformat:jackson-dataformat-csv` (Boot-managed) for dataset import.

## Risks
- Pack version drift: the latest dataset may target a newer pack version than the room's snapshot. Mitigation: copy and reset re-check role and tag keys against the room snapshot and return 409 `pack_mismatch` if any fail. The import stores `pack_version`.
- Large pools on every GET: up to 1,000 players per room (cap on add), plus one indexed query. Measure the 1440 render with 600 rows in T-23.
- The room's own ladder can differ from the pack's. Copied rows are not re-priced; only added or edited rows are checked against the room config (documented in the decision record, T-26).
- A stale tab racing auction start. Mitigation: the room row lock plus a state check inside the same transaction (T-13 runs them concurrently).
- Admin import is a file upload. Mitigation: 1 MB multipart cap, a 2,000-row cap, a dedicated parser with depth and size limits, and no file is stored.
