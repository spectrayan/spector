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
package com.spectrayan.spector.node.api.dto;

import com.spectrayan.spector.memory.metamemory.MemoryInsight;

/**
 * Response DTO for {@code POST /memory/introspect}.
 *
 * @param query           the introspection query
 * @param known           whether the topic is known in memory
 * @param confidence      overall confidence score (0.0–1.0)
 * @param totalMemories   number of memories matching the topic
 * @param avgImportance   average importance across matching memories
 * @param avgValence      average emotional valence
 * @param avgAgeDays      average age of matching memories in days
 * @param staleness       staleness score (0.0 = fresh, 1.0 = stale)
 * @param stale           whether knowledge may be outdated
 * @param hasGaps         whether there are identified knowledge gaps
 * @param gaps            identified knowledge gaps
 * @param recallsPerDay   recall frequency for this topic
 * @param recommendation  actionable recommendation text
 */
public record IntrospectResponseDto(
        String query,
        boolean known,
        float confidence,
        int totalMemories,
        float avgImportance,
        float avgValence,
        float avgAgeDays,
        float staleness,
        boolean stale,
        boolean hasGaps,
        String[] gaps,
        float recallsPerDay,
        String recommendation
) {

    /**
     * Creates a response DTO from the domain model.
     */
    public static IntrospectResponseDto from(MemoryInsight insight) {
        return new IntrospectResponseDto(
                insight.query(),
                insight.isKnown(),
                insight.confidence(),
                insight.totalMemories(),
                insight.avgImportance(),
                insight.avgValence(),
                insight.avgAgeDays(),
                insight.staleness(),
                insight.isStale(),
                insight.hasGaps(),
                insight.gaps(),
                insight.recallsPerDay(),
                insight.recommendation()
        );
    }
}
