-- Manual stat entry: the immutable log of every field a Commissioner saved or cleared, and an
-- optional label for a dataset match (shown as "Match n · label"). The override table itself (V10)
-- is unchanged. old_value is NULL for a first value and new_value is NULL for a clear.

CREATE TABLE scoring_override_log (
    seq             bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id         uuid        NOT NULL REFERENCES rooms (id),
    actor           uuid        NOT NULL REFERENCES users (id),
    occurred_at     timestamptz NOT NULL,
    match_id        text        NOT NULL,
    pool_player_id  uuid        NOT NULL,
    stat            text        NOT NULL,
    event_type      text        NOT NULL DEFAULT '',
    old_value       numeric,
    new_value       numeric
);

CREATE INDEX scoring_override_log_room_idx ON scoring_override_log (room_id, seq);

-- Append-only like the rule log, with the same trigger function (V10).
CREATE TRIGGER scoring_override_log_no_update_delete
    BEFORE UPDATE OR DELETE ON scoring_override_log
    FOR EACH ROW EXECUTE FUNCTION scoring_log_reject_change();

CREATE TRIGGER scoring_override_log_no_truncate
    BEFORE TRUNCATE ON scoring_override_log
    FOR EACH STATEMENT EXECUTE FUNCTION scoring_log_reject_change();

ALTER TABLE scoring_matches ADD COLUMN label text;
