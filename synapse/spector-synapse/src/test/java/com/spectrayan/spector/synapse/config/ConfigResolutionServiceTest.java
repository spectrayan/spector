/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.config;

import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.synapse.config.model.ConfigCategory;
import com.spectrayan.spector.synapse.config.repository.ConfigRepository;
import com.spectrayan.spector.synapse.config.service.ConfigResolutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConfigResolutionService: Projection from SpectorProperties")
class ConfigResolutionServiceTest {

    @Test
    @DisplayName("projects ingestion defaults directly from SpectorProperties chunk configuration")
    void testIngestionDefaultsProjection() {
        ConfigRepository repo = Mockito.mock(ConfigRepository.class);
        ConfigResolutionService service = new ConfigResolutionService(repo);

        Map<String, Object> resolved = service.resolve(null, null, ConfigCategory.INGESTION);
        SpectorProperties props = SpectorProperties.load();

        assertThat(resolved.get("chunk-size"))
                .isEqualTo(props.memory().getRemember().getChunk().getSize());
        assertThat(resolved.get("chunk-overlap"))
                .isEqualTo(props.memory().getRemember().getChunk().getOverlap());
        assertThat(resolved.get("parent-child-linking")).isEqualTo(false);
    }

    @Test
    @DisplayName("projects rag defaults directly from SpectorProperties recall configuration")
    void testRagDefaultsProjection() {
        ConfigRepository repo = Mockito.mock(ConfigRepository.class);
        ConfigResolutionService service = new ConfigResolutionService(repo);

        Map<String, Object> resolved = service.resolve(null, null, ConfigCategory.RAG);
        SpectorProperties props = SpectorProperties.load();

        assertThat(resolved.get("top-k"))
                .isEqualTo(com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_QUERY_DEFAULT_TOP_K);
        assertThat(resolved.get("similarity-threshold"))
                .isEqualTo((double) props.memory().getGraphExpansionThreshold());
    }

    @Test
    @DisplayName("projects llm provider defaults directly from SpectorProperties generation configuration")
    void testLlmProviderDefaultsProjection() {
        ConfigRepository repo = Mockito.mock(ConfigRepository.class);
        ConfigResolutionService service = new ConfigResolutionService(repo);

        Map<String, Object> resolved = service.resolve(null, null, ConfigCategory.LLM_PROVIDER);
        SpectorProperties props = SpectorProperties.load();

        assertThat(resolved.get("provider"))
                .isEqualTo(props.provider().getGeneration().getType());
        assertThat(resolved.get("model"))
                .isEqualTo(props.provider().getGeneration().getModel());
        assertThat(resolved.get("base-url"))
                .isEqualTo(props.provider().getGeneration().getBaseUrl());
        assertThat(resolved.get("temperature"))
                .isEqualTo(0.7);
    }
}
