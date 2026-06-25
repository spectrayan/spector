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

import com.spectrayan.spector.memory.model.ScoreBreakdown;

/**
 * Serializable DTO for the scoring pipeline breakdown.
 *
 * <p>Used in {@link WhyNotResponseDto} and can be added to
 * {@link RecallResponseDto} for per-result score transparency.</p>
 *
 * @param similarity         raw cosine similarity between query and memory vectors
 * @param importanceDecay    importance × decay factor product
 * @param tagBoostFactor     bloom filter tag match boost multiplier
 * @param habituationPenalty habituation (repeated access) penalty multiplier
 * @param graphBoost         hebbian/temporal graph co-activation boost
 * @param valenceAlignment   emotional valence alignment multiplier
 * @param finalScore         final composite score after all factors
 * @param weakestMultiplier  name of the factor that most reduced the score
 */
public record ScoreBreakdownDto(
        float similarity,
        float importanceDecay,
        float tagBoostFactor,
        float habituationPenalty,
        float graphBoost,
        float valenceAlignment,
        float finalScore,
        String weakestMultiplier
) {

    /**
     * Creates a DTO from the domain model.
     *
     * @param bd the score breakdown from the recall pipeline
     * @return serializable DTO, or null if input is null
     */
    public static ScoreBreakdownDto from(ScoreBreakdown bd) {
        if (bd == null) return null;
        return new ScoreBreakdownDto(
                bd.similarity(),
                bd.importanceDecay(),
                bd.tagBoostFactor(),
                bd.habituationPenalty(),
                bd.graphBoost(),
                bd.valenceAlignment(),
                bd.finalScore(),
                bd.weakestMultiplier()
        );
    }
}
