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
package com.spectrayan.spector.memory.pathway.dream;

import com.spectrayan.spector.commons.pathway.ConductionOutcome;
import com.spectrayan.spector.commons.pathway.DefaultPathwayContext;
import com.spectrayan.spector.commons.pathway.RelayTrace;
import com.spectrayan.spector.config.properties.DreamProperties;
import com.spectrayan.spector.kernel.api.DreamMode;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamReport;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamSignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ADR-0036 R6: DreamGate ABORT Policy Tests")
class DreamGateAbortTest {

    @Test
    @DisplayName("Closed dream gate aborts conduction, stops remaining relays, and marks Finish.SHORT_CIRCUITED")
    void closedDreamGateAbortsConduction() throws Exception {
        var disabledConfig = DreamProperties.disabled();

        try (var pathway = DreamPathway.builder()
                .dreamConfig(disabledConfig)
                .build()) {

            var ctx = DefaultPathwayContext.builder().traceEnabled(true).build();
            var signal = DreamSignal.builder()
                    .mode(DreamMode.REM)
                    .config(disabledConfig)
                    .seedMemoryIds(List.of("seed-1", "seed-2"))
                    .seedVectors(List.of(new float[]{0.1f, 0.2f}, new float[]{0.3f, 0.4f}))
                    .build();
            signal.bind(ctx);

            DreamReport report = pathway.execute(null, signal);

            assertThat(report).isNotNull();
            assertThat(report.seedsSampled()).isEqualTo(0);
            assertThat(report.scenesConstructed()).isEqualTo(0);

            // Context outcome finish should be SHORT_CIRCUITED
            assertThat(ctx.outcome().finish()).isEqualTo(ConductionOutcome.Finish.SHORT_CIRCUITED);

            // Traces should show only dream_gate with status SHORT_CIRCUITED
            assertThat(signal.traces()).hasSize(1);
            RelayTrace trace = signal.traces().getFirst();
            assertThat(trace.relayName()).isEqualTo("dream_gate");
            assertThat(trace.status()).isEqualTo(RelayTrace.TraceStatus.SHORT_CIRCUITED);

            // Downstream state must not have been touched
            assertThat(signal.fragments()).isEmpty();
            assertThat(signal.constructedScenes()).isEmpty();
        }
    }
}
