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

-- Issue #1181: Durable storage for committed HTTP Idempotency-Key responses.

CREATE TABLE IF NOT EXISTS idempotency_records (
    namespace_scope       VARCHAR(256)  NOT NULL,
    idempotency_key       VARCHAR(256)  NOT NULL,
    request_method        VARCHAR(16)   NOT NULL,
    request_path          VARCHAR(1024) NOT NULL,
    payload_sha256        VARCHAR(64)   NOT NULL,
    status_code           INT           NOT NULL,
    response_headers_json CLOB          NOT NULL,
    response_body         BLOB          NOT NULL,
    committed_at_ms       BIGINT        NOT NULL,
    expires_at_ms         BIGINT        NOT NULL,
    PRIMARY KEY (namespace_scope, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_expires_at ON idempotency_records (expires_at_ms);
CREATE INDEX IF NOT EXISTS idx_idempotency_committed_at ON idempotency_records (committed_at_ms);
