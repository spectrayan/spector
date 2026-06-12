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

import java.util.Map;

/**
 * Result of sparse encoding: a map of term → learned weight.
 *
 * <p>Unlike dense embeddings ({@link EmbeddingResult}) which produce a fixed-size
 * float[] vector, sparse encodings are represented as a map where only non-zero
 * terms are present. This is typically sparse — fewer than 200 non-zero entries
 * from a vocabulary of ~30K terms.</p>
 *
 * <h3>Weight Semantics</h3>
 * <ul>
 *   <li>Weights are non-negative (terms with zero weight are omitted)</li>
 *   <li>Higher weight = stronger association between the term and the text</li>
 *   <li>SPLADE: weights represent log-saturated term importance from MLM logits</li>
 *   <li>Li-LSR: weights are precomputed from training data lookup tables</li>
 * </ul>
 *
 * <h3>Example</h3>
 * <pre>{@code
 *   // SPLADE encoding of "memory consolidation during sleep"
 *   SparseEncodingResult result = provider.encode(text);
 *   result.weights();
 *   // → {"memory": 2.3, "consolidation": 1.8, "sleep": 2.1,
 *   //    "rem": 0.7, "hippocampus": 0.5, "brain": 0.3, ...}
 *   //
 *   // Note: "rem" and "hippocampus" are EXPANDED terms — not in the original text.
 *   // This is the key advantage over BM25: neural term expansion.
 * }</pre>
 *
 * @param weights    map of term → learned weight (non-zero entries only)
 * @param tokenCount number of input tokens consumed by the encoder
 * @param modelName  the source model/method name
 */
public record SparseEncodingResult(
        Map<String, Float> weights,
        int tokenCount,
        String modelName
) {

    /**
     * Returns the number of non-zero terms in this sparse vector.
     *
     * @return sparsity count
     */
    public int nonZeroCount() {
        return weights.size();
    }

    /**
     * Returns the L1 norm (sum of all weights) of this sparse vector.
     *
     * @return sum of weights
     */
    public float l1Norm() {
        float sum = 0f;
        for (float w : weights.values()) {
            sum += w;
        }
        return sum;
    }
}
