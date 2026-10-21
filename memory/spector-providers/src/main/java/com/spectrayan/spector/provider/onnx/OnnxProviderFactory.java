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
import com.spectrayan.spector.provider.AbstractProviderFactory;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.PoolingMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   <li>{@code tokenizerPath} (or {@code vocabPath}) — path to the model's {@code tokenizer.json};
 *       required with {@code modelPath}</li>
 *   <li>{@code poolingMode} (or {@code pooling-mode}) — {@code mean} (default) or {@code cls} (BGE models)</li>
 *   <li>{@code intraOpThreads} (or {@code intra-op-threads}) — ONNX Runtime threads per inference
 *       (default 1; see {@link #DEFAULT_INTRA_OP_THREADS})</li>
 *   <li>{@code executionProvider} — execution backend label reported by the provider
 *       (default: {@code CPU}); it is not passed to the ONNX model</li>
 * </ul>
 *
 * <p>If no model name is configured, {@code all-MiniLM-L6-v2} is used. A named model that is not bundled
 * fails with SPE-110-011; nothing is substituted. With {@code modelPath}, the output size is probed at
 * load and must match a configured {@code dimensions}.</p>
 */
public class OnnxProviderFactory extends AbstractProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(OnnxProviderFactory.class);

    /** Model used when no name is configured. */
    public static final String DEFAULT_MODEL = "all-MiniLM-L6-v2";

    /**
     * Default ONNX Runtime intra-op threads per embedding session. The runtime's own default (one per core)
     * returned wrong vectors for a few percent of calls with onnxruntime 1.20; one thread is exact.
     */
    public static final int DEFAULT_INTRA_OP_THREADS = 1;

    /** Output size of the bundled models. */
    static final int BUNDLED_DIMENSIONS = 384;

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
        String executionProvider = config.property("executionProvider").orElse("CPU");
        int threads = intraOpThreads(firstProperty(config, "intraOpThreads", "intra-op-threads"));
        String modelPath = firstProperty(config, "modelPath", "model-path");
        if (!modelPath.isBlank()) {
            String tokenizerPath = firstProperty(config, "tokenizerPath", "tokenizer-path", "vocabPath", "vocab-path");
            PoolingMode pooling = poolingMode(firstProperty(config, "poolingMode", "pooling-mode"));
            EmbeddingModel delegate = loadFromPath(modelPath, tokenizerPath, pooling, threads);
            int probed = probeDimensions(delegate, modelPath);
            if (config.dimensions() > 0 && config.dimensions() != probed) {
                throw new SpectorConfigException(ErrorCode.EMBEDDING_MODEL_INVALID, "spector.provider.embedding.dimensions",
                        "is " + config.dimensions() + " but " + Path.of(modelPath).getFileName() + " produces "
                                + probed + "-dimensional vectors");
            }
            String name = config.model() != null && !config.model().isBlank()
                    ? config.model() : Path.of(modelPath).getFileName().toString();
            log.info("[ONNX] Loaded embedding model {} from model-path ({} dims, pooling={})", name, probed,
                    pooling.name().toLowerCase(Locale.ROOT));
            return Optional.of(new OnnxEmbeddingProvider(delegate, name, probed, executionProvider));
        }
        String modelName = config.model() != null && !config.model().isBlank() ? config.model() : DEFAULT_MODEL;
        EmbeddingModel delegate = createEmbeddingModel(modelName, null, null, threads);
        int dimensions = BUNDLED_DIMENSIONS;
        if (config.dimensions() > 0 && config.dimensions() != dimensions) {
            log.info("[ONNX] Bundled model {} produces {}-dimensional vectors; configured dimensions={} do not apply",
                    modelName, dimensions, config.dimensions());
        }
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
     * Loads a bundled LangChain4j ONNX embedding model by name, or a model from {@code modelPath}.
     *
     * <p>Bundled models: {@code all-MiniLM-L6-v2} (also the model for a blank name) and
     * {@code bge-small-en-v1.5}, each only if its LangChain4j artifact is on the classpath. Any other name,
     * including an Ollama model name such as {@code nomic-embed-text}, fails: there is no substitute model.</p>
     *
     * @param modelName     model name used to select a bundled model
     * @param modelPath     path to an ONNX model file, or blank/{@code null} to use a bundled model
     * @param tokenizerPath path to the {@code tokenizer.json}; required with {@code modelPath}
     * @return the loaded embedding model
     * @throws SpectorConfigException (SPE-110-011) if the model cannot be loaded
     */
    public static EmbeddingModel createEmbeddingModel(String modelName, String modelPath, String tokenizerPath) {
        return createEmbeddingModel(modelName, modelPath, tokenizerPath, DEFAULT_INTRA_OP_THREADS);
    }

    /**
     * As {@link #createEmbeddingModel(String, String, String)}, with an explicit ONNX Runtime intra-op
     * thread count for the session.
     *
     * @param modelName      model name used to select a bundled model
     * @param modelPath      path to an ONNX model file, or blank/{@code null} to use a bundled model
     * @param tokenizerPath  path to the {@code tokenizer.json}; required with {@code modelPath}
     * @param intraOpThreads intra-op threads (at least 1)
     * @return the loaded embedding model
     * @throws SpectorConfigException (SPE-110-011) if the model cannot be loaded
     */
    public static EmbeddingModel createEmbeddingModel(String modelName, String modelPath, String tokenizerPath,
                                                      int intraOpThreads) {
        if (modelPath != null && !modelPath.isBlank()) {
            return loadFromPath(modelPath, tokenizerPath, PoolingMode.MEAN, intraOpThreads);
        }
        String lower = modelName != null ? modelName.trim().toLowerCase(Locale.ROOT) : "";
        String className;
        if (lower.isEmpty() || lower.equals("all-minilm-l6-v2") || lower.equals("all-minilm-l6-v2-q")
                || lower.equals("sentence-transformers/all-minilm-l6-v2")) {
            return ThreadLimitedOnnxEmbeddingModel.bundledMiniLm(intraOpThreads);
        } else if (lower.equals("bge-small-en-v1.5") || lower.equals("bge-small-en-v15") || lower.equals("bge-small-en")
                || lower.equals("baai/bge-small-en-v1.5")) {
            className = "dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel";
        } else {
            throw new SpectorConfigException(ErrorCode.EMBEDDING_MODEL_INVALID, "spector.provider.embedding.model",
                    "'" + modelName + "' is not a bundled in-process ONNX model (bundled: all-MiniLM-L6-v2, "
                            + "bge-small-en-v1.5); set model-path and vocab-path for any other ONNX model");
        }
        try {
            Class<?> clazz = Class.forName(className);
            return (EmbeddingModel) clazz.getConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new SpectorConfigException(ErrorCode.EMBEDDING_MODEL_INVALID, "spector.provider.embedding.model",
                    "'" + modelName + "' is not on the classpath (" + className + ")");
        }
    }

    private static EmbeddingModel loadFromPath(String modelPath, String tokenizerPath, PoolingMode pooling,
                                               int intraOpThreads) {
        requireFile(modelPath, "spector.provider.embedding.model-path");
        if (tokenizerPath == null || tokenizerPath.isBlank()) {
            throw new SpectorConfigException(ErrorCode.EMBEDDING_MODEL_INVALID, "spector.provider.embedding.vocab-path",
                    "is required when model-path is set (the model's tokenizer.json)");
        }
        requireFile(tokenizerPath, "spector.provider.embedding.vocab-path");
        try {
            return ThreadLimitedOnnxEmbeddingModel.fromFiles(Path.of(modelPath), Path.of(tokenizerPath), pooling,
                    intraOpThreads);
        } catch (SpectorConfigException e) {
            throw e;
        } catch (RuntimeException | LinkageError e) {
            throw new SpectorConfigException(ErrorCode.EMBEDDING_MODEL_INVALID, "spector.provider.embedding.model-path",
                    Path.of(modelPath).getFileName() + " could not be loaded (" + e.getClass().getSimpleName()
                            + (e.getMessage() != null ? ": " + e.getMessage() : "") + ")");
        }
    }

    private static int probeDimensions(EmbeddingModel model, String modelPath) {
        try {
            float[] v = model.embed("dimension probe").content().vector();
            if (v == null || v.length == 0) {
                throw new IllegalStateException("empty vector");
            }
            return v.length;
        } catch (RuntimeException e) {
            throw new SpectorConfigException(ErrorCode.EMBEDDING_MODEL_INVALID, "spector.provider.embedding.model-path",
                    Path.of(modelPath).getFileName() + " failed the probe embedding (" + e.getClass().getSimpleName() + ")");
        }
    }

    static int intraOpThreads(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_INTRA_OP_THREADS;
        }
        try {
            int n = Integer.parseInt(value.trim());
            if (n >= 1) {
                return n;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new SpectorConfigException(ErrorCode.EMBEDDING_MODEL_INVALID, "spector.provider.embedding.intra-op-threads",
                "'" + value + "' is not a whole number of at least 1");
    }

    static PoolingMode poolingMode(String value) {
        if (value == null || value.isBlank()) {
            return PoolingMode.MEAN;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "mean" -> PoolingMode.MEAN;
            case "cls" -> PoolingMode.CLS;
            default -> throw new SpectorConfigException(ErrorCode.EMBEDDING_MODEL_INVALID,
                    "spector.provider.embedding.pooling-mode", "'" + value + "' is not one of mean, cls");
        };
    }

    private static void requireFile(String path, String property) {
        Path p = Path.of(path);
        try {
            if (!Files.isRegularFile(p) || !Files.isReadable(p) || Files.size(p) == 0) {
                throw new SpectorConfigException(ErrorCode.EMBEDDING_MODEL_INVALID, property,
                        path + " does not exist, is not readable or is empty");
            }
        } catch (IOException e) {
            throw new SpectorConfigException(ErrorCode.EMBEDDING_MODEL_INVALID, property, path + " is not readable");
        }
    }

    private static String firstProperty(ProviderConfig config, String... keys) {
        for (String k : keys) {
            Optional<String> v = config.property(k);
            if (v.isPresent() && !v.get().isBlank()) {
                return v.get().trim();
            }
        }
        return "";
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
