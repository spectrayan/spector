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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Full text to Hypervector encoding pipeline.
 */
public final class TextEncoder {

    private static final int DEFAULT_DIMENSIONS = 10_000;
    private static final int DEFAULT_NGRAM_SIZE = 3;

    private final int dimensions;
    private final int ngramSize;

    /**
     * Creates a new TextEncoder with specified dimensions and n-gram size.
     * @param dimensions the dimensionality of the hypervectors
     * @param ngramSize the size of n-grams to extract
     */
    public TextEncoder(int dimensions, int ngramSize) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive");
        }
        if (ngramSize <= 0) {
            throw new IllegalArgumentException("n-gram size must be positive");
        }
        this.dimensions = dimensions;
        this.ngramSize = ngramSize;
    }

    /**
     * Creates a new TextEncoder with specified dimensions and default n=3.
     * @param dimensions the dimensionality of the hypervectors
     */
    public TextEncoder(int dimensions) {
        this(dimensions, DEFAULT_NGRAM_SIZE);
    }

    /**
     * Creates a new TextEncoder with default dimensions (10,000) and n=3.
     */
    public TextEncoder() {
        this(DEFAULT_DIMENSIONS, DEFAULT_NGRAM_SIZE);
    }

    /**
     * Encodes a string into a Hypervector.
     * @param text the input text
     * @return the encoded Hypervector
     */
    public Hypervector encode(String text) {
        Objects.requireNonNull(text, "Text cannot be null");
        
        List<String> ngrams = NgramEncoder.encode(text, ngramSize);
        if (ngrams.isEmpty()) {
            return Hypervector.zero(dimensions);
        }
        
        List<Hypervector> permutedVectors = new ArrayList<>(ngrams.size());
        
        for (int i = 0; i < ngrams.size(); i++) {
            String ngram = ngrams.get(i);
            Hypervector seed = HypervectorFactory.seedFor(ngram, dimensions);
            Hypervector permuted = HdcAlgebra.permute(seed, i);
            permutedVectors.add(permuted);
        }
        
        return HdcAlgebra.bundle(permutedVectors);
    }
}
