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

-- Issue #1060 / ADR-0087 §5.4: Emergency break-glass namespace access for non-BYOK tenants.
-- Time-boxed, dual-admin approved, strictly read-only, and audited per memory read.

CREATE TABLE IF NOT EXISTS break_glass_requests (
    id                   VARCHAR(64) PRIMARY KEY,
    tenant_id            VARCHAR(64) NOT NULL DEFAULT 'default',
    namespace_id         VARCHAR(128) NOT NULL,
    owner_account_id     VARCHAR(128) NOT NULL,
    requester_account_id VARCHAR(128) NOT NULL,
    requester_role       VARCHAR(64) NOT NULL,
    justification        VARCHAR(1024) NOT NULL,
    ticket_ref           VARCHAR(128),
    duration_minutes     INT NOT NULL DEFAULT 60,
    status               VARCHAR(32) NOT NULL DEFAULT 'PENDING_APPROVAL',
    approver_account_id  VARCHAR(128),
    approval_comment     VARCHAR(512),
    grant_id             VARCHAR(64),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at          TIMESTAMP WITH TIME ZONE,
    expires_at           TIMESTAMP WITH TIME ZONE,
    revoked_at           TIMESTAMP WITH TIME ZONE,
    revoked_by           VARCHAR(128),
    revoke_reason        VARCHAR(512)
);

CREATE INDEX IF NOT EXISTS idx_break_glass_tenant_status ON break_glass_requests (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_break_glass_ns_req_status ON break_glass_requests (namespace_id, requester_account_id, status);
CREATE INDEX IF NOT EXISTS idx_break_glass_expires ON break_glass_requests (status, expires_at);
