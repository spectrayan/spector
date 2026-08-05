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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Tests for {@link SpectorConfigFactory} — verifies property-to-config mapping.
 */
class SpectorConfigFactoryTest {

    @Test
    void hnswDefaults_fromClasspath() {
        var hnsw = SpectorConfigFactory.hnswDefaults(SpectorProperties.load());

        assertThat(hnsw.m()).isEqualTo(16);
        assertThat(hnsw.efConstruction()).isEqualTo(200);
        assertThat(hnsw.efSearch()).isEqualTo(50);
    }

    @Test
    void ivfDefaults_fromClasspath() {
        var ivf = SpectorConfigFactory.ivfDefaults(SpectorProperties.load());

        assertThat(ivf.nlist()).isEqualTo(0);
        assertThat(ivf.nprobe()).isEqualTo(0);
        assertThat(ivf.pqSubspaces()).isEqualTo(0);
    }

    @Test
    void spectrumDefaults_fromClasspath() {
        var spectrum = SpectorConfigFactory.spectrumDefaults(SpectorProperties.load());

        assertThat(spectrum.nCentroids()).isEqualTo(256);
        assertThat(spectrum.nProbe()).isEqualTo(16);
        assertThat(spectrum.shardThreshold()).isEqualTo(20_000);
        assertThat(spectrum.oversamplingFactor()).isEqualTo(3);
        assertThat(spectrum.kmeansIterations()).isEqualTo(25);
    }

    @Test
    void embeddingDefaults_fromClasspath() {
        var embed = SpectorConfigFactory.embeddingDefaults(SpectorProperties.load());

        assertThat(embed.model()).isEqualTo("nomic-embed-text");
        assertThat(embed.baseUrl()).isEqualTo("http://localhost:11434");
        assertThat(embed.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(embed.batchSize()).isEqualTo(32);
    }

    @Test
    void rerankerDefaults_fromClasspath() {
        var reranker = SpectorConfigFactory.rerankerDefaults(SpectorProperties.load());

        assertThat(reranker.enabled()).isFalse();
        assertThat(reranker.ollamaUrl()).isEqualTo("http://localhost:11434");
        assertThat(reranker.model()).isEqualTo("llama3.2");
        assertThat(reranker.maxCandidates()).isEqualTo(20);
    }

    @Test
    void clusterDefaults_fromClasspath() {
        var cluster = SpectorConfigFactory.clusterDefaults(SpectorProperties.load());

        assertThat(cluster.shardCount()).isEqualTo(1);
        assertThat(cluster.replicaCount()).isEqualTo(0);
        assertThat(cluster.shardStrategy()).isEqualTo("HASH");
    }

    @Test
    void memoryDefaults_fromClasspath() {
        var memory = SpectorConfigFactory.memoryDefaults(SpectorProperties.load());

        assertThat(memory.enabled()).isFalse();
        assertThat(memory.persistenceMode()).isEqualTo("DISK");
        assertThat(memory.persistencePath()).isEqualTo(Path.of(".spector", "memory"));
        assertThat(memory.dimensions()).isEqualTo(384);
        assertThat(memory.capacity()).isEqualTo(100_000);
        assertThat(memory.decayEnabled()).isTrue();
        assertThat(memory.consolidationInterval()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void ingestionDefaults_fromClasspath() {
        var ingestion = SpectorConfigFactory.ingestionDefaults(SpectorProperties.load());

        assertThat(ingestion.rootDirectory()).isEqualTo(Path.of("."));
        assertThat(ingestion.filePattern()).isEqualTo("**/*.md");
        assertThat(ingestion.skipDirs()).contains(".git");
        assertThat(ingestion.chunkSize()).isEqualTo(800);
        assertThat(ingestion.chunkOverlap()).isEqualTo(100);
    }
}
