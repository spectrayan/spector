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

import com.spectrayan.spector.provider.DefaultProviderRegistry;
import com.spectrayan.spector.provider.DelegatingLlmProvider;
import com.spectrayan.spector.provider.ProviderRegistry;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.generic.DenseDerivedSparseProvider;
import com.spectrayan.spector.provider.embedding.generic.DenseDerivedTokenProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.kernel.id.TsidGenerator;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.api.SalienceProfileProvider;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.metrics.MeteredSpectorMemory;
import com.spectrayan.spector.metrics.SpectorMetrics;

import com.spectrayan.spector.provider.langchain4j.LangChain4jHelper;
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
import com.spectrayan.spector.memory.persist.DataEncryptor;
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
import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;
import com.spectrayan.spector.commons.concurrent.SpectorExecutors;
import com.spectrayan.spector.commons.concurrent.spi.SpectorExecutorProvider;
import com.spectrayan.spector.core.spi.AcceleratorRegistry;
import com.spectrayan.spector.memory.pathway.reflect.spi.ReflectSweepExecutors;
import com.spectrayan.spector.spring.concurrent.SpringExecutorProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Spring Boot auto-configuration for embedded Spector Cognitive Memory.
 *
 * <p>Automatically creates and wires the {@link SpectorMemory} bean when Spector is on the classpath.</p>
 */
@AutoConfiguration(after = SpectorEmbeddingAutoConfiguration.class)
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

    @Bean(name = "spectorSharedPool")
    @ConditionalOnMissingBean(name = "spectorSharedPool")
    public ThreadPoolTaskExecutor spectorSharedPool() {
        var ex = new ThreadPoolTaskExecutor();
        int n = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors() - 1));
        ex.setCorePoolSize(n);
        ex.setMaxPoolSize(n);
        ex.setQueueCapacity(256);
        ex.setThreadNamePrefix("spector-pool-shared-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(10);
        return ex;
    }

    @Bean(name = "spectorWriterPool")
    @ConditionalOnMissingBean(name = "spectorWriterPool")
    public ThreadPoolTaskExecutor spectorWriterPool() {
        var ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(1024);
        ex.setThreadNamePrefix("spector-pool-writer-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(15);
        return ex;
    }

    @Bean(name = "spectorVirtualExecutor")
    @ConditionalOnMissingBean(name = "spectorVirtualExecutor")
    public AsyncTaskExecutor spectorVirtualExecutor() {
        var ex = new SimpleAsyncTaskExecutor("spector-vt-default-");
        ex.setVirtualThreads(true);
        ex.setTaskTerminationTimeout(Duration.ofSeconds(10).toMillis());
        return ex;
    }

    @Bean(name = "taskScheduler")
    @ConditionalOnMissingBean(name = "taskScheduler")
    public ThreadPoolTaskScheduler spectorTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)));
        scheduler.setThreadNamePrefix("spector-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        return scheduler;
    }

    @Bean
    @ConditionalOnMissingBean(SpectorExecutorProvider.class)
    public SpectorExecutorProvider spectorExecutorProvider(
            @Qualifier("spectorSharedPool") ThreadPoolTaskExecutor shared,
            @Qualifier("spectorWriterPool") ThreadPoolTaskExecutor writer,
            @Qualifier("spectorVirtualExecutor") AsyncTaskExecutor virtual,
            @org.springframework.beans.factory.annotation.Value("${spector.threads.writer-per-namespace:false}") boolean writerPerNamespace) {
        var provider = new SpringExecutorProvider(shared, writer, virtual, writerPerNamespace);
        SpectorExecutors.install(provider);
        log.info("[Spector] Installed SpringExecutorProvider: {}", provider.describe());
        return provider;
    }

    /**
     * Creates the {@link SpectorMemory} bean when memory is enabled (default: true).
     */
    @Bean
    @ConditionalOnBean(EmbeddingProvider.class)
    @ConditionalOnMissingBean(SpectorMemory.class)
    @ConditionalOnProperty(prefix = "spector.memory", name = "enabled", havingValue = "true", matchIfMissing = true)
    SpectorMemory spectorMemory(SpectorConfigProperties props,
                                     ObjectProvider<EmbeddingProvider> embedderProvider,
                                     ObjectProvider<LlmProvider> textGenProvider,
                                     ObjectProvider<MeterRegistry> registryProvider,
                                     ObjectProvider<SalienceProfileProvider> salienceProvider,
                                     ObjectProvider<SpectorCacheManager> cacheManagerProvider,
                                     ObjectProvider<io.micrometer.observation.ObservationRegistry> observationRegistryProvider,
                                     ObjectProvider<com.spectrayan.spector.config.ObservabilityConfig> observabilityConfigProvider,
                                     ObjectProvider<SpectorExecutorProvider> executorProvider) {

            SpectorExecutorProvider execProvider = executorProvider.getIfAvailable();
            if (execProvider != null) {
                SpectorExecutors.install(execProvider);
            }

            var spectorProps = props.toSpectorProperties();
            var memoryProps = spectorProps.memory();
            EmbeddingProvider embedder = embedderProvider.getIfAvailable();

            if (embedder == null) {
                throw new SpectorInternalException(ErrorCode.ARGUMENT_NULL, "EmbeddingProvider bean (configure provider or set spector.memory.enabled=false)");
            }

            int embedderDims = props.getProvider().getEmbedding().getDimensions();
            if (embedderDims <= 0) {
                try {
                    embedderDims = embedder.dimensions();
                } catch (Exception e) {
                    log.debug("[Spector] Could not probe embedder dimensions eagerly (provider offline): {}", e.getMessage());
                }
            }
            if (embedderDims > 0 && memoryProps.getDimensions() != embedderDims) {
                log.info("[Spector] Aligning memory dimensions from {} to active embedder dimensions ({})",
                        memoryProps.getDimensions(), embedderDims);
                memoryProps.setDimensions(embedderDims);
                props.getMemory().setDimensions(embedderDims);
            }

            if (spectorProps.hardware() != null) {
                AcceleratorRegistry.setBatchThreshold(
                        spectorProps.hardware().getGpuBatchThreshold());
            }
            if (spectorProps.concurrency() != null) {
                ConcurrentTasks.setStructuredEnabled(
                        spectorProps.concurrency().isStructured());
            }
            if (spectorProps.memory() != null && spectorProps.memory().getCircadian() != null
                    && spectorProps.memory().getCircadian().getOrchestrator() != null
                    && !spectorProps.memory().getCircadian().getOrchestrator().isBlank()) {
                ReflectSweepExecutors.setOrchestrator(
                        spectorProps.memory().getCircadian().getOrchestrator());
            }

            var builder = SpectorMemoryBuilder.createEmpty()
                    .fromProperties(spectorProps)
                    .embeddingProvider(embedder);

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
}
