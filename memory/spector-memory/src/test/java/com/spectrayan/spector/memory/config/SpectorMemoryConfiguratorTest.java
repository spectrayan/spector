/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.config;

import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpectorMemoryConfiguratorTest {

    @Test
    @DisplayName("SpectorMemory.builder() creates a fluent builder instance")
    void testSpectorMemoryBuilderPublicApi() {
        SpectorMemoryBuilder builder = SpectorMemory.builder();
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("SpectorMemoryConfigurator loads from YAML file cleanly")
    void testConfigureFromYaml(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("spector.yml");
        String yaml = """
                spector:
                  mode: MEMORY
                  memory:
                    enabled: true
                    dimensions: 128
                    capacity: 500
                    persistence-mode: EPHEMERAL
                    text-search-mode: HYBRID
                    graph-expansion-mode: GATED
                    graph-expansion-threshold: 0.45
                  ingestion:
                    chunk-size: 1500
                    chunk-overlap: 150
                """;
        Files.writeString(configFile, yaml);

        SpectorProperties props = SpectorProperties.load(configFile);
        assertThat(props).isNotNull();

        // Create memory using configurator with mock embedding provider
        SpectorMemoryBuilder builder = SpectorMemory.builder()
                .fromProperties(com.spectrayan.spector.config.SpectorConfigFactory.memoryProperties(props))
                .embeddingProvider(new DummyEmbedder(128));

        try (SpectorMemory memory = builder.build()) {
            assertThat(memory).isNotNull();
        }
    }

    private static class DummyEmbedder implements EmbeddingProvider {
        private final int dims;
        DummyEmbedder(int dims) { this.dims = dims; }

        @Override public int dimensions() { return dims; }
        @Override public String modelName() { return "dummy"; }
        @Override public EmbeddingResult embed(String text) { return new EmbeddingResult(new float[dims], dims, "dummy"); }
        @Override public void close() {}
    }
}
