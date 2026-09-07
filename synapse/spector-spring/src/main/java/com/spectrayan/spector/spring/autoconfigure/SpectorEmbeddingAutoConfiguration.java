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
                                                   ObjectProvider<SpectorCacheManager> cacheManagerProvider) {
        OnnxProviderFactory onnxProviderFactory = new OnnxProviderFactory(cacheManagerProvider.getIfAvailable());
        var providerConfig = generateProviderConfig(props);
        String model = providerConfig.model();
        int dims = providerConfig.dimensions();
        if (model == null || model.isBlank() || "nomic-embed-text".equalsIgnoreCase(model) || "default".equalsIgnoreCase(model)) {
            model = "all-MiniLM-L6-v2";
            dims = 384;
        } else if (model.toLowerCase(java.util.Locale.ROOT).contains("minilm") || model.toLowerCase(java.util.Locale.ROOT).contains("bge-small")) {
            dims = 384;
        }
        providerConfig = new ProviderConfig(
                providerConfig.name(),
                providerConfig.type(),
                model,
                providerConfig.apiKey(),
                providerConfig.baseUrl(),
                dims,
                providerConfig.properties()
        );
        return onnxProviderFactory.createEmbeddingProvider(providerConfig)
                .orElseThrow(() -> new IllegalStateException("Failed to create ONNX embedding provider"));
    }

    @Bean(name = "defaultFallbackOnnxEmbeddingProvider")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.provider.embedding.fallback", name = "enabled", havingValue = "true", matchIfMissing = true)
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
}
