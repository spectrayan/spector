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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

/**
 * High-performance harmonic basis projector implementing Bochner's Theorem for temporal vector search.
 *
 * <p>Maps an epoch millisecond timestamp into a stationary 8-dimensional unit-norm harmonic Fourier basis
 * vector across 4 non-overlapping octave period bands (1 hour, 1 day, 7 days, 365 days):</p>
 *
 * <pre>
 *   τ(t) = (1 / √n_P) ⨁ [cos(2π t / P_k), sin(2π t / P_k)] ∈ ℝ^8
 * </pre>
 *
 * <h3>Key Properties (ADR-0030 v1)</h3>
 * <ul>
 *   <li><b>Strict Unit Norm</b>: {@code ‖τ(t)‖₂ ≡ 1.0} identically for all timestamps without runtime normalization.</li>
 *   <li><b>Stationary Kernel</b>: {@code ⟨τ(t_q), τ(t_i)⟩ = ψ(t_q - t_i)} depends solely on elapsed time difference.</li>
 *   <li><b>Exact Zero Peak</b>: {@code ⟨τ(t), τ(t)⟩ ≡ 1.0}.</li>
 *   <li><b>Numerical Stability</b>: Time is evaluated in double-precision days since Unix epoch, preventing 64-bit float precision loss.</li>
 * </ul>
 */
public final class Time2VecProjector {

    /** Number of periodic harmonic pairs. */
    public static final int PERIOD_PAIRS = 4;

    /** Total dimensionality of the harmonic basis vector (2 * PERIOD_PAIRS). */
    public static final int DIMENSIONS = 8;

    /** Scale factor (1 / √4 = 0.5f) ensuring unit vector norm. */
    public static final float NORM_SCALE = 0.5f;

    /** Number of milliseconds in one standard solar day. */
    private static final double MS_PER_DAY = 86_400_000.0;

    /** Harmonic periods expressed in standard day units: 1 hour, 1 day, 7 days, 365 days. */
    private static final double[] PERIODS_DAYS = {
            1.0 / 24.0,  // 1 hour
            1.0,         // 1 day (circadian)
            7.0,         // 7 days (weekly)
            365.0        // 365 days (annual)
    };

    /** Pre-calculated angular frequencies ω_k = 2π / P_k. */
    private static final double[] OMEGAS = new double[PERIOD_PAIRS];

    static {
        for (int k = 0; k < PERIOD_PAIRS; k++) {
            OMEGAS[k] = (2.0 * Math.PI) / PERIODS_DAYS[k];
        }
    }

    private Time2VecProjector() {
        // utility class
    }

    /**
     * Projects an epoch millisecond timestamp into the 8-dimensional unit-norm harmonic basis vector.
     *
     * @param timestampMs epoch timestamp in milliseconds
     * @return 8-element float array representing τ(t) with strict ‖τ(t)‖₂ = 1.0
     */
    public static float[] project(final long timestampMs) {
        final float[] tau = new float[DIMENSIONS];
        projectInto(timestampMs, tau, 0);
        return tau;
    }

    /**
     * Projects an epoch millisecond timestamp directly into an existing float array.
     *
     * @param timestampMs epoch timestamp in milliseconds
     * @param target      target float array
     * @param offset      starting index in target array
     */
    public static void projectInto(final long timestampMs, final float[] target, final int offset) {
        if (target == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "Target array cannot be null");
        }
        if (offset < 0 || offset + DIMENSIONS > target.length) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Target array cannot accommodate " + DIMENSIONS + " elements at offset " + offset);
        }

        final double tDays = (double) timestampMs / MS_PER_DAY;

        for (int k = 0; k < PERIOD_PAIRS; k++) {
            final double angle = OMEGAS[k] * tDays;
            target[offset + 2 * k] = (float) (NORM_SCALE * Math.cos(angle));
            target[offset + 2 * k + 1] = (float) (NORM_SCALE * Math.sin(angle));
        }
    }

    /**
     * Computes the harmonic alignment inner product ⟨τ_q, τ_i⟩ between two 8-dimensional harmonic basis vectors.
     *
     * @param tauA first harmonic vector
     * @param tauB second harmonic vector
     * @return inner product in [-1.0, 1.0] (1.0 = identical periodic phase)
     */
    public static float dot(final float[] tauA, final float[] tauB) {
        if (tauA == null || tauB == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "Harmonic vectors cannot be null");
        }
        if (tauA.length < DIMENSIONS || tauB.length < DIMENSIONS) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Harmonic vectors must contain at least " + DIMENSIONS + " elements");
        }

        return tauA[0] * tauB[0] + tauA[1] * tauB[1]
             + tauA[2] * tauB[2] + tauA[3] * tauB[3]
             + tauA[4] * tauB[4] + tauA[5] * tauB[5]
             + tauA[6] * tauB[6] + tauA[7] * tauB[7];
    }

    /**
     * Fuses a normalized semantic vector with a temporal harmonic vector into a unified (D + 8)-dimensional embedding.
     *
     * @param vector   semantic embedding vector (D dimensions)
     * @param tau      harmonic temporal vector (8 dimensions)
     * @param betaTime temporal weight in [0.0, 1.0]
     * @return concatenated fused vector of length (D + 8)
     */
    public static float[] fuse(final float[] vector, final float[] tau, final float betaTime) {
        if (vector == null || tau == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "Input vectors cannot be null");
        }
        final float clampedBeta = Math.max(0.0f, Math.min(1.0f, betaTime));
        final float spatialScale = (float) Math.sqrt(1.0f - clampedBeta);
        final float temporalScale = (float) Math.sqrt(clampedBeta);

        final float[] fused = new float[vector.length + DIMENSIONS];
        for (int i = 0; i < vector.length; i++) {
            fused[i] = vector[i] * spatialScale;
        }
        for (int k = 0; k < DIMENSIONS; k++) {
            fused[vector.length + k] = tau[k] * temporalScale;
        }
        return fused;
    }
}
