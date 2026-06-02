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
) implements SpectorEvent {
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
