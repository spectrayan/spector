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
import java.util.List;
import java.util.Objects;

/**
 * Result of generating multi-vector token-level embeddings (e.g. ColBERT) for a text.
 *
 * @param tokenEmbeddings 2D matrix of floats representing individual term vectors
 * @param tokenStrings    list of constituent token strings corresponding to the matrix rows
 * @param modelName       name of the model that generated the embeddings
 */
public record TokenEmbeddingResult(
        float[][] tokenEmbeddings,
        List<String> tokenStrings,
        String modelName
) {

    public TokenEmbeddingResult {
        Objects.requireNonNull(tokenEmbeddings, "tokenEmbeddings must not be null");
        Objects.requireNonNull(tokenStrings, "tokenStrings must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
        if (tokenEmbeddings.length != tokenStrings.size()) {
            throw new IllegalArgumentException("Matrix rows (" + tokenEmbeddings.length +
                    ") must match tokens list size (" + tokenStrings.size() + ")");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TokenEmbeddingResult that)) return false;
        return Arrays.deepEquals(tokenEmbeddings, that.tokenEmbeddings) &&
                Objects.equals(tokenStrings, that.tokenStrings) &&
                Objects.equals(modelName, that.modelName);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(tokenStrings, modelName);
        result = 31 * result + Arrays.deepHashCode(tokenEmbeddings);
        return result;
    }

    @Override
    public String toString() {
        return "TokenEmbeddingResult[tokens=" + tokenStrings.size() +
                ", dimensions=" + (tokenEmbeddings.length > 0 ? tokenEmbeddings[0].length : 0) +
                ", model='" + modelName + "']";
    }
}
