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
package com.spectrayan.spector.memory.valhalla;

import com.spectrayan.spector.commons.valhalla.ValueClassValidator;
import com.spectrayan.spector.memory.aisme.continuity.IdentityTrajectorySnapshot;
import com.spectrayan.spector.memory.aisme.fegr.EventDensityMetrics;
import com.spectrayan.spector.memory.aisme.importance.CompositeImportanceSignals;
import com.spectrayan.spector.memory.aisme.phi.ConsciousnessContinuityState;
import com.spectrayan.spector.memory.cortex.PartitionSummary;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.BigFiveTraits;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.GraphStats;
import com.spectrayan.spector.memory.neuromod.dopamine.FlashbulbPolicy;
import com.spectrayan.spector.memory.neuromod.neurodivergent.IcnuWeights;
import com.spectrayan.spector.memory.neuromod.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints;
import com.spectrayan.spector.memory.persist.migration.SchemaVersion;
import com.spectrayan.spector.memory.replication.ReplicaApplyEngine;
import com.spectrayan.spector.memory.sync.CrdtMergeStrategy;
import com.spectrayan.spector.memory.synapse.CognitiveScorer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class MemoryValhallaReadinessTest {

    @Test
    @DisplayName("Spector Memory value candidates pass JEP 390 / JEP 401 compliance audit")
    void testMemoryValueCandidates() {
        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(CognitiveResult.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(CognitiveScorer.ScoredRecord.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(CompositeImportanceSignals.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(FlashbulbPolicy.FlashbulbDecision.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(RememberHints.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(LateralEvaluator.LateralMetrics.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(IcnuWeights.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(IdentityTrajectorySnapshot.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(ConsciousnessContinuityState.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(EventDensityMetrics.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(AgentSoul.EmotionalBaseline.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(BigFiveTraits.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(GraphStats.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(PartitionSummary.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(CrdtMergeStrategy.MergedHeader.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(CrdtMergeStrategy.SourceHeader.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(ReplicaApplyEngine.ApplyResult.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(SchemaVersion.class))
                .doesNotThrowAnyException();
    }
}
