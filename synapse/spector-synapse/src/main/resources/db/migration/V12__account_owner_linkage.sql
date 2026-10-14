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

-- Issue #1056: Admin Account & Agent-Principal Registry (/api/v1/admin/accounts) — owner linkage and indexing.

ALTER TABLE users ADD COLUMN IF NOT EXISTS owner_account_id VARCHAR(13);

CREATE INDEX IF NOT EXISTS idx_users_owner_account_id ON users (owner_account_id);
CREATE INDEX IF NOT EXISTS idx_users_tenant_kind ON users (tenant_id, kind);
