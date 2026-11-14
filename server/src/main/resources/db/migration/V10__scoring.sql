-- Scoring: the room's edited rule set, its freeze and change log, and the match, stat and points
-- data. Matches and provider stats are stored once per season dataset (V6) and shared by every room
-- on it; manual stats (overrides) and points are per room. A room with no scoring_rule_sets row
-- plays its pack-default snapshot (rooms.config). Timestamps are timestamptz (stored UTC).

CREATE TABLE scoring_rule_sets (
    room_id     uuid        PRIMARY KEY REFERENCES rooms (id),
    -- NULL means the room plays its pack-default snapshot.
    rules       jsonb,
    -- Compare-and-set counter; 0 until the first change.
    version     int         NOT NULL DEFAULT 0 CHECK (version >= 0),
    -- Set once the server clock is seen at or past the first match's start; never cleared.
    frozen_at   timestamptz,
    updated_at  timestamptz NOT NULL,
    updated_by  uuid        REFERENCES users (id)
);

CREATE TABLE scoring_rule_log (
    seq          bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id      uuid        NOT NULL REFERENCES rooms (id),
    kind         text        NOT NULL,
    actor        uuid        NOT NULL REFERENCES users (id),
    occurred_at  timestamptz NOT NULL,
    reason       text,
    attested     boolean     NOT NULL DEFAULT false,
    -- The whole rule set before and after, so the old set is never lost.
    before       jsonb       NOT NULL,
    after        jsonb       NOT NULL,
    diff         jsonb       NOT NULL,
    CONSTRAINT scoring_rule_log_kind_valid CHECK (kind IN ('edit', 'revert', 'post_freeze_change'))
);

CREATE INDEX scoring_rule_log_room_idx ON scoring_rule_log (room_id, seq);

-- One fixture per season dataset match; starts_at is its scheduled start.
CREATE TABLE scoring_matches (
    dataset_id  uuid        NOT NULL REFERENCES season_datasets (id),
    match_id    text        NOT NULL,
    starts_at   timestamptz NOT NULL,
    PRIMARY KEY (dataset_id, match_id)
);

CREATE INDEX scoring_matches_start_idx ON scoring_matches (dataset_id, starts_at);

-- Provider stat values, shared by every room on the dataset. event_type '' is the stat itself;
-- a named event type holds the count of that type.
CREATE TABLE scoring_stat_values (
    dataset_id        uuid    NOT NULL,
    match_id          text    NOT NULL,
    season_player_id  uuid    NOT NULL REFERENCES season_players (id),
    stat              text    NOT NULL,
    event_type        text    NOT NULL DEFAULT '',
    value             numeric NOT NULL,
    PRIMARY KEY (dataset_id, match_id, season_player_id, stat, event_type),
    FOREIGN KEY (dataset_id, match_id) REFERENCES scoring_matches (dataset_id, match_id)
);

-- Manual stat values a Commissioner entered for one room: sparse, and they beat provider values.
CREATE TABLE scoring_stat_overrides (
    room_id         uuid    NOT NULL REFERENCES rooms (id),
    match_id        text    NOT NULL,
    pool_player_id  uuid    NOT NULL,
    stat            text    NOT NULL,
    event_type      text    NOT NULL DEFAULT '',
    value           numeric NOT NULL,
    PRIMARY KEY (room_id, match_id, pool_player_id, stat, event_type)
);

-- The result per room, match and pool player; replaced on every recompute, never appended.
CREATE TABLE scoring_points (
    room_id           uuid        NOT NULL REFERENCES rooms (id),
    match_id          text        NOT NULL,
    pool_player_id    uuid        NOT NULL,
    total             numeric     NOT NULL,
    lines             jsonb       NOT NULL,
    rule_set_version  int         NOT NULL,
    computed_at       timestamptz NOT NULL,
    PRIMARY KEY (room_id, match_id, pool_player_id)
);

-- The rule change log is append-only: no update, delete or truncate, whoever asks.
CREATE FUNCTION scoring_log_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER scoring_rule_log_no_update_delete
    BEFORE UPDATE OR DELETE ON scoring_rule_log
    FOR EACH ROW EXECUTE FUNCTION scoring_log_reject_change();

CREATE TRIGGER scoring_rule_log_no_truncate
    BEFORE TRUNCATE ON scoring_rule_log
    FOR EACH STATEMENT EXECUTE FUNCTION scoring_log_reject_change();
