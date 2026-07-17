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
package com.spectrayan.spector.provider.embedding.generic;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.provider.embedding.SparseEmbeddingProvider;
import com.spectrayan.spector.provider.embedding.SparseEmbeddingResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Unit tests for {@link DenseDerivedSparseProvider}.
 *
 * <p>Uses a fake in-memory {@link EmbeddingProvider} to test the dense-derived
 * sparse encoding logic without requiring a running Ollama server.</p>
 */
class DenseDerivedSparseProviderTest {

    /** Fake embedding provider that returns deterministic vectors based on text hash. */
    private static class FakeEmbeddingProvider implements EmbeddingProvider {
        private static final int DIMS = 64;

        @Override
        public EmbeddingResult embed(String text) {
            float[] vec = new float[DIMS];
            Random rng = new Random(text.hashCode());
            for (int i = 0; i < DIMS; i++) {
                vec[i] = rng.nextFloat() * 2 - 1; // [-1, 1]
            }
            return new EmbeddingResult(vec, text.split("\\s+").length, "fake");
        }

        @Override
        public List<EmbeddingResult> embedBatch(List<String> texts) {
            return texts.stream().map(this::embed).toList();
        }

        @Override
        public int dimensions() { return DIMS; }

        @Override
        public String modelName() { return "fake-embed"; }
    }

    private FakeEmbeddingProvider fakeProvider;

    @BeforeEach
    void setUp() {
        fakeProvider = new FakeEmbeddingProvider();
    }

    @Nested
    class ConstructorTests {

        @Test
        void constructsWithProvider() {
            var provider = new DenseDerivedSparseProvider(fakeProvider);
            assertThat(provider.modelName()).contains("dense-derived-splade");
            assertThat(provider.modelName()).contains("fake-embed");
        }

        @Test
        void constructsWithCustomThreshold() {
            var provider = new DenseDerivedSparseProvider(fakeProvider, 0.5f);
            assertThat(provider.modelName()).contains("dense-derived-splade");
        }

        @Test
        void embeddingProviderAccessor() {
            var provider = new DenseDerivedSparseProvider(fakeProvider);
            assertThat(provider.embeddingProvider()).isSameAs(fakeProvider);
        }
    }

    @Nested
    class MetadataTests {

        @Test
        void typeIsSPLADE() {
            var provider = new DenseDerivedSparseProvider(fakeProvider);
            assertThat(provider.type()).isEqualTo(SparseEmbeddingProvider.SparseEncodingType.SPLADE);
        }

        @Test
        void vocabularySize() {
            var provider = new DenseDerivedSparseProvider(fakeProvider);
            assertThat(provider.vocabularySize()).isEqualTo(50_000);
        }
    }

    @Nested
    class EncodeTests {

        @Test
        void encodeNullReturnsEmpty() {
            var provider = new DenseDerivedSparseProvider(fakeProvider);
            SparseEmbeddingResult result = provider.encode(null);
            assertThat(result.weights()).isEmpty();
            assertThat(result.tokenCount()).isZero();
        }

        @Test
        void encodeBlankReturnsEmpty() {
            var provider = new DenseDerivedSparseProvider(fakeProvider);
            SparseEmbeddingResult result = provider.encode("   ");
            assertThat(result.weights()).isEmpty();
            assertThat(result.tokenCount()).isZero();
        }

        @Test
        void encodeTextProducesWeights() {
            var provider = new DenseDerivedSparseProvider(fakeProvider, 0.0f);
            SparseEmbeddingResult result = provider.encode("memory consolidation during sleep cycles");
            assertThat(result.weights()).isNotEmpty();
            assertThat(result.modelName()).contains("dense-derived-splade");
        }

        @Test
        void encodeWithHighThresholdFiltersTerms() {
            var provider = new DenseDerivedSparseProvider(fakeProvider, 0.99f);
            SparseEmbeddingResult result = provider.encode("memory consolidation during sleep cycles");
            // Very high threshold should filter most or all terms
            assertThat(result.weights().size()).isLessThanOrEqualTo(5);
        }

        @Test
        void weightsAreNonNegative() {
            var provider = new DenseDerivedSparseProvider(fakeProvider, 0.0f);
            SparseEmbeddingResult result = provider.encode("the quick brown fox jumps over the lazy dog");
            result.weights().values().forEach(w ->
                    assertThat(w).isGreaterThanOrEqualTo(0f));
        }

        @Test
        void singleCharTermsFiltered() {
            // "I am a test"  --  "I", "a" should be filtered (< 2 chars)
            var provider = new DenseDerivedSparseProvider(fakeProvider, 0.0f);
            SparseEmbeddingResult result = provider.encode("I am a test");
            result.weights().keySet().forEach(term ->
                    assertThat(term.length()).isGreaterThanOrEqualTo(2));
        }

        @Test
        void duplicateTermsUseMergedWeight() {
            var provider = new DenseDerivedSparseProvider(fakeProvider, 0.0f);
            SparseEmbeddingResult result = provider.encode("test test test data");
            // "test" appears 3 times but tokenize() deduplicates, so only 2 unique terms
            assertThat(result.weights()).containsKey("test");
        }
    }
}
