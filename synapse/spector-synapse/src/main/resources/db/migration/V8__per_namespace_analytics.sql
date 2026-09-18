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

-- ADR-0083: Namespace-isolated memory analytics and stats telemetry.
-- Upgrades memory_analytics_snapshot to support per-namespace telemetry and multi-pod disambiguation.
-- Portable migration strategy across H2 and PostgreSQL: create table with composite PK, migrate, and rename.

CREATE TABLE memory_analytics_snapshot_new (
    snapshot_time TIMESTAMP NOT NULL,
    namespace_id VARCHAR(64) DEFAULT 'default' NOT NULL,
    instance_id VARCHAR(64) DEFAULT 'local' NOT NULL,
    account_id VARCHAR(64),
    total_count BIGINT NOT NULL,
    working_count INT NOT NULL,
    episodic_count INT NOT NULL,
    semantic_count INT NOT NULL,
    procedural_count INT NOT NULL,
    hebbian_edges INT NOT NULL,
    temporal_links INT NOT NULL,
    entity_nodes INT NOT NULL,
    entity_edges INT NOT NULL,
    avg_latency_ms DOUBLE PRECISION NOT NULL,
    recall_count BIGINT NOT NULL,
    remember_count BIGINT NOT NULL,
    consolidations_run BIGINT NOT NULL,
    PRIMARY KEY (snapshot_time, namespace_id, instance_id)
);

INSERT INTO memory_analytics_snapshot_new (
    snapshot_time, namespace_id, instance_id, account_id,
    total_count, working_count, episodic_count, semantic_count, procedural_count,
    hebbian_edges, temporal_links, entity_nodes, entity_edges,
    avg_latency_ms, recall_count, remember_count, consolidations_run
)
SELECT
    snapshot_time, 'default', 'local', NULL,
    total_count, working_count, episodic_count, semantic_count, procedural_count,
    hebbian_edges, temporal_links, entity_nodes, entity_edges,
    avg_latency_ms, recall_count, remember_count, consolidations_run
FROM memory_analytics_snapshot;

DROP TABLE memory_analytics_snapshot;

ALTER TABLE memory_analytics_snapshot_new RENAME TO memory_analytics_snapshot;

-- Index for efficient namespace-scoped time-range queries
CREATE INDEX idx_analytics_ns_time ON memory_analytics_snapshot (namespace_id, snapshot_time);
