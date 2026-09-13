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
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.Locale;
import java.util.Optional;

/**
 * Factory for creating In-Process Native ONNX embedding providers using LangChain4j.
 *
 * <p>Supports in-memory zero-network vector generation across arbitrary dimensions
 * (384, 768, 1024, etc.) adapting LangChain4j {@link EmbeddingModel} implementations.</p>
 *
 * <p>No API key or endpoint is used. Text generation is not supported.</p>
 *
 * <h3>Configuration Properties</h3>
 * <ul>
 *   <li>{@code modelPath} (or {@code model-path}) — path to an ONNX model file (optional)</li>
 *   <li>{@code tokenizerPath} (or {@code vocabPath}) — path to the tokenizer file (optional)</li>
 *   <li>{@code executionProvider} — execution backend label reported by the provider
 *       (default: {@code CPU}); it is not passed to the ONNX model</li>
 * </ul>
 *
 * <p>If no model name is configured, {@code all-MiniLM-L6-v2} is used.</p>
 */
public class OnnxProviderFactory extends AbstractProviderFactory {

    /**
     * Creates a factory without a cache manager; embedding providers are returned without caching.
     */
    public OnnxProviderFactory() {
        super();
    }

    /**
     * Creates a factory with the given cache manager.
     *
     * @param cacheManager cache manager used to wrap created embedding providers with caching when
     *                     caching is enabled in the provider configuration; may be {@code null}
     */
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

    /**
     * Creates an in-process ONNX embedding provider.
     *
     * @param config provider configuration supplying the model name, optional dimensions, and the
     *               properties listed in the class documentation
     * @return an {@link OnnxEmbeddingProvider}; never empty
     * @throws IllegalStateException if no ONNX model can be loaded (see
     *                               {@link #createEmbeddingModel(String, String, String)})
     */
    @Override
    protected Optional<EmbeddingProvider> createRawEmbeddingProvider(ProviderConfig config) {
        String modelName = config.model() != null && !config.model().isBlank() ? config.model() : "all-MiniLM-L6-v2";
        int dimensions = resolveDimensions(modelName, config.dimensions());
        String modelPath = config.property("modelPath").orElse(config.property("model-path").orElse(""));
        String tokenizerPath = config.property("tokenizerPath").orElse(config.property("vocabPath").orElse(""));
        String executionProvider = config.property("executionProvider").orElse("CPU");

        EmbeddingModel delegate = createEmbeddingModel(modelName, modelPath, tokenizerPath);
        return Optional.of(new OnnxEmbeddingProvider(delegate, modelName, dimensions, executionProvider));
    }

    /**
     * Text generation is not supported by this factory.
     *
     * @param config provider configuration (ignored)
     * @return always {@link Optional#empty()}
     */
    @Override
    public Optional<LlmProvider> createGenerationProvider(ProviderConfig config) {
        return Optional.empty();
    }

    /**
     * Loads a LangChain4j ONNX embedding model.
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li>If {@code modelPath} is non-blank, builds {@code OnnxEmbeddingModel} from that path
     *       (and {@code tokenizerPath}, if non-blank). Failure here is not retried with other options.</li>
     *   <li>If the model name contains {@code bge-small}/{@code bge_small}, tries the pre-packaged
     *       {@code BgeSmallEnV15QuantizedEmbeddingModel} if it is on the classpath.</li>
     *   <li>If the model name contains {@code minilm}, or is blank, {@code default}, or {@code onnx},
     *       tries the pre-packaged {@code AllMiniLmL6V2QuantizedEmbeddingModel} if it is on the classpath.</li>
     * </ol>
     *
     * @param modelName     model name used to select a pre-packaged model
     * @param modelPath     path to an ONNX model file, or blank/{@code null} to use a pre-packaged model
     * @param tokenizerPath path to the tokenizer file, or blank/{@code null}
     * @return the loaded embedding model
     * @throws IllegalStateException if the model cannot be built from {@code modelPath}, or no
     *                               matching pre-packaged model is available
     */
    public static EmbeddingModel createEmbeddingModel(String modelName, String modelPath, String tokenizerPath) {
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
            } catch (Exception e) {
                throw new IllegalStateException("Failed to instantiate OnnxEmbeddingModel from path: " + modelPath, e);
            }
        }

        // 2. Try pre-packaged model classes if available on classpath
        String lower = modelName != null ? modelName.toLowerCase(Locale.ROOT) : "";
        if (lower.contains("bge-small") || lower.contains("bge_small")) {
            try {
                Class<?> clazz = Class.forName("dev.langchain4j.model.embedding.onnx.bgesmallenq.BgeSmallEnV15QuantizedEmbeddingModel");
                return (EmbeddingModel) clazz.getConstructor().newInstance();
            } catch (Exception ignored) {}
        }

        if (lower.contains("all-minilm") || lower.contains("minilm") || lower.isBlank() || lower.equals("default") || lower.equals("onnx")) {
            try {
                Class<?> clazz = Class.forName("dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel");
                return (EmbeddingModel) clazz.getConstructor().newInstance();
            } catch (Exception ignored) {}
        }

        throw new IllegalStateException("No ONNX embedding model found on classpath for model: " + modelName
                + ". Please specify 'modelPath' in configuration or add langchain4j-embeddings-all-minilm-l6-v2 dependency.");
    }

    /**
     * Resolves the embedding dimensions for a model.
     *
     * <p>Model names containing {@code minilm} or {@code bge-small}/{@code bge_small} always resolve
     * to 384. Otherwise a positive {@code configuredDims} is used; failing that, names containing
     * {@code large} or {@code 1024} resolve to 1024, names containing {@code base}, {@code 768},
     * {@code nomic}, or {@code mpnet} resolve to 768, and anything else resolves to 384.</p>
     *
     * @param model          model name (may be {@code null})
     * @param configuredDims configured dimensions, or 0 or less if not configured
     * @return the resolved dimensions
     */
    public static int resolveDimensions(String model, int configuredDims) {
        String lower = model != null ? model.toLowerCase(Locale.ROOT) : "";
        if (lower.contains("minilm") || lower.contains("bge-small") || lower.contains("bge_small")) {
            return 384;
        }
        if (configuredDims > 0) return configuredDims;
        if (lower.contains("large") || lower.contains("1024")) return 1024;
        if (lower.contains("base") || lower.contains("768") || lower.contains("nomic") || lower.contains("mpnet")) return 768;
        return 384;
    }
}
