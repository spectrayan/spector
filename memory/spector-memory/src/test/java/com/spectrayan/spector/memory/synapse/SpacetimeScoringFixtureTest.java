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
import com.spectrayan.spector.memory.cortex.SemanticRecordMemory;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;
import com.spectrayan.spector.memory.recall.relay.SpacetimeScoringRelay;
import com.spectrayan.spector.memory.synapse.CognitiveScorer.ScoredRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

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
            final SemanticRecordMemory store = new SemanticRecordMemory(DIMS, 10);
            final CognitiveRecordLayout layout = new CognitiveRecordLayout(DIMS);
            final long now = System.currentTimeMillis();

            // Record 0: Normal past memory
            final CognitiveHeader pastHeader = new CognitiveHeader(
                    now - 60_000L, 0L, 1.0f, 5.0f, 0, (short) 0, (byte) 0, (byte) 0);
            store.append(pastHeader, new byte[layout.quantizedVecBytes()]);

            // Record 1: Future memory (tomorrow)
            final CognitiveHeader futureHeader = new CognitiveHeader(
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
            final SemanticRecordMemory store = new SemanticRecordMemory(DIMS, 10);
            final CognitiveRecordLayout layout = new CognitiveRecordLayout(DIMS);
            final long now = System.currentTimeMillis();

            // Record 0: Future memory (tomorrow)
            final CognitiveHeader futureHeader = new CognitiveHeader(
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
        @DisplayName("Fixture A: High-mass flashbulb memory (5y old) is protected from stale pruning")
        void highMassFlashbulbMemoryRetained() {
            final SemanticRecordMemory store = new SemanticRecordMemory(DIMS, 10);
            final CognitiveRecordLayout layout = new CognitiveRecordLayout(DIMS);
            final long now = System.currentTimeMillis();

            // Record 0: 5-year-old memory with high importance (9.0) and high storage strength
            final byte flags = SynapticHeaderConstants.withMemoryType((byte) 0, MemoryType.EPISODIC.ordinal());
            final CognitiveHeader flashbulb = new CognitiveHeader(
                    now - FIVE_YEARS_MS, 0L, 1.0f, 9.0f, 5, (short) 0, (byte) 50, flags, (byte) 100, 4.0f);
            store.append(flashbulb, new byte[layout.quantizedVecBytes()]);

            final float[] queryVec = new float[DIMS];
            final RecallOptions opts = RecallOptions.builder()
                    .topK(5)
                    .build();

            final List<ScoredRecord> results = CognitiveScorer.score(
                    store.segment(), 1, layout, queryVec, opts, now, 0L, null, null);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).score()).isGreaterThan(0.0f);

            store.close();
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
            final float ageDays1 = 7.0f;
            final CognitiveResult c1 = new CognitiveResult(
                    "c1", "7 days ago memory", 0.70f, 5.0f, ageDays1, 0, (byte) 0,
                    MemoryType.EPISODIC, null, new String[0], 0.5f, 0.5f);

            // Candidate 2: 3.5 days ago (half-week anti-phase)
            final float ageDays2 = 3.5f;
            final CognitiveResult c2 = new CognitiveResult(
                    "c2", "3.5 days ago memory", 0.70f, 5.0f, ageDays2, 0, (byte) 0,
                    MemoryType.EPISODIC, null, new String[0], 0.5f, 0.5f);

            signal.setCandidates(List.of(c1, c2));

            final SpacetimeScoringRelay relay = new SpacetimeScoringRelay();
            final boolean success = relay.transmit(signal);

            assertThat(success).isTrue();
            final List<CognitiveResult> updated = signal.candidates();
            assertThat(updated).hasSize(2);

            // c1 should have received a higher harmonic alignment boost than c2
            final CognitiveResult updatedC1 = updated.stream().filter(c -> c.id().equals("c1")).findFirst().orElseThrow();
            final CognitiveResult updatedC2 = updated.stream().filter(c -> c.id().equals("c2")).findFirst().orElseThrow();

            assertThat(updatedC1.score()).isGreaterThan(updatedC2.score());
        }
    }
}
