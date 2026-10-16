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

-- Issue #1058 / ADR-0083 & ADR-0087: Aggregated tenant, account & namespace analytics rollups (content-free).
-- Adds tenant_id, storage_bytes, dream_cycles_run, and error_count to memory_analytics_snapshot.

ALTER TABLE memory_analytics_snapshot ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT 'default' NOT NULL;
ALTER TABLE memory_analytics_snapshot ADD COLUMN IF NOT EXISTS storage_bytes BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE memory_analytics_snapshot ADD COLUMN IF NOT EXISTS dream_cycles_run BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE memory_analytics_snapshot ADD COLUMN IF NOT EXISTS error_count BIGINT DEFAULT 0 NOT NULL;

CREATE INDEX IF NOT EXISTS idx_analytics_tenant_time ON memory_analytics_snapshot (tenant_id, snapshot_time);
CREATE INDEX IF NOT EXISTS idx_analytics_account_time ON memory_analytics_snapshot (account_id, snapshot_time);
