--
-- Copyright 2026 Spectrayan
--
-- Licensed under the Business Source License 1.1 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
--
-- Change Date: July 6, 2030
-- Change License: Apache License, Version 2.0
--

-- ADR-0083: Add namespace isolation columns to memory_analytics_snapshot.
-- Enables per-namespace history when spector.memory.analytics.history.enabled=true.

ALTER TABLE memory_analytics_snapshot ADD COLUMN namespace_id VARCHAR(64) DEFAULT 'default' NOT NULL;
ALTER TABLE memory_analytics_snapshot ADD COLUMN account_id VARCHAR(64);
ALTER TABLE memory_analytics_snapshot ADD COLUMN instance_id VARCHAR(64) DEFAULT 'local' NOT NULL;

-- Replace single-column PK with composite PK.
-- H2 syntax: drop existing constraint then add composite.
ALTER TABLE memory_analytics_snapshot DROP PRIMARY KEY;
ALTER TABLE memory_analytics_snapshot ADD PRIMARY KEY (snapshot_time, namespace_id, instance_id);

-- Index for efficient namespace-scoped time-range queries.
CREATE INDEX idx_analytics_ns_time ON memory_analytics_snapshot (namespace_id, snapshot_time);
