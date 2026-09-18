/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.memory.model;

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
