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

import java.util.Arrays;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Immutable binary hypervector record.
 * Represents a high-dimensional binary vector backed by a long array.
 * 
 * @param words The backing long array storing the bits.
 * @param dimensions The total number of bits/dimensions.
 */
public record Hypervector(long[] words, int dimensions) {

    /**
     * Constructs a Hypervector.
     * @param words The array of longs representing bits.
     * @param dimensions The exact number of dimensions.
     */
    public Hypervector {
        Objects.requireNonNull(words, "words cannot be null");
        int expectedLength = (dimensions + 63) / 64;
        if (words.length != expectedLength) {
            throw new IllegalArgumentException("Invalid words array length for dimensions: " + dimensions);
        }
        // Defensive copy
        words = words.clone();
        
        // Zero out padding bits in the last word to ensure correct equals() and hashing
        if (dimensions > 0 && dimensions % 64 != 0) {
            long mask = (1L << (dimensions % 64)) - 1;
            words[words.length - 1] &= mask;
        }
    }

    /**
     * Generates a random binary hypervector.
     *
     * @param dimensions The number of dimensions.
     * @param seed The random seed.
     * @return A random hypervector.
     */
    public static Hypervector random(int dimensions, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        int expectedLength = (dimensions + 63) / 64;
        long[] newWords = new long[expectedLength];
        for (int i = 0; i < expectedLength; i++) {
            newWords[i] = random.nextLong();
        }
        return new Hypervector(newWords, dimensions);
    }

    /**
     * Creates a hypervector of all zeros.
     *
     * @param dimensions The number of dimensions.
     * @return A zero hypervector.
     */
    public static Hypervector zero(int dimensions) {
        int expectedLength = (dimensions + 63) / 64;
        return new Hypervector(new long[expectedLength], dimensions);
    }

    /**
     * Creates a hypervector from an existing bits array.
     *
     * @param words The backing words.
     * @param dimensions The number of dimensions.
     * @return A new hypervector.
     */
    public static Hypervector fromBits(long[] words, int dimensions) {
        return new Hypervector(words, dimensions);
    }

    /**
     * Returns the length of the backing words array.
     *
     * @return the number of long words.
     */
    public int wordCount() {
        return words.length;
    }

    /**
     * Returns the bit value at the specified index.
     *
     * @param index The bit index.
     * @return The bit value (0 or 1).
     */
    public int bitAt(int index) {
        if (index < 0 || index >= dimensions) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index);
        }
        int wordIndex = index / 64;
        int bitIndex = index % 64;
        return (int) ((words[wordIndex] >>> bitIndex) & 1L);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Hypervector that = (Hypervector) o;
        return dimensions == that.dimensions && Arrays.equals(words, that.words);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(dimensions);
        result = 31 * result + Arrays.hashCode(words);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Hypervector[dim=").append(dimensions).append(", words=[");
        int numToShow = Math.min(3, words.length);
        for (int i = 0; i < numToShow; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("0x%016x", words[i]));
        }
        if (words.length > numToShow) {
            sb.append(", ...");
        }
        sb.append("]]");
        return sb.toString();
    }
    
    @Override
    public long[] words() {
        return words.clone();
    }
}
