tags: [packs, rooms, datasets, schema]
Season datasets live in the packs module as immutable, versioned Postgres rows (season_datasets, season_players); rooms copy the latest at creation.
Why: datasets sit beside the pack rules they are checked against, and rooms read them only through packs.service.SeasonDatasetService and packs.dto (R-1..R-5).
Cost: packs is no longer file-only; both tables are append-only through triggers, so a bad import is fixed by a newer version, never by an edit.
Alternatives: datasets in rooms (no admin, room-less import home) or a new pool module (7th module, changes ModuleBoundariesTest).
