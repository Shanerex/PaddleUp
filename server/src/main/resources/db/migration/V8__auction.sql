-- Auction engine: one header row per auction, an append-only event log (the only source of truth,
-- replayed on boot) and the append-only lot cards frozen at go-live.
-- Timestamps are timestamptz (stored UTC, mapped to Instant).

-- Boot discovery index. Updated in the same transaction as each event, never the source of truth.
CREATE TABLE auctions (
    room_id           uuid        PRIMARY KEY REFERENCES rooms (id),
    phase             text        NOT NULL,
    starts_at         timestamptz NOT NULL,
    scheduled_by      uuid        NOT NULL REFERENCES users (id),
    -- Set when go-live was refused at the start instant; cleared by a reschedule.
    start_refused_at  timestamptz,
    last_seq          bigint      NOT NULL DEFAULT 0 CHECK (last_seq >= 0),
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    CONSTRAINT auctions_phase_valid CHECK (phase IN ('scheduled', 'live', 'ended'))
);

CREATE INDEX auctions_unended_idx ON auctions (phase) WHERE phase <> 'ended';

CREATE TABLE auction_events (
    room_id        uuid        NOT NULL REFERENCES rooms (id),
    seq            bigint      NOT NULL CHECK (seq >= 1),
    occurred_at    timestamptz NOT NULL,
    kind           text        NOT NULL,
    franchise_id   uuid,
    lot_no         int,
    actor_user_id  uuid        REFERENCES users (id),
    intent_id      uuid,
    payload        jsonb       NOT NULL DEFAULT '{}'::jsonb,
    -- A gap-free seq per room; a second writer with the same seq fails here.
    PRIMARY KEY (room_id, seq)
);

-- A client intent id is applied once per actor.
CREATE UNIQUE INDEX auction_events_intent_unique
    ON auction_events (room_id, actor_user_id, intent_id) WHERE intent_id IS NOT NULL;

-- Lot cards, frozen at go-live: the event log holds only ids.
CREATE TABLE auction_lots (
    room_id           uuid   NOT NULL REFERENCES rooms (id),
    lot_no            int    NOT NULL CHECK (lot_no >= 1),
    pool_player_id    uuid   NOT NULL,
    set_name          text   NOT NULL,
    position_in_set   int    NOT NULL,
    name              text   NOT NULL,
    country           text   NOT NULL,
    role_key          text   NOT NULL,
    tag_keys          text[] NOT NULL,
    base_price        bigint NOT NULL CHECK (base_price > 0),
    PRIMARY KEY (room_id, lot_no)
);

-- Both are append-only: no update, delete or truncate, whoever asks.
CREATE FUNCTION auction_log_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER auction_events_no_update_delete
    BEFORE UPDATE OR DELETE ON auction_events
    FOR EACH ROW EXECUTE FUNCTION auction_log_reject_change();

CREATE TRIGGER auction_events_no_truncate
    BEFORE TRUNCATE ON auction_events
    FOR EACH STATEMENT EXECUTE FUNCTION auction_log_reject_change();

CREATE TRIGGER auction_lots_no_update_delete
    BEFORE UPDATE OR DELETE ON auction_lots
    FOR EACH ROW EXECUTE FUNCTION auction_log_reject_change();

CREATE TRIGGER auction_lots_no_truncate
    BEFORE TRUNCATE ON auction_lots
    FOR EACH STATEMENT EXECUTE FUNCTION auction_log_reject_change();
