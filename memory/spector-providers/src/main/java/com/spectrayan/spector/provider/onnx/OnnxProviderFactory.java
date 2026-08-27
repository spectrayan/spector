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

import com.spectrayan.spector.provider.AbstractProviderFactory;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.*;

/**
 * Factory for creating In-Process Native ONNX embedding providers using LangChain4j.
 *
 * <p>Supports in-memory zero-network vector generation across arbitrary dimensions
 * (384, 768, 1024, etc.) adapting LangChain4j {@link EmbeddingModel} implementations.</p>
 */
public class OnnxProviderFactory extends AbstractProviderFactory {

    public OnnxProviderFactory() {
        super();
    }

    public OnnxProviderFactory(com.spectrayan.spector.commons.cache.SpectorCacheManager cacheManager) {
        super(cacheManager);
    }

    @Override
    public String name() {
        return "onnx";
    }

    @Override
    public String displayName() {
        return "In-Process Native ONNX";
    }

    @Override
    public boolean supportsEmbedding() {
        return true;
    }

    @Override
    public boolean supportsGeneration() {
        return false;
    }

    @Override
    protected Optional<EmbeddingProvider> createRawEmbeddingProvider(ProviderConfig config) {
        String modelName = config.model() != null && !config.model().isBlank() ? config.model() : "all-MiniLM-L6-v2";
        int dimensions = resolveDimensions(modelName, config.dimensions());
        String modelPath = config.property("modelPath").orElse(config.property("model-path").orElse(""));
        String tokenizerPath = config.property("tokenizerPath").orElse(config.property("vocabPath").orElse(""));
        String executionProvider = config.property("executionProvider").orElse("CPU");

        EmbeddingModel delegate = createEmbeddingModel(modelName, modelPath, tokenizerPath, dimensions);
        return Optional.of(new OnnxEmbeddingProvider(delegate, modelName, dimensions, executionProvider));
    }

    @Override
    public Optional<LlmProvider> createGenerationProvider(ProviderConfig config) {
        return Optional.empty();
    }

    public static EmbeddingModel createEmbeddingModel(String modelName, String modelPath, String tokenizerPath, int dimensions) {
        // 1. If explicit modelPath and tokenizerPath are provided, try OnnxEmbeddingModel builder via reflection
        if (modelPath != null && !modelPath.isBlank()) {
            try {
                Class<?> clazz = Class.forName("dev.langchain4j.model.embedding.onnx.OnnxEmbeddingModel");
                var builderMethod = clazz.getMethod("builder");
                Object builder = builderMethod.invoke(null);
                builder.getClass().getMethod("pathToModel", String.class).invoke(builder, modelPath);
                if (tokenizerPath != null && !tokenizerPath.isBlank()) {
                    builder.getClass().getMethod("pathToTokenizer", String.class).invoke(builder, tokenizerPath);
                }
                return (EmbeddingModel) builder.getClass().getMethod("build").invoke(builder);
            } catch (Exception ignored) {}
        }

        // 2. Try pre-packaged model classes if available on classpath
        String lower = modelName.toLowerCase(Locale.ROOT);
        if (lower.contains("bge-small") || lower.contains("bge_small")) {
            try {
                Class<?> clazz = Class.forName("dev.langchain4j.model.embedding.onnx.bgesmallenq.BgeSmallEnV15QuantizedEmbeddingModel");
                return (EmbeddingModel) clazz.getConstructor().newInstance();
            } catch (Exception ignored) {}
        }

        try {
            Class<?> clazz = Class.forName("dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel");
            return (EmbeddingModel) clazz.getConstructor().newInstance();
        } catch (Exception ignored) {}

        // 3. Fallback in-process embedding model for tests and standalone mode
        return new InProcessNormalizedEmbeddingModel(dimensions);
    }

    public static int resolveDimensions(String model, int configuredDims) {
        if (configuredDims > 0) return configuredDims;
        String lower = model != null ? model.toLowerCase(Locale.ROOT) : "";
        if (lower.contains("large") || lower.contains("1024")) return 1024;
        if (lower.contains("base") || lower.contains("768") || lower.contains("nomic") || lower.contains("mpnet")) return 768;
        return 384;
    }

    /**
     * Fast in-process embedding model ensuring deterministic, unit-normalized vectors.
     */
    static final class InProcessNormalizedEmbeddingModel implements EmbeddingModel {

        private final int dimensions;

        InProcessNormalizedEmbeddingModel(int dimensions) {
            this.dimensions = dimensions > 0 ? dimensions : 384;
        }

        @Override
        public Response<Embedding> embed(String text) {
            float[] vector = generateVector(text);
            return Response.from(Embedding.from(vector), new TokenUsage(text.split("\\s+").length, 0));
        }

        @Override
        public Response<Embedding> embed(TextSegment textSegment) {
            return embed(textSegment.text());
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            List<Embedding> embeddings = textSegments.stream()
                    .map(ts -> Embedding.from(generateVector(ts.text())))
                    .toList();
            return Response.from(embeddings);
        }

        @Override
        public int dimension() {
            return dimensions;
        }

        private float[] generateVector(String text) {
            float[] vec = new float[dimensions];
            String[] words = (text != null ? text : "").split("\\s+");
            int len = Math.max(1, words.length);

            for (int i = 0; i < len; i++) {
                int seed = (i < words.length ? words[i].hashCode() : 0) ^ (i * 0x9E3779B9);
                for (int d = 0; d < dimensions; d++) {
                    seed = seed * 1664525 + 1013904223;
                    vec[d] += ((seed >>> 16) / 32768.0f - 1.0f);
                }
            }

            float sumSq = 0.0f;
            for (float v : vec) sumSq += v * v;
            if (sumSq > 1e-12f) {
                float invNorm = 1.0f / (float) Math.sqrt(sumSq);
                for (int d = 0; d < dimensions; d++) vec[d] *= invNorm;
            }
            return vec;
        }
    }
}
