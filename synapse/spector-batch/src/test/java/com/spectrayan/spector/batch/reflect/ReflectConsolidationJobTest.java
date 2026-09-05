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
package com.spectrayan.spector.batch.reflect;

import com.spectrayan.spector.batch.SpectorBatchAutoConfiguration;
import com.spectrayan.spector.batch.TestBatchConfig;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.EpisodicMemory;
import com.spectrayan.spector.memory.model.ConversationRole;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.pathway.reflect.ReflectFilter;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepProgress;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepSpec;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@ContextConfiguration(classes = {TestBatchConfig.class, SpectorBatchAutoConfiguration.class})
@DisplayName("ReflectConsolidationJob & SpringBatchReflectSweepExecutor Integration Tests")
class ReflectConsolidationJobTest {

    @Autowired
    private SpringBatchReflectSweepExecutor executor;

    private EpisodicMemory logStore;

    @BeforeEach
    void setUp() {
        logStore = new EpisodicMemory(1024 * 1024);
    }

    @AfterEach
    void tearDown() {
        if (logStore != null) {
            logStore.close();
        }
    }

    @Test
    @DisplayName("SpringBatchReflectSweepExecutor metadata and availability checks")
    void testExecutorMetadata() {
        assertThat(executor.name()).isEqualTo("spring-batch");
        assertThat(executor.priority()).isEqualTo(100);
        assertThat(executor.available()).isTrue();
    }

    @Test
    @DisplayName("Executes reflection sweep over unconsolidated turns and tracks progress")
    void testExecuteSweep() {
        // Append 2 turns for session 501L
        logStore.appendTurn(ConversationRole.USER, 1, 1000L, 501L, "I love distributed systems.".getBytes(),
                (short) 1, 0, 0, 0, 0L, (short) 1, SourceModality.TEXT);
        logStore.appendTurn(ConversationRole.ASSISTANT, 2, 2000L, 501L, "Distributed systems are resilient.".getBytes(),
                (short) 1, 0, 0, 0, 0L, (short) 1, SourceModality.TEXT);

        SpectorMemory memory = Mockito.mock(SpectorMemory.class);
        SpectorMemoryAdmin admin = Mockito.mock(SpectorMemoryAdmin.class);
        CognitiveMemoryRouter router = Mockito.mock(CognitiveMemoryRouter.class);

        when(memory.admin()).thenReturn(admin);
        when(admin.cognitiveRouter()).thenReturn(router);
        when(router.episodic()).thenReturn(logStore);

        ReflectReport mockKernelReport = new ReflectReport(2, 0, 0, 0, Duration.ofMillis(50), null, 0, 0, 0f, 2);
        when(admin.reflectKernel(any())).thenReturn(mockKernelReport);

        String sweepId = "test-batch-sweep-501";
        ReflectSweepSpec spec = ReflectSweepSpec.builder()
                .sweepId(sweepId)
                .filter(ReflectFilter.unconsolidated())
                .runCompanionRelays(false)
                .build();

        ReflectReport report = executor.execute(memory, spec);

        assertThat(report).isNotNull();
        assertThat(report.consolidatedCount()).isEqualTo(2);
        assertThat(report.logTurnsConsolidated()).isEqualTo(2);

        ReflectSweepProgress progress = executor.progress(sweepId);
        assertThat(progress).isNotNull();
        assertThat(progress.status()).isEqualTo(ReflectSweepStatus.COMPLETE);
        assertThat(progress.factsIngested()).isEqualTo(2);
        assertThat(progress.turnsMarked()).isEqualTo(2);
    }
}
