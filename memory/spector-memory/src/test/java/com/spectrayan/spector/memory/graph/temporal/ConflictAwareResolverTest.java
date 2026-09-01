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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.spectrayan.spector.memory.model.ConflictActionPolicy;
import com.spectrayan.spector.memory.model.EvidenceDistribution;
import com.spectrayan.spector.memory.model.FactHistory.FactSnapshot;

/**
 * Unit tests for {@link ConflictAwareResolver} and {@link EvidenceDistribution} resolution.
 */
class ConflictAwareResolverTest {

    @Test
    void resolve_resolvesHighestConfidenceFact() {
        ConflictAwareResolver resolver = new ConflictAwareResolver();
        TemporalFact f1 = new TemporalFact(1, 10, 20, 30, 0, (short) 0, 100, 200, 150, 0.80f, 0, (byte) 0);
        TemporalFact f2 = new TemporalFact(2, 10, 20, 31, 0, (short) 0, 100, 200, 160, 0.95f, 0, (byte) 0);

        TemporalFact winner = resolver.resolve(List.of(f1, f2));
        assertThat(winner.factId()).isEqualTo(2);
        assertThat(winner.confidence()).isEqualTo(0.95f);
    }

    @Test
    void resolveDistribution_recommendsAskClarifyingQuestion_onTightUnresolvedConflict() {
        FactSnapshot snap1 = new FactSnapshot(10, "Austin", 100, Long.MAX_VALUE, 1000, 0.85f, -1);
        FactSnapshot snap2 = new FactSnapshot(11, "Dallas", 100, Long.MAX_VALUE, 1001, 0.82f, -1);

        EvidenceDistribution dist = ConflictAwareResolver.resolveDistribution("HQ", "location", List.of(snap1, snap2));
        assertThat(dist.consensusWinner()).isEqualTo(snap1);
        assertThat(dist.competingHypotheses()).containsExactly(snap2);
        assertThat(dist.recommendedPolicy()).isEqualTo(ConflictActionPolicy.ASK_CLARIFYING_QUESTION);
        assertThat(dist.hasContradictions()).isTrue();
    }

    @Test
    void resolveDistribution_recommendsPresentAlternatives_onSupersededFacts() {
        FactSnapshot active = new FactSnapshot(20, "Director", 200, Long.MAX_VALUE, 2000, 0.95f, -1);
        FactSnapshot old = new FactSnapshot(19, "Manager", 100, 200, 1000, 0.90f, 20);

        EvidenceDistribution dist = ConflictAwareResolver.resolveDistribution("Alice", "title", List.of(active, old));
        assertThat(dist.consensusWinner()).isEqualTo(active);
        assertThat(dist.competingHypotheses()).containsExactly(old);
        assertThat(dist.recommendedPolicy()).isEqualTo(ConflictActionPolicy.PRESENT_ALTERNATIVES);
    }

    @Test
    void resolveDistribution_recommendsAbstain_whenAllConfidencesLow() {
        FactSnapshot snap1 = new FactSnapshot(1, "Claim A", 100, Long.MAX_VALUE, 1000, 0.20f, -1);
        FactSnapshot snap2 = new FactSnapshot(2, "Claim B", 100, Long.MAX_VALUE, 1001, 0.25f, -1);

        EvidenceDistribution dist = ConflictAwareResolver.resolveDistribution("Topic", "claim", List.of(snap1, snap2));
        assertThat(dist.recommendedPolicy()).isEqualTo(ConflictActionPolicy.ABSTAIN);
    }
}
