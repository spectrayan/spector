/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.aisme.segmentation;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable packaged autobiographical episode segment partitioned by Event Segmentation Theory boundaries.
 *
 * <h3>Biological Analog: Hippocampal Consolidated Episodic Event</h3>
 * <p>Encapsulates a coherent temporal-causal episode package with its sensory centroid embedding,
 * frame span, peak prediction surprisal, and partitioning rationale.</p>
 *
 * @param segmentId unique identifier for the episodic segment
 * @param startTimestampMs epoch timestamp in milliseconds when the episode began
 * @param endTimestampMs epoch timestamp in milliseconds when the episode boundary occurred
 * @param frameCount total count of sensory observation frames contained in this episode
 * @param centroidVector mean sensory embedding vector across all constituent frames
 * @param peakSurprisal maximum instantaneous prediction surprisal recorded during the episode
 * @param boundaryReason specific trigger mechanism that initiated the boundary cut
 */
public record EpisodicSegment(
        String segmentId,
        long startTimestampMs,
        long endTimestampMs,
        int frameCount,
        float[] centroidVector,
        float peakSurprisal,
        BoundaryReason boundaryReason
) {
    public EpisodicSegment {
        Objects.requireNonNull(segmentId, "segmentId must not be null");
        Objects.requireNonNull(boundaryReason, "boundaryReason must not be null");
        if (centroidVector != null) {
            centroidVector = centroidVector.clone();
        }
    }

    @Override
    public float[] centroidVector() {
        return centroidVector != null ? centroidVector.clone() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EpisodicSegment that)) return false;
        return startTimestampMs == that.startTimestampMs &&
                endTimestampMs == that.endTimestampMs &&
                frameCount == that.frameCount &&
                Float.compare(that.peakSurprisal, peakSurprisal) == 0 &&
                Objects.equals(segmentId, that.segmentId) &&
                Arrays.equals(centroidVector, that.centroidVector) &&
                boundaryReason == that.boundaryReason;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(segmentId, startTimestampMs, endTimestampMs, frameCount, peakSurprisal, boundaryReason);
        result = 31 * result + Arrays.hashCode(centroidVector);
        return result;
    }
}
