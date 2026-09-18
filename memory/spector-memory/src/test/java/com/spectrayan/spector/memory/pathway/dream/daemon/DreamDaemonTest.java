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
package com.spectrayan.spector.memory.pathway.dream.daemon;

import com.spectrayan.spector.config.properties.DreamProperties;
import com.spectrayan.spector.memory.pathway.dream.DreamPathway;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DreamDaemonTest {

    @Test
    void testDreamDaemonBackgroundExecution() throws Exception {
        DreamProperties config = DreamProperties.builder()
                .enabled(true)
                .dreamCycleFrequency(1) // run every cycle for test
                .build();

        try (DreamPathway pathway = DreamPathway.builder().dreamConfig(config).build()) {
            DreamDaemon daemon = new DreamDaemon(pathway, null);

            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            try {
                // Execute periodically in background thread
                executor.scheduleWithFixedDelay(daemon, 10, 50, TimeUnit.MILLISECONDS);

                // Allow 3 cycles to execute
                Thread.sleep(200);

                assertThat(daemon.totalCyclesRun()).isGreaterThanOrEqualTo(2);
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
