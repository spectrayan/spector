--
-- Copyright 2026 Spectrayan
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--
--

-- ADR-0084: Dual-Plane Conversation Persistence — Operational Execution Plane.
-- Durable relational storage for chat sessions, execution turns, event replay, and graph checkpoints.
-- Portable ANSI SQL DDL compatible across H2 (OSS / testing) and PostgreSQL (enterprise).

-- 1. Chat Sessions: Root entity for operational chat threads and drawer listing
CREATE TABLE CHAT_SESSION (
    id          VARCHAR(64)   PRIMARY KEY,
    title       VARCHAR(255),
    status      VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    archived    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP     NOT NULL,
    updated_at  TIMESTAMP     NOT NULL
);

-- Index for session listing ordered by recency
CREATE INDEX idx_chat_session_updated ON CHAT_SESSION (updated_at);

-- 2. Chat Turns: Individual conversational turns within a session
CREATE TABLE CHAT_TURN (
    id            VARCHAR(64)   PRIMARY KEY,
    session_id    VARCHAR(64)   NOT NULL,
    seq           INT           NOT NULL,
    status        VARCHAR(32)   NOT NULL DEFAULT 'RUNNING',
    model         VARCHAR(128),
    primed_count  INT           NOT NULL DEFAULT 0,
    input_tokens  INT           NOT NULL DEFAULT 0,
    output_tokens INT           NOT NULL DEFAULT 0,
    latency_ms    BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMP     NOT NULL,
    CONSTRAINT fk_turn_session FOREIGN KEY (session_id) REFERENCES CHAT_SESSION (id) ON DELETE CASCADE
);

-- Index for loading turns in session order
CREATE INDEX idx_chat_turn_session ON CHAT_TURN (session_id, seq);

-- 3. Chat Events: Granular, immutable operational event stream for structured turn replay
CREATE TABLE CHAT_EVENT (
    id            VARCHAR(64)   PRIMARY KEY,
    turn_id       VARCHAR(64)   NOT NULL,
    seq           INT           NOT NULL,
    type          VARCHAR(32)   NOT NULL,
    payload_json  CLOB          NOT NULL,
    created_at    TIMESTAMP     NOT NULL,
    CONSTRAINT fk_event_turn FOREIGN KEY (turn_id) REFERENCES CHAT_TURN (id) ON DELETE CASCADE
);

-- Index for sequential event reconstruction per turn
CREATE INDEX idx_chat_event_turn ON CHAT_EVENT (turn_id, seq);

-- 4. Graph Checkpoints: Serialized LangGraph4j StateGraph checkpoints for turn resumption
CREATE TABLE GRAPH_CHECKPOINT (
    thread_id      VARCHAR(64)   PRIMARY KEY,
    checkpoint_id  VARCHAR(64)   NOT NULL,
    state          BLOB          NOT NULL,
    written_at     TIMESTAMP     NOT NULL
);
