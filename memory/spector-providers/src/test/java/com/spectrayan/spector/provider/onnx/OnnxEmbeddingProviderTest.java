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
package com.spectrayan.spector.provider.onnx;

import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class OnnxEmbeddingProviderTest {

    private final OnnxProviderFactory factory = new OnnxProviderFactory();

    @Nested
    @DisplayName("Dimension & Model Resolution Tests")
    class DimensionTests {

        @Test
        void defaultMiniLmResolvesTo384Dimensions() {
            var config = new ProviderConfig("onnx-test", "onnx", "all-MiniLM-L6-v2", "", "", 0, Map.of());
            var provider = factory.createEmbeddingProvider(config).orElseThrow();
            assertThat(provider.dimensions()).isEqualTo(384);

            EmbeddingResult res = provider.embed("Cognitive memory architecture in Spector");
            assertThat(res.vector()).hasSize(384);
            assertThat(res.model()).isEqualTo("all-MiniLM-L6-v2");
        }

        @Test
        void bgeSmallResolvesTo384Dimensions() {
            var config = new ProviderConfig("onnx-test", "onnx", "bge-small-en-v1.5", "", "", 0, Map.of());
            var provider = factory.createEmbeddingProvider(config).orElseThrow();
            assertThat(provider.dimensions()).isEqualTo(384);
            EmbeddingResult res = provider.embed("Quick associative graph traversal");
            assertThat(res.vector()).hasSize(384);
        }

        @Test
        void bgeBaseResolvesTo768Dimensions() {
            var config = new ProviderConfig("onnx-test", "onnx", "bge-base-en-v1.5", "", "", 0, Map.of());
            var provider = factory.createEmbeddingProvider(config).orElseThrow();
            assertThat(provider.dimensions()).isEqualTo(768);
            EmbeddingResult res = provider.embed("Dense hippocampal memory trace");
            assertThat(res.vector()).hasSize(768);
        }

        @Test
        void bgeLargeResolvesTo1024Dimensions() {
            var config = new ProviderConfig("onnx-test", "onnx", "bge-large-en-v1.5", "", "", 0, Map.of());
            var provider = factory.createEmbeddingProvider(config).orElseThrow();
            assertThat(provider.dimensions()).isEqualTo(1024);
            EmbeddingResult res = provider.embed("Neocortical sleep consolidation");
            assertThat(res.vector()).hasSize(1024);
        }

        @Test
        void customExplicitDimensionsOverride() {
            var config = new ProviderConfig("onnx-test", "onnx", "custom-model", "", "", 512, Map.of());
            var provider = factory.createEmbeddingProvider(config).orElseThrow();
            assertThat(provider.dimensions()).isEqualTo(512);
            EmbeddingResult res = provider.embed("Testing explicit 512 dimensions");
            assertThat(res.vector()).hasSize(512);
        }
    }

    @Nested
    @DisplayName("Vector Normalization & Quality Tests")
    class NormalizationTests {

        @Test
        void vectorIsL2NormalizedToUnitLength() {
            var config = new ProviderConfig("onnx-test", "onnx", "all-MiniLM-L6-v2", "", "", 384, Map.of());
            var provider = factory.createEmbeddingProvider(config).orElseThrow();
            EmbeddingResult res = provider.embed("Unit length normalization check");

            float sumSq = 0.0f;
            for (float v : res.vector()) {
                sumSq += v * v;
            }
            float norm = (float) Math.sqrt(sumSq);
            assertThat(norm).isBetween(0.9999f, 1.0001f);
        }

        @Test
        void batchEmbeddingPreservesOrderAndDimensions() {
            var config = new ProviderConfig("onnx-test", "onnx", "all-MiniLM-L6-v2", "", "", 384, Map.of());
            var provider = factory.createEmbeddingProvider(config).orElseThrow();
            List<String> inputs = List.of("First query", "Second query", "Third query");
            List<EmbeddingResult> results = provider.embedBatch(inputs);

            assertThat(results).hasSize(3);
            for (int i = 0; i < 3; i++) {
                assertThat(results.get(i).vector()).hasSize(384);
            }
        }
    }

    @Nested
    @DisplayName("Virtual Thread Concurrency Tests")
    class ConcurrencyTests {

        @Test
        void concurrentEmbedUnderVirtualThreads() throws InterruptedException, ExecutionException {
            var config = new ProviderConfig("onnx-test", "onnx", "all-MiniLM-L6-v2", "", "", 384, Map.of());
            var provider = factory.createEmbeddingProvider(config).orElseThrow();
            int threadCount = 100;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            List<Future<EmbeddingResult>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> provider.embed("Concurrent query number " + idx)));
            }

            for (var f : futures) {
                EmbeddingResult res = f.get();
                assertThat(res.vector()).hasSize(384);
                float sumSq = 0.0f;
                for (float v : res.vector()) sumSq += v * v;
                assertThat((float) Math.sqrt(sumSq)).isBetween(0.9999f, 1.0001f);
            }
            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("OnnxProviderFactory Tests")
    class FactoryTests {

        @Test
        void factoryInstantiatesOnnxProvider() {
            assertThat(factory.name()).isEqualTo("onnx");
            assertThat(factory.displayName()).isEqualTo("In-Process Native ONNX");
            assertThat(factory.supportsEmbedding()).isTrue();
            assertThat(factory.supportsGeneration()).isFalse();

            var config = new ProviderConfig(
                    "onnx-test",
                    "onnx",
                    "all-MiniLM-L6-v2",
                    "",
                    "",
                    384,
                    Map.of("executionProvider", "CPU", "modelPath", "/models/test.onnx")
            );

            var providerOpt = factory.createEmbeddingProvider(config);
            assertThat(providerOpt).isPresent();
            assertThat(providerOpt.get().dimensions()).isEqualTo(384);
            assertThat(providerOpt.get().modelName()).isEqualTo("all-MiniLM-L6-v2");
        }
    }
}
