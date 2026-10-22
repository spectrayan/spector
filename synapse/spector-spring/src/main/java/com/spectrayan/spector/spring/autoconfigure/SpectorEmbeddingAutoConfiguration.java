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
package com.spectrayan.spector.spring.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import com.spectrayan.spector.commons.cache.SpectorCacheManager;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.anthropic.AnthropicProviderFactory;
import com.spectrayan.spector.provider.azure.AzureOpenAiProviderFactory;
import com.spectrayan.spector.provider.bedrock.BedrockProviderFactory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.provider.google.GoogleProviderFactory;
import com.spectrayan.spector.provider.mistral.MistralProviderFactory;
import com.spectrayan.spector.provider.ollama.OllamaProviderFactory;
import com.spectrayan.spector.provider.onnx.OnnxProviderFactory;
import com.spectrayan.spector.provider.openai.OpenAiProviderFactory;

import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * Auto-configuration dedicated to resolving and registering {@link EmbeddingProvider} beans.
 * <p>
 * Executed prior to {@link SpectorAutoConfiguration} so downstream cognitive memory
 * and vector store beans have access to an initialized embedder bean.
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(SpectorConfigProperties.class)
@ConditionalOnClass(EmbeddingProvider.class)
public class SpectorEmbeddingAutoConfiguration {

    private static final String TYPE_PROPERTY = "spector.provider.embedding.type";
    private static final String MODEL_PROPERTY = "spector.provider.embedding.model";

    private static final Logger log = LoggerFactory.getLogger(SpectorEmbeddingAutoConfiguration.class);

    @Bean(name = "openAiEmbeddingProvider")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.provider.embedding", name = "type", havingValue = "OpenAi", matchIfMissing = false)
    EmbeddingProvider spectorOpenAIEmbeddingProvider(SpectorConfigProperties props,
                                                     ObjectProvider<SpectorCacheManager> cacheManagerProvider) {
        OpenAiProviderFactory openAiProviderFactory = new OpenAiProviderFactory(cacheManagerProvider.getIfAvailable());
        return openAiProviderFactory.createEmbeddingProvider(generateProviderConfig(props))
                .orElseThrow(RuntimeException::new);
    }

    @Bean(name = "ollamaEmbeddingProvider")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.provider.embedding", name = "type", havingValue = "Ollama", matchIfMissing = false)
    EmbeddingProvider spectorOllamaEmbeddingProvider(SpectorConfigProperties props,
                                                     ObjectProvider<SpectorCacheManager> cacheManagerProvider) {
        OllamaProviderFactory factory = new OllamaProviderFactory(cacheManagerProvider.getIfAvailable());
        return factory.createEmbeddingProvider(generateProviderConfig(props))
                .orElseThrow(() -> new IllegalStateException("Failed to create Ollama embedding provider"));
    }

    @Bean(name = "anthropicEmbeddingProvider")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.provider.embedding", name = "type", havingValue = "Anthropic", matchIfMissing = false)
    EmbeddingProvider anthropicEmbeddingProvider(SpectorConfigProperties props,
                                                 ObjectProvider<SpectorCacheManager> cacheManagerProvider) {
        AnthropicProviderFactory anthropicProviderFactory = new AnthropicProviderFactory(cacheManagerProvider.getIfAvailable());
        return anthropicProviderFactory.createEmbeddingProvider(generateProviderConfig(props))
                .orElseThrow(RuntimeException::new);
    }

    @Bean(name = "azureOpenAiEmbeddingProvider")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.provider.embedding", name = "type", havingValue = "AzureOpenAi", matchIfMissing = false)
    EmbeddingProvider spectorAzureOpenAiEmbeddingProvider(SpectorConfigProperties props,
                                                          ObjectProvider<SpectorCacheManager> cacheManagerProvider) {
        AzureOpenAiProviderFactory azureOpenAiProviderFactory = new AzureOpenAiProviderFactory(cacheManagerProvider.getIfAvailable());
        return azureOpenAiProviderFactory.createEmbeddingProvider(generateProviderConfig(props))
                .orElseThrow(RuntimeException::new);
    }

    @Bean(name = "bedrockEmbeddingProvider")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.provider.embedding", name = "type", havingValue = "Bedrock", matchIfMissing = false)
    EmbeddingProvider spectorBedrockEmbeddingProvider(SpectorConfigProperties props,
                                                      ObjectProvider<SpectorCacheManager> cacheManagerProvider) {
        BedrockProviderFactory bedrockProviderFactory = new BedrockProviderFactory(cacheManagerProvider.getIfAvailable());
        return bedrockProviderFactory.createEmbeddingProvider(generateProviderConfig(props))
                .orElseThrow(RuntimeException::new);
    }

    @Bean(name = "googleEmbeddingProvider")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.provider.embedding", name = "type", havingValue = "Google", matchIfMissing = false)
    EmbeddingProvider spectorGoogleEmbeddingProvider(SpectorConfigProperties props,
                                                     ObjectProvider<SpectorCacheManager> cacheManagerProvider) {
        GoogleProviderFactory googleProviderFactory = new GoogleProviderFactory(cacheManagerProvider.getIfAvailable());
        return googleProviderFactory.createEmbeddingProvider(generateProviderConfig(props))
                .orElseThrow(RuntimeException::new);
    }

    @Bean(name = "mistralEmbeddingProvider")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.provider.embedding", name = "type", havingValue = "Mistral", matchIfMissing = false)
    EmbeddingProvider spectorMistralEmbeddingProvider(SpectorConfigProperties props,
                                                      ObjectProvider<SpectorCacheManager> cacheManagerProvider) {
        MistralProviderFactory mistralProviderFactory = new MistralProviderFactory(cacheManagerProvider.getIfAvailable());
        return mistralProviderFactory.createEmbeddingProvider(generateProviderConfig(props))
                .orElseThrow(RuntimeException::new);
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnBean(EmbeddingModel.class)
    EmbeddingProvider embeddingProvider(EmbeddingModel springEmbeddingModel) {
        class SpringAIEmbeddedProviderWrapper implements EmbeddingProvider {
            private final EmbeddingModel springAIEmbeddedModel;

            SpringAIEmbeddedProviderWrapper(EmbeddingModel springAIEmbeddedModel) {
                this.springAIEmbeddedModel = springAIEmbeddedModel;
            }

            @Override
            public EmbeddingResult embed(String text) {
                float[] vector = this.springAIEmbeddedModel.embed(text).content().vector();
                return EmbeddingResult.of(vector, this.springAIEmbeddedModel.modelName());
            }

            @Override
            public int dimensions() {
                return this.springAIEmbeddedModel.dimension();
            }

            @Override
            public String modelName() {
                return this.springAIEmbeddedModel.modelName();
            }
        }
        return new SpringAIEmbeddedProviderWrapper(springEmbeddingModel);
    }

    @Bean(name = "onnxEmbeddingProvider")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.provider.embedding", name = "type", havingValue = "Onnx", matchIfMissing = false)
    EmbeddingProvider spectorOnnxEmbeddingProvider(SpectorConfigProperties props,
                                                   ObjectProvider<SpectorCacheManager> cacheManagerProvider,
                                                   Environment environment) {
        OnnxProviderFactory onnxProviderFactory = new OnnxProviderFactory(cacheManagerProvider.getIfAvailable());
        // The factory resolves the model: blank means all-MiniLM-L6-v2; any other name must be a bundled
        // model or come with model-path. A name the user set is never rewritten (an Ollama name such as
        // nomic-embed-text used to become MiniLM silently). Only the property class's Ollama default, which
        // applies when no model is configured at all, is replaced by "no model named".
        var providerConfig = generateProviderConfig(props);
        if (!environment.containsProperty(MODEL_PROPERTY)) {
            providerConfig = new ProviderConfig(providerConfig.name(), providerConfig.type(), "",
                    providerConfig.apiKey(), providerConfig.baseUrl(), providerConfig.dimensions(),
                    providerConfig.properties());
        }
        return onnxProviderFactory.createEmbeddingProvider(providerConfig)
                .orElseThrow(() -> new IllegalStateException("Failed to create ONNX embedding provider"));
    }

    @Bean(name = "defaultFallbackOnnxEmbeddingProvider")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.provider.embedding.fallback", name = "enabled", havingValue = "true", matchIfMissing = false)
    EmbeddingProvider defaultFallbackOnnxEmbeddingProvider(SpectorConfigProperties props,
                                                           ObjectProvider<SpectorCacheManager> cacheManagerProvider) {
        log.info("[Spector] No external EmbeddingProvider configured. Auto-configuring in-process fallback ONNX embedder (all-MiniLM-L6-v2-q, 384 dims).");
        OnnxProviderFactory onnxProviderFactory = new OnnxProviderFactory(cacheManagerProvider.getIfAvailable());
        var embedding = props.getProvider().getEmbedding();
        java.util.Map<String, String> properties = new java.util.HashMap<>(embedding.getProperties());
        properties.put("cache.enabled", String.valueOf(embedding.isCacheEnabled()));
        properties.put("cache.max-size", String.valueOf(embedding.getCacheMaxSize()));
        if (embedding.getCacheTtl() != null) {
            properties.put("cache.ttl-seconds", String.valueOf(embedding.getCacheTtl().toSeconds()));
        }
        if (embedding.getCacheStatsLogInterval() != null) {
            properties.put("cache.stats-log-interval-seconds", String.valueOf(embedding.getCacheStatsLogInterval().toSeconds()));
        }
        ProviderConfig fallbackConfig = new ProviderConfig(
                "onnx",
                "onnx",
                "all-MiniLM-L6-v2",
                null,
                null,
                384,
                properties
        );
        return onnxProviderFactory.createEmbeddingProvider(fallbackConfig)
                .orElseThrow(() -> new IllegalStateException("Failed to create fallback ONNX embedding provider"));
    }

    /**
     * Last resort when no embedding provider bean matched: fail startup instead of running without
     * embeddings or with a substitute model. The in-process MiniLM fallback is opt-in
     * ({@code spector.provider.embedding.fallback.enabled=true}).
     */
    @Bean(name = "unmatchedEmbeddingProvider")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.provider.embedding.fallback", name = "enabled", havingValue = "false", matchIfMissing = true)
    EmbeddingProvider unmatchedEmbeddingProvider(Environment environment) {
        String type = environment.getProperty(TYPE_PROPERTY);
        String what = type == null || type.isBlank() ? "no value is set" : "'" + type + "' matched no embedding provider";
        log.error("[Spector] spector.provider.embedding.type: {} ({})", what,
                com.spectrayan.spector.commons.error.ErrorCode.CONFIG_VALUE_INVALID.id());
        throw new com.spectrayan.spector.commons.error.SpectorConfigException(
                com.spectrayan.spector.commons.error.ErrorCode.CONFIG_VALUE_INVALID, TYPE_PROPERTY,
                what + " (supported: onnx, ollama, openai, azureopenai, anthropic, google, mistral, bedrock); "
                        + "set fallback.enabled=true to opt in to the in-process all-MiniLM-L6-v2 model");
    }

    ProviderConfig generateProviderConfig(SpectorConfigProperties props) {
        var embedding = props.getProvider().getEmbedding();
        java.util.Map<String, String> properties = new java.util.HashMap<>(embedding.getProperties());
        properties.put("cache.enabled", String.valueOf(embedding.isCacheEnabled()));
        properties.put("cache.max-size", String.valueOf(embedding.getCacheMaxSize()));
        if (embedding.getCacheTtl() != null) {
            properties.put("cache.ttl-seconds", String.valueOf(embedding.getCacheTtl().toSeconds()));
        }
        if (embedding.getCacheStatsLogInterval() != null) {
            properties.put("cache.stats-log-interval-seconds", String.valueOf(embedding.getCacheStatsLogInterval().toSeconds()));
        }

        // Forward the typed fields the factories actually read. These were declared on EmbeddingProperties,
        // bound from configuration, and then never reached a factory through this path — so setting
        // spector.provider.embedding.model-path, execution-provider, timeout, batch-size or max-concurrent
        // did nothing at all. Property-map keys match what the factories look up:
        // OnnxProviderFactory reads modelPath/model-path, tokenizerPath/vocabPath and executionProvider;
        // OllamaProviderFactory reads timeout, batchSize and maxConcurrent.
        putIfPresent(properties, "modelPath", embedding.getModelPath());
        putIfPresent(properties, "vocabPath", embedding.getVocabPath());
        putIfPresent(properties, "poolingMode", embedding.getPoolingMode());
        if (embedding.getIntraOpThreads() > 0) {
            properties.put("intraOpThreads", Integer.toString(embedding.getIntraOpThreads()));
        }
        putIfPresent(properties, "executionProvider", embedding.getExecutionProvider());
        if (embedding.getTimeout() != null && !embedding.getTimeout().isZero()) {
            properties.putIfAbsent("timeout", String.valueOf(embedding.getTimeout().toSeconds()));
        }
        if (embedding.getBatchSize() > 0) {
            properties.putIfAbsent("batchSize", String.valueOf(embedding.getBatchSize()));
        }
        if (embedding.getMaxConcurrent() > 0) {
            properties.putIfAbsent("maxConcurrent", String.valueOf(embedding.getMaxConcurrent()));
        }

        return new ProviderConfig(
                embedding.getType(),
                embedding.getType(),
                embedding.getModel(),
                embedding.getApiKey(),
                embedding.getBaseUrl(),
                embedding.getDimensions(),
                properties
        );
    }

    /**
     * Copies a configured value into the properties map, leaving an explicit entry already present in
     * {@code spector.provider.embedding.properties} untouched.
     *
     * <p>{@code putIfAbsent} rather than {@code put}: the free-form properties map is the lower-level escape
     * hatch, so an operator who set a key there directly has been more specific than one who set the typed
     * field, and must win.</p>
     */
    private static void putIfPresent(java.util.Map<String, String> properties, String key, String value) {
        if (value != null && !value.isBlank()) {
            properties.putIfAbsent(key, value);
        }
    }
}
