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
package com.spectrayan.spector.synapse.config;

import com.spectrayan.spector.connector.core.CamelConnectorEngine;
import com.spectrayan.spector.connector.core.RouteLifecycleService;
import com.spectrayan.spector.connector.sink.SpectorIngestionSink;
import com.spectrayan.spector.connector.spi.CredentialProvider;
import com.spectrayan.spector.connector.spi.InMemoryExecutionLogger;
import com.spectrayan.spector.connector.spi.InMemoryRouteConfigProvider;
import com.spectrayan.spector.connector.spi.RouteConfigProvider;
import com.spectrayan.spector.connector.template.TemplateRegistry;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Spring Boot Auto-Configuration for the Spector Connector Subsystem.
 *
 * <p>Wires together the standalone {@link CamelConnectorEngine},
 * {@link RouteLifecycleService}, {@link TemplateRegistry}, and
 * {@link SpectorIngestionSink} with Spring's DI container.</p>
 */
@Configuration
public class ConnectorAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ConnectorAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public TemplateRegistry templateRegistry(@Value("${spector.connectors.templates-dir:}") String templatesDir) {
        Path externalDir = (templatesDir != null && !templatesDir.isBlank()) ? Paths.get(templatesDir) : null;
        log.info("[ConnectorAutoConfig] Initializing TemplateRegistry (externalDir={})", externalDir);
        return new TemplateRegistry(externalDir);
    }

    @Bean
    @ConditionalOnMissingBean
    public RouteConfigProvider routeConfigProvider() {
        return new InMemoryRouteConfigProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public CredentialProvider credentialProvider() {
        return Optional::ofNullable;
    }

    @Bean
    @ConditionalOnMissingBean
    public SpectorIngestionSink spectorIngestionSink(
            org.springframework.beans.factory.ObjectProvider<SpectorMemory> memoryProvider,
            org.springframework.beans.factory.ObjectProvider<EmbeddingProvider> embeddingProvider) {
        log.info("[ConnectorAutoConfig] Initializing SpectorIngestionSink");
        SpectorMemory memory = memoryProvider.getIfAvailable();
        EmbeddingProvider ep = embeddingProvider.getIfAvailable();
        com.spectrayan.spector.ingestion.IngestionTarget target = null;
        if (memory != null) {
            try {
                target = memory.target();
            } catch (Exception ignored) {
            }
        }
        if (target == null) {
            target = (docId, text, vector) -> {};
        }
        EmbeddingProvider activeEp = ep != null ? ep : new EmbeddingProvider() {
            @Override
            public com.spectrayan.spector.provider.embedding.EmbeddingResult embed(String text) {
                return com.spectrayan.spector.provider.embedding.EmbeddingResult.of(new float[384], "noop");
            }
            @Override
            public int dimensions() {
                return 384;
            }
            @Override
            public String modelName() {
                return "noop";
            }
            @Override
            public void close() {}
        };
        return new SpectorIngestionSink(target, activeEp, new InMemoryExecutionLogger());
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnMissingBean
    public CamelConnectorEngine camelConnectorEngine(SpectorIngestionSink sink,
                                                     RouteConfigProvider configProvider,
                                                     TemplateRegistry templateRegistry) {
        log.info("[ConnectorAutoConfig] Initializing CamelConnectorEngine");
        return new CamelConnectorEngine(sink, configProvider, templateRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public RouteLifecycleService routeLifecycleService(CamelConnectorEngine engine,
                                                       TemplateRegistry templateRegistry,
                                                       CredentialProvider credentialProvider) {
        log.info("[ConnectorAutoConfig] Initializing RouteLifecycleService");
        return new RouteLifecycleService(engine, templateRegistry, credentialProvider);
    }
}
