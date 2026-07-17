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
 * Service Provider Interface for per-token text embedding (ColBERT-style).
 *
 * <p>Unlike {@link EmbeddingProvider} which produces a single vector per text,
 * this produces one vector per token — enabling token-level late-interaction
 * matching via MaxSim scoring. This is the foundation of ColBERT v2 reranking.</p>
 *
 * <h3>ColBERT MaxSim Scoring</h3>
 * <pre>
 *   score(Q, D) = Σ_i  max_j  cosine(q_i, d_j)
 *
 *   For each query token q_i, find the document token d_j with maximum
 *   similarity, then sum across all query tokens. This enables token-level
 *   grounding: the model can verify that specific query terms are actually
 *   present in the document.
 * </pre>
 *
 * <h3>Storage Considerations</h3>
 * <p>Per-token embeddings are expensive: a 200-token document with 128-dim
 * ColBERT embeddings requires 200 × 128 = 25,600 floats (~100KB). This is
 * why ColBERT is used as a <b>reranker</b> (compute on-the-fly for top-50
 * candidates) rather than as a first-stage retriever (would require storing
 * token embeddings for all documents).</p>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>{@link #encode(String)} produces one embedding per token</li>
 *   <li>Token embeddings are typically 128-dimensional (vs 384-768 for dense)</li>
 *   <li>Implementations must be thread-safe</li>
 * </ul>
 *
 * <h3>Example</h3>
 * <pre>{@code
 *   TokenEmbeddingProvider colbert = new OnnxColBERTProvider("models/colbert-v2");
 *   TokenEmbeddingResult result = colbert.encode("memory consolidation");
 *   // result.embeddings() → float[2][128]
 *   // result.tokens() → ["memory", "consolidation"]
 * }</pre>
 */
public interface TokenEmbeddingProvider extends AutoCloseable {

    /**
     * Encodes text into per-token embeddings.
     *
     * @param text the input text
     * @return per-token embedding result
     */
    TokenEmbeddingResult encode(String text);

    /**
     * Encodes multiple texts in a single batch call.
     *
     * <p>Default implementation calls {@link #encode(String)} sequentially.
     * Providers that support native batching should override for efficiency.</p>
     *
     * @param texts list of input texts
     * @return list of token embedding results (same order as input)
     */
    default List<TokenEmbeddingResult> encodeBatch(List<String> texts) {
        return texts.stream().map(this::encode).toList();
    }

    /**
     * Returns the dimensionality of each token embedding.
     *
     * <p>ColBERT v2 typically uses 128 dimensions per token.</p>
     *
     * @return token embedding dimensions
     */
    int tokenDimensions();

    /**
     * Returns the name of the underlying model.
     *
     * @return model identifier (e.g., "colbert-v2", "colbert-xm")
     */
    String modelName();

    /**
     * Returns the maximum number of tokens this model supports per input.
     *
     * @return max token count (default: 512)
     */
    default int maxTokens() {
        return 512;
    }

    /**
     * Default no-op close. Override if the provider holds resources.
     */
    @Override
    default void close() {}
}
