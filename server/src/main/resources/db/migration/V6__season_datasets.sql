-- Season datasets: one immutable, versioned player list per pack, imported by an app admin.
-- Rows are never changed once written; a new import is a new version. Room copies live in V7.
-- Timestamps are timestamptz (stored UTC, mapped to Instant).

CREATE TABLE season_datasets (
    id              uuid        PRIMARY KEY,
    pack_id         text        NOT NULL,
    version         int         NOT NULL CHECK (version >= 1),
    pack_version    text        NOT NULL,
    source_format   text        NOT NULL CHECK (source_format IN ('csv', 'json')),
    imported_by     uuid        NOT NULL REFERENCES users (id),
    imported_at     timestamptz NOT NULL,
    player_count    int         NOT NULL CHECK (player_count >= 1),
    rejected_count  int         NOT NULL CHECK (rejected_count >= 0),
    -- Set names in order of first appearance in the file.
    set_order       text[]      NOT NULL,
    CONSTRAINT season_datasets_pack_version_unique UNIQUE (pack_id, version)
);

CREATE TABLE season_players (
    id                  uuid   PRIMARY KEY,
    dataset_id          uuid   NOT NULL REFERENCES season_datasets (id),
    position            int    NOT NULL,
    name                text   NOT NULL,
    country             text   NOT NULL,
    role_key            text   NOT NULL,
    tag_keys            text[] NOT NULL,
    base_price          bigint NOT NULL CHECK (base_price > 0),
    set_name            text   NOT NULL,
    provider_player_id  text
);

CREATE INDEX season_players_dataset_idx ON season_players (dataset_id, position);

-- Both tables are append-only: no update, delete or truncate, whoever asks.
CREATE FUNCTION season_data_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER season_datasets_no_update_delete
    BEFORE UPDATE OR DELETE ON season_datasets
    FOR EACH ROW EXECUTE FUNCTION season_data_reject_change();

CREATE TRIGGER season_datasets_no_truncate
    BEFORE TRUNCATE ON season_datasets
    FOR EACH STATEMENT EXECUTE FUNCTION season_data_reject_change();

CREATE TRIGGER season_players_no_update_delete
    BEFORE UPDATE OR DELETE ON season_players
    FOR EACH ROW EXECUTE FUNCTION season_data_reject_change();

CREATE TRIGGER season_players_no_truncate
    BEFORE TRUNCATE ON season_players
    FOR EACH STATEMENT EXECUTE FUNCTION season_data_reject_change();
