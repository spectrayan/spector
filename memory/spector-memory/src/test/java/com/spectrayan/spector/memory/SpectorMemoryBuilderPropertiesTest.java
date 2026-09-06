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
package com.spectrayan.spector.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.config.SpectorConfigSource;
import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoringMode;

import org.junit.jupiter.api.Test;

class SpectorMemoryBuilderPropertiesTest {

    @Test
    void fromProperties_hydratesAllSubdomainsAndDefaultRecallOptions() {
        SpectorConfigSource source = SpectorConfigSource.builder()
                .override("spector.memory.recall.mmr.enabled", "true")
                .override("spector.memory.recall.mmr.lambda", "0.8")
                .override("spector.memory.recall.scoring-mode", "similarity_only")
                .override("spector.memory.recall.text-search.enabled", "false")
                .override("spector.memory.remember.chunk.size", "1200")
                .override("spector.memory.remember.chunk.overlap", "150")
                .override("spector.memory.remember.surprise-warmup", "25")
                .override("spector.memory.graph.expansion-threshold", "0.55")
                .build();

        SpectorProperties props = SpectorProperties.from(source);
        SpectorMemoryBuilder builder = SpectorMemoryBuilder.create().fromProperties(props);

        RecallOptions defaultRecall = builder.defaultRecallOptions();
        assertThat(defaultRecall.enableMmr()).isTrue();
        assertThat(defaultRecall.mmrLambda()).isEqualTo(0.8f);
        assertThat(defaultRecall.scoringMode()).isEqualTo(ScoringMode.SIMILARITY);
        assertThat(defaultRecall.enableTextSearch()).isFalse();

        assertThat(builder.surpriseWarmup()).isEqualTo(25);
        assertThat(builder.chunkConfig().maxChunkSize()).isEqualTo(1200);
        assertThat(builder.chunkConfig().overlap()).isEqualTo(150);
        assertThat(builder.graphScoringPolicy().graphExpansionThreshold()).isEqualTo(0.55f);
    }

    @Test
    void fromProperties_hydratesAllCapacitiesSegmentSizesAndSubDomains() {
        SpectorConfigSource source = SpectorConfigSource.builder()
                .override("spector.memory.working-capacity", "250")
                .override("spector.memory.episodic-partition-capacity", "50000")
                .override("spector.memory.procedural-capacity", "15000")
                .override("spector.memory.entity-graph-capacity", "80000")
                .override("spector.memory.text-segment-size", "2097152")
                .override("spector.memory.episodic-segment-size", "4194304")
                .override("spector.memory.circadian.volume-trigger", "50")
                .override("spector.memory.dream.max-dreams-per-cycle", "7")
                .override("spector.memory.twofactor.enabled", "false")
                .override("spector.memory.twofactor.s-gain", "0.25")
                .override("spector.provider.embedding.batch-size", "48")
                .override("spector.memory.checkpoint-interval-seconds", "120")
                .override("spector.memory.namespace-id", "test-ns")
                .override("spector.memory.persist-working-memory", "true")
                .build();

        SpectorProperties props = SpectorProperties.from(source);
        SpectorMemoryBuilder builder = SpectorMemoryBuilder.createEmpty().fromProperties(props);

        assertThat(builder.workingCapacity()).isEqualTo(250);
        assertThat(builder.episodicPartitionCapacity()).isEqualTo(50000);
        assertThat(builder.proceduralCapacity()).isEqualTo(15000);
        assertThat(builder.entityGraphCapacity()).isEqualTo(80000);
        assertThat(builder.textSegmentSize()).isEqualTo(2097152L);
        assertThat(builder.episodicSegmentSize()).isEqualTo(4194304L);
        assertThat(builder.circadianPolicy().volumeTrigger()).isEqualTo(50);
        assertThat(builder.dreamConfig().maxDreamsPerCycle()).isEqualTo(7);
        assertThat(builder.twoFactorConfig().enabled()).isFalse();
        assertThat(builder.twoFactorConfig().sGain()).isEqualTo(0.25f);
        assertThat(builder.embedBatchSize()).isEqualTo(48);
        assertThat(builder.namespaceId()).isEqualTo("test-ns");
        assertThat(builder.persistWorkingMemory()).isTrue();
    }

    @Test
    void create_seedsFromSnapshotByDefault() {
        SpectorMemoryBuilder builder = SpectorMemoryBuilder.create();
        // Should have loaded defaults from classpath spector-defaults.yml
        assertThat(builder.dimensions()).isEqualTo(384);
        assertThat(builder.semanticCapacity()).isEqualTo(100_000);
        assertThat(builder.circadianPolicy()).isNotNull();
        assertThat(builder.dreamConfig()).isNotNull();
        assertThat(builder.twoFactorConfig()).isNotNull();
    }

    @Test
    void createEmpty_returnsUnseededBuilder() {
        SpectorMemoryBuilder builder = SpectorMemoryBuilder.createEmpty();
        assertThat(builder.dimensions()).isEqualTo(0);
        assertThat(builder.spectorProperties()).isNull();
    }

    @Test
    void straySyspropDoesNotOverrideSnapshot() {
        String sysPropKey = "spector.memory.graphExpansionThreshold";
        String originalVal = System.getProperty(sysPropKey);
        try {
            System.setProperty(sysPropKey, "0.99");

            SpectorConfigSource source = SpectorConfigSource.builder()
                    .override("spector.memory.graph.expansion-threshold", "0.35")
                    .build();

            SpectorProperties props = SpectorProperties.from(source);
            SpectorMemoryBuilder builder = SpectorMemoryBuilder.create().fromProperties(props);

            assertThat(builder.graphScoringPolicy().graphExpansionThreshold()).isEqualTo(0.35f);
        } finally {
            if (originalVal != null) {
                System.setProperty(sysPropKey, originalVal);
            } else {
                System.clearProperty(sysPropKey);
            }
        }
    }
}
