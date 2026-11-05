-- Room pools: each room's own editable copy of a season dataset (V6), taken at room creation or
-- reset. One header row per room carries the single pool_version used for compare-and-set on pool
-- edits, set order and reset. A room with no header row has no copy yet.
-- Timestamps are timestamptz (stored UTC, mapped to Instant).

CREATE TABLE room_pools (
    room_id          uuid        PRIMARY KEY REFERENCES rooms (id),
    dataset_id       uuid        NOT NULL REFERENCES season_datasets (id),
    dataset_version  int         NOT NULL CHECK (dataset_version >= 1),
    -- Players in the source dataset when copied. Edit count = edited + added rows + removed source
    -- rows (source_count minus the rows that still point at a source player).
    source_count     int         NOT NULL CHECK (source_count >= 0),
    pool_version     int         NOT NULL DEFAULT 1 CHECK (pool_version >= 1),
    -- Set names in play order; a set left empty by edits stays in the order.
    set_order        text[]      NOT NULL,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL
);

CREATE TABLE room_pool_players (
    id                  uuid   PRIMARY KEY,
    room_id             uuid   NOT NULL REFERENCES room_pools (room_id),
    source_player_id    uuid   REFERENCES season_players (id),
    origin              text   NOT NULL,
    name                text   NOT NULL,
    country             text   NOT NULL,
    role_key            text   NOT NULL,
    tag_keys            text[] NOT NULL,
    base_price          bigint NOT NULL CHECK (base_price > 0),
    set_name            text   NOT NULL,
    provider_player_id  text,
    position            int    NOT NULL,
    CONSTRAINT room_pool_players_origin_valid CHECK (origin IN ('source', 'edited', 'added')),
    -- Only an added row has no source; the other two always point at one.
    CONSTRAINT room_pool_players_origin_source CHECK (
        (origin = 'added' AND source_player_id IS NULL)
        OR (origin <> 'added' AND source_player_id IS NOT NULL))
);

CREATE INDEX room_pool_players_room_idx ON room_pool_players (room_id, position, id);
