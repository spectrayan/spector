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
package com.spectrayan.spector.memory.synapse;

import com.spectrayan.spector.core.spacetime.Time2VecProjector;
import com.spectrayan.spector.memory.cortex.SemanticMemory;
import com.spectrayan.spector.memory.cortex.StrengthMemory;
import com.spectrayan.spector.memory.kernel.layout.EngramLayout;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.recall.relay.RecallSignal;
import com.spectrayan.spector.memory.pathway.recall.relay.SpacetimeScoringRelay;
import com.spectrayan.spector.memory.synapse.CognitiveScorer.ScoredRecord;
import com.spectrayan.spector.memory.synapse.scan.CognitiveScoreFusion;
import com.spectrayan.spector.memory.synapse.scan.RecordGates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Spacetime Retrieval & Synaptic Relay Fixtures (ADR-0030 v1)")
class SpacetimeScoringFixtureTest {

    private static final int DIMS = 8;
    private static final long ONE_DAY_MS = 86_400_000L;
    private static final long FIVE_YEARS_MS = 5L * 365 * ONE_DAY_MS;

    @Nested
    @DisplayName("Phase 1b: Causal Horizon Gating (Fixture E)")
    class CausalHorizonTests {

        @Test
        @DisplayName("Fixture E: Future memory (t_i > t_q) is hard-dropped when allowFuture=false")
        void futureMemoryDroppedByDefault() {
            final SemanticMemory store = new SemanticMemory(DIMS, 10);
            final EngramLayout layout = new EngramLayout(DIMS);
            final long now = System.currentTimeMillis();

            // Record 0: Normal past memory
            final EncodingHeader pastHeader = new EncodingHeader(
                    now - 60_000L, 0L, 1.0f, 5.0f, 0, (short) 0, (byte) 0, (byte) 0);
            store.append(pastHeader, new byte[layout.quantizedVecBytes()]);

            // Record 1: Future memory (tomorrow)
            final EncodingHeader futureHeader = new EncodingHeader(
                    now + ONE_DAY_MS, 0L, 1.0f, 9.0f, 0, (short) 0, (byte) 0, (byte) 0);
            store.append(futureHeader, new byte[layout.quantizedVecBytes()]);

            final float[] queryVec = new float[DIMS];
            final RecallOptions optsDefault = RecallOptions.builder()
                    .topK(10)
                    .allowFuture(false)
                    .build();

            final List<ScoredRecord> results = CognitiveScorer.score(
                    store.segment(), 2, layout, queryVec, optsDefault, now, 0L, null, null);

            // Future memory MUST be rejected before entering heap
            assertThat(results).hasSize(1);
            assertThat(results.get(0).header().timestampMs()).isEqualTo(pastHeader.timestampMs());

            store.close();
        }

        @Test
        @DisplayName("Fixture E: Future memory (t_i > t_q) is admitted when allowFuture=true (DMN mode)")
        void futureMemoryAdmittedInDmnMode() {
            final SemanticMemory store = new SemanticMemory(DIMS, 10);
            final EngramLayout layout = new EngramLayout(DIMS);
            final long now = System.currentTimeMillis();

            // Record 0: Future memory (tomorrow)
            final EncodingHeader futureHeader = new EncodingHeader(
                    now + ONE_DAY_MS, 0L, 1.0f, 8.0f, 0, (short) 0, (byte) 0, (byte) 0);
            store.append(futureHeader, new byte[layout.quantizedVecBytes()]);

            final float[] queryVec = new float[DIMS];
            final RecallOptions optsDmn = RecallOptions.builder()
                    .topK(10)
                    .allowFuture(true)
                    .build();

            final List<ScoredRecord> results = CognitiveScorer.score(
                    store.segment(), 1, layout, queryVec, optsDmn, now, 0L, null, null);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).header().timestampMs()).isEqualTo(futureHeader.timestampMs());

            store.close();
        }
    }

    @Nested
    @DisplayName("Phase 4 & 6: Flashbulb Cognitive Mass Retention (Fixture A)")
    class CognitiveMassRetentionTests {

        @Test
        @DisplayName("Fixture A: High-mass memory with I < 1.0 is exempted from stale pruning via M >= 0.30")
        void highMassExemptsLowImportanceStaleMemory() {
            final SemanticMemory store = new SemanticMemory(DIMS, 10);
            final EngramLayout layout = new EngramLayout(DIMS);
            final long now = System.currentTimeMillis();

            // Record 0: 5-year-old memory with low base importance (0.8 < 1.0) but high arousal (240) and storage strength (4.0)
            // M = (0.8 / 10) * (1 + 240/128) * (4.0^0.3) = 0.08 * 2.875 * 1.516 = 0.349 >= FLASHBULB_MASS_FLOOR (0.30)
            final float highMass = CognitiveScoreFusion.computeCognitiveMass(0.8f, (byte) 240, 4.0f);
            assertThat(highMass).isGreaterThanOrEqualTo(RecordGates.FLASHBULB_MASS_FLOOR);

            final float lowMass = CognitiveScoreFusion.computeCognitiveMass(0.5f, (byte) 0, 1.0f);
            assertThat(lowMass).isLessThan(RecordGates.FLASHBULB_MASS_FLOOR);

            // Record 0: High-mass memory (I=0.8 < 1.0, A=240, S=4.0) -> Survives Phase 4 via M >= FLASHBULB_MASS_FLOOR exemption
            final byte flagsResolved = EncodingHeaderFields.withMemoryType(
                    EncodingHeaderFields.FLAG_RESOLVED, MemoryType.EPISODIC.ordinal());
            final EncodingHeader highMassHeader = new EncodingHeader(
                    now - FIVE_YEARS_MS, 0L, 1.0f, 0.8f, 0, (short) 0, (byte) 50, flagsResolved, (byte) 240, 4.0f);
            store.append(highMassHeader, new byte[layout.quantizedVecBytes()]);

            // Record 1: Stale & weak control memory (5 years old, I=0.5 < 1.0, A=0, S=1.0, RESOLVED) -> Must be pruned
            final EncodingHeader lowMassHeader = new EncodingHeader(
                    now - FIVE_YEARS_MS, 0L, 1.0f, 0.5f, 0, (short) 0, (byte) 0, flagsResolved, (byte) 0, 1.0f);
            store.append(lowMassHeader, new byte[layout.quantizedVecBytes()]);

            final StrengthMemory strengthStore = StrengthMemory.heap(10, 10, 10);
            strengthStore.initializeDefault(MemoryType.SEMANTIC, 0, 0.8f, 4.0f, 0);
            strengthStore.initializeDefault(MemoryType.SEMANTIC, 1, 0.5f, 1.0f, 0);

            final float[] queryVec = new float[DIMS];
            final RecallOptions opts = RecallOptions.builder()
                    .topK(5)
                    .minImportance(0.1f)
                    .build();

            final List<ScoredRecord> results = CognitiveScorer.score(
                    store.segment(), 2, layout, queryVec, opts, now, 0L, null, null, null, null, strengthStore, MemoryType.SEMANTIC);

            // Only the high-mass memory survives Phase 4 screening despite I < 1.0
            assertThat(results).hasSize(1);
            assertThat(results.get(0).header().timestampMs()).isEqualTo(highMassHeader.timestampMs());
            assertThat(results.get(0).score()).isGreaterThan(0.0f);

            store.close();
        }
    }

    @Nested
    @DisplayName("Phase 6: Continuous Mass-Dilated Log Recency")
    class Phase6ContinuousMassDilationTests {

        @Test
        @DisplayName("Higher cognitive mass significantly dilates time decay over identical elapsed intervals")
        void massDilatesTimeDecay() {
            final long now = 1774900000000L;
            final long monthAgo = now - (30L * ONE_DAY_MS);

            // High mass: M = 4.0
            final float decayHighMass = CognitiveScoreFusion.computeMassDilatedDecay(
                    monthAgo, now, 4.0f, (byte) 50, 0, false);

            // Low mass: M = 0.2
            final float decayLowMass = CognitiveScoreFusion.computeMassDilatedDecay(
                    monthAgo, now, 0.2f, (byte) 0, 0, false);

            // High-mass memory experiences far less temporal degradation
            assertThat(decayHighMass).isGreaterThan(decayLowMass * 1.5f);
        }
    }

    @Nested
    @DisplayName("SpacetimeScoringRelay: Shortlist Harmonic Re-ranking")
    class ShortlistHarmonicRelayTests {

        @Test
        @DisplayName("Candidates aligned with circadian/weekly query harmonics receive positive boost")
        void harmonicAlignmentBoostsScore() {
            final long queryTimeMs = 1774900000000L; // Reference query clock
            final float[] queryTau = Time2VecProjector.project(queryTimeMs);

            final RecallOptions opts = RecallOptions.builder()
                    .enableSpacetime(true)
                    .spacetimeHarmonicWeight(0.20f)
                    .build();

            final RecallSignal signal = RecallSignal.forVectorQuery(new float[DIMS], opts);
            signal.setQueryTimeMs(queryTimeMs);
            signal.setQueryTau(queryTau);

            // Candidate 1: Exactly 7 days ago (100% weekly + circadian harmonic phase match)
            final long timeC1 = queryTimeMs - (7L * ONE_DAY_MS);
            final CognitiveResult c1 = new CognitiveResult(
                    "c1", "7 days ago memory", 0.70f, 5.0f, 7.0f, 0, (byte) 0,
                    MemoryType.EPISODIC, null, new String[0], 0.5f, 0.5f,
                    CognitiveResult.RetrievalMode.STANDARD, null, null,
                    SourceModality.TEXT, Map.of(), (byte) 0, timeC1);

            // Candidate 2: 3.5 days ago (half-week anti-phase)
            final long timeC2 = queryTimeMs - (long) (3.5 * ONE_DAY_MS);
            final CognitiveResult c2 = new CognitiveResult(
                    "c2", "3.5 days ago memory", 0.70f, 5.0f, 3.5f, 0, (byte) 0,
                    MemoryType.EPISODIC, null, new String[0], 0.5f, 0.5f,
                    CognitiveResult.RetrievalMode.STANDARD, null, null,
                    SourceModality.TEXT, Map.of(), (byte) 0, timeC2);

            signal.setCandidates(List.of(c1, c2));

            final SpacetimeScoringRelay relay = new SpacetimeScoringRelay();
            final boolean success = relay.transmit(signal);

            assertThat(success).isTrue();
            final List<CognitiveResult> updated = signal.candidates();
            assertThat(updated).hasSize(2);

            final CognitiveResult updatedC1 = updated.stream().filter(c -> c.id().equals("c1")).findFirst().orElseThrow();
            final CognitiveResult updatedC2 = updated.stream().filter(c -> c.id().equals("c2")).findFirst().orElseThrow();

            assertThat(updatedC1.score()).isGreaterThan(updatedC2.score());
        }

        @Test
        @DisplayName("SpacetimeScoringRelay generates RecallTrace step when trace is present on candidate")
        void harmonicTracingGeneratesPipelineTraceStep() {
            final long queryTimeMs = 1774900000000L;
            final float[] queryTau = Time2VecProjector.project(queryTimeMs);

            final RecallOptions opts = RecallOptions.builder()
                    .enableSpacetime(true)
                    .spacetimeHarmonicWeight(0.15f)
                    .build();

            final RecallSignal signal = RecallSignal.forVectorQuery(new float[DIMS], opts);
            signal.setQueryTimeMs(queryTimeMs);
            signal.setQueryTau(queryTau);

            final com.spectrayan.spector.memory.model.RecallTrace initialTrace =
                    new com.spectrayan.spector.memory.model.RecallTrace("c1", List.of(
                            new com.spectrayan.spector.memory.model.RecallTrace.TraceStep(
                                    "INITIAL", 0.5f, 0.5f, 1, 1, "seed")));

            final CognitiveResult c1 = new CognitiveResult(
                    "c1", "traced memory", 0.50f, 5.0f, 1.0f, 0, (byte) 0,
                    MemoryType.EPISODIC, null, new String[0], 0.5f, 0.5f,
                    CognitiveResult.RetrievalMode.STANDARD, null, initialTrace,
                    SourceModality.TEXT, Map.of(), (byte) 0, queryTimeMs - ONE_DAY_MS);

            signal.setCandidates(List.of(c1));

            final SpacetimeScoringRelay relay = new SpacetimeScoringRelay();
            final boolean success = relay.transmit(signal);

            assertThat(success).isTrue();
            final CognitiveResult result = signal.candidates().get(0);
            assertThat(result.trace()).isNotNull();
            assertThat(result.trace().steps()).hasSize(2);
            assertThat(result.trace().steps().get(1).phaseName()).isEqualTo(RelayNames.SPACETIME_SCORING);
        }
    }
}
