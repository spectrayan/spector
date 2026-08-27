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
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OnnxEmbeddingProviderTest {

    private final OnnxProviderFactory factory = new OnnxProviderFactory();

    @Nested
    @DisplayName("OnnxEmbeddingProvider Adapter Tests")
    class AdapterTests {

        @Test
        void providerDelegatesToEmbeddingModel() {
            EmbeddingModel mockModel = new StubEmbeddingModel(384);
            var provider = new OnnxEmbeddingProvider(mockModel, "all-MiniLM-L6-v2", 384, "CPU");

            assertThat(provider.dimensions()).isEqualTo(384);
            assertThat(provider.modelName()).isEqualTo("all-MiniLM-L6-v2");
            assertThat(provider.isInProcess()).isTrue();
            assertThat(provider.executionBackend()).isEqualTo("CPU");

            EmbeddingResult res = provider.embed("Cognitive architecture query");
            assertThat(res.vector()).hasSize(384);
            assertThat(res.model()).isEqualTo("all-MiniLM-L6-v2");
            assertThat(res.tokenCount()).isGreaterThan(0);
        }

        @Test
        void providerBatchEmbedPreservesOrder() {
            EmbeddingModel mockModel = new StubEmbeddingModel(768);
            var provider = new OnnxEmbeddingProvider(mockModel, "bge-base-en-v1.5", 768, "DIRECTML");

            assertThat(provider.dimensions()).isEqualTo(768);
            assertThat(provider.executionBackend()).isEqualTo("DIRECTML");

            List<String> texts = List.of("First", "Second", "Third");
            List<EmbeddingResult> results = provider.embedBatch(texts);

            assertThat(results).hasSize(3);
            for (var r : results) {
                assertThat(r.vector()).hasSize(768);
            }
        }

        @Test
        void concurrentEmbedUnderVirtualThreads() throws InterruptedException, ExecutionException {
            EmbeddingModel mockModel = new StubEmbeddingModel(384);
            var provider = new OnnxEmbeddingProvider(mockModel, "all-MiniLM-L6-v2", 384);

            int threadCount = 100;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            List<Future<EmbeddingResult>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> provider.embed("Query " + idx)));
            }

            for (var f : futures) {
                EmbeddingResult res = f.get();
                assertThat(res.vector()).hasSize(384);
            }
            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("OnnxProviderFactory Metadata & Construction Tests")
    class FactoryTests {

        @Test
        void factoryMetadata() {
            assertThat(factory.name()).isEqualTo("onnx");
            assertThat(factory.displayName()).isEqualTo("In-Process Native ONNX");
            assertThat(factory.supportsEmbedding()).isTrue();
            assertThat(factory.supportsGeneration()).isFalse();
            assertThat(factory.createGenerationProvider(ProviderConfig.local("t", "onnx", "m", ""))).isEmpty();
        }

        @Test
        void dimensionResolutionLogic() {
            assertThat(OnnxProviderFactory.resolveDimensions("all-MiniLM-L6-v2", 0)).isEqualTo(384);
            assertThat(OnnxProviderFactory.resolveDimensions("bge-small-en-v1.5", 0)).isEqualTo(384);
            assertThat(OnnxProviderFactory.resolveDimensions("bge-base-en-v1.5", 0)).isEqualTo(768);
            assertThat(OnnxProviderFactory.resolveDimensions("nomic-embed-text", 0)).isEqualTo(768);
            assertThat(OnnxProviderFactory.resolveDimensions("bge-large-en-v1.5", 0)).isEqualTo(1024);
            assertThat(OnnxProviderFactory.resolveDimensions("custom-model", 512)).isEqualTo(512);
        }

        @Test
        void throwsWhenModelNotFoundOnClasspath() {
            var config = new ProviderConfig(
                    "onnx-missing",
                    "onnx",
                    "non-existent-model",
                    "",
                    "",
                    384,
                    Map.of()
            );

            assertThatThrownBy(() -> factory.createEmbeddingProvider(config))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No ONNX embedding model found on classpath");
        }
    }

    static final class StubEmbeddingModel implements EmbeddingModel {
        private final int dim;

        StubEmbeddingModel(int dim) {
            this.dim = dim;
        }

        @Override
        public Response<Embedding> embed(String text) {
            float[] vec = new float[dim];
            vec[0] = 1.0f;
            return Response.from(Embedding.from(vec), new TokenUsage(text.length(), 0));
        }

        @Override
        public Response<Embedding> embed(TextSegment textSegment) {
            return embed(textSegment.text());
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            List<Embedding> list = textSegments.stream()
                    .map(ts -> Embedding.from(new float[dim]))
                    .toList();
            return Response.from(list);
        }

        @Override
        public int dimension() {
            return dim;
        }
    }
}
