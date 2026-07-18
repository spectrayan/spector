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
 * Interface for generating sparse bag-of-words weights (SPLADE) from text strings.
 */
public interface SparseEmbeddingProvider extends AutoCloseable {

    /**
     * Generates sparse term weights for a single text.
     *
     * @param text the text to encode
     * @return sparse term-weight mapping
     */
    SparseEmbeddingResult encode(String text);

    /**
     * Generates sparse term weights for a list of texts in batch.
     *
     * @param texts the texts to encode
     * @return list of sparse embedding results in matching order
     */
    default List<SparseEmbeddingResult> encodeBatch(List<String> texts) {
        return texts.stream().map(this::encode).toList();
    }

    /**
     * Returns the unique model identifier.
     */
    String modelName();

    /**
     * Returns the vocabulary size (number of possible unique terms).
     *
     * @return maximum vocabulary size
     */
    int vocabularySize();

    /**
     * Returns the type of sparse encoding method.
     *
     * @return the encoding method type
     */
    default SparseEncodingType type() {
        return SparseEncodingType.SPLADE;
    }

    /**
     * Default no-op close. Override if the provider holds resources.
     */
    @Override
    default void close() {}

    /**
     * Enumeration of sparse encoding method types.
     */
    enum SparseEncodingType {
        /** SPLADE: neural term expansion via masked language model. */
        SPLADE,
        /** Li-LSR: inference-free lookup-table-based sparse retrieval. */
        LI_LSR,
        /** SPLARE: sparse autoencoder-based learned sparse retrieval. */
        SPLARE
    }
}
