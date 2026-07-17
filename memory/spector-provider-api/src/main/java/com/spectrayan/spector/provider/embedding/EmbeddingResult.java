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
 * Result of generating embeddings for a piece of text.
 *
 * @param vector    the raw dense float vector
 * @param tokens    number of tokens used (0 if unknown/untracked)
 * @param modelName name of the model that generated the vector
 */
public record EmbeddingResult(
        float[] vector,
        int tokens,
        String modelName
) {

    public EmbeddingResult {
        Objects.requireNonNull(vector, "vector must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmbeddingResult that)) return false;
        return tokens == that.tokens &&
                Arrays.equals(vector, that.vector) &&
                Objects.equals(modelName, that.modelName);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(tokens, modelName);
        result = 31 * result + Arrays.hashCode(vector);
        return result;
    }

    @Override
    public String toString() {
        return "EmbeddingResult[dimensions=" + vector.length +
                ", tokens=" + tokens +
                ", model='" + modelName + "']";
    }
}
