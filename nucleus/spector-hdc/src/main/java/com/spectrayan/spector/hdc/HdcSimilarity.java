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
package com.spectrayan.spector.hdc;

import java.util.Objects;

/**
 * High-level text similarity API using Hyperdimensional Computing.
 */
public final class HdcSimilarity {

    private final TextEncoder encoder;

    /**
     * Creates a new HdcSimilarity with specified dimensions and n-gram size.
     * @param dimensions the dimensionality of the hypervectors
     * @param ngramSize the size of n-grams to extract
     */
    public HdcSimilarity(int dimensions, int ngramSize) {
        this.encoder = new TextEncoder(dimensions, ngramSize);
    }

    /**
     * Creates a new HdcSimilarity with default dimensions (10,000) and n=3.
     */
    public HdcSimilarity() {
        this.encoder = new TextEncoder();
    }

    /**
     * Calculates the similarity between two texts.
     * @param textA the first text
     * @param textB the second text
     * @return a similarity score between 0.0 and 1.0
     */
    public double similarity(String textA, String textB) {
        Objects.requireNonNull(textA, "textA cannot be null");
        Objects.requireNonNull(textB, "textB cannot be null");
        
        Hypervector vectorA = encode(textA);
        Hypervector vectorB = encode(textB);
        
        return HammingDistance.similarity(vectorA, vectorB);
    }

    /**
     * Encodes a text into a Hypervector. Exposed for reuse.
     * @param text the input text
     * @return the encoded Hypervector
     */
    public Hypervector encode(String text) {
        return encoder.encode(text);
    }

    /**
     * Returns a new builder for HdcSimilarity.
     * @return a Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for HdcSimilarity.
     */
    public static final class Builder {
        private int dimensions = 10_000;
        private int ngramSize = 3;

        private Builder() {}

        public Builder dimensions(int dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public Builder ngramSize(int ngramSize) {
            this.ngramSize = ngramSize;
            return this;
        }

        public HdcSimilarity build() {
            return new HdcSimilarity(dimensions, ngramSize);
        }
    }
}
