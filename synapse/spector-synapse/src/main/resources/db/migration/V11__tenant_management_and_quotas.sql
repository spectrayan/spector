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

-- Issue #1055: Tenant Management API (/api/v1/admin/tenants), settings, quotas, and mutation audit log (ADR-0087).

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS plan VARCHAR(64) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS byok_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS break_glass_approver_required BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS break_glass_max_duration_minutes INT NOT NULL DEFAULT 60;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS max_accounts INT NOT NULL DEFAULT -1;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS max_namespaces INT NOT NULL DEFAULT -1;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS max_storage_bytes BIGINT NOT NULL DEFAULT -1;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_tenants_status ON tenants (status);

-- Ensure default tenant record exists
INSERT INTO tenants (tenant_id, name, description, status, plan)
SELECT 'default', 'Default Tenant', 'Primary system tenant', 'ACTIVE', 'STANDARD'
WHERE NOT EXISTS (SELECT 1 FROM tenants WHERE tenant_id = 'default');

-- Administrative mutation audit log (append-only)
CREATE TABLE IF NOT EXISTS admin_audit_log (
    audit_id        VARCHAR(36)   NOT NULL,
    timestamp       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_id        VARCHAR(64)   NOT NULL,
    actor_tenant_id VARCHAR(64),
    action          VARCHAR(64)   NOT NULL,
    target_type     VARCHAR(64)   NOT NULL,
    target_id       VARCHAR(128)  NOT NULL,
    details_json    CLOB,
    PRIMARY KEY (audit_id)
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_target ON admin_audit_log (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_admin_audit_time ON admin_audit_log (timestamp);
