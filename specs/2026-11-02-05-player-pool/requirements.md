---
status: final
---
# Requirements: 2026-11-02-05-player-pool

## Problem
The auction draws lots from a season pool matching the room's Sport Pack roles and tags (PRD FR-20..22). The app admin seeds a season dataset; each Commissioner edits a room copy, resets it, and orders sets.

## State x entry-point matrix
Lifetimes: season dataset (D), room pool copy (C), set order (S). Pre = before auction start; Live = after.
| entry point | D: none / has version | C: Pre | C: Live | S: Pre | S: Live |
|---|---|---|---|---|---|
| Admin import | v1 / v+1 if a row is valid, rooms untouched (R-3,R-4) | n/a | n/a | n/a | n/a |
| Room creation | none: no copy, start blocked / copy of latest (R-5) | n/a | n/a | dataset order (R-9) | n/a |
| Commissioner edit | n/a | allowed (R-6) | 409 (R-8) | allowed (R-9) | 409 (R-8) |
| Reset copy | n/a | replaces C and S with latest D (R-7) | 409 (R-8) | reset to D order (R-7) | 409 (R-8) |
| Stale tab write | n/a | 409 on old version (R-8) | 409 (R-8) | 409 on old version (R-8) | 409 (R-8) |
| Room settings save (purse, price steps) | any: allowed | rejected if a pool player's base price no longer fits (R-16) | 409 (R-8) | n/a | n/a |
| Auction start | n/a | blocked unless the pool can fill every squad (R-15) | n/a | n/a | n/a |
| Season data newer than the copy, room cannot take it | roles/tags not in pack, or a price off the purse/steps | copy skipped and logged (R-17); Reset refused with reason (R-17) | n/a | n/a | n/a |

## Requirements
- R-1: A player has name, country, pack role key, pack tag keys, base price, auction set and optional provider player ID (FR-20).
- R-2: Only accounts on the server-config email allow-list can import a season dataset (CSV or JSON) for a pack id and version; others get 403.
- R-3: Import is partial: each row is validated against the pack's roles, tags and price rules; valid rows import, rejected rows are reported with row and field errors. Zero valid rows creates no version and returns 422 with the report.
- R-4: Each import with at least one valid row is a new immutable dataset version; existing room copies never change.
- R-5: A room gets its own copy of the latest dataset for its pack at room creation; auction start is blocked while no copy exists.
- R-6: Only the Commissioner can add, edit or remove one player per request before auction start; each request is all-or-nothing under the R-3 rules. Members read only.
- R-7: The Commissioner can reset the copy and set order to the latest dataset version before auction start, discarding all edits.
- R-8: Pool, set-order and reset writes carry the read version; stale versions and any write after auction start return 409.
- R-9: Sets default to dataset order, each player is in exactly one set, and the Commissioner can reorder sets before auction start (FR-22).
- R-10: Player Pool screen: members view the pool read-only; the Commissioner adds, edits, removes and resets while pre-auction.
- R-11: Set Order screen: the Commissioner reorders sets while pre-auction; members see the order read-only.
- R-12: Admin Import screen: the allow-listed admin uploads a file and sees the rejected-row report (R-3).
- R-13: Screens for R-10..R-12 are drawn in docs/design/source (Room.dc.html pool and set-order screens, AdminImport.dc.html; App Screens turn 7) and approved before the plan; this spec sets no layout.
- R-14: No core code, schema or UI string names a sport, role, stat or currency; labels come from the Sport Pack.
- R-15: Auction start is refused unless the room's pool holds at least franchises x minimum squad size players (and at least one), because the pool locks at start; the error says how many are needed.
- R-16: A room settings save is rejected with a field error on the purse or price steps when any player already in the pool has a base price they would not allow; nothing is saved, and a pool never holds a player the room cannot afford.
- R-17: A room can take a season dataset only if every role and tag is in its pack and every base price fits its purse and price steps. Otherwise room creation makes no copy and writes a `pool_copy_skipped` log row naming the version and why, and Reset or Load season data returns 409 `pack_mismatch` (roles or tags) or `price_mismatch` (prices), each with a message that names the cause. Only the Commissioner is told which; the pool read stays cheap (no player rows read).
- R-18: The Player Pool screen says why newest data can't be loaded (prices vs roles and tags), hides Reset to latest and keeps Load season data off; the hint under the button matches the cause (Room `poolUnfit`, `poolUnfitPack`, `poolEmptyUnfit`, `poolEmptyUnfitPack`).

## Acceptance criteria
- AC-1: A valid CSV and its equivalent JSON, imported as admin, produce datasets with identical players.
- AC-2: An account not on the allow-list gets 403 on import and no dataset is created; an allow-listed account succeeds.
- AC-3: A file with valid rows and rows with an unknown role or tag imports the valid rows as a new version; the report names each rejected row and field; no rejected row is stored.
- AC-4: A file with zero valid rows returns 422 with the report and creates no version; the latest version is unchanged.
- AC-5: A second import with a valid row creates v+1; a room copy made earlier still has the v contents.
- AC-6: A new room's copy equals the latest dataset at creation; editing the copy leaves the dataset unchanged.
- AC-7: With no dataset for the pack, auction start returns an error and the room stays pre-auction.
- AC-8: Commissioner add, edit and remove persist on reload; an edit with an unknown role or tag is rejected with a field error and changes nothing.
- AC-9: A member write to the pool, set order or reset returns 403.
- AC-10: Reset pre-auction replaces the copy and set order with the latest dataset version and discards earlier edits.
- AC-11: After auction start, a pool edit, set-order change or reset returns 409 and changes nothing.
- AC-12: A write with a stale version returns 409; after reload the second tab shows the latest data.
- AC-13: Reordering sets pre-auction persists and the new order is returned to every member.
- AC-14: Player Pool screen (Room.dc.html `pool`, `poolMember`): a member sees the pool with no edit controls.
- AC-15: Player Pool screen: the Commissioner's add, edit, remove and reset work pre-auction, and those controls are absent after start.
- AC-16: Set Order screen (Room.dc.html `sets`, `setsMember`): the Commissioner's reorder persists after reload.
- AC-17: Admin Import screen (AdminImport.dc.html): uploading a mixed file shows the rejected rows with row and field.
- AC-18: Each screen in AC-14..AC-17 and AC-24 matches its approved mock at 390 and 1440, and the stub football pack runs with zero core changes (R-14).
- AC-19: Starting the auction with no pool, an empty pool, or fewer players than managers x minimum squad size is refused, names the count needed, and the room stays pre-auction; a big enough pool starts.
- AC-20: A settings save that lowers the purse (or changes price steps) below a pool player's base price returns 400 with a purse or price-steps field error and changes nothing; a save that still fits succeeds.
- AC-21: With newest data holding a role or tag the pack lacks, room creation makes no copy and logs `pool_copy_skipped` with reason `pack`; with a price the room cannot afford the reason is `price`.
- AC-22: Reset or Load season data against such data returns 409 `pack_mismatch` or `price_mismatch` with a cause-specific message, and changes nothing.
- AC-23: The pool read returns which fit applies to the Commissioner only, and reads the distinct roles, tags and prices of the newest version in the database, never its player rows.
- AC-24: Player Pool screen (Room.dc.html `poolUnfit`, `poolUnfitPack`, `poolEmptyUnfit`, `poolEmptyUnfitPack`): each shows the cause-specific notice or empty-state text, no Reset to latest, and a disabled Load season data with the matching hint.

## Out of scope
- Live provider stat fetching beyond storing the provider ID (scoring feature).
- Retentions, RTM allocation and the auction itself.
- Pack authoring or editing.
