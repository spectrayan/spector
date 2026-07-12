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

/**
 * Per-token embeddings result from a {@link TokenEmbeddingProvider}.
 *
 * <p>Contains a 2D embedding matrix where each row is a token's embedding vector.
 * Used for ColBERT-style MaxSim scoring where each query token is matched against
 * all document tokens independently.</p>
 *
 * <h3>Memory Layout</h3>
 * <pre>
 *   embeddings[0] → [0.12, -0.34, 0.56, ...]  // token 0 (128 dims)
 *   embeddings[1] → [0.78, 0.11, -0.22, ...]   // token 1 (128 dims)
 *   ...
 *   embeddings[N-1] → [...]                     // token N-1
 * </pre>
 *
 * @param embeddings  2D array: {@code [tokenCount][tokenDimensions]}
 * @param tokens      the tokenized text (for debugging/tracing)
 * @param tokenCount  number of tokens produced
 * @param modelName   the source model name
 */
public record TokenEmbeddingResult(
        float[][] embeddings,
        String[] tokens,
        int tokenCount,
        String modelName
) {

    /**
     * Returns the embedding vector for a specific token index.
     *
     * @param tokenIndex zero-based token index
     * @return the token's embedding vector
     * @throws ArrayIndexOutOfBoundsException if tokenIndex is out of range
     */
    public float[] tokenEmbedding(int tokenIndex) {
        return embeddings[tokenIndex];
    }

    /**
     * Returns the dimensionality of each token embedding.
     *
     * @return dimensions per token (typically 128 for ColBERT)
     */
    public int dimensions() {
        return embeddings.length > 0 ? embeddings[0].length : 0;
    }
}
