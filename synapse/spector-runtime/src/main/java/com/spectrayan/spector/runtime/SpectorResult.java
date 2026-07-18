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
package com.spectrayan.spector.runtime;

import com.spectrayan.spector.config.SpectorMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SourceModality;

/**
 * Product-level result type for cognitive memory queries.
 *
 * <p>Provides a view of memory mode results including cognitive metadata
 * (importance, age, valence).</p>
 *
 * @param id              memory ID
 * @param text            text content
 * @param score           composite relevance score (0.0–1.0)
 * @param rawSimilarity   raw vector similarity (always null)
 * @param importance      memory importance score
 * @param ageDays         age of the memory in days
 * @param valence         emotional valence (-128 to 127)
 * @param mode            the operating mode (always MEMORY)
 * @param tags            associated tags
 * @param memoryType      memory tier
 * @param sourceModality  what the memory originally was (TEXT, IMAGE, AUDIO, VIDEO)
 * @param sourceUri       URI to the original asset (null for text-only memories)
 */
public record SpectorResult(
        String id,
        String text,
        float score,
        Float rawSimilarity,
        Float importance,
        Float ageDays,
        Byte valence,
        SpectorMode mode,
        String[] tags,
        MemoryType memoryType,
        SourceModality sourceModality,
        String sourceUri
) {

    /** Creates a memory-mode result (backward compatible — defaults to TEXT modality). */
    public static SpectorResult fromMemory(String id, String text, float score,
                                            float importance, float ageDays,
                                            byte valence, String[] tags, MemoryType memoryType) {
        return new SpectorResult(id, text, score, null, importance, ageDays, valence,
                SpectorMode.MEMORY, tags, memoryType, SourceModality.TEXT, null);
    }

    /** Creates a memory-mode result with multimodal metadata. */
    public static SpectorResult fromMemory(String id, String text, float score,
                                            float importance, float ageDays,
                                            byte valence, String[] tags, MemoryType memoryType,
                                            SourceModality modality, String sourceUri) {
        return new SpectorResult(id, text, score, null, importance, ageDays, valence,
                SpectorMode.MEMORY, tags, memoryType,
                modality != null ? modality : SourceModality.TEXT, sourceUri);
    }

    /** Returns true if this result represents a multimodal (non-text) memory. */
    public boolean isMultimodal() {
        return sourceModality != null && sourceModality != SourceModality.TEXT;
    }
}
