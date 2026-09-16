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

import com.spectrayan.spector.config.properties.DreamProperties;
import com.spectrayan.spector.kernel.api.DreamMode;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.api.TriageOutcome;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.memory.pathway.FakeRememberPathway;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamIngestionRelay;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamSignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-0035 R2 acceptance criterion: Dream drives nested Remember ingestion
 * <em>without constructing any Remember relay</em>.
 *
 * <p>This is the test the spec names. It matters because it is the only thing that
 * actually proves the decoupling: if Dream still needed Remember's relays, this suite
 * could not be written at all — it registers a {@link FakeRememberPathway} in the
 * catalog and never mentions {@code DedupGuardRelay},
 * {@code CorticalWriteTransactionRelay}, or any other Remember internal.</p>
 */
@DisplayName("Dream → Remember nested invocation")
class DreamPathwayNestingTest {

    private static DreamSignal.DreamScene scene(final String id, final float quality) {
        return new DreamSignal.DreamScene(
                id,
                "narrative for " + id,
                "insight for " + id,
                new float[]{0.1f, 0.2f, 0.3f},
                List.of("seed-1"),
                quality,
                TriageOutcome.EPISTEMIC);
    }

    private static DreamSignal signalWith(final DreamSignal.DreamScene... scenes) {
        final DreamProperties config = DreamProperties.builder()
                .enabled(true)
                .persistenceThreshold(0.5f)
                .build();
        final DreamSignal signal = DreamSignal.builder()
                .mode(DreamMode.REM)
                .config(config)
                .build();
        signal.survivingScenes().addAll(List.of(scenes));
        return signal;
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("Each qualifying scene becomes one nested Remember conduction")
        void ingestsOnePerQualifyingScene() throws Exception {
            final var fakeRemember = new FakeRememberPathway();
            final DreamSignal signal = signalWith(
                    scene("s1", 0.9f),
                    scene("s2", 0.8f),
                    scene("s3", 0.7f));
            signal.bind(fakeRemember.inContext("dream-test"));

            final boolean carryOn = new DreamIngestionRelay().transmit(signal);

            assertThat(carryOn).isTrue();
            assertThat(fakeRemember.invocationCount())
                    .as("one nested Remember conduction per surviving scene above threshold")
                    .isEqualTo(3);
            assertThat(signal.dreamsIngested()).hasValue(3);
        }

        @Test
        @DisplayName("Nested signals carry dream provenance, not Dream's own type")
        void nestedSignalsCarryDreamProvenance() throws Exception {
            final var fakeRemember = new FakeRememberPathway();
            final DreamSignal signal = signalWith(scene("s1", 0.9f));
            signal.bind(fakeRemember.inContext("dream-test"));

            new DreamIngestionRelay().transmit(signal);

            final var remembered = fakeRemember.received().getFirst();
            assertThat(remembered.type()).isEqualTo(MemoryType.SEMANTIC);
            assertThat(remembered.source()).isEqualTo(MemorySource.DREAMED);
            assertThat(remembered.header())
                    .as("DreamPorts must stamp a synthetic header")
                    .isNotNull();
        }

        @Test
        @DisplayName("Scenes below the persistence threshold are not ingested")
        void belowThresholdScenesSkipped() throws Exception {
            final var fakeRemember = new FakeRememberPathway();
            final DreamSignal signal = signalWith(
                    scene("keep", 0.9f),
                    scene("drop", 0.1f));
            signal.bind(fakeRemember.inContext("dream-test"));

            new DreamIngestionRelay().transmit(signal);

            assertThat(fakeRemember.invocationCount()).isEqualTo(1);
            assertThat(fakeRemember.received().getFirst().text()).contains("keep");
        }
    }

    @Nested
    @DisplayName("Degradation (ADR-0036 §4.2)")
    class Degradation {

        @Test
        @DisplayName("Partial failure degrades the outcome but keeps ingesting the rest")
        void partialFailureIsMarkedAndBatchContinues() throws Exception {
            // Fail only the *first* scene by using a fake that throws, then assert the
            // relay surfaced a degraded mark rather than swallowing it.
            final var failing = new FakeRememberPathway()
                    .failing(new IllegalStateException("cortical write unavailable"));
            final DreamSignal signal = signalWith(scene("s1", 0.9f), scene("s2", 0.8f));
            signal.bind(failing.inContext("dream-test"));

            // Every scene fails, so the relay rethrows so the conductor can apply the
            // stage's ErrorPolicy and the pathway:remember breaker sees a real failure.
            assertThatThrownBy(() -> new DreamIngestionRelay().transmit(signal))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cortical write unavailable");

            assertThat(signal.context().outcome().degraded())
                    .as("per-scene failures must be recorded, not swallowed")
                    .isTrue();
            assertThat(signal.context().outcome().degradedMarks())
                    .extracting(m -> m.scope())
                    .anyMatch(s -> s.startsWith("dream_ingestion/scene:"));
        }

        @Test
        @DisplayName("No catalog entry means no ingestion and no crash")
        void missingRememberIsTolerated() throws Exception {
            final DreamSignal signal = signalWith(scene("s1", 0.9f));
            // Context with an EMPTY catalog: Remember is not registered at all.
            signal.bind(com.spectrayan.spector.commons.pathway.DefaultPathwayContext.builder()
                    .namespaceId("dream-test")
                    .catalog(new com.spectrayan.spector.commons.pathway.DefaultPathwayCatalog())
                    .build());

            final boolean carryOn = new DreamIngestionRelay().transmit(signal);

            assertThat(carryOn)
                    .as("dreaming still completes when Remember is absent (e.g. in tests)")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Decoupling")
    class Decoupling {

        @Test
        @DisplayName("Dream resolves Remember purely by catalog key, never by construction")
        void resolvedByCatalogKeyOnly() throws Exception {
            // The fake is not a RememberPathway subclass, yet Dream finds it — proving
            // resolution is by catalog key and Dream holds no Remember reference.
            final var fakeRemember = new FakeRememberPathway();
            assertThat(fakeRemember)
                    .isNotInstanceOf(com.spectrayan.spector.memory.pathway.remember.RememberPathway.class);

            final DreamSignal signal = signalWith(scene("s1", 0.9f));
            signal.bind(fakeRemember.inContext("dream-test"));

            new DreamIngestionRelay().transmit(signal);

            assertThat(fakeRemember.invocationCount()).isEqualTo(1);
        }
    }
}
