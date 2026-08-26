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
import com.spectrayan.spector.provider.embedding.EmbeddingConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.commons.ParseUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Factory for creating In-Process Native ONNX embedding providers.
 *
 * <p>Supports in-memory zero-network vector generation across arbitrary dimensions
 * (384, 768, 1024, etc.) and hardware execution providers (CPU, DIRECTML, CUDA).</p>
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
        int timeout = ParseUtils.parseInteger(config.property("timeout").orElse(null)).orElse(10);
        int maxConcurrent = ParseUtils.parseInteger(config.property("maxConcurrent").orElse(null)).orElse(0);
        int batchSize = ParseUtils.parseInteger(config.property("batchSize").orElse(null)).orElse(32);
        String modelPath = config.property("modelPath").orElse(config.property("model-path").orElse(""));

        Map<String, String> properties = new HashMap<>(config.properties());
        if (!modelPath.isBlank()) {
            properties.put("modelPath", modelPath);
        }

        var embeddingConfig = new EmbeddingConfig(
                config.model(),
                null,
                Duration.ofSeconds(timeout),
                batchSize,
                maxConcurrent,
                modelPath,
                properties
        );

        return Optional.of(new OnnxEmbeddingProvider(embeddingConfig, config.dimensions()));
    }

    @Override
    public Optional<LlmProvider> createGenerationProvider(ProviderConfig config) {
        return Optional.empty();
    }
}
