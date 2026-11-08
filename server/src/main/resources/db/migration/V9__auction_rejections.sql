-- Rejected intents, kept so a resend of the same intent id gets its first answer even after a
-- restart. A rejection is not an auction event: it changes no state and is never broadcast, so it
-- stays out of the event log. Append-only like the log, one row per actor and intent id.
CREATE TABLE auction_rejections (
    room_id        uuid        NOT NULL REFERENCES rooms (id),
    actor_user_id  uuid        NOT NULL REFERENCES users (id),
    intent_id      uuid        NOT NULL,
    lot_no         int         NOT NULL CHECK (lot_no >= 0),
    reason         text        NOT NULL,
    occurred_at    timestamptz NOT NULL,
    PRIMARY KEY (room_id, actor_user_id, intent_id)
);

CREATE TRIGGER auction_rejections_no_update_delete
    BEFORE UPDATE OR DELETE ON auction_rejections
    FOR EACH ROW EXECUTE FUNCTION auction_log_reject_change();

CREATE TRIGGER auction_rejections_no_truncate
    BEFORE TRUNCATE ON auction_rejections
    FOR EACH STATEMENT EXECUTE FUNCTION auction_log_reject_change();
