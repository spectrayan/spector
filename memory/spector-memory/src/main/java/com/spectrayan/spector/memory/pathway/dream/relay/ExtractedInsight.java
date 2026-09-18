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
package com.spectrayan.spector.memory.pathway.dream.relay;

import com.spectrayan.spector.kernel.id.MemoryId;

import java.util.List;

/**
 * Record representing the distilled cognitive residue of a dream (insight/rule/relation).
 * Biological analog: Semantic knowledge extracted from episodic memories during sleep-dependent consolidation.
 *
 * @since 1.4.0
 */
public record ExtractedInsight(
    String id,
    String insightText,
    float[] embedding,
    InsightType type,
    List<String> sourceMemoryIds,
    float confidence,
    float expectedFreeEnergy
) {
    public enum InsightType {
        SEMANTIC,
        PROCEDURAL,
        CONTRADICTION
    }
}
