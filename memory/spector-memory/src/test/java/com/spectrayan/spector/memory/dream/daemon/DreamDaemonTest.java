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
package com.spectrayan.spector.memory.dream.daemon;

import com.spectrayan.spector.memory.pathway.DreamPathway;
import com.spectrayan.spector.memory.dream.relay.DreamConfig;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DreamDaemonTest {

    @Test
    void testDreamDaemonBackgroundExecution() throws Exception {
        DreamConfig config = DreamConfig.builder()
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
