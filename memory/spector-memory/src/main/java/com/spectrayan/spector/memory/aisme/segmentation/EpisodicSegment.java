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
