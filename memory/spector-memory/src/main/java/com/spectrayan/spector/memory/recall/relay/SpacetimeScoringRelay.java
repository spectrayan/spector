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
package com.spectrayan.spector.memory.recall.relay;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.spacetime.Time2VecProjector;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.pathway.RelayNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Applies shortlist harmonic Spacetime re-ranking (ADR-0030 v1) to recall candidates.
 *
 * <p>Computes the 8-dimensional harmonic inner product {@code ⟨τ(t_q), τ(t_i)⟩} between the query's
 * temporal position and each candidate memory's timestamp, adjusting the final score by {@code ρ * dot}.
 * Operates strictly on the candidate shortlist (K ≤ 200) to keep latency under 35 µs.</p>
 */
public final class SpacetimeScoringRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(SpacetimeScoringRelay.class);
    private static final double MS_PER_DAY = 86_400_000.0;

    @Override
    public boolean transmit(final RecallSignal signal) {
        if (!signal.options().enableSpacetime()) {
            return true;
        }

        final float[] queryTau = signal.queryTau();
        if (queryTau == null) {
            log.debug("Spacetime re-ranking skipped: queryTau is null");
            return true;
        }

        final List<CognitiveResult> candidates = signal.candidates();
        if (candidates.isEmpty()) {
            return true;
        }

        final float rho = signal.options().spacetimeHarmonicWeight();
        final long queryTimeMs = signal.queryTimeMs() > 0 ? signal.queryTimeMs() : signal.timestampMs();

        final List<CognitiveResult> updated = new ArrayList<>(candidates.size());

        for (final CognitiveResult candidate : candidates) {
            // Direct candidate timestamp lookup (fallback to ageDays reconstruction only if timestampMs is unset)
            final long candidateTimeMs = candidate.timestampMs() > 0
                    ? candidate.timestampMs()
                    : queryTimeMs - (long) (candidate.ageDays() * MS_PER_DAY);
            final float[] candidateTau = Time2VecProjector.project(candidateTimeMs);
            final float harmonicAlignment = Time2VecProjector.dot(queryTau, candidateTau);

            final float harmonicDelta = rho * harmonicAlignment;
            final float newScore = candidate.score() + harmonicDelta;

            if (candidate.trace() != null) {
                final java.util.List<com.spectrayan.spector.memory.model.RecallTrace.TraceStep> steps =
                        new ArrayList<>(candidate.trace().steps());
                steps.add(new com.spectrayan.spector.memory.model.RecallTrace.TraceStep(
                        RelayNames.SPACETIME_SCORING,
                        candidate.score(),
                        newScore,
                        candidates.size(),
                        candidates.size(),
                        String.format("harmonicAlignment=%.4f, rho=%.2f, delta=%+.4f", harmonicAlignment, rho, harmonicDelta)));
                final com.spectrayan.spector.memory.model.RecallTrace updatedTrace =
                        new com.spectrayan.spector.memory.model.RecallTrace(
                                candidate.id(), java.util.Collections.unmodifiableList(steps));
                updated.add(candidate.withScoreAndTrace(newScore, updatedTrace));
            } else {
                updated.add(candidate.withScore(newScore));
            }
        }

        // Re-sort candidates by adjusted score descending
        updated.sort(Comparator.comparingDouble(CognitiveResult::score).reversed());
        signal.setCandidates(updated);

        log.debug("Spacetime harmonic re-ranking applied to {} candidates (rho={})", updated.size(), rho);
        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.SPACETIME_SCORING;
    }
}
