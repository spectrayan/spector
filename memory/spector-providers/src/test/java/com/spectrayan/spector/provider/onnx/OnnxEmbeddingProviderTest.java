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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import dev.langchain4j.model.embedding.onnx.PoolingMode;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
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
                    .isInstanceOfSatisfying(SpectorConfigException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.EMBEDDING_MODEL_INVALID));
        }

        @Test
        void ollamaModelNameIsNotSwappedForMiniLm() {
            var config = new ProviderConfig("onnx", "onnx", "nomic-embed-text", "", "", 768, Map.of());
            assertThatThrownBy(() -> factory.createEmbeddingProvider(config))
                    .isInstanceOfSatisfying(SpectorConfigException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.EMBEDDING_MODEL_INVALID))
                    .hasMessageContaining("nomic-embed-text");
        }

        @Test
        void blankModelUsesBundledMiniLm() {
            var provider = factory.createEmbeddingProvider(new ProviderConfig("onnx", "onnx", "", "", "", 0, Map.of()));
            assertThat(provider).isPresent();
            assertThat(provider.get().dimensions()).isEqualTo(384);
        }
    }

    @Nested
    @DisplayName("model-path loading (G1)")
    class ModelPathTests {

        private static final Path MODELS = Path.of("/workspace/state/spector-mcp/models");

        @TempDir
        Path tmp;

        private ProviderConfig pathConfig(int dims, Map<String, String> props) {
            return new ProviderConfig("onnx", "onnx", "", "", "", dims, props);
        }

        @Test
        void missingTokenizerFails() throws Exception {
            Path model = Files.writeString(tmp.resolve("model.onnx"), "x");
            assertThatThrownBy(() -> factory.createEmbeddingProvider(pathConfig(0, Map.of("modelPath", model.toString()))))
                    .isInstanceOfSatisfying(SpectorConfigException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.EMBEDDING_MODEL_INVALID))
                    .hasMessageContaining("vocab-path");
        }

        @Test
        void missingModelFileFails() {
            var props = Map.of("modelPath", tmp.resolve("absent.onnx").toString(),
                    "vocabPath", tmp.resolve("tokenizer.json").toString());
            assertThatThrownBy(() -> factory.createEmbeddingProvider(pathConfig(0, props)))
                    .isInstanceOfSatisfying(SpectorConfigException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.EMBEDDING_MODEL_INVALID));
        }

        @Test
        void invalidPoolingModeFails() {
            assertThatThrownBy(() -> OnnxProviderFactory.poolingMode("max"))
                    .isInstanceOfSatisfying(SpectorConfigException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.EMBEDDING_MODEL_INVALID));
            assertThat(OnnxProviderFactory.poolingMode("")).isEqualTo(PoolingMode.MEAN);
            assertThat(OnnxProviderFactory.poolingMode("CLS")).isEqualTo(PoolingMode.CLS);
        }

        @Test
        void loadsMiniLmFromDiskAndMatchesTheBundledModel() {
            Path dir = MODELS.resolve("all-MiniLM-L6-v2");
            assumeTrue(Files.isRegularFile(dir.resolve("model_quantized.onnx")), "local MiniLM not present");
            var props = Map.of("modelPath", dir.resolve("model_quantized.onnx").toString(),
                    "vocabPath", dir.resolve("tokenizer.json").toString());
            EmbeddingProvider fromDisk = factory.createEmbeddingProvider(pathConfig(384, props)).orElseThrow();
            assertThat(fromDisk.dimensions()).isEqualTo(384);
            float[] a = fromDisk.embed("Alex works at Acme in Denver.").vector();
            EmbeddingProvider bundled = factory.createEmbeddingProvider(pathConfig(0, Map.of())).orElseThrow();
            float[] b = bundled.embed("Alex works at Acme in Denver.").vector();
            assertThat(cosine(a, b)).isGreaterThan(0.99);
        }

        @Test
        void wrongConfiguredDimensionFails() {
            Path dir = MODELS.resolve("all-MiniLM-L6-v2");
            assumeTrue(Files.isRegularFile(dir.resolve("model_quantized.onnx")), "local MiniLM not present");
            var props = Map.of("modelPath", dir.resolve("model_quantized.onnx").toString(),
                    "vocabPath", dir.resolve("tokenizer.json").toString());
            assertThatThrownBy(() -> factory.createEmbeddingProvider(pathConfig(768, props)))
                    .isInstanceOfSatisfying(SpectorConfigException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.EMBEDDING_MODEL_INVALID))
                    .hasMessageContaining("384");
        }

        @Test
        void loadsBgeSmallWithClsPooling() {
            Path dir = MODELS.resolve("bge-small-en-v1.5");
            assumeTrue(Files.isRegularFile(dir.resolve("model_quantized.onnx")), "local bge-small not present");
            var props = Map.of("modelPath", dir.resolve("model_quantized.onnx").toString(),
                    "vocabPath", dir.resolve("tokenizer.json").toString(), "poolingMode", "cls");
            EmbeddingProvider bge = factory.createEmbeddingProvider(pathConfig(384, props)).orElseThrow();
            float[] cat = bge.embed("a cat sat on the mat").vector();
            float[] kitten = bge.embed("a kitten rested on the rug").vector();
            float[] tax = bge.embed("quarterly tax filing deadline").vector();
            assertThat(cosine(cat, kitten)).isGreaterThan(cosine(cat, tax));
        }

        private static double cosine(float[] a, float[] b) {
            double dot = 0, na = 0, nb = 0;
            for (int i = 0; i < a.length; i++) {
                dot += a[i] * b[i];
                na += a[i] * a[i];
                nb += b[i] * b[i];
            }
            return dot / Math.sqrt(na * nb);
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
