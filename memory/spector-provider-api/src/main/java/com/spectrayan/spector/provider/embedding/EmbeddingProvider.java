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
package com.spectrayan.spector.provider.embedding;

import java.util.List;

/**
 * Interface for generating dense vector embeddings from text strings.
 */
public interface EmbeddingProvider {

    /**
     * Generates a dense vector embedding for a single text.
     *
     * @param text the text to embed
     * @return dense embedding vector and token metadata
     */
    EmbeddingResult embed(String text);

    /**
     * Generates dense vector embeddings for a list of texts in batch.
     *
     * @param texts the texts to embed
     * @return list of dense embedding results in matching order
     */
    List<EmbeddingResult> embedBatch(List<String> texts);

    /**
     * Returns the output dimensionality of the generated vectors.
     */
    int dimensions();

    /**
     * Returns the unique model identifier.
     */
    String modelName();
}
