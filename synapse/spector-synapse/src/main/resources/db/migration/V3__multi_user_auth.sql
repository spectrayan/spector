--
-- Copyright 2026 Spectrayan
--
-- Licensed under the Business Source License 1.1 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
--
-- Change Date: July 6, 2030
-- Change License: Apache License, Version 2.0
--

-- Multi-user authentication schema (single-tenant, multi-user).
-- No tenants table and no tenant foreign keys: the isolation boundary is the User.

-- Users: identity keyed by a 13-char TSID (user_id). username is a login handle only
-- and never appears in paths, the JWT `sub` claim, API key hashes, or foreign keys.
CREATE TABLE users (
    user_id               VARCHAR(13)  NOT NULL,
    username              VARCHAR(64)  NOT NULL,
    password_hash         VARCHAR(512) NOT NULL,
    email                 VARCHAR(320),
    display_name          VARCHAR(255),
    roles                 VARCHAR(512) NOT NULL DEFAULT '',
    scopes                VARCHAR(1024) NOT NULL DEFAULT '',
    must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_count    INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP,
    last_login_at         TIMESTAMP,
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP    NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uq_users_username UNIQUE (username)
);

-- Per-user API keys: only the SHA-256 hex hash (64 lowercase hex chars) is persisted.
CREATE TABLE api_keys (
    key_id      VARCHAR(13)  NOT NULL,
    user_id     VARCHAR(13)  NOT NULL,
    key_hash    VARCHAR(64)  NOT NULL,
    scopes      VARCHAR(1024) NOT NULL DEFAULT '',
    expires_at  TIMESTAMP,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL,
    PRIMARY KEY (key_id),
    CONSTRAINT fk_api_keys_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE INDEX idx_api_keys_key_hash ON api_keys (key_hash);
CREATE INDEX idx_api_keys_user_id ON api_keys (user_id);

-- Refresh tokens: only the SHA-256 hex hash (64 lowercase hex chars) is persisted.
CREATE TABLE refresh_tokens (
    token_id    VARCHAR(13)  NOT NULL,
    user_id     VARCHAR(13)  NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMP,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (token_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- JTI blocklist: logged-out / revoked access-token identifiers, purged after expiry.
CREATE TABLE jti_blocklist (
    jti         VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP,
    PRIMARY KEY (jti)
);
