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
package com.spectrayan.spector.memory.simulation;

import com.spectrayan.spector.core.spacetime.ExpressTense;
import com.spectrayan.spector.core.spacetime.SpacetimeSimulationMode;
import com.spectrayan.spector.core.spacetime.Time2VecProjector;
import com.spectrayan.spector.memory.ExpressPathway;
import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.SemanticRecordMemory;
import com.spectrayan.spector.memory.express.relay.ExpressReport;
import com.spectrayan.spector.memory.express.relay.ExpressSignal;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.simulation.relay.SpacetimeSeedRelay;
import com.spectrayan.spector.memory.synapse.CognitiveScorer;
import com.spectrayan.spector.memory.synapse.CognitiveScorer.ScoredRecord;
import com.spectrayan.spector.memory.synapse.scan.CognitiveScoreFusion;
import com.spectrayan.spector.memory.synapse.scan.RecordGates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Spacetime Simulation on Wander, Dream, and Express Pathways (ADR-0031)")
class SpacetimeSimulationFixtureTest {

    private static final int DIMS = 8;
    private static final long ONE_DAY_MS = 86_400_000L;

    @Nested
    @DisplayName("Continuous Recency Scaling (λ)")
    class ContinuousLambdaRecencyTests {

        @Test
        @DisplayName("λ = 1.0 matches factual recall recency decay")
        void standardRecallDecay() {
            final long now = System.currentTimeMillis();
            final long oneYearAgo = now - (365L * ONE_DAY_MS);
            final float decayLambda1 = CognitiveScoreFusion.computeMassDilatedDecay(
                    oneYearAgo, now, 0.5f, (byte) 0, 0, false, 1.0f);
            final float decayStandard = CognitiveScoreFusion.computeMassDilatedDecay(
                    oneYearAgo, now, 0.5f, (byte) 0, 0, false);

            assertThat(decayLambda1).isEqualTo(decayStandard);
            assertThat(decayLambda1).isLessThan(0.30f);
        }

        @Test
        @DisplayName("λ = 0.30 flattens decay for Wander and REM dream exploration")
        void flattenedWanderDecay() {
            final long now = System.currentTimeMillis();
            final long oneYearAgo = now - (365L * ONE_DAY_MS);

            final float decayLambda1 = CognitiveScoreFusion.computeMassDilatedDecay(
                    oneYearAgo, now, 0.5f, (byte) 0, 0, false, 1.0f);
            final float decayLambda03 = CognitiveScoreFusion.computeMassDilatedDecay(
                    oneYearAgo, now, 0.5f, (byte) 0, 0, false, 0.30f);

            assertThat(decayLambda03).isGreaterThan(decayLambda1);
            assertThat(decayLambda03).isGreaterThan(0.40f);
        }

        @Test
        @DisplayName("λ = 0.00 removes age decay penalty entirely (timeless prospection)")
        void timelessProspectionDecay() {
            final long now = System.currentTimeMillis();
            final long tenYearsAgo = now - (10L * 365 * ONE_DAY_MS);

            final float decayTimeless = CognitiveScoreFusion.computeMassDilatedDecay(
                    tenYearsAgo, now, 0.1f, (byte) 0, 0, false, 0.00f);

            assertThat(decayTimeless).isEqualTo(1.0f);
        }
    }

    @Nested
    @DisplayName("Spacetime Seed Selection & Anti-Phase Invariance (Fixture W)")
    class FixtureW_WanderAndDreamSeedSelectionTests {

        private CognitiveResult createResult(String id, long timestampMs, float baseScore, float importance) {
            final float ageDays = (float) Math.max(0.0, (System.currentTimeMillis() - timestampMs) / (double) ONE_DAY_MS);
            return new CognitiveResult(
                    id, "Memory " + id, baseScore, importance, ageDays, 0, (byte) 0,
                    MemoryType.EPISODIC, MemorySource.OBSERVED, new String[0],
                    1.0f, 1.0f, CognitiveResult.RetrievalMode.STANDARD, null, null,
                    SourceModality.TEXT, Collections.emptyMap(), (byte) 0, timestampMs
            );
        }

        @Test
        @DisplayName("Fixture W: Anti-phase candidates (negative ψ) are selected in WANDER and DREAM_REM, excluded in DREAM_NREM")
        void antiPhaseCandidatesSelectedInWanderAndRem() {
            final long simulationTime = 1_700_000_000_000L; // Fixed epoch reference
            final float[] queryTau = Time2VecProjector.project(simulationTime);

            // Candidate 0: Exactly in-phase (Δt = 0) -> ψ = +1.0
            final CognitiveResult inPhase = createResult("in-phase-0", simulationTime, 0.8f, 5.0f);

            // Candidate 1: Exact anti-phase shift (half-day + half-hour offset producing negative harmonic overlap)
            final long antiPhaseTime = simulationTime - (12L * 3600_000L + 1800_000L);
            final CognitiveResult antiPhase = createResult("anti-phase-0", antiPhaseTime, 0.7f, 5.0f);

            final float[] tauAnti = Time2VecProjector.project(antiPhaseTime);
            final float psiAnti = Time2VecProjector.dot(queryTau, tauAnti);
            assertThat(psiAnti).isLessThan(0.0f); // Assert strictly negative harmonic overlap

            // Candidate 2: Standard spatial candidate
            final CognitiveResult spatial = createResult("spatial-0", simulationTime - 3600_000L, 0.95f, 5.0f);

            // Candidate 3: High-mass flashbulb memory (I=4.0 -> M = 0.40 >= 0.30)
            final CognitiveResult flashbulb = createResult("flashbulb-0", simulationTime - (500L * ONE_DAY_MS), 0.5f, 4.0f);

            final List<CognitiveResult> candidates = List.of(inPhase, antiPhase, spatial, flashbulb);

            // 1. WANDER mode: allows anti-phase
            final List<CognitiveResult> wanderSeeds = SpacetimeSeedRelay.selectSeeds(
                    candidates, simulationTime, queryTau, SpacetimeSimulationMode.WANDER, 0.30f,
                    4, 2, 2, 2, 16, RecordGates.FLASHBULB_MASS_FLOOR, 0.20f);

            final List<String> wanderSeedIds = wanderSeeds.stream().map(CognitiveResult::id).toList();
            assertThat(wanderSeedIds).contains("in-phase-0", "anti-phase-0", "spatial-0", "flashbulb-0");

            // 2. DREAM_REM mode: allows anti-phase
            final List<CognitiveResult> remSeeds = SpacetimeSeedRelay.selectSeeds(
                    candidates, simulationTime, queryTau, SpacetimeSimulationMode.DREAM_REM, 0.30f,
                    4, 2, 2, 2, 16, RecordGates.FLASHBULB_MASS_FLOOR, 0.20f);

            final List<String> remSeedIds = remSeeds.stream().map(CognitiveResult::id).toList();
            assertThat(remSeedIds).contains("anti-phase-0");

            // 3. DREAM_NREM mode: disables anti-phase (anti-phase candidate excluded if not in spatial top-N)
            final List<CognitiveResult> nremSeeds = SpacetimeSeedRelay.selectSeeds(
                    List.of(inPhase, antiPhase), simulationTime, queryTau, SpacetimeSimulationMode.DREAM_NREM, 1.00f,
                    1, 1, 1, 1, 16, RecordGates.FLASHBULB_MASS_FLOOR, 0.20f);

            final List<String> nremSeedIds = nremSeeds.stream().map(CognitiveResult::id).toList();
            assertThat(nremSeedIds).contains("in-phase-0");
            assertThat(nremSeedIds).doesNotContain("anti-phase-0");
        }
    }

    @Nested
    @DisplayName("Dream Provenance & Future Causal Isolation (Fixture D)")
    class FixtureD_DreamProvenanceAndCausalGatingTests {

        @Test
        @DisplayName("Fixture D: Dream memory with future timestamp (t_s > now) is dropped by default recall, admitted when allowFuture=true")
        void syntheticDreamRowDroppedByDefaultRecall() {
            final SemanticRecordMemory store = new SemanticRecordMemory(DIMS, 10);
            final CognitiveRecordLayout layout = new CognitiveRecordLayout(DIMS);
            final long now = System.currentTimeMillis();

            // Record 0: Factual waking memory
            final CognitiveHeader wakingHeader = new CognitiveHeader(
                    now - 3600_000L, 0L, 1.0f, 5.0f, 0, (short) 0, (byte) 0, (byte) 0);
            store.append(wakingHeader, new byte[layout.quantizedVecBytes()]);

            // Record 1: Synthetic future dream memory (t_s = now + 1 day, FLAG_DREAMED | FLAG_SIMULATED)
            final byte dreamConsolidationFlags = (byte) (SynapticHeaderConstants.FLAG_DREAMED | SynapticHeaderConstants.FLAG_SIMULATED);
            final CognitiveHeader dreamHeader = new CognitiveHeader(
                    now + ONE_DAY_MS, 0L, 1.0f, 6.0f, 0, (short) 0, (byte) 0, (byte) 0,
                    (byte) 0, 1.0f, (byte) 0, (byte) 0, (byte) 0, (short) 0, 0.0f, dreamConsolidationFlags);
            store.append(dreamHeader, new byte[layout.quantizedVecBytes()]);

            final float[] queryVec = new float[DIMS];

            // 1. Default Waking Recall (allowFuture = false) -> Drops Record 1 (synthetic future dream)
            final RecallOptions standardOptions = RecallOptions.builder()
                    .topK(10)
                    .allowFuture(false)
                    .build();

            final List<ScoredRecord> standardResults = CognitiveScorer.score(
                    store.segment(), 2, layout, queryVec, standardOptions, now, 0L, null, null);

            assertThat(standardResults).hasSize(1);
            assertThat(standardResults.get(0).header().timestampMs()).isEqualTo(wakingHeader.timestampMs());

            // 2. Simulation / Prospective Recall (allowFuture = true) -> Admits Record 1
            final RecallOptions simOptions = RecallOptions.builder()
                    .topK(10)
                    .allowFuture(true)
                    .build();

            final List<ScoredRecord> simResults = CognitiveScorer.score(
                    store.segment(), 2, layout, queryVec, simOptions, now, 0L, null, null);

            assertThat(simResults).hasSize(2);

            store.close();
        }
    }

    @Nested
    @DisplayName("Express Tense Filtering (Fixture X)")
    class FixtureX_ExpressTenseFilteringTests {

        private CognitiveResult createResultWithFlags(String id, byte consolidationFlags, long timestampMs) {
            return new CognitiveResult(
                    id, "Memory " + id, 0.9f, 5.0f, 0.1f, 0, (byte) 0,
                    MemoryType.EPISODIC, MemorySource.OBSERVED, new String[0],
                    1.0f, 1.0f, CognitiveResult.RetrievalMode.STANDARD, null, null,
                    SourceModality.TEXT, Collections.emptyMap(), consolidationFlags, timestampMs
            );
        }

        @Test
        @DisplayName("Fixture X: ExpressPathway with FACT tense filters out dreamed/simulated memories; SIM tense admits them")
        void expressTenseFiltering() {
            final ExpressPathway pathway = ExpressPathway.builder().build();
            final long now = System.currentTimeMillis();

            final CognitiveResult factMemory = createResultWithFlags("fact-1", (byte) 0, now - 1000L);
            final CognitiveResult dreamMemory = createResultWithFlags("dream-1", (byte) (SynapticHeaderConstants.FLAG_DREAMED | SynapticHeaderConstants.FLAG_SIMULATED), now - 500L);
            final CognitiveResult futureSimMemory = createResultWithFlags("future-sim-1", SynapticHeaderConstants.FLAG_SIMULATED, now + 10_000L);

            final List<CognitiveResult> allCandidates = List.of(factMemory, dreamMemory, futureSimMemory);
            final AgentSoul testSoul = AgentSoul.builder().id("soul-default").build();

            // 1. Express under FACT tense
            final ExpressSignal factSignal = new ExpressSignal.Builder()
                    .queryText("What is the status of the database?")
                    .candidates(allCandidates)
                    .expressTense(ExpressTense.FACT)
                    .simulationTimeMs(now)
                    .interoceptiveState(InteroceptiveState.NEUTRAL)
                    .soulContext(testSoul)
                    .build();

            final ExpressReport factReport = pathway.express(factSignal);
            assertThat(factReport.contextPack().groundedMemories()).hasSize(1);
            assertThat(factReport.contextPack().groundedMemories().get(0).id()).isEqualTo("fact-1");
            assertThat(factReport.internalMonologue()).doesNotContain("SIMULATION");

            // 2. Express under SIM tense
            final ExpressSignal simSignal = new ExpressSignal.Builder()
                    .queryText("Simulate future database load scenario")
                    .candidates(allCandidates)
                    .expressTense(ExpressTense.SIM)
                    .simulationTimeMs(now + 20_000L)
                    .interoceptiveState(InteroceptiveState.NEUTRAL)
                    .soulContext(testSoul)
                    .build();

            final ExpressReport simReport = pathway.express(simSignal);
            assertThat(simReport.contextPack().groundedMemories()).hasSize(3);
            assertThat(simReport.internalMonologue()).contains("SIMULATION / COUNTERFACTUAL");
        }
    }
}
