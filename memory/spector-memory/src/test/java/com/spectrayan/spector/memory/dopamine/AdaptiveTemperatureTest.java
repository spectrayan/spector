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
package com.spectrayan.spector.memory.dopamine;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.CognitiveResult.RetrievalMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreBreakdown;
import com.spectrayan.spector.memory.model.TemperatureOptions;
import com.spectrayan.spector.memory.synapse.TemperatureSoftmax;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit test suite for Adaptive Softmax Retrieval Temperature (Issue #504).
 */
@DisplayName("Adaptive Retrieval Temperature Tests")
class AdaptiveTemperatureTest {

    @Nested
    @DisplayName("Effective Temperature Calculation")
    class EffectiveTemperatureCalculationTests {

        @Test
        @DisplayName("Returns base temperature when adaptive temperature is disabled")
        void testDisabledAdaptiveTemperature() {
            RecallOptions options = RecallOptions.builder()
                    .adaptiveTemperature(false)
                    .baseTemperature(1.2f)
                    .build();

            assertThat(options.computeEffectiveTemperature(3.5))
                    .isCloseTo(1.2f, within(1e-5f));
            assertThat(options.computeEffectiveTemperature(-2.0))
                    .isCloseTo(1.2f, within(1e-5f));
        }

        @Test
        @DisplayName("Ignores negative surprise z-scores and keeps base temperature")
        void testNegativeSurpriseZScore() {
            RecallOptions options = RecallOptions.builder()
                    .adaptiveTemperature(true)
                    .baseTemperature(1.0f)
                    .temperatureSurpriseCoefficient(0.15f)
                    .build();

            // max(0, -1.5) = 0 -> T = 1.0 * (1 + 0.15 * 0) = 1.0
            assertThat(options.computeEffectiveTemperature(-1.5))
                    .isCloseTo(1.0f, within(1e-5f));
        }

        @Test
        @DisplayName("Scales temperature proportionally for positive surprise z-scores")
        void testPositiveSurpriseZScore() {
            RecallOptions options = RecallOptions.builder()
                    .adaptiveTemperature(true)
                    .baseTemperature(1.0f)
                    .temperatureSurpriseCoefficient(0.15f)
                    .build();

            // z = 2.0 -> T = 1.0 * (1 + 0.15 * 2.0) = 1.30
            assertThat(options.computeEffectiveTemperature(2.0))
                    .isCloseTo(1.30f, within(1e-5f));

            // z = 4.0 -> T = 1.0 * (1 + 0.15 * 4.0) = 1.60
            assertThat(options.computeEffectiveTemperature(4.0))
                    .isCloseTo(1.60f, within(1e-5f));
        }

        @Test
        @DisplayName("Clamps temperature at minTemperature and maxTemperature bounds")
        void testTemperatureClamping() {
            RecallOptions options = RecallOptions.builder()
                    .adaptiveTemperature(true)
                    .baseTemperature(1.0f)
                    .temperatureSurpriseCoefficient(0.5f)
                    .temperatureBounds(0.2f, 2.5f)
                    .build();

            // Extreme outlier z = 10.0 -> raw T = 1.0 * (1 + 0.5 * 10.0) = 6.0 -> clamped to 2.5
            assertThat(options.computeEffectiveTemperature(10.0))
                    .isCloseTo(2.5f, within(1e-5f));

            // Low base temp with negative z -> clamped to min
            RecallOptions lowOptions = RecallOptions.builder()
                    .adaptiveTemperature(false)
                    .baseTemperature(0.05f)
                    .minTemperature(0.2f)
                    .maxTemperature(3.0f)
                    .build();

            assertThat(lowOptions.computeEffectiveTemperature(0.0))
                    .isCloseTo(0.2f, within(1e-5f));
        }

        @Test
        @DisplayName("Composed TemperatureOptions sub-record accessor works cleanly")
        void testTemperatureOptionsAccessor() {
            RecallOptions options = RecallOptions.builder()
                    .adaptiveTemperature(true)
                    .baseTemperature(1.5f)
                    .temperatureSurpriseCoefficient(0.20f)
                    .temperatureBounds(0.5f, 4.0f)
                    .build();

            TemperatureOptions to = options.temperature();
            assertThat(to.adaptiveTemperature()).isTrue();
            assertThat(to.baseTemperature()).isEqualTo(1.5f);
            assertThat(to.temperatureSurpriseCoefficient()).isEqualTo(0.20f);
            assertThat(to.minTemperature()).isEqualTo(0.5f);
            assertThat(to.maxTemperature()).isEqualTo(4.0f);

            assertThat(to.computeEffective(2.0))
                    .isCloseTo(1.5f * (1.0f + 0.20f * 2.0f), within(1e-5f));
        }
    }

    @Nested
    @DisplayName("SurpriseDetector Query-Side Peek")
    class SurpriseDetectorPeekTests {

        @Test
        @DisplayName("Returns 0.0 z-score during warmup period without mutating count")
        void testWarmupBehavior() {
            SurpriseDetector detector = new SurpriseDetector(20);

            // Feed 5 samples
            for (int i = 0; i < 5; i++) {
                detector.computeImportance(0.5f);
            }
            assertThat(detector.stats().count()).isEqualTo(5);

            // Query peek should return 0.0 and not increment sample count
            double peekZ = detector.querySurpriseZScore(1.8f);
            assertThat(peekZ).isEqualTo(0.0);
            assertThat(detector.stats().count()).isEqualTo(5);
        }

        @Test
        @DisplayName("Computes accurate z-score after warmup without side effects")
        void testPostWarmupPeek() {
            SurpriseDetector detector = new SurpriseDetector(10);

            // Feed 15 identical distances of 1.0f except variance
            for (int i = 0; i < 15; i++) {
                detector.computeImportance(1.0f + (i % 3) * 0.1f);
            }
            long countBefore = detector.stats().count();

            double peekZ = detector.querySurpriseZScore(2.5f);
            assertThat(peekZ).isGreaterThan(1.0);
            assertThat(detector.stats().count()).isEqualTo(countBefore);
        }
    }

    @Nested
    @DisplayName("Temperature Softmax Transformation")
    class TemperatureSoftmaxTests {

        private List<CognitiveResult> createSampleCandidates() {
            List<CognitiveResult> results = new ArrayList<>();
            results.add(createResult("m1", 0.90f));
            results.add(createResult("m2", 0.70f));
            results.add(createResult("m3", 0.50f));
            results.add(createResult("m4", 0.30f));
            return results;
        }

        private CognitiveResult createResult(String id, float score) {
            ScoreBreakdown bd = new ScoreBreakdown(score, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, score);
            return new CognitiveResult(
                    id, "Text for " + id, score, 5.0f, 1.0f,
                    1, (byte) 0, MemoryType.SEMANTIC, MemorySource.OBSERVED,
                    new String[]{"test"}, 1.0f, 1.0f, RetrievalMode.STANDARD, bd, null, null, null
            );
        }

        @Test
        @DisplayName("T = 1.0 leaves candidate scores unchanged (identity)")
        void testIdentityTemperature() {
            List<CognitiveResult> candidates = createSampleCandidates();
            float[] origScores = candidates.stream().map(CognitiveResult::score).mapToDouble(f -> f).collect(
                    () -> new float[candidates.size()],
                    (arr, val) -> arr[resultsIndex(candidates, val)] = (float) val,
                    (a, b) -> {}
            );
            List<Float> original = candidates.stream().map(CognitiveResult::score).toList();

            TemperatureSoftmax.applySoftmaxTemperature(candidates, 1.0f);

            for (int i = 0; i < candidates.size(); i++) {
                assertThat(candidates.get(i).score()).isCloseTo(original.get(i), within(1e-5f));
            }
        }

        private int resultsIndex(List<CognitiveResult> list, double val) {
            for (int i = 0; i < list.size(); i++) {
                if (Math.abs(list.get(i).score() - val) < 1e-6) return i;
            }
            return 0;
        }

        @Test
        @DisplayName("T > 1.0 flattens distribution (reduces top-to-bottom ratio)")
        void testHighTemperatureFlattening() {
            List<CognitiveResult> candidates = createSampleCandidates();
            float originalRatio = candidates.get(0).score() / candidates.get(3).score(); // 0.90 / 0.30 = 3.0

            TemperatureSoftmax.applySoftmaxTemperature(candidates, 3.0f);

            float newRatio = candidates.get(0).score() / candidates.get(3).score();
            // Higher temperature flattens score ratios (makes retrieval broader/softer)
            assertThat(newRatio).isLessThan(originalRatio);
            assertThat(candidates.get(0).score()).isGreaterThan(candidates.get(1).score());
            assertThat(candidates.get(1).score()).isGreaterThan(candidates.get(2).score());
            assertThat(candidates.get(2).score()).isGreaterThan(candidates.get(3).score());
        }

        @Test
        @DisplayName("T < 1.0 sharpens distribution (increases top candidate dominance)")
        void testLowTemperatureSharpening() {
            List<CognitiveResult> candidates = createSampleCandidates();
            float originalRatio = candidates.get(0).score() / candidates.get(3).score(); // 3.0

            TemperatureSoftmax.applySoftmaxTemperature(candidates, 0.3f);

            float newRatio = candidates.get(0).score() / candidates.get(3).score();
            // Lower temperature sharpens score ratios (concentrates probability on top hit)
            assertThat(newRatio).isGreaterThan(originalRatio);
        }

        @Test
        @DisplayName("Handles single item or empty list gracefully")
        void testEdgeCases() {
            List<CognitiveResult> empty = new ArrayList<>();
            TemperatureSoftmax.applySoftmaxTemperature(empty, 2.0f);
            assertThat(empty).isEmpty();

            List<CognitiveResult> single = new ArrayList<>();
            single.add(createResult("s1", 0.75f));
            TemperatureSoftmax.applySoftmaxTemperature(single, 2.0f);
            assertThat(single.get(0).score()).isEqualTo(0.75f);
        }

        @Test
        @DisplayName("Numerical stability: handles large and small scores without NaN or Infinity")
        void testNumericalStability() {
            List<CognitiveResult> largeScores = new ArrayList<>();
            largeScores.add(createResult("l1", 1000.0f));
            largeScores.add(createResult("l2", 950.0f));
            largeScores.add(createResult("l3", 500.0f));

            TemperatureSoftmax.applySoftmaxTemperature(largeScores, 0.1f);

            for (CognitiveResult r : largeScores) {
                assertThat(Float.isNaN(r.score())).isFalse();
                assertThat(Float.isInfinite(r.score())).isFalse();
                assertThat(r.score()).isGreaterThanOrEqualTo(0.0f);
            }
        }
    }
}
