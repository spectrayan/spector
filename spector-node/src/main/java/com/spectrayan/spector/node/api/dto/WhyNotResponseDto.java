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

import com.spectrayan.spector.memory.model.WhyNotExplanation;

/**
 * Response DTO for {@code POST /memory/why-not}.
 *
 * <p>Explains why a specific memory was NOT returned for a given query.</p>
 *
 * @param memoryId   the investigated memory ID
 * @param reason     diagnostic reason (e.g., NOT_FOUND, SUPPRESSED, OUTRANKED)
 * @param exists     whether the memory exists at all
 * @param suppressed whether the memory is currently suppressed
 * @param scoreGap   gap between the memory's score and the topK cutoff
 * @param breakdown  detailed scoring breakdown (null if not applicable)
 * @param summary    human-readable summary text
 */
public record WhyNotResponseDto(
        String memoryId,
        String reason,
        boolean exists,
        boolean suppressed,
        float scoreGap,
        ScoreBreakdownDto breakdown,
        String summary
) {

    /**
     * Creates a response DTO from the domain model.
     */
    public static WhyNotResponseDto from(WhyNotExplanation explanation) {
        return new WhyNotResponseDto(
                explanation.memoryId(),
                explanation.reason().name(),
                explanation.exists(),
                explanation.suppressed(),
                explanation.scoreGap(),
                ScoreBreakdownDto.from(explanation.breakdown()),
                explanation.summary()
        );
    }
}
