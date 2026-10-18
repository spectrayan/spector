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

-- Issue #1068: Add owner_account_id, tenant_id, and namespace_id to CHAT_SESSION
-- so operational chat sessions are scoped per authenticated user, tenant, and namespace.

ALTER TABLE CHAT_SESSION ADD COLUMN IF NOT EXISTS owner_account_id VARCHAR(128) NOT NULL DEFAULT 'default';
ALTER TABLE CHAT_SESSION ADD COLUMN IF NOT EXISTS tenant_id        VARCHAR(64)  NOT NULL DEFAULT 'default';
ALTER TABLE CHAT_SESSION ADD COLUMN IF NOT EXISTS namespace_id     VARCHAR(128) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_chat_session_owner_tenant ON CHAT_SESSION (tenant_id, owner_account_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_chat_session_namespace    ON CHAT_SESSION (tenant_id, namespace_id);
