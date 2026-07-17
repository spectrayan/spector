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

import java.util.Arrays;
import java.util.Objects;

/**
 * Result of generating multi-vector token-level embeddings (e.g. ColBERT).
 *
 * @param embeddings  2D matrix of floats representing individual term vectors
 * @param tokens      the tokenized terms
 * @param tokenCount  number of tokens produced
 * @param modelName   name of the model that generated the embeddings
 */
public record TokenEmbeddingResult(
        float[][] embeddings,
        String[] tokens,
        int tokenCount,
        String modelName
) {

    public TokenEmbeddingResult {
        Objects.requireNonNull(embeddings, "embeddings must not be null");
        Objects.requireNonNull(tokens, "tokens must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
        if (embeddings.length != tokenCount || tokens.length != tokenCount) {
            throw new IllegalArgumentException("embeddings size and tokens size must match tokenCount");
        }
    }

    public float[] tokenEmbedding(int tokenIndex) {
        return embeddings[tokenIndex];
    }

    public int dimensions() {
        return embeddings.length > 0 ? embeddings[0].length : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TokenEmbeddingResult that)) return false;
        return tokenCount == that.tokenCount &&
                Arrays.deepEquals(embeddings, that.embeddings) &&
                Arrays.equals(tokens, that.tokens) &&
                Objects.equals(modelName, that.modelName);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(tokenCount, modelName);
        result = 31 * result + Arrays.deepHashCode(embeddings);
        result = 31 * result + Arrays.hashCode(tokens);
        return result;
    }

    @Override
    public String toString() {
        return "TokenEmbeddingResult[tokens=" + tokenCount +
                ", dimensions=" + dimensions() +
                ", model='" + modelName + "']";
    }
}
