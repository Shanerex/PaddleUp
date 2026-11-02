-- Sliding-window throttle for email sign-in: one row per counted event.
-- scope names the limit (e.g. start-email); key_hash is a SHA-256 of the email or client address,
-- so no address is stored. Rows older than the longest window are purged on write.

CREATE TABLE auth_throttle_events (
    id          bigserial   PRIMARY KEY,
    scope       text        NOT NULL,
    key_hash    text        NOT NULL,
    occurred_at timestamptz NOT NULL
);

CREATE INDEX auth_throttle_events_lookup_idx ON auth_throttle_events (scope, key_hash, occurred_at);
CREATE INDEX auth_throttle_events_age_idx ON auth_throttle_events (occurred_at);
