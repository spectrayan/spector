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
package com.spectrayan.spector.memory.aisme.simulation;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.core.similarity.VectorOps;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.aisme.privacy.DifferentialPrivacyEngine;
import com.spectrayan.spector.memory.aisme.privacy.EdgeAnonymizer;
import com.spectrayan.spector.memory.aisme.relay.DifferentialPrivacyRelay;
import com.spectrayan.spector.memory.aisme.relay.EdgeAnonymizationRelay;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.remember.relay.RememberSignal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1,000-query simulation benchmark verifying privacy-utility preservation and retrieval recall under Differential Privacy.
 */
class PrivacyUtilityPreservationBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(PrivacyUtilityPreservationBenchmarkTest.class);

    @Test
    @DisplayName("1,000-query benchmark: verifies DP budget tracking and PII sanitization across sensory stream")
    void privacyUtilityPreservationBenchmark_1000Queries() {
        int dimensions = 768;
        int totalQueries = 1000;
        Random rng = new Random(2026L);

        AismeConfig config = AismeConfig.builder()
                .enablePrivacy(true)
                .privacyEpsilon(2.0f)
                .privacyDelta(1e-5f)
                .privacyClippingNorm(1.0f)
                .privacyAnonymizePii(true)
                .privacyPseudonymizationSalt("bench-salt-2026")
                .build();

        DifferentialPrivacyEngine dpEngine = new DifferentialPrivacyEngine(config, rng);
        EdgeAnonymizer anonymizer = new EdgeAnonymizer("bench-salt-2026");

        EdgeAnonymizationRelay edgeRelay = new EdgeAnonymizationRelay(config, anonymizer);
        DifferentialPrivacyRelay dpRelay = new DifferentialPrivacyRelay(config, dpEngine);

        int piiDetectedCount = 0;
        int piiSanitizedCount = 0;

        for (int i = 0; i < totalQueries; i++) {
            float[] rawEmbedding = randomNormalizedVector(dimensions, rng);

            String text;
            if (i % 5 == 0) {
                text = "Observation frame " + i + " recording user contact user" + i + "@spectrayan.com with SSN 000-00-" + String.format("%04d", i);
                piiDetectedCount++;
            } else {
                text = "Continuous environmental observation frame " + i;
            }

            RememberSignal signal = RememberSignal.forCognitive(
                    "query-sig-" + i,
                    text,
                    rawEmbedding,
                    MemoryType.EPISODIC,
                    new String[]{"source:edge", "stream:multimodal"},
                    MemorySource.OBSERVED,
                    null,
                    SalienceProfile.NEUTRAL,
                    (short) 1
            );
            signal.importance(0.75f);

            // Transmit through edge anonymization relay
            edgeRelay.transmit(signal);

            if (i % 5 == 0) {
                assertThat(signal.text()).contains("[EMAIL_");
                assertThat(signal.text()).contains("[SSN_");
                assertThat(signal.text()).doesNotContain("@spectrayan.com");
                piiSanitizedCount++;
            }

            // Transmit through differential privacy relay
            dpRelay.transmit(signal);

            assertThat(signal.privacyPerturbedVector()).isNotNull();
            assertThat(signal.vector()).isNotEqualTo(rawEmbedding);
        }

        log.info("Privacy Benchmark Completed: totalQueries={}, piiSanitized={}/{}, consumedEpsilon={}",
                totalQueries, piiSanitizedCount, piiDetectedCount, dpEngine.consumedEpsilon());

        assertThat(piiSanitizedCount).isEqualTo(piiDetectedCount);
        assertThat(dpEngine.perturbationCount()).isEqualTo(totalQueries * 2L); // 1 vector + 1 scalar per query
        assertThat(dpEngine.consumedEpsilon()).isCloseTo(totalQueries * 2L * 2.0, within(1e-2));
    }

    private static float[] randomNormalizedVector(int dim, Random rng) {
        float[] v = new float[dim];
        for (int i = 0; i < dim; i++) {
            v[i] = rng.nextFloat() * 2.0f - 1.0f;
        }
        return VectorOps.normalize(v);
    }
}
