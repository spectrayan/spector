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

import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.dream.DreamJournalMemory;
import com.spectrayan.spector.memory.dream.relay.DreamConfig;
import com.spectrayan.spector.memory.dream.relay.DreamMode;
import com.spectrayan.spector.memory.dream.relay.DreamReport;
import com.spectrayan.spector.memory.dream.relay.DreamSignal;
import com.spectrayan.spector.memory.hebbian.HebbianGraphMemory;
import com.spectrayan.spector.memory.kernel.shape.DistributedMemoryTensor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DreamPathwayTest {

    @Test
    void testFullDreamPathwayExecution() throws Exception {
        int dim = 8;
        DreamConfig dreamConfig = DreamConfig.builder()
                .enabled(true)
                .dreamNoiseScale(0.15f)
                .journalEnabled(true)
                .build();

        AismeConfig aismeConfig = AismeConfig.defaultConfig();
        HebbianGraphMemory hebbianGraph = new HebbianGraphMemory(50);

        try (DistributedMemoryTensor dmt = new DistributedMemoryTensor(dim);
             DreamJournalMemory journal = new DreamJournalMemory(null, 50, 256);
             DreamPathway pathway = DreamPathway.builder()
                     .dreamConfig(dreamConfig)
                     .aismeConfig(aismeConfig)
                     .hebbianGraph(hebbianGraph)
                     .distributedMemoryTensor(dmt)
                     .dreamJournalMemory(journal)
                     .build()) {

            float[] v1 = new float[]{0.5f, 0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
            float[] v2 = new float[]{0.0f, 0.0f, 0.8f, 0.6f, 0.0f, 0.0f, 0.0f, 0.0f};

            DreamSignal signal = DreamSignal.builder()
                    .mode(DreamMode.REM)
                    .config(dreamConfig)
                    .aismeConfig(aismeConfig)
                    .hebbianGraph(hebbianGraph)
                    .distributedMemoryTensor(dmt)
                    .dreamJournalMemory(journal)
                    .seedMemoryIds(List.of("seed-mem-1", "seed-mem-2"))
                    .seedVectors(List.of(v1, v2))
                    .build();

            DreamReport report = pathway.conduct(signal);

            assertThat(report).isNotNull();
            assertThat(report.seedsSampled()).isEqualTo(2);
            assertThat(report.scenesConstructed()).isGreaterThanOrEqualTo(1);
            assertThat(report.mode()).isEqualTo(DreamMode.REM);
        }
    }

    @Test
    void testDreamPathwayConvenienceMethod() throws Exception {
        DreamConfig dreamConfig = DreamConfig.builder()
                .enabled(true)
                .build();

        try (DreamPathway pathway = DreamPathway.builder()
                .dreamConfig(dreamConfig)
                .build()) {

            DreamReport report = pathway.dream(DreamMode.DAYDREAM, null, null);
            assertThat(report).isNotNull();
            assertThat(report.mode()).isEqualTo(DreamMode.DAYDREAM);
        }
    }
}
