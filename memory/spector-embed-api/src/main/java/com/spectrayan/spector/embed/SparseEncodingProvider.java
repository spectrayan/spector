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
package com.spectrayan.spector.embed;

import java.util.List;

/**
 * Service Provider Interface for sparse text encoding (SPLADE, Li-LSR, SPLARE).
 *
 * <p>Encodes text into sparse term-weight vectors for learned sparse retrieval.
 * Unlike dense {@link EmbeddingProvider} which produces a fixed-size float[] vector,
 * sparse encoding produces a {@link SparseEncodingResult} containing only non-zero
 * terms and their learned weights — typically &lt;200 entries from a ~30K vocabulary.</p>
 *
 * <h3>Retrieval Architecture</h3>
 * <p>Sparse vectors integrate with the same inverted-index infrastructure used by BM25:
 * <ul>
 *   <li>At ingestion: encoder produces {@code {term → weight}} for each document</li>
 *   <li>Weights are stored in posting lists (like TF-IDF, but neural)</li>
 *   <li>At query: encoder produces query sparse vector, scored via inner product</li>
 *   <li>Fusion with dense retrieval via RRF in the recall pipeline</li>
 * </ul>
 *
 * <h3>Implementations</h3>
 * <ul>
 *   <li><b>SPLADE</b>: Neural model that learns term expansion (e.g., "car" → also activates
 *       "vehicle", "automobile"). Requires model inference at both index and query time.</li>
 *   <li><b>Li-LSR</b>: Inference-free learned sparse retrieval via precomputed lookup tables.
 *       No model needed at query time — table lookup replaces neural inference.</li>
 *   <li><b>SPLARE</b> (future): Sparse autoencoders for multilingual sparse features.</li>
 * </ul>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>{@link #encode(String)} must return a result with consistent vocabulary</li>
 *   <li>Weights are non-negative (zero entries are omitted from the result)</li>
 *   <li>Implementations must be thread-safe</li>
 * </ul>
 *
 * <h3>Example</h3>
 * <pre>{@code
 *   SparseEncodingProvider splade = new OnnxSpladeProvider("models/splade-v3");
 *   SparseEncodingResult result = splade.encode("memory consolidation during sleep");
 *   // result.weights() → {"memory": 2.3, "consolidation": 1.8, "sleep": 2.1,
 *   //                      "rem": 0.7, "hippocampus": 0.5, ...}
 * }</pre>
 */
public interface SparseEncodingProvider extends AutoCloseable {

    /**
     * Encodes a single text string into a sparse term-weight vector.
     *
     * @param text the input text
     * @return sparse encoding result containing non-zero term weights
     */
    SparseEncodingResult encode(String text);

    /**
     * Encodes multiple texts in a single batch call.
     *
     * <p>Default implementation calls {@link #encode(String)} sequentially.
     * Providers that support native batching (e.g., ONNX Runtime) should override
     * this for efficiency.</p>
     *
     * @param texts list of input texts
     * @return list of sparse encoding results (same order as input)
     */
    default List<SparseEncodingResult> encodeBatch(List<String> texts) {
        return texts.stream().map(this::encode).toList();
    }

    /**
     * Returns the name of the underlying model or method.
     *
     * @return model identifier (e.g., "splade-v3", "li-lsr-msmarco", "splare-multilingual")
     */
    String modelName();

    /**
     * Returns the vocabulary size (number of possible unique terms).
     *
     * <p>SPLADE typically uses a 30K WordPiece vocabulary.
     * Li-LSR may use a larger or domain-specific vocabulary.</p>
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
     * Default no-op close. Override if the provider holds resources
     * (e.g., ONNX Runtime session).
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
