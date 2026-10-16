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

-- Issue #1059 / ADR-0087: Append-only tamper-evident audit log for admin actions and privileged access.
-- Records every /api/v1/admin/** mutation, BYOK key operation, break-glass request/approval/read,
-- cross-tenant action, quota change, role/grant change, and compliance erasure with SHA-256 hash chaining.

CREATE TABLE IF NOT EXISTS audit_event (
    id               VARCHAR(64) PRIMARY KEY,
    ts               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id        VARCHAR(64) NOT NULL DEFAULT 'default',
    actor_account_id VARCHAR(128) NOT NULL,
    actor_role       VARCHAR(64),
    action           VARCHAR(128) NOT NULL,
    target_type      VARCHAR(64) NOT NULL,
    target_id        VARCHAR(128) NOT NULL,
    namespace_id     VARCHAR(64),
    reason           VARCHAR(1024),
    request_id       VARCHAR(128),
    outcome          VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    details_json     TEXT,
    prev_hash        VARCHAR(64) NOT NULL DEFAULT 'GENESIS',
    entry_hash       VARCHAR(64) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_event_tenant_ts ON audit_event (tenant_id, ts);
CREATE INDEX IF NOT EXISTS idx_audit_event_actor_ts ON audit_event (actor_account_id, ts);
CREATE INDEX IF NOT EXISTS idx_audit_event_namespace_ts ON audit_event (namespace_id, ts);
CREATE INDEX IF NOT EXISTS idx_audit_event_action_ts ON audit_event (action, ts);
CREATE INDEX IF NOT EXISTS idx_audit_event_target ON audit_event (target_type, target_id);
