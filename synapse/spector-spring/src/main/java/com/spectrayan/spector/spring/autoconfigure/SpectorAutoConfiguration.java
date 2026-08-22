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

import com.spectrayan.spector.client.SpectorClient;
import com.spectrayan.spector.provider.DefaultProviderRegistry;
import com.spectrayan.spector.provider.DelegatingLlmProvider;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.ProviderRegistry;
import com.spectrayan.spector.provider.anthropic.AnthropicProviderFactory;
import com.spectrayan.spector.provider.azure.AzureOpenAiProviderFactory;
import com.spectrayan.spector.provider.bedrock.BedrockProviderFactory;
import com.spectrayan.spector.provider.embedding.EmbeddingConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.provider.embedding.generic.DenseDerivedSparseProvider;
import com.spectrayan.spector.provider.embedding.generic.DenseDerivedTokenProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.id.TsidGenerator;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.SalienceProfileProvider;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.metrics.MeteredSpectorMemory;
import com.spectrayan.spector.metrics.SpectorMetrics;

import com.spectrayan.spector.provider.google.GoogleProviderFactory;
import com.spectrayan.spector.provider.langchain4j.LangChain4jHelper;
import com.spectrayan.spector.provider.mistral.MistralProviderFactory;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;
import com.spectrayan.spector.provider.ollama.OllamaProviderFactory;
import com.spectrayan.spector.provider.openai.OpenAiProviderFactory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.spector.SpectorVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import com.spectrayan.spector.commons.cache.SpectorCacheErrorHandler;
import com.spectrayan.spector.commons.cache.SpectorCacheKeyGenerator;
import com.spectrayan.spector.commons.cache.SpectorCacheManager;
import com.spectrayan.spector.memory.DataEncryptor;
import com.spectrayan.spector.spring.cache.EncryptingJsonCacheSerializer;
import com.spectrayan.spector.spring.cache.SpringSpectorCacheManagerAdapter;
import org.springframework.cache.CacheManager;

import java.lang.reflect.Method;
import java.nio.file.Path;
import com.spectrayan.spector.commons.error.SpectorInternalException;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.mcp.tools.SpectorToolRegistry;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Spring Boot auto-configuration for embedded Spector Cognitive Memory.
 *
 * <p>Automatically creates and wires the {@link SpectorMemory} bean when Spector is on the classpath.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(SpectorConfigProperties.class)
@ConditionalOnClass(SpectorMemory.class)
public class SpectorAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SpectorAutoConfiguration.class);

    /**
     * Creates the {@link SpectorCacheManager} bean backed by Spring's {@link CacheManager}
     * when a Spring CacheManager is present.
     */
    @Bean
    @ConditionalOnBean(CacheManager.class)
    @ConditionalOnMissingBean(SpectorCacheManager.class)
    SpectorCacheManager spectorCacheManager(
            CacheManager springCacheManager,
            ObjectProvider<com.fasterxml.jackson.databind.ObjectMapper> mapperProvider,
            ObjectProvider<DataEncryptor> encryptorProvider) {

        DataEncryptor encryptor = encryptorProvider.getIfAvailable(() -> DataEncryptor.NOOP);
        var mapper = mapperProvider.getIfAvailable(com.fasterxml.jackson.databind.ObjectMapper::new);

        var builder = SpringSpectorCacheManagerAdapter.builder(springCacheManager)
                .keyGenerator(SpectorCacheKeyGenerator.forNamespace("default"))
                .errorHandler(SpectorCacheErrorHandler.LOGGING);

        if (encryptor != null && encryptor.isEnabled()) {
            builder.serializer(new EncryptingJsonCacheSerializer(mapper, encryptor));
        }

        log.info("SpectorCacheManager auto-configured with Spring CacheManager delegate (encryption={})",
                encryptor != null && encryptor.isEnabled());
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean(SpectorCacheManager.class)
    SpectorCacheManager defaultSpectorCacheManager() {
        return com.spectrayan.spector.commons.cache.TtlConcurrentMapCacheManager.defaultManager();
    }

    /**
     * Creates the {@link SpectorMemory} bean when memory is enabled (default: true).
     */
    @Bean
    @ConditionalOnBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.memory", name = "enabled", havingValue = "true", matchIfMissing = true)
    SpectorMemory spectorMemory(SpectorConfigProperties props,
                                 ObjectProvider<EmbeddingProvider> embedderProvider,
                                 ObjectProvider<LlmProvider> textGenProvider,
                                 ObjectProvider<MeterRegistry> registryProvider,
                                 ObjectProvider<SalienceProfileProvider> salienceProvider,
                                 ObjectProvider<SpectorCacheManager> cacheManagerProvider,
                                 ObjectProvider<io.micrometer.observation.ObservationRegistry> observationRegistryProvider,
                                 ObjectProvider<com.spectrayan.spector.config.ObservabilityConfig> observabilityConfigProvider) {

        var memoryProps = props.getMemory();
        EmbeddingProvider embedder = embedderProvider.getIfAvailable();

        if (embedder == null) {
            throw new SpectorInternalException(ErrorCode.ARGUMENT_NULL, "EmbeddingProvider bean (configure provider or set spector.memory.enabled=false)");
        }

        var builder = DefaultSpectorMemory.builder()
                .dimensions(memoryProps.getDimensions())
                .embeddingProvider(embedder)
                .persistenceMode(MemoryPersistenceMode.valueOf(memoryProps.getPersistenceMode().name()))
                .semanticCapacity(memoryProps.getCapacity())
                .hebbianGraphCapacity(memoryProps.getCapacity())
                .temporalChainCapacity(memoryProps.getCapacity())
                .entityGraphCapacity(memoryProps.getCapacity())
                .embedBatchSize(props.getProvider().getEmbedding().getBatchSize())
                .bundleMode(memoryProps.isBundleMode())
                .coactivationPairCapacity(memoryProps.coactivationPairCapacity())
                .coactivationEdgeCapacity(memoryProps.coactivationEdgeCapacity())
                .temporalFactsInitialSize(memoryProps.temporalFactsInitialSize())
                .indexMidxCapacity(memoryProps.indexMidxCapacity())
                .indexIdplSize(memoryProps.indexIdplSize())
                .typeRegistryCapacity(memoryProps.typeRegistryCapacity())
                .typeRegistrySize(memoryProps.typeRegistrySize())
                .insulaSize(memoryProps.insulaSize());

        if (memoryProps.getPersistencePath() != null) {
            builder.persistence(Path.of(memoryProps.getPersistencePath()));
        }

        if (memoryProps.getAisme() != null) {
            builder.aismeConfig(com.spectrayan.spector.memory.aisme.config.AismeConfig.fromProperties(memoryProps.getAisme()));
        }

        //  Entity extraction (LLM if LlmProvider is present)
        LlmProvider textGen = textGenProvider.getIfAvailable();
        if (textGen != null) {
            builder.entityExtractionMode(EntityExtractionMode.LLM);
            builder.LlmProvider(textGen);
        } else {
            builder.entityExtractionMode(EntityExtractionMode.NONE);
        }

        //  Salience profile provider (user-driven importance modulation)
        SalienceProfileProvider salience = salienceProvider.getIfAvailable();
        if (salience != null) {
            builder.salienceProfileProvider(salience);
            log.info("SpectorMemory: user salience profile provider wired");
        }

        //  SPLADE + ColBERT providers (auto-created from embedding provider)
        if (memoryProps.isSpladeEnabled()) {
            builder.SparseEmbeddingProvider(
                    new DenseDerivedSparseProvider(embedder));
        }
        if (memoryProps.isColbertEnabled()) {
            builder.tokenEmbeddingProvider(
                    new DenseDerivedTokenProvider(embedder));
        }

        SpectorCacheManager cacheManager = cacheManagerProvider.getIfAvailable();
        if (cacheManager != null) {
            builder.cacheManager(cacheManager);
        }

        io.micrometer.observation.ObservationRegistry obsRegistry = observationRegistryProvider.getIfAvailable();
        com.spectrayan.spector.config.ObservabilityConfig obsConfig = observabilityConfigProvider.getIfAvailable();

        if (obsRegistry != null && obsConfig != null) {
            builder.observationHook(new com.spectrayan.spector.metrics.observation.MicrometerMemoryObservationHook(obsRegistry, obsConfig));
        }

        SpectorMemory raw = builder.build();
        log.info("SpectorMemory auto-configured: dims={}, persistence={}, path={}, entity={}, SPLADE={}, ColBERT={}, salience={}",
                memoryProps.getDimensions(), memoryProps.getPersistenceMode(),
                memoryProps.getPersistencePath(), textGen != null ? "enabled" : "disabled",
                memoryProps.isSpladeEnabled(), memoryProps.isColbertEnabled(),
                salience != null);

        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null && props.getMetrics().isEnabled()) {
            SpectorMetrics.init(registry);
            log.info("Spector metrics enabled via Spring MeterRegistry");
            new com.spectrayan.spector.metrics.observation.SpectorMemoryGauges(raw).bindTo(registry);
        }

        if (obsRegistry != null && obsConfig != null) {
            return new com.spectrayan.spector.metrics.ObservedSpectorMemory(raw, obsRegistry, obsConfig);
        }

        return raw;
    }

    @Configuration
    static class SpringHttpClientAutoConfiguration {
        SpringHttpClientAutoConfiguration(ApplicationContext context) {
            // 1. Try to find and register RestClient.Builder
            try {
                Class<?> restClientBuilderClass = Class.forName("org.springframework.web.client.RestClient$Builder");
                Object provider = context.getBeanProvider(restClientBuilderClass);
                Method getIfAvailable = provider.getClass().getMethod("getIfAvailable");
                Object builder = getIfAvailable.invoke(provider);
                if (builder != null) {
                    log.info("[Spector] Auto-registering Spring RestClient.Builder in LangChain4jHelper");
                    LangChain4jHelper.setSpringRestClientBuilder(builder);
                }
            } catch (ClassNotFoundException e) {
                // RestClient is not on the classpath
            } catch (Exception e) {
                log.warn("[Spector] Failed to auto-register RestClient.Builder: {}", e.getMessage());
            }

            // 2. Try to find and register WebClient.Builder
            try {
                Class<?> webClientBuilderClass = Class.forName("org.springframework.web.reactive.function.client.WebClient$Builder");
                Object provider = context.getBeanProvider(webClientBuilderClass);
                Method getIfAvailable = provider.getClass().getMethod("getIfAvailable");
                Object builder = getIfAvailable.invoke(provider);
                if (builder != null) {
                    log.info("[Spector] Auto-registering Spring WebClient.Builder in LangChain4jHelper");
                    LangChain4jHelper.setSpringWebClientBuilder(builder);
                }
            } catch (ClassNotFoundException e) {
                // WebClient is not on the classpath
            } catch (Exception e) {
                log.warn("[Spector] Failed to auto-register WebClient.Builder: {}", e.getMessage());
            }
        }
    }

    /**
     * Registers core MCP memory tools automatically when memory is available.
     */
    @Bean
    @ConditionalOnBean(SpectorMemory.class)
    @ConditionalOnMissingBean(name = "coreMemoryTools")
    public List<McpToolHandler> coreMemoryTools(SpectorMemory memory) {
        return SpectorToolRegistry.handlers("1.0.0", memory);
    }
    /**
     * Autoconfigures a dedicated {@link OpenAiProviderFactory} when explicit
     * Spector embedding properties are provided.
     * <p>
     * This bean takes precedence if 'spector.embedding.provider-name is set to 'Ollama'.
     *
     * @param props bound {@link SpectorConfigProperties} containing Spector configuration
     * @return an instance of {@link EmbeddingProvider} initialized with Spector properties
     */
    @Bean(name = "openAiEmbeddingProvider")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.provider.embedding", name = "type", havingValue = "OpenAi", matchIfMissing = false)
    EmbeddingProvider spectorOpenAIEmbeddingProvider(SpectorConfigProperties props,
                                                     ObjectProvider<com.spectrayan.spector.commons.cache.SpectorCacheManager> cacheManagerProvider) {
        OpenAiProviderFactory openAiProviderFactory = new OpenAiProviderFactory(cacheManagerProvider.getIfAvailable());
        return openAiProviderFactory.createEmbeddingProvider(generateProviderConfig(props))
                .orElseThrow(RuntimeException::new);
    }

    @Bean
    @ConditionalOnMissingBean(ProviderRegistry.class)
    public ProviderRegistry providerRegistry() {
        return new DefaultProviderRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(TsidGenerator.class)
    public TsidGenerator tsidGenerator() {
        return new TsidGenerator();
    }

    @Bean
    @ConditionalOnMissingBean(LlmProvider.class)
    public LlmProvider llmProvider(ProviderRegistry providerRegistry, SpectorConfigProperties props) {
        try {
            var genProps = props.getProvider().getGeneration();
            String type = genProps.getType();
            if (type == null || type.isBlank() || "ollama".equalsIgnoreCase(type)) {
                Duration timeout = Duration.ofSeconds(300);
                if (genProps.getProperties() != null && genProps.getProperties().containsKey("timeout")) {
                    try {
                        timeout = Duration.ofSeconds(Long.parseLong(genProps.getProperties().get("timeout")));
                    } catch (NumberFormatException ignored) {}
                }
                var llm = new com.spectrayan.spector.provider.ollama.OllamaLlmProvider(
                        genProps.getModel(), genProps.getBaseUrl(), timeout);
                providerRegistry.registerGeneration("ollama", llm);
                log.info("[Spector] Registered default Ollama text generation provider: model={}, baseUrl={}",
                        genProps.getModel(), genProps.getBaseUrl());
            }
        } catch (Exception e) {
            log.warn("[Spector] Failed to register default text generation provider: {}", e.getMessage());
        }
        return new DelegatingLlmProvider(providerRegistry);
    }

    @Bean(name = "ollamaEmbeddingProvider")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.provider.embedding", name = "type", havingValue = "Ollama", matchIfMissing = false)
    EmbeddingProvider spectorOllamaEmbeddingProvider(SpectorConfigProperties props,
                                                     ObjectProvider<com.spectrayan.spector.commons.cache.SpectorCacheManager> cacheManagerProvider) {
        OllamaProviderFactory factory = new OllamaProviderFactory(cacheManagerProvider.getIfAvailable());
        return factory.createEmbeddingProvider(generateProviderConfig(props))
                .orElseThrow(() -> new IllegalStateException("Failed to create Ollama embedding provider"));
    }

    @Bean(name = "anthropicEmbeddingProvider")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.provider.embedding", name = "type", havingValue = "Anthropic", matchIfMissing = false)
    EmbeddingProvider antrhopicEmbeddingProvider(SpectorConfigProperties props,
                                                 ObjectProvider<com.spectrayan.spector.commons.cache.SpectorCacheManager> cacheManagerProvider) {
        AnthropicProviderFactory anthropicProviderFactory = new AnthropicProviderFactory(cacheManagerProvider.getIfAvailable());
        return anthropicProviderFactory.createEmbeddingProvider(generateProviderConfig(props))
                .orElseThrow(RuntimeException::new);
    }

    @Bean(name = "azureOpenAiEmbeddingProvider")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.provider.embedding", name = "type", havingValue = "AzureOpenAi", matchIfMissing = false)
    EmbeddingProvider spectorAzureOpenAiEmbeddingProvider(SpectorConfigProperties props,
                                                          ObjectProvider<com.spectrayan.spector.commons.cache.SpectorCacheManager> cacheManagerProvider) {
        AzureOpenAiProviderFactory azureOpenAiProviderFactory = new AzureOpenAiProviderFactory(cacheManagerProvider.getIfAvailable());
        return azureOpenAiProviderFactory.createEmbeddingProvider(generateProviderConfig(props))
                .orElseThrow(RuntimeException::new);
    }

    @Bean(name = "bedrockEmbeddingProvider")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.provider.embedding", name = "type", havingValue = "Bedrock", matchIfMissing = false)
    EmbeddingProvider spectorBedrockEmbeddingProvider(SpectorConfigProperties props,
                                                      ObjectProvider<com.spectrayan.spector.commons.cache.SpectorCacheManager> cacheManagerProvider) {
        BedrockProviderFactory bedrockProviderFactory = new BedrockProviderFactory(cacheManagerProvider.getIfAvailable());
        return bedrockProviderFactory.createEmbeddingProvider(generateProviderConfig(props))
                .orElseThrow(RuntimeException::new);
    }

    @Bean(name = "googleEmbeddingProvider")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.provider.embedding", name = "type", havingValue = "Google", matchIfMissing = false)
    EmbeddingProvider spectorGoogleEmbeddingProvider(SpectorConfigProperties props,
                                                     ObjectProvider<com.spectrayan.spector.commons.cache.SpectorCacheManager> cacheManagerProvider) {
        GoogleProviderFactory googleProviderFactory = new GoogleProviderFactory(cacheManagerProvider.getIfAvailable());
        return googleProviderFactory.createEmbeddingProvider(generateProviderConfig(props))
                .orElseThrow(RuntimeException::new);
    }

    @Bean(name = "mistralEmbeddingProvider")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnProperty(prefix = "spector.provider.embedding", name = "type", havingValue = "Mistral", matchIfMissing = false)
    EmbeddingProvider spectorMistralEmbeddingProvider(SpectorConfigProperties props,
                                                      ObjectProvider<com.spectrayan.spector.commons.cache.SpectorCacheManager> cacheManagerProvider) {
        MistralProviderFactory mistralProviderFactory = new MistralProviderFactory(cacheManagerProvider.getIfAvailable());
        return mistralProviderFactory.createEmbeddingProvider(generateProviderConfig(props))
                .orElseThrow(RuntimeException::new);
    }
    /**
     * Auto-configures an {@link EmbeddingProvider} by wrapping an existing Spring AI {@link EmbeddingModel} bean.
     * <p>
     * Serves as a fallback mechanism when no explicit Spector embedding configuration is provided,
     * but an active Spring AI {@link EmbeddingModel} exists in the Spring application context.
     *
     * @param springEmbeddingModel the existing Spring AI {@link EmbeddingModel} bean
     * @return an {@link EmbeddingProvider} delegating to the wrapped Spring AI model
     */
    @Bean
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    @ConditionalOnBean(EmbeddingModel.class)
    EmbeddingProvider embeddingProvider(EmbeddingModel springEmbeddingModel) {

        /**
         * Inner adapter class wrapping Spring AI's {@link EmbeddingModel}
         * to satisfy Spector's {@link EmbeddingProvider} contract.
         */
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
    /**
     * Auto-configures {@link SpectorVectorStore} using local {@link SpectorMemory}.
     *
     * @param memory local embedded memory instance
     * @return {@link SpectorVectorStore} backed by local memory
     */
    @Bean(name = "spectorVectorMemoryStore")
    @ConditionalOnBean(SpectorMemory.class)
    @ConditionalOnMissingBean(SpectorVectorStore.class)
    SpectorVectorStore spectorVectorMemoryStore(SpectorMemory memory){
        return new SpectorVectorStore(memory);
    }
    /**
     * Auto-configures {@link SpectorVectorStore} using remote {@link SpectorClient}.
     * <p>
     * Evaluated only if no {@link SpectorVectorStore} bean (like the memory one above) was created.
     *
     * @param client remote client instance
     * @return {@link SpectorVectorStore} backed by remote client
     */
    @Bean(name = "spectorVectorClientStore")
    @ConditionalOnBean(SpectorClient.class)
    @ConditionalOnMissingBean(SpectorVectorStore.class)
    SpectorVectorStore spectorVectorClientStore(SpectorClient client){
        return new SpectorVectorStore(client);
    }

    @Bean
    @ConditionalOnMissingBean(SpectorClient.class)
    @ConditionalOnProperty(prefix = "spector.client",name = "host")
    SpectorClient spectorClient(SpectorConfigProperties props){
        com.spectrayan.spector.config.properties.ClientProperties clientProps = props.getClient();
        SpectorClient.Builder builder = SpectorClient.builder();

        if (clientProps.getHost() != null) {
            builder.host(clientProps.getHost());
            if (clientProps.getPort() > 0) {
                builder.port(clientProps.getPort());
            }
            if (clientProps.getApiKey() != null) {
                builder.apiKey(clientProps.getApiKey());
            }
            if (clientProps.getRequestTimeout() != null) {
                builder.requestTimeout(clientProps.getRequestTimeout());
            }
            if (clientProps.getConnectTimeout() != null) {
                builder.connectTimeout(clientProps.getConnectTimeout());
            }
            if (clientProps.getMaxConnections() > 0) {
                builder.maxConnections(clientProps.getMaxConnections());
            }

        }
        return builder.build();
    }
    /**
     * Helper method to map {@link SpectorConfigProperties} to Spector's native {@link EmbeddingConfig}.
     *
     * @param props bound configuration properties
     * @return an initialized {@link EmbeddingConfig} instance
     */
    EmbeddingConfig generateEmbeddingConfig(SpectorConfigProperties props) {
        var embedding = props.getProvider().getEmbedding();
        return new EmbeddingConfig(
                embedding.getModel(),
                embedding.getBaseUrl(),
                embedding.getTimeout(),
                embedding.getBatchSize(),
                embedding.getMaxConcurrent()
        );
    }
    /**
     * Helper method to map {@link SpectorConfigProperties} to Spector's native {@link ProviderConfig}.
     *
     * @param props bound configuration properties
     * @return an initialized {@link ProviderConfig} instance
     */
    ProviderConfig generateProviderConfig(SpectorConfigProperties props){
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
