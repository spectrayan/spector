/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.node.event;

import java.time.Instant;
import java.util.List;

/**
 * Cortex embedding projection event — carries PCA/random-projected 3D
 * coordinates of stored vectors for the vector space visualization panel.
 *
 * <p>Emitted periodically by {@code CortexMetricsPublisher} at a slow cadence
 * (every ~10s) and on query execution to update the query dot position.</p>
 *
 * @param nodeId          node that computed the projection
 * @param timestamp       when the projection was computed
 * @param points          projected 3D coordinates of stored vectors
 * @param queryProjection projected 3D position of the last query vector (null if none)
 */
public record SpectorCortexEmbeddingProjectionEvent(
        String nodeId, Instant timestamp,
        List<ProjectedPointDto> points,
        float[] queryProjection
) implements SpectorNodeEvent {
    @Override public String eventType() { return "cortex.embedding.projection"; }

    /**
     * Serializable projected point DTO for SSE transmission.
     *
     * @param id         memory record ID
     * @param x          projected X coordinate
     * @param y          projected Y coordinate
     * @param z          projected Z coordinate
     * @param tier       memory tier
     * @param importance importance score (0-1)
     * @param label      human-readable label
     */
    public record ProjectedPointDto(
            String id,
            float x, float y, float z,
            String tier,
            float importance,
            String label
    ) {}
}
