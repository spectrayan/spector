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
package com.spectrayan.spector.memory.simulation.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.spacetime.SpacetimeSimulationMode;
import com.spectrayan.spector.core.spacetime.Time2VecProjector;
import com.spectrayan.spector.memory.dream.relay.DreamSignal;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.synapse.scan.CognitiveScoreFusion;
import com.spectrayan.spector.memory.synapse.scan.RecordGates;
import com.spectrayan.spector.memory.wander.relay.WanderSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spacetime Shortlist Seed Selection Relay for generative simulations, mind-wandering (DMN),
 * and REM/NREM dreaming (ADR-0031 Part C).
 *
 * <p>Operates strictly on pre-gathered candidate shortlists (\(K \le 64\)), constructing a deterministic
 * multi-strategy seed union (spatial relevance, in-phase harmonic alignment, anti-phase divergence,
 * and high-mass flashbulbs) with continuous \(\lambda\)-scaled recency and single-pass composite scoring.</p>
 *
 * <h3>Unified Seed Selection Algorithm:</h3>
 * <ol>
 *   <li>Evaluates 8D harmonic basis vector \(\vec{\tau}(t_s)\) at the simulation clock \(t_s\).</li>
 *   <li>Computes inner product \(\psi_i = \langle \vec{\tau}(t_s), \vec{\tau}(t_i) \rangle \in [-1.0, 1.0]\) for each shortlist candidate.</li>
 *   <li>Applies continuous mass-dilated logarithmic recency \(R_\lambda(\Delta t, M_i)\).</li>
 *   <li>Extracts spatial, in-phase (max \(\psi\)), anti-phase (min \(\psi\), if enabled), and flashbulb subsets.</li>
 *   <li>Deduplicates candidates in insertion order into a {@link LinkedHashMap} capped at \(N \le 16\).</li>
 *   <li>Computes single non-branching compound score:
 *       \[ s'_i = s_i + \rho_+ \max(\psi_i, 0) + \rho_- \max(-\psi_i, 0) + \gamma \cdot \mathbf{1}[M_i \ge \texttt{FLASHBULB\_MASS\_FLOOR}] \]
 *   </li>
 * </ol>
 *
 * @since 1.5.0
 */
public final class SpacetimeSeedRelay {

    private static final Logger log = LoggerFactory.getLogger(SpacetimeSeedRelay.class);

    public static final int DEFAULT_N_SPATIAL = 8;
    public static final int DEFAULT_N_HARMONIC = 4;
    public static final int DEFAULT_N_ANTI = 4;
    public static final int DEFAULT_N_FLASH = 2;
    public static final int DEFAULT_UNION_CAP = 16;
    public static final float DEFAULT_GAMMA = 0.20f;

    private SpacetimeSeedRelay() {}

    /**
     * Internal scored seed candidate tracking harmonic metrics.
     */
    public record SeedCandidate(
            CognitiveResult result,
            float psi,
            float cognitiveMass,
            float adjustedScore
    ) {}

    /**
     * Executes the ADR-0031 shortlist multi-seed union and compound scoring.
     *
     * @param candidates         pre-gathered shortlist candidates
     * @param simulationTimeMs   simulation clock \(t_s\)
     * @param queryTau           pre-computed \(\vec{\tau}(t_s)\) basis vector, or null to compute on the fly
     * @param mode               simulation mode preset (WANDER, DREAM_NREM, DREAM_REM, etc.)
     * @param recencyLambda      continuous recency scaling factor \(\lambda\)
     * @param nSpatial           number of spatial candidates to extract
     * @param nHarmonic          number of in-phase harmonic candidates to extract
     * @param nAnti              number of anti-phase harmonic candidates to extract
     * @param nFlash             number of high-mass flashbulb candidates to extract
     * @param unionCap           maximum number of unique seeds to admit in the final union
     * @param flashbulbMassFloor minimum cognitive mass required for flashbulb qualification
     * @param gamma              score boost applied to qualified flashbulb memories
     * @return deduplicated and compound-scored list of seed candidates
     */
    public static List<CognitiveResult> selectSeeds(
            final List<CognitiveResult> candidates,
            final long simulationTimeMs,
            final float[] queryTau,
            final SpacetimeSimulationMode mode,
            final float recencyLambda,
            final int nSpatial,
            final int nHarmonic,
            final int nAnti,
            final int nFlash,
            final int unionCap,
            final float flashbulbMassFloor,
            final float gamma) {

        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        final float[] tauS = (queryTau != null && queryTau.length == Time2VecProjector.DIMENSIONS)
                ? queryTau
                : Time2VecProjector.project(simulationTimeMs);

        final List<SeedCandidate> evaluated = new ArrayList<>(candidates.size());

        for (final CognitiveResult cr : candidates) {
            final long itemTimeMs = cr.timestampMs() > 0L ? cr.timestampMs() : simulationTimeMs;
            final float[] tauI = Time2VecProjector.project(itemTimeMs);
            final float psi = Time2VecProjector.dot(tauS, tauI);

            final float cognitiveMass = CognitiveScoreFusion.computeCognitiveMass(
                    cr.importance(), (byte) 0, 1.0f);

            final float rLambda = CognitiveScoreFusion.computeMassDilatedDecay(
                    itemTimeMs, simulationTimeMs, cognitiveMass, (byte) 0, cr.agentRecallCount(), false, recencyLambda);

            final float baseAdjustedScore = cr.score() * rLambda;
            evaluated.add(new SeedCandidate(cr, psi, cognitiveMass, baseAdjustedScore));
        }

        // 1. Spatial Subset (highest baseAdjustedScore)
        final List<SeedCandidate> spatialSubset = new ArrayList<>(evaluated);
        spatialSubset.sort(Comparator.comparingDouble(SeedCandidate::adjustedScore).reversed());
        final List<SeedCandidate> topSpatial = spatialSubset.subList(0, Math.min(nSpatial, spatialSubset.size()));

        // 2. In-Phase Harmonic Subset (highest psi)
        final List<SeedCandidate> inPhaseSubset = new ArrayList<>(evaluated);
        inPhaseSubset.sort(Comparator.comparingDouble(SeedCandidate::psi).reversed());
        final List<SeedCandidate> topInPhase = inPhaseSubset.subList(0, Math.min(nHarmonic, inPhaseSubset.size()));

        // 3. Anti-Phase Harmonic Subset (most negative psi, if permitted by mode)
        final List<SeedCandidate> topAntiPhase;
        if (mode != null && mode.allowsAntiPhase()) {
            final List<SeedCandidate> antiPhaseSubset = new ArrayList<>(evaluated);
            antiPhaseSubset.sort(Comparator.comparingDouble(SeedCandidate::psi)); // ascending -> lowest / most negative psi
            topAntiPhase = antiPhaseSubset.subList(0, Math.min(nAnti, antiPhaseSubset.size()));
        } else {
            topAntiPhase = Collections.emptyList();
        }

        // 4. Flashbulb Subset (M_i >= flashbulbMassFloor, sorted by cognitive mass)
        final List<SeedCandidate> flashSubset = new ArrayList<>();
        for (final SeedCandidate sc : evaluated) {
            if (sc.cognitiveMass() >= flashbulbMassFloor) {
                flashSubset.add(sc);
            }
        }
        flashSubset.sort(Comparator.comparingDouble(SeedCandidate::cognitiveMass).reversed());
        final List<SeedCandidate> topFlash = flashSubset.subList(0, Math.min(nFlash, flashSubset.size()));

        // 5. Deterministic Insertion-Order Deduplicated Union
        final Map<String, SeedCandidate> unionMap = new LinkedHashMap<>();

        addCandidatesToUnion(unionMap, topSpatial, unionCap);
        addCandidatesToUnion(unionMap, topInPhase, unionCap);
        addCandidatesToUnion(unionMap, topAntiPhase, unionCap);
        addCandidatesToUnion(unionMap, topFlash, unionCap);

        // 6. Compound Score Calculation
        final float rhoPlus = mode != null ? mode.rhoPlus() : 0.35f;
        final float rhoMinus = mode != null ? mode.rhoMinus() : 0.35f;

        final List<CognitiveResult> finalResults = new ArrayList<>(unionMap.size());
        for (final SeedCandidate sc : unionMap.values()) {
            final float psi = sc.psi();
            final float psiPlus = Math.max(0.0f, psi);
            final float psiMinus = Math.max(0.0f, -psi);
            final float flashBoost = (sc.cognitiveMass() >= flashbulbMassFloor) ? gamma : 0.0f;

            final float finalScore = sc.adjustedScore() + (rhoPlus * psiPlus) + (rhoMinus * psiMinus) + flashBoost;
            finalResults.add(sc.result().withScore(finalScore));
        }

        if (log.isDebugEnabled()) {
            log.debug("SPACETIME_SEED trace: t_s={}, mode={}, lambda={}, rho+=({}), rho-=({}), candidate_count={}, selected_seeds={}",
                    simulationTimeMs, mode, recencyLambda, rhoPlus, rhoMinus, candidates.size(), unionMap.keySet());
        }

        return Collections.unmodifiableList(finalResults);
    }

    private static void addCandidatesToUnion(
            final Map<String, SeedCandidate> unionMap,
            final List<SeedCandidate> candidates,
            final int unionCap) {
        for (final SeedCandidate sc : candidates) {
            if (unionMap.size() >= unionCap) {
                break;
            }
            final String id = sc.result().id();
            if (id != null && !unionMap.containsKey(id)) {
                unionMap.put(id, sc);
            }
        }
    }

    /**
     * Wander Pathway Synaptic Relay adapter.
     */
    public static final class WanderSeedRelay implements SynapticRelay<WanderSignal> {
        @Override
        public boolean transmit(final WanderSignal signal) {
            if (signal == null) {
                return true;
            }
            final List<CognitiveResult> rawCandidates = signal.candidateSeeds();
            if (rawCandidates == null || rawCandidates.isEmpty()) {
                return true;
            }

            final List<CognitiveResult> selected = selectSeeds(
                    rawCandidates,
                    signal.simulationTimeMs(),
                    signal.queryTau(),
                    signal.spacetimeMode(),
                    signal.recencyLambda(),
                    DEFAULT_N_SPATIAL,
                    DEFAULT_N_HARMONIC,
                    DEFAULT_N_ANTI,
                    DEFAULT_N_FLASH,
                    DEFAULT_UNION_CAP,
                    RecordGates.FLASHBULB_MASS_FLOOR,
                    DEFAULT_GAMMA
            );

            signal.setCandidateSeeds(selected);
            return true;
        }
    }

    /**
     * Dream Pathway Synaptic Relay adapter.
     */
    public static final class DreamSeedRelay implements SynapticRelay<DreamSignal> {
        @Override
        public boolean transmit(final DreamSignal signal) {
            if (signal == null) {
                return true;
            }
            final List<CognitiveResult> rawCandidates = signal.candidateSeeds();
            if (rawCandidates == null || rawCandidates.isEmpty()) {
                return true;
            }

            final List<CognitiveResult> selected = selectSeeds(
                    rawCandidates,
                    signal.simulationTimeMs(),
                    signal.queryTau(),
                    signal.spacetimeMode(),
                    signal.recencyLambda(),
                    DEFAULT_N_SPATIAL,
                    DEFAULT_N_HARMONIC,
                    DEFAULT_N_ANTI,
                    DEFAULT_N_FLASH,
                    DEFAULT_UNION_CAP,
                    RecordGates.FLASHBULB_MASS_FLOOR,
                    DEFAULT_GAMMA
            );

            signal.setCandidateSeeds(selected);
            return true;
        }
    }
}
