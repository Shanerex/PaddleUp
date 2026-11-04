-- Rooms: a private league with its own rules, the franchises that joined it, its single active
-- invite code, an append-only log, and rooms-owned join rate-limit events.
-- All timestamps are timestamptz (stored UTC, mapped to Instant). Membership is derived: the
-- Commissioner column plus franchise owners, so exactly one Commissioner holds by construction.

CREATE TABLE rooms (
    id                    uuid        PRIMARY KEY,
    state                 text        NOT NULL,
    commissioner_user_id  uuid        NOT NULL REFERENCES users (id),
    pack_id               text        NOT NULL,
    pack_version          text        NOT NULL,
    preset_id             text        NOT NULL,
    pack_snapshot         jsonb       NOT NULL,
    config                jsonb       NOT NULL,
    config_version        int         NOT NULL DEFAULT 1,
    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL,
    CONSTRAINT rooms_state_valid CHECK (
        state IN ('setup', 'retention', 'auction_scheduled', 'auction_live', 'season_live',
                  'completed'))
);

CREATE INDEX rooms_commissioner_idx ON rooms (commissioner_user_id);

CREATE TABLE franchises (
    id             uuid        PRIMARY KEY,
    room_id        uuid        NOT NULL REFERENCES rooms (id),
    owner_user_id  uuid        NOT NULL REFERENCES users (id),
    name           text        NOT NULL,
    colour         text        NOT NULL,
    monogram       text        NOT NULL,
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    CONSTRAINT franchises_room_owner_unique UNIQUE (room_id, owner_user_id),
    CONSTRAINT franchises_room_colour_unique UNIQUE (room_id, colour)
);

CREATE UNIQUE INDEX franchises_room_name_unique ON franchises (room_id, lower(name));
CREATE INDEX franchises_owner_idx ON franchises (owner_user_id);

CREATE TABLE room_invites (
    id          uuid        PRIMARY KEY,
    room_id     uuid        NOT NULL REFERENCES rooms (id),
    code        text        NOT NULL,
    issued_by   uuid        NOT NULL REFERENCES users (id),
    issued_at   timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    CONSTRAINT room_invites_code_unique UNIQUE (code)
);

-- One active (not revoked) invite per room.
CREATE UNIQUE INDEX room_invites_one_active ON room_invites (room_id) WHERE revoked_at IS NULL;

CREATE TABLE room_log (
    id             bigserial   PRIMARY KEY,
    room_id        uuid        NOT NULL REFERENCES rooms (id),
    kind           text        NOT NULL,
    actor_user_id  uuid        REFERENCES users (id),
    detail         jsonb       NOT NULL DEFAULT '{}'::jsonb,
    occurred_at    timestamptz NOT NULL
);

CREATE INDEX room_log_room_idx ON room_log (room_id, id);

-- The log is append-only: no update, delete or truncate, whoever asks.
CREATE FUNCTION room_log_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'room_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER room_log_no_update_delete
    BEFORE UPDATE OR DELETE ON room_log
    FOR EACH ROW EXECUTE FUNCTION room_log_reject_change();

CREATE TRIGGER room_log_no_truncate
    BEFORE TRUNCATE ON room_log
    FOR EACH STATEMENT EXECUTE FUNCTION room_log_reject_change();

-- Failed invite-code attempts behind the join rate limits; same sliding-window shape as V4.
CREATE TABLE room_join_throttle_events (
    id          bigserial   PRIMARY KEY,
    scope       text        NOT NULL,
    key_hash    text        NOT NULL,
    occurred_at timestamptz NOT NULL
);

CREATE INDEX room_join_throttle_lookup_idx
    ON room_join_throttle_events (scope, key_hash, occurred_at);
CREATE INDEX room_join_throttle_age_idx ON room_join_throttle_events (occurred_at);
