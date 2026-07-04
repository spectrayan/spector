-- Spector Synapse — Initial Schema
-- Flyway migration V1: chat sessions, chat messages, agent souls

-- ── Chat Sessions ──
CREATE TABLE IF NOT EXISTS chat_sessions (
    session_id  VARCHAR(36) PRIMARY KEY,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    preview     VARCHAR(200),
    model       VARCHAR(100)
);

-- ── Chat Messages ──
CREATE TABLE IF NOT EXISTS chat_messages (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id  VARCHAR(36)  NOT NULL,
    role        VARCHAR(20)  NOT NULL,  -- user, assistant, system, tool
    content     CLOB         NOT NULL,
    model       VARCHAR(100),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_messages_session
        FOREIGN KEY (session_id) REFERENCES chat_sessions(session_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_chat_messages_session ON chat_messages(session_id, created_at);

-- ── Agent Souls ──
CREATE TABLE IF NOT EXISTS agent_souls (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    description     VARCHAR(500),
    system_prompt   CLOB,
    personality     CLOB,         -- JSON: key-value personality traits
    model           VARCHAR(100),
    tools           CLOB,         -- JSON: list of enabled tool names
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ── API Keys (for multi-key support, future) ──
CREATE TABLE IF NOT EXISTS api_keys (
    id          VARCHAR(36) PRIMARY KEY,
    key_hash    VARCHAR(64) NOT NULL,
    name        VARCHAR(100),
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used   TIMESTAMP,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX idx_api_keys_hash ON api_keys(key_hash);
