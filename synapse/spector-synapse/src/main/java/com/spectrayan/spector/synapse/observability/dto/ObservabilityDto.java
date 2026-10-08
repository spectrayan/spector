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
package com.spectrayan.spector.synapse.observability.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Strongly-typed DTOs for the content-free Observability API (ADR-0083).
 *
 * <p>Invariant: Under no circumstances may any DTO in this package declare
 * fields for raw memory content (such as text, vectors, raw tags, or unvetted metadata).</p>
 */
public final class ObservabilityDto {

    private ObservabilityDto() {}

    /**
     * Chronological memory event for timeline visualization (strictly content-free).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TimelineEventDto(
            String eventType,
            String memoryId,
            String namespace,
            String timestamp,
            String tier,
            float importance,
            byte valence,
            int recallCount
    ) {}

    /**
     * Timeline response envelope.
     */
    public record TimelineResponse(
            List<TimelineEventDto> events,
            int totalEvents
    ) {}

    /**
     * Algorithmic cognitive score breakdown for traced recall.
     */
    public record ScoreBreakdownDto(
            double similarity,
            double importanceDecay,
            double tagBoostFactor,
            double habituationPenalty,
            double graphBoost,
            double valenceAlignment,
            double finalScore
    ) {}

    /**
     * Individual candidate scoring trace result (content-free).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TracedRecallItemDto(
            String id,
            double score,
            String memoryType,
            float importance,
            double ageDays,
            int recallCount,
            byte valence,
            String retrievalMode,
            ScoreBreakdownDto breakdown
    ) {}

    /**
     * Traced recall response envelope.
     */
    public record TracedRecallResponse(
            String query,
            List<TracedRecallItemDto> results,
            int totalResults,
            long latencyMicros,
            boolean traceEnabled
    ) {}

    /**
     * Request payload for traced recall.
     */
    public record TracedRecallRequest(
            String query,
            int topK
    ) {
        public TracedRecallRequest {
            if (topK <= 0) topK = 10;
        }
    }

    /**
     * Aggregate memory store statistics.
     */
    public record ObservabilityStatsResponse(
            int totalMemories,
            int indexedMemories,
            Map<String, Integer> tierDistribution
    ) {}

    /**
     * Single histogram age distribution bin.
     */
    public record AgeBucketDto(
            String label,
            int count
    ) {}

    /**
     * Age distribution response envelope.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AgeDistributionResponse(
            List<AgeBucketDto> buckets,
            int totalMemories,
            String oldestMemory,
            String newestMemory
    ) {}
}
