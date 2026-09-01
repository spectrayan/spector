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
package com.spectrayan.spector.memory.graph.temporal;

import java.util.Comparator;
import java.util.List;

/**
 * A TANGLE-style conflict-aware contradiction resolver.
 * 
 * This resolver selects the highest-confidence fact as the primary answer.
 * On a confidence tie, it falls back to the highest txTime (most recent).
 * On a full tie, it falls back to the highest factId for deterministic resolution.
 * 
 * Multi-evidence recall is achieved through SpectorMemory.factHistory() which returns ALL versions.
 * Named after the TANGLE benchmark philosophy of preserving conflicting evidence rather than forcing lossy resolution.
 */
public final class ConflictAwareResolver implements ContradictionResolver {

    private static final Comparator<TemporalFact> COMPARATOR = Comparator
            .<TemporalFact>comparingDouble(TemporalFact::confidence)
            .thenComparingLong(TemporalFact::txTime)
            .thenComparingInt(TemporalFact::factId);

    @Override
    public TemporalFact resolve(List<TemporalFact> conflicting) {
        return conflicting.stream()
                .max(COMPARATOR)
                .orElseThrow();
    }

    /**
     * Resolves a collection of fact snapshots into a structured EvidenceDistribution with action policy.
     *
     * @param subject   the subject entity name
     * @param predicate the relationship predicate
     * @param snapshots the candidate fact snapshots
     * @return the structured evidence distribution
     */
    public static com.spectrayan.spector.memory.model.EvidenceDistribution resolveDistribution(
            String subject, String predicate, List<com.spectrayan.spector.memory.model.FactHistory.FactSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return new com.spectrayan.spector.memory.model.EvidenceDistribution(
                    subject, predicate, null, List.of(), 0.0f,
                    com.spectrayan.spector.memory.model.ConflictActionPolicy.ABSTAIN,
                    "No evidence candidates available."
            );
        }

        if (snapshots.size() == 1) {
            var single = snapshots.get(0);
            var policy = single.confidence() < 0.30f
                    ? com.spectrayan.spector.memory.model.ConflictActionPolicy.ABSTAIN
                    : com.spectrayan.spector.memory.model.ConflictActionPolicy.ACCEPT_WINNER;
            return new com.spectrayan.spector.memory.model.EvidenceDistribution(
                    subject, predicate, single, List.of(), 0.0f, policy,
                    "Single evidence hypothesis."
            );
        }

        // Sort by confidence desc, then txTime desc
        var sorted = snapshots.stream()
                .sorted(Comparator.<com.spectrayan.spector.memory.model.FactHistory.FactSnapshot>comparingDouble(
                        com.spectrayan.spector.memory.model.FactHistory.FactSnapshot::confidence)
                        .thenComparingLong(com.spectrayan.spector.memory.model.FactHistory.FactSnapshot::txTime).reversed())
                .toList();

        var winner = sorted.get(0);
        var runnerUp = sorted.get(1);
        var competing = sorted.subList(1, sorted.size());

        float delta = winner.confidence() - runnerUp.confidence();
        float entropy = (float) (1.0 - Math.min(1.0, Math.max(0.0, delta)));

        com.spectrayan.spector.memory.model.ConflictActionPolicy policy;
        String rationale;

        if (winner.confidence() < 0.30f && runnerUp.confidence() < 0.30f) {
            policy = com.spectrayan.spector.memory.model.ConflictActionPolicy.ABSTAIN;
            rationale = "All evidence confidences below minimum threshold (0.30).";
        } else if (winner.supersededByFactId() < 0 && runnerUp.supersededByFactId() < 0 && delta < 0.15f) {
            policy = com.spectrayan.spector.memory.model.ConflictActionPolicy.ASK_CLARIFYING_QUESTION;
            rationale = String.format("Conflicting coexisting hypotheses share high confidence spread (delta_conf = %.2f < 0.15).", delta);
        } else if (runnerUp.supersededByFactId() > 0 || (winner.validFrom() != runnerUp.validFrom())) {
            policy = com.spectrayan.spector.memory.model.ConflictActionPolicy.PRESENT_ALTERNATIVES;
            rationale = "Temporal supersession detected across distinct validity intervals.";
        } else {
            policy = com.spectrayan.spector.memory.model.ConflictActionPolicy.ACCEPT_WINNER;
            rationale = String.format("Clear winner with confidence margin (delta_conf = %.2f).", delta);
        }

        return new com.spectrayan.spector.memory.model.EvidenceDistribution(
                subject, predicate, winner, competing, entropy, policy, rationale
        );
    }
}
