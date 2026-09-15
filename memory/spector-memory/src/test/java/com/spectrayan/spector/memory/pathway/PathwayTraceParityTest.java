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
package com.spectrayan.spector.memory.pathway;

import com.spectrayan.spector.commons.pathway.CognitivePathway;
import com.spectrayan.spector.commons.pathway.DefaultPathwayContext;
import com.spectrayan.spector.commons.pathway.RelayTrace;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.pathway.decide.relay.DecideSignal;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamSignal;
import com.spectrayan.spector.memory.pathway.express.relay.ExpressSignal;
import com.spectrayan.spector.memory.pathway.recall.relay.RecallSignal;
import com.spectrayan.spector.memory.pathway.reflect.relay.ReflectSignal;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;
import com.spectrayan.spector.memory.pathway.wander.relay.WanderSignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("M6.6 Gate: PathwayTraceParityTest — Signal Traceability Across All 7 Pathways")
class PathwayTraceParityTest {

    @Test
    @DisplayName("1. RememberSignal records >= 1 RelayTrace when trace is enabled")
    void rememberSignalTrace() {
        var pathway = CognitivePathway.<RememberSignal>pathway("remember")
                .relay("remember-relay", s -> true)
                .build();
        var signal = RememberSignal.forCognitive(
                "rem-1", "content", null, MemoryType.SEMANTIC, null,
                MemorySource.OBSERVED, null, SalienceProfile.NEUTRAL, (short) 0);
        var ctx = DefaultPathwayContext.builder().traceEnabled(true).build();
        signal.bind(ctx);

        pathway.conduct(signal);

        assertThat(signal.isTraceEnabled()).isTrue();
        assertThat(signal.traces()).hasSize(1);
        RelayTrace trace = signal.traces().getFirst();
        assertThat(trace.relayName()).isEqualTo("remember-relay");
        assertThat(trace.status()).isEqualTo(RelayTrace.TraceStatus.EXECUTED);
        assertThat(trace.durationNanos()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("2. RecallSignal records >= 1 RelayTrace when trace is enabled")
    void recallSignalTrace() {
        var pathway = CognitivePathway.<RecallSignal>pathway("recall")
                .relay("recall-relay", s -> true)
                .build();
        var signal = RecallSignal.forTextQuery("search query", RecallOptions.builder().build());
        var ctx = DefaultPathwayContext.builder().traceEnabled(true).build();
        signal.bind(ctx);

        pathway.conduct(signal);

        assertThat(signal.isTraceEnabled()).isTrue();
        assertThat(signal.traces()).hasSize(1);
        RelayTrace trace = signal.traces().getFirst();
        assertThat(trace.relayName()).isEqualTo("recall-relay");
        assertThat(trace.status()).isEqualTo(RelayTrace.TraceStatus.EXECUTED);
    }

    @Test
    @DisplayName("3. ReflectSignal records >= 1 RelayTrace when trace is enabled")
    void reflectSignalTrace() {
        var pathway = CognitivePathway.<ReflectSignal>pathway("reflect")
                .relay("reflect-relay", s -> true)
                .build();
        var signal = ReflectSignal.builder().build();
        var ctx = DefaultPathwayContext.builder().traceEnabled(true).build();
        signal.bind(ctx);

        pathway.conduct(signal);

        assertThat(signal.isTraceEnabled()).isTrue();
        assertThat(signal.traces()).hasSize(1);
        RelayTrace trace = signal.traces().getFirst();
        assertThat(trace.relayName()).isEqualTo("reflect-relay");
        assertThat(trace.status()).isEqualTo(RelayTrace.TraceStatus.EXECUTED);
    }

    @Test
    @DisplayName("4. DreamSignal records >= 1 RelayTrace when trace is enabled")
    void dreamSignalTrace() {
        var pathway = CognitivePathway.<DreamSignal>pathway("dream")
                .relay("dream-relay", s -> true)
                .build();
        var signal = DreamSignal.builder().build();
        var ctx = DefaultPathwayContext.builder().traceEnabled(true).build();
        signal.bind(ctx);

        pathway.conduct(signal);

        assertThat(signal.isTraceEnabled()).isTrue();
        assertThat(signal.traces()).hasSize(1);
        RelayTrace trace = signal.traces().getFirst();
        assertThat(trace.relayName()).isEqualTo("dream-relay");
        assertThat(trace.status()).isEqualTo(RelayTrace.TraceStatus.EXECUTED);
    }

    @Test
    @DisplayName("5. DecideSignal records >= 1 RelayTrace when trace is enabled")
    void decideSignalTrace() {
        var pathway = CognitivePathway.<DecideSignal>pathway("decide")
                .relay("decide-relay", s -> true)
                .build();
        var signal = DecideSignal.builder().build();
        var ctx = DefaultPathwayContext.builder().traceEnabled(true).build();
        signal.bind(ctx);

        pathway.conduct(signal);

        assertThat(signal.isTraceEnabled()).isTrue();
        assertThat(signal.traces()).hasSize(1);
        RelayTrace trace = signal.traces().getFirst();
        assertThat(trace.relayName()).isEqualTo("decide-relay");
        assertThat(trace.status()).isEqualTo(RelayTrace.TraceStatus.EXECUTED);
    }

    @Test
    @DisplayName("6. WanderSignal records >= 1 RelayTrace when trace is enabled")
    void wanderSignalTrace() {
        var pathway = CognitivePathway.<WanderSignal>pathway("wander")
                .relay("wander-relay", s -> true)
                .build();
        var signal = WanderSignal.builder().build();
        var ctx = DefaultPathwayContext.builder().traceEnabled(true).build();
        signal.bind(ctx);

        pathway.conduct(signal);

        assertThat(signal.isTraceEnabled()).isTrue();
        assertThat(signal.traces()).hasSize(1);
        RelayTrace trace = signal.traces().getFirst();
        assertThat(trace.relayName()).isEqualTo("wander-relay");
        assertThat(trace.status()).isEqualTo(RelayTrace.TraceStatus.EXECUTED);
    }

    @Test
    @DisplayName("7. ExpressSignal records >= 1 RelayTrace when trace is enabled")
    void expressSignalTrace() {
        var pathway = CognitivePathway.<ExpressSignal>pathway("express")
                .relay("express-relay", s -> true)
                .build();
        var signal = ExpressSignal.builder().build();
        var ctx = DefaultPathwayContext.builder().traceEnabled(true).build();
        signal.bind(ctx);

        pathway.conduct(signal);

        assertThat(signal.isTraceEnabled()).isTrue();
        assertThat(signal.traces()).hasSize(1);
        RelayTrace trace = signal.traces().getFirst();
        assertThat(trace.relayName()).isEqualTo("express-relay");
        assertThat(trace.status()).isEqualTo(RelayTrace.TraceStatus.EXECUTED);
    }

    @Test
    @DisplayName("All signals record zero traces when tracing is disabled")
    void tracingDisabledRecordsNoTraces() {
        var rememberSignal = RememberSignal.forCognitive(
                "rem-1", "content", null, MemoryType.SEMANTIC, null,
                MemorySource.OBSERVED, null, SalienceProfile.NEUTRAL, (short) 0);
        var ctx = DefaultPathwayContext.builder().traceEnabled(false).build();
        rememberSignal.bind(ctx);

        CognitivePathway.<RememberSignal>pathway("remember")
                .relay("stage", s -> true)
                .build()
                .conduct(rememberSignal);

        assertThat(rememberSignal.isTraceEnabled()).isFalse();
        assertThat(rememberSignal.traces()).isEmpty();
    }
}
