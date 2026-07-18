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

import java.util.Map;
import java.util.Objects;

/**
 * Result of generating sparse token-weight encodings (e.g. SPLADE) for a text.
 *
 * @param weights    map of token strings to their calculated weights
 * @param tokenCount number of tokens used (0 if unknown/untracked)
 * @param modelName  name of the model that generated the weights
 */
public record SparseEmbeddingResult(
        Map<String, Float> weights,
        int tokenCount,
        String modelName
) {

    public SparseEmbeddingResult {
        Objects.requireNonNull(weights, "weights must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
    }

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

    @Override
    public String toString() {
        return "SparseEmbeddingResult[uniqueTokens=" + weights.size() +
                ", tokens=" + tokenCount +
                ", model='" + modelName + "']";
    }
}
