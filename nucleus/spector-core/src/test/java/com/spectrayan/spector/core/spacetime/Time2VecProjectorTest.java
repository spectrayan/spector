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
package com.spectrayan.spector.core.spacetime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Time2VecProjector Mathematical Properties (ADR-0030 v1)")
class Time2VecProjectorTest {

    private static final float EPSILON = 1e-5f;

    @Nested
    @DisplayName("Unit Norm Properties")
    class UnitNormTests {

        @Test
        @DisplayName("Strict Unit Norm: ‖τ(t)‖₂ == 1.0 across 10,000 random timestamps")
        void testUnitNormProperty() {
            final Random rng = new Random(42);
            final long now = System.currentTimeMillis();
            final long fiftyYearsMs = 50L * 365 * 86_400_000L;

            for (int i = 0; i < 10_000; i++) {
                final long t = now + (rng.nextLong() % fiftyYearsMs);
                final float[] tau = Time2VecProjector.project(t);

                assertThat(tau).hasSize(Time2VecProjector.DIMENSIONS);

                float normSq = 0.0f;
                for (float v : tau) {
                    normSq += v * v;
                }
                assertThat(normSq).isCloseTo(1.0f, within(EPSILON));
            }
        }

        @Test
        @DisplayName("Self inner product ⟨τ(t), τ(t)⟩ ≡ 1.0")
        void testSelfDotProduct() {
            final long now = 1774900000000L;
            final float[] tau = Time2VecProjector.project(now);
            final float dot = Time2VecProjector.dot(tau, tau);

            assertThat(dot).isCloseTo(1.0f, within(EPSILON));
        }
    }

    @Nested
    @DisplayName("Stationarity & Periodicity Properties")
    class StationarityTests {

        @Test
        @DisplayName("Stationary Kernel: ⟨τ(t), τ(t + Δ)⟩ depends only on Δ, not t")
        void testStationaryProperty() {
            final long delta = 3_600_000L; // 1 hour elapsed
            final long t1 = 1700000000000L;
            final long t2 = 1750000000000L;
            final long t3 = 1800000000000L;

            final float dot1 = Time2VecProjector.dot(
                    Time2VecProjector.project(t1), Time2VecProjector.project(t1 + delta));
            final float dot2 = Time2VecProjector.dot(
                    Time2VecProjector.project(t2), Time2VecProjector.project(t2 + delta));
            final float dot3 = Time2VecProjector.dot(
                    Time2VecProjector.project(t3), Time2VecProjector.project(t3 + delta));

            assertThat(dot1).isCloseTo(dot2, within(EPSILON));
            assertThat(dot2).isCloseTo(dot3, within(EPSILON));
        }

        @Test
        @DisplayName("Harmonic periodicity: Peak correlation at exact harmonic octave boundaries")
        void testCircadianHarmonicCorrelation() {
            final long t0 = 1774900000000L;
            final long oneDayMs = 86_400_000L;

            final float[] tau0 = Time2VecProjector.project(t0);
            final float[] tauOneDay = Time2VecProjector.project(t0 + oneDayMs);

            // After exactly 24 hours (1 day), 1-hour and 1-day harmonics have exact phase match (2 of 4 octaves)
            final float dot = Time2VecProjector.dot(tau0, tauOneDay);
            assertThat(dot).isGreaterThan(0.4f);
        }
    }

    @Nested
    @DisplayName("Vector Fusion Properties")
    class FusionTests {

        @Test
        @DisplayName("Fused vector concatenation preserves dimension (D + 8)")
        void testFusedVectorDimension() {
            final float[] semanticVec = new float[]{0.6f, 0.8f}; // D = 2
            final float[] tau = Time2VecProjector.project(System.currentTimeMillis());

            final float[] fused = Time2VecProjector.fuse(semanticVec, tau, 0.1f);
            assertThat(fused).hasSize(2 + 8);
        }

        @Test
        @DisplayName("Fused vector norm is preserved when spatial and temporal vectors are unit-norm")
        void testFusedNormPreservation() {
            final float[] unitSpatial = new float[]{0.6f, 0.8f}; // norm = 1.0
            final float[] tau = Time2VecProjector.project(System.currentTimeMillis());

            final float[] fused = Time2VecProjector.fuse(unitSpatial, tau, 0.25f);

            float normSq = 0.0f;
            for (float v : fused) {
                normSq += v * v;
            }
            assertThat(normSq).isCloseTo(1.0f, within(EPSILON));
        }
    }
}
