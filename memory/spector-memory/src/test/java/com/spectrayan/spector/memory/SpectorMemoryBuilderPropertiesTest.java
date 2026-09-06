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
