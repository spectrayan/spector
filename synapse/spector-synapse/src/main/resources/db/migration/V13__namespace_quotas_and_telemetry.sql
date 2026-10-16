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

-- Issue #1057: Admin Namespace Lifecycle (/api/v1/admin/namespaces) — per-namespace quotas and write telemetry.

ALTER TABLE namespaces ADD COLUMN IF NOT EXISTS max_memories INT;
ALTER TABLE namespaces ADD COLUMN IF NOT EXISTS max_storage_bytes BIGINT;
ALTER TABLE namespaces ADD COLUMN IF NOT EXISTS max_ops_per_minute INT;
ALTER TABLE namespaces ADD COLUMN IF NOT EXISTS last_write_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_namespaces_status ON namespaces (status);
CREATE INDEX IF NOT EXISTS idx_namespaces_type ON namespaces (type);
