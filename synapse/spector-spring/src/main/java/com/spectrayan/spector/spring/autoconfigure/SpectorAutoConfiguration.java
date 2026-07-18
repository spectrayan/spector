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

import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.SalienceProfileProvider;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.metrics.MeteredSpectorMemory;
import com.spectrayan.spector.metrics.SpectorMetrics;

import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import com.spectrayan.spector.commons.error.SpectorInternalException;
import com.spectrayan.spector.commons.error.ErrorCode;

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
     * Creates the {@link SpectorMemory} bean when memory is enabled (default: true).
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spector.memory", name = "enabled", havingValue = "true", matchIfMissing = true)
    SpectorMemory spectorMemory(SpectorConfigProperties props,
                                 ObjectProvider<EmbeddingProvider> embedderProvider,
                                 ObjectProvider<LlmProvider> textGenProvider,
                                 ObjectProvider<MeterRegistry> registryProvider,
                                 ObjectProvider<SalienceProfileProvider> salienceProvider) {

        var memoryProps = props.getMemory();
        EmbeddingProvider embedder = embedderProvider.getIfAvailable();

        if (embedder == null) {
            throw new SpectorInternalException(ErrorCode.ARGUMENT_NULL, "EmbeddingProvider bean (configure provider or set spector.memory.enabled=false)");
        }

        var builder = DefaultSpectorMemory.builder()
                .dimensions(memoryProps.getDimensions())
                .embeddingProvider(embedder)
                .persistenceMode(MemoryPersistenceMode.valueOf(memoryProps.getPersistenceMode()))
                .semanticCapacity(memoryProps.getCapacity())
                .hebbianGraphCapacity(memoryProps.getCapacity())
                .temporalChainCapacity(memoryProps.getCapacity())
                .entityGraphCapacity(memoryProps.getCapacity())
                .embedBatchSize(props.getEmbedding().getBatchSize());

        if (memoryProps.getPersistencePath() != null) {
            builder.persistence(Path.of(memoryProps.getPersistencePath()));
        }

        // -€-€ Entity extraction (LLM if LlmProvider is present) -€-€
        LlmProvider textGen = textGenProvider.getIfAvailable();
        if (textGen != null) {
            builder.entityExtractionMode(EntityExtractionMode.LLM);
            builder.LlmProvider(textGen);
        } else {
            builder.entityExtractionMode(EntityExtractionMode.NONE);
        }

        // -€-€ Salience profile provider (user-driven importance modulation) -€-€
        SalienceProfileProvider salience = salienceProvider.getIfAvailable();
        if (salience != null) {
            builder.salienceProfileProvider(salience);
            log.info("SpectorMemory: user salience profile provider wired");
        }

        // -€-€ SPLADE + ColBERT providers (auto-created from embedding provider) -€-€
        if (memoryProps.isSpladeEnabled()) {
            builder.SparseEmbeddingProvider(
                    new com.spectrayan.spector.provider.embedding.generic.DenseDerivedSparseProvider(embedder));
        }
        if (memoryProps.isColbertEnabled()) {
            builder.tokenEmbeddingProvider(
                    new com.spectrayan.spector.provider.embedding.generic.DenseDerivedTokenProvider(embedder));
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
            return new MeteredSpectorMemory(raw, registry);
        }

        return raw;
    }

    @org.springframework.context.annotation.Configuration
    static class SpringHttpClientAutoConfiguration {
        SpringHttpClientAutoConfiguration(org.springframework.context.ApplicationContext context) {
            // 1. Try to find and register RestClient.Builder
            try {
                Class<?> restClientBuilderClass = Class.forName("org.springframework.web.client.RestClient$Builder");
                Object provider = context.getBeanProvider(restClientBuilderClass);
                java.lang.reflect.Method getIfAvailable = provider.getClass().getMethod("getIfAvailable");
                Object builder = getIfAvailable.invoke(provider);
                if (builder != null) {
                    log.info("[Spector] Auto-registering Spring RestClient.Builder in LangChain4jHelper");
                    com.spectrayan.spector.provider.langchain4j.LangChain4jHelper.setSpringRestClientBuilder(builder);
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
                java.lang.reflect.Method getIfAvailable = provider.getClass().getMethod("getIfAvailable");
                Object builder = getIfAvailable.invoke(provider);
                if (builder != null) {
                    log.info("[Spector] Auto-registering Spring WebClient.Builder in LangChain4jHelper");
                    com.spectrayan.spector.provider.langchain4j.LangChain4jHelper.setSpringWebClientBuilder(builder);
                }
            } catch (ClassNotFoundException e) {
                // WebClient is not on the classpath
            } catch (Exception e) {
                log.warn("[Spector] Failed to auto-register WebClient.Builder: {}", e.getMessage());
            }
        }
    }
}
