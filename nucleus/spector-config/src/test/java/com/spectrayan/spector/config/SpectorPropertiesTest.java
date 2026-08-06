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
package com.spectrayan.spector.config;

import com.spectrayan.spector.config.model.*;
import com.spectrayan.spector.config.properties.*;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Tests for {@link SpectorProperties} hierarchical configuration loading.
 */
class SpectorPropertiesTest {

    @Test
    void loadDefaults_returnsClasspathValues() {
        SpectorProperties props = SpectorProperties.load();

        // Verify values from spector-defaults.yml
        assertThat(props.getInt("spector.memory.dimensions", -1)).isEqualTo(384);
        assertThat(props.getInt("spector.memory.capacity", -1)).isEqualTo(100_000);
        assertThat(props.getString("spector.memory.persistence-mode", "")).isEqualTo("DISK");
    }

    @Test
    void loadDefaults_hnswParams() {
        SpectorProperties props = SpectorProperties.load();

        assertThat(props.getInt("spector.hnsw.m", -1)).isEqualTo(16);
        assertThat(props.getInt("spector.hnsw.ef-construction", -1)).isEqualTo(200);
        assertThat(props.getInt("spector.hnsw.ef-search", -1)).isEqualTo(50);
    }

    @Test
    void loadDefaults_embeddingConfig() {
        SpectorProperties props = SpectorProperties.load();

        assertThat(props.getString("spector.provider.embedding.model")).isEqualTo("nomic-embed-text");
        assertThat(props.getString("spector.provider.embedding.base-url")).isEqualTo("http://localhost:11434");
        assertThat(props.getInt("spector.provider.embedding.batch-size", -1)).isEqualTo(32);
        assertThat(props.getInt("spector.provider.embedding.max-retries", -1)).isEqualTo(3);
    }

    @Test
    void loadDefaults_persistenceFiles() {
        SpectorProperties props = SpectorProperties.load();

        assertThat(props.getString("spector.persistence.files.index")).isEqualTo("index.spct");
        assertThat(props.getString("spector.persistence.files.vectors")).isEqualTo("vectors.mmap");
        assertThat(props.getString("spector.persistence.files.documents")).isEqualTo("documents.dat");
        assertThat(props.getString("spector.persistence.files.id-mappings")).isEqualTo("id-mappings.dat");
    }

    @Test
    void duration_humanReadable() {
        SpectorProperties props = SpectorProperties.builder()
                .override("timeout.seconds", "30s")
                .override("timeout.millis", "500ms")
                .override("timeout.minutes", "5m")
                .override("timeout.hours", "1h")
                .build();

        assertThat(props.getDuration("timeout.seconds", Duration.ZERO)).isEqualTo(Duration.ofSeconds(30));
        assertThat(props.getDuration("timeout.millis", Duration.ZERO)).isEqualTo(Duration.ofMillis(500));
        assertThat(props.getDuration("timeout.minutes", Duration.ZERO)).isEqualTo(Duration.ofMinutes(5));
        assertThat(props.getDuration("timeout.hours", Duration.ZERO)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void builderOverrides_takePrecedence() {
        SpectorProperties props = SpectorProperties.builder()
                .override("spector.memory.dimensions", "1024")
                .override("spector.provider.embedding.model", "custom-model")
                .build();

        assertThat(props.getInt("spector.memory.dimensions", -1)).isEqualTo(1024);
        assertThat(props.getString("spector.provider.embedding.model")).isEqualTo("custom-model");
        assertThat(props.getInt("spector.hnsw.m", -1)).isEqualTo(16); // non-overridden remains default
    }

    @Test
    void systemProperties_takePrecedenceOverOverrides() {
        String key = "spector.test.sysprop.key";
        System.setProperty(key, "from-system");
        try {
            SpectorProperties props = SpectorProperties.builder()
                    .override(key, "from-override")
                    .build();

            // System properties win over everything
            assertThat(props.getString(key)).isEqualTo("from-system");
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void yamlFileOverride(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("spector.yml");
        Files.writeString(configFile, """
                spector:
                  memory:
                    dimensions: 1024
                    capacity: 500000
                """);

        SpectorProperties props = SpectorProperties.load(configFile);

        assertThat(props.getInt("spector.memory.dimensions", -1)).isEqualTo(1024);
        assertThat(props.getInt("spector.memory.capacity", -1)).isEqualTo(500_000);
        // Other values still come from classpath defaults
        assertThat(props.getInt("spector.hnsw.m", -1)).isEqualTo(16);
    }

    @Test
    void propertiesFileOverride(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("custom.properties");
        Files.writeString(configFile, """
                spector.memory.dimensions=2048
                spector.provider.embedding.model=mxbai-embed-large
                """);

        SpectorProperties props = SpectorProperties.builder()
                .configFile(configFile)
                .build();

        assertThat(props.getInt("spector.memory.dimensions", -1)).isEqualTo(2048);
        assertThat(props.getString("spector.provider.embedding.model")).isEqualTo("mxbai-embed-large");
    }

    @Test
    void missingKey_returnsDefault() {
        SpectorProperties props = SpectorProperties.load();

        assertThat(props.getString("nonexistent.key")).isNull();
        assertThat(props.getString("nonexistent.key", "fallback")).isEqualTo("fallback");
        assertThat(props.getInt("nonexistent.key", 42)).isEqualTo(42);
        assertThat(props.getBoolean("nonexistent.key", true)).isTrue();
    }

    @Test
    void containsKey() {
        SpectorProperties props = SpectorProperties.load();

        assertThat(props.containsKey("spector.memory.dimensions")).isTrue();
        assertThat(props.containsKey("nonexistent.key")).isFalse();
    }

    @Test
    void path_resolution() {
        SpectorProperties props = SpectorProperties.builder()
                .override("data.dir", "/tmp/spector")
                .build();

        assertThat(props.getPath("data.dir", null)).isEqualTo(Path.of("/tmp/spector"));
        assertThat(props.getPath("missing.key", Path.of("/default"))).isEqualTo(Path.of("/default"));
    }

    @Test
    void persistenceFiles_fromProperties() {
        SpectorProperties props = SpectorProperties.builder()
                .override("spector.persistence.files.index", "custom-index.bin")
                .override("spector.persistence.files.vectors", "custom-vectors.bin")
                .build();

        var files = PersistenceFiles.fromProperties(props);
        assertThat(files.indexFile()).isEqualTo("custom-index.bin");
        assertThat(files.vectorsFile()).isEqualTo("custom-vectors.bin");
        assertThat(files.documentsFile()).isEqualTo("documents.dat"); // default retained
    }
}
