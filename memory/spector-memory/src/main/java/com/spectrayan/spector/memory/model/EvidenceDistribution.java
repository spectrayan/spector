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
package com.spectrayan.spector.memory.model;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import java.util.List;
import java.util.Objects;

/**
 * Structured multi-evidence distribution capturing competing hypotheses and action policies (ADR-0008).
 */
public record EvidenceDistribution(
        String subject,
        String predicate,
        FactHistory.FactSnapshot consensusWinner,
        List<FactHistory.FactSnapshot> competingHypotheses,
        float epistemicEntropy,
        ConflictActionPolicy recommendedPolicy,
        String rationale
) {
    public EvidenceDistribution {
        Objects.requireNonNull(subject, "subject cannot be null");
        Objects.requireNonNull(predicate, "predicate cannot be null");
        competingHypotheses = competingHypotheses != null ? List.copyOf(competingHypotheses) : List.of();
        recommendedPolicy = recommendedPolicy != null ? recommendedPolicy : ConflictActionPolicy.ACCEPT_WINNER;
        rationale = rationale != null ? rationale : "";
    }

    /**
     * True if there are active competing hypotheses.
     */
    public boolean hasContradictions() {
        return !competingHypotheses.isEmpty();
    }
}
