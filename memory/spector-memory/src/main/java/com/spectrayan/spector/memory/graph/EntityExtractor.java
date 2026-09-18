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
package com.spectrayan.spector.memory.graph;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;

import java.util.List;

/**
 * Service Provider Interface for entity extraction from memory text.
 *
 * <p>Implementations analyze text to identify named entities and their relationships.
 * This follows the same pluggable pattern as
 * {@link com.spectrayan.spector.memory.pathway.pipeline.TagExtractor} and
 * {@link com.spectrayan.spector.provider.embedding.EmbeddingProvider}.</p>
 *
 * <h3>Implementations</h3>
 * <ul>
 *   <li>{@link LlmEntityExtractor}  --  LLM-powered extraction via LlmProvider</li>
 *   <li>{@link NoOpEntityExtractor}  --  returns empty list (when extraction is disabled)</li>
 * </ul>
 *
 * @see ExtractedEntity
 * @see HyperEntityGraphMemory
 */
public interface EntityExtractor {

    /**
     * Extracts entities and their relationships from text.
     *
     * @param id   the memory identifier
     * @param text the memory content to analyze
     * @return list of extracted entities with typed relations
     */
    List<ExtractedEntity> extract(String id, String text);

    /**
     * Returns whether this extractor is available and ready.
     *
     * @return true if the extractor can process requests
     */
    default boolean isAvailable() {
        return true;
    }
}
