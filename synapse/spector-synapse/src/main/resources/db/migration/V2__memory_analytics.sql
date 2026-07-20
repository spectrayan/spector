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

CREATE TABLE memory_analytics_snapshot (
    snapshot_time TIMESTAMP NOT NULL PRIMARY KEY,
    total_count BIGINT NOT NULL,
    working_count INT NOT NULL,
    episodic_count INT NOT NULL,
    semantic_count INT NOT NULL,
    procedural_count INT NOT NULL,
    hebbian_edges INT NOT NULL,
    temporal_links INT NOT NULL,
    entity_nodes INT NOT NULL,
    entity_edges INT NOT NULL,
    avg_latency_ms DOUBLE NOT NULL,
    recall_count INT NOT NULL,
    remember_count INT NOT NULL,
    consolidations_run INT NOT NULL
);
