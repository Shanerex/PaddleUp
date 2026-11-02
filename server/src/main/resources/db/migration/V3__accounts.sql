-- Accounts: users, linked sign-in identities and one-time email login tokens.
-- All timestamps are timestamptz (stored UTC, mapped to Instant).

CREATE TABLE users (
    id                 uuid        PRIMARY KEY,
    email              text        NOT NULL,
    display_name       text        NOT NULL,
    avatar             text        NOT NULL,
    google_picture_url text,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    CONSTRAINT users_email_lower CHECK (email = lower(email)),
    CONSTRAINT users_email_unique UNIQUE (email)
);

CREATE TABLE user_identities (
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider   text        NOT NULL,
    subject    text        NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT user_identities_provider_subject UNIQUE (provider, subject)
);

CREATE INDEX user_identities_user_idx ON user_identities (user_id);

CREATE TABLE login_tokens (
    id         uuid        PRIMARY KEY,
    email      text        NOT NULL,
    link_hash  text        NOT NULL,
    code_hash  text        NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    attempts   int         NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    CONSTRAINT login_tokens_link_hash_unique UNIQUE (link_hash)
);

CREATE INDEX login_tokens_email_idx ON login_tokens (email, created_at DESC);
