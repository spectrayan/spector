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

import com.spectrayan.spector.config.model.*;
import com.spectrayan.spector.config.properties.*;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Tests for {@link SpectorConfigFactory} — verifies property-to-config mapping.
 */
class SpectorConfigFactoryTest {

    @Test
    void hnswProperties_fromClasspath() {
        var hnsw = SpectorConfigFactory.hnswProperties(SpectorConfigSource.load());

        assertThat(hnsw.m()).isEqualTo(16);
        assertThat(hnsw.efConstruction()).isEqualTo(200);
        assertThat(hnsw.efSearch()).isEqualTo(50);
    }

    @Test
    void ivfProperties_fromClasspath() {
        var ivf = SpectorConfigFactory.ivfProperties(SpectorConfigSource.load());

        assertThat(ivf.nlist()).isEqualTo(0);
        assertThat(ivf.nprobe()).isEqualTo(0);
        assertThat(ivf.pqSubspaces()).isEqualTo(0);
    }

    @Test
    void spectrumProperties_fromClasspath() {
        var spectrum = SpectorConfigFactory.spectrumProperties(SpectorConfigSource.load());

        assertThat(spectrum.nCentroids()).isEqualTo(256);
        assertThat(spectrum.nProbe()).isEqualTo(16);
        assertThat(spectrum.shardThreshold()).isEqualTo(20_000);
        assertThat(spectrum.oversamplingFactor()).isEqualTo(3);
        assertThat(spectrum.kmeansIterations()).isEqualTo(25);
    }

    @Test
    void embeddingProperties_fromClasspath() {
        var embed = SpectorConfigFactory.embeddingProperties(SpectorConfigSource.load());

        assertThat(embed.model()).isEqualTo("nomic-embed-text");
        assertThat(embed.baseUrl()).isEqualTo("http://localhost:11434");
        assertThat(embed.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(embed.batchSize()).isEqualTo(32);
    }


    @Test
    void memoryProperties_fromClasspath() {
        var memory = SpectorConfigFactory.memoryProperties(SpectorConfigSource.load());

        assertThat(memory.enabled()).isFalse();
        assertThat(memory.persistenceMode()).isEqualTo(PersistenceMode.DISK);
        assertThat(memory.persistencePath()).isEqualTo(Path.of(".spector", "memory").toString());
        assertThat(memory.dimensions()).isEqualTo(384);
        assertThat(memory.capacity()).isEqualTo(100_000);
        assertThat(memory.nodesPerPartition()).isEqualTo(10_000);
        assertThat(memory.decay().getMinThreshold()).isGreaterThan(0.0);
        assertThat(memory.consolidation().getInterval()).isEqualTo(Duration.ofSeconds(60).toMillis());
    }

    @Test
    void ingestionProperties_fromClasspath() {
        var ingestion = SpectorConfigFactory.ingestionProperties(SpectorConfigSource.load());

        assertThat(ingestion.rootDirectory()).isEqualTo(Path.of("."));
        assertThat(ingestion.filePattern()).isEqualTo("**/*.md");
        assertThat(ingestion.skipDirs()).contains(".git");
        assertThat(ingestion.chunkSize()).isEqualTo(800);
        assertThat(ingestion.chunkOverlap()).isEqualTo(100);
    }

    @Test
    void memoryProperties_flexibleCaseInsensitiveEnums() {
        SpectorConfigSource props = SpectorConfigSource.builder()
                .override("spector.memory.persistence-mode", "in-memory")
                .override("spector.memory.default-ingestion-tier", "semantic")
                .override("spector.memory.hnsw-prefilter", "Enabled")
                .override("spector.memory.tag-extractor", "llm")
                .override("spector.memory.text-search-mode", "full-stack")
                .build();

        var memory = SpectorConfigFactory.memoryProperties(props);

        assertThat(memory.getPersistenceMode()).isEqualTo(PersistenceMode.IN_MEMORY);
        assertThat(memory.getDefaultIngestionTier()).isEqualTo(RememberTier.SEMANTIC);
        assertThat(memory.getHnswPrefilter()).isEqualTo(HnswPrefilterMode.ENABLED);
        assertThat(memory.getTagExtractor()).isEqualTo(TagExtractorMode.LLM);
        assertThat(memory.getTextSearchMode()).isEqualTo(TextSearchMode.FULL_STACK);
    }

    @Test
    void recallProperties_legacyRecallMmrFallback() {
        SpectorConfigSource props = SpectorConfigSource.builder()
                .override("spector.recall.mmr.enabled", "true")
                .override("spector.recall.mmr.lambda", "0.85")
                .build();

        var recall = SpectorConfigFactory.recallProperties(props);
        assertThat(recall.getMmr().isEnabled()).isTrue();
        assertThat(recall.getMmr().getLambda()).isEqualTo(0.85f);

        var memory = SpectorConfigFactory.memoryProperties(props);
        assertThat(memory.isEnableMmr()).isTrue();
        assertThat(memory.getMmrLambda()).isEqualTo(0.85f);
    }

    @Test
    void recallProperties_legacyRetrievalMmrFallback() {
        SpectorConfigSource props = SpectorConfigSource.builder()
                .override("spector.memory.retrieval.enable-mmr", "true")
                .override("spector.memory.retrieval.mmr-lambda", "0.75")
                .build();

        var recall = SpectorConfigFactory.recallProperties(props);
        assertThat(recall.getMmr().isEnabled()).isTrue();
        assertThat(recall.getMmr().getLambda()).isEqualTo(0.75f);

        var memory = SpectorConfigFactory.memoryProperties(props);
        assertThat(memory.isEnableMmr()).isTrue();
        assertThat(memory.getMmrLambda()).isEqualTo(0.75f);
    }

    @Test
    void rememberProperties_canonicalChunkSizeWinsOverLegacy() {
        SpectorConfigSource props = SpectorConfigSource.builder()
                .override("spector.ingestion.chunk-size", "512")
                .override("spector.memory.remember.chunk.size", "1024")
                .override("spector.ingestion.chunk-overlap", "50")
                .override("spector.memory.remember.chunk.overlap", "128")
                .build();

        var remember = SpectorConfigFactory.rememberProperties(props);
        assertThat(remember.getChunk().getSize()).isEqualTo(1024);
        assertThat(remember.getChunk().getOverlap()).isEqualTo(128);
    }

    @Test
    void recallProperties_engineRoutingAndPathwayDerivation() {
        SpectorConfigSource propsDirect = SpectorConfigSource.builder()
                .override("spector.memory.recall.engine", "direct")
                .build();

        var memoryDirect = SpectorConfigFactory.memoryProperties(propsDirect);
        assertThat(memoryDirect.getRecall().getEngine()).isEqualTo("direct");
        assertThat(memoryDirect.isPathwayEnabled()).isFalse();

        SpectorConfigSource propsPathway = SpectorConfigSource.builder()
                .override("spector.memory.recall.engine", "pathway")
                .build();

        var memoryPathway = SpectorConfigFactory.memoryProperties(propsPathway);
        assertThat(memoryPathway.getRecall().getEngine()).isEqualTo("pathway");
        assertThat(memoryPathway.isPathwayEnabled()).isTrue();
    }
}
