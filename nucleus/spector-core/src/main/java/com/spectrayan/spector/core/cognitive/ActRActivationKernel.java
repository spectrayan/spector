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
package com.spectrayan.spector.core.cognitive;

/**
 * Pure mathematical kernel for Anderson's ACT-R base-level activation, spaced practice dynamics,
 * and associative fan effect (ADR-0033 Domain 1).
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class ActRActivationKernel {

    private ActRActivationKernel() {}

    /**
     * Computes Anderson's ACT-R base-level activation:
     * <p>{@code B_i = ln(Σ t_j^-d)}, normalized via algebraic sigmoid identity {@code σ(B_i) = sum / (sum + 1)}.</p>
     *
     * @param recallAgesMs  array of past retrieval ages in milliseconds
     * @param decayExponent power-law decay exponent (typically ~0.5 in ACT-R literature)
     * @return normalized activation in [0.0, 1.0], or -1.0f if no recall history exists
     */
    public static float computeBaseLevelActivation(final long[] recallAgesMs, final float decayExponent) {
        if (recallAgesMs == null || recallAgesMs.length == 0) {
            return -1.0f;
        }

        double sum = 0.0;
        int validCount = 0;
        for (final long ageMs : recallAgesMs) {
            if (ageMs < 0) {
                continue;
            }
            final double ageSec = Math.max(1.0, ageMs / 1000.0);
            sum += Math.pow(ageSec, -decayExponent);
            validCount++;
        }

        if (validCount == 0) {
            return -1.0f;
        }

        return (float) (sum / (sum + 1.0));
    }

    /**
     * Computes ACT-R base-level activation from relative-second timestamps in an 8-slot ring buffer
     * using discrete logarithmic bucket lookups (ADR-0028/ADR-0033).
     *
     * <p>Contracts preserved:
     * <ul>
     *   <li>{@code relativeSeconds[i] == 0} indicates an empty/unused slot (skipped).</li>
     *   <li>{@code recallAgeMs <= 0} floors to 1000 ms.</li>
     *   <li>Creation-time initial encoding bucket is added after the ring buffer loop.</li>
     *   <li>Returns {@code -1.0f} sentinel when {@code validSlots == 0}.</li>
     *   <li>Normalized via algebraic identity {@code sum / (sum + 1.0f)}.</li>
     * </ul>
     * </p>
     *
     * @param relativeSeconds ring buffer slots containing relative seconds from creation (0 = empty)
     * @param creationMs      creation timestamp (epoch millis)
     * @param nowMs           reference query timestamp (epoch millis)
     * @param decayBuckets    precomputed 12-element decay bucket table
     * @return normalized activation in [0.0, 1.0], or -1.0f if no recall history exists
     */
    public static float computeBucketActivation(
            final int[] relativeSeconds, final long creationMs, final long nowMs, final float[] decayBuckets) {
        if (relativeSeconds == null || relativeSeconds.length == 0) {
            return -1.0f;
        }

        float sum = 0.0f;
        int validSlots = 0;

        for (final int relSec : relativeSeconds) {
            if (relSec == 0) {
                continue;
            }

            long recallAgeMs = (nowMs - creationMs) - (relSec * 1000L);
            if (recallAgeMs <= 0) {
                recallAgeMs = 1000L;
            }

            final long recallTimestampMs = nowMs - recallAgeMs;
            final int bucket = PowerLawDecayKernel.ageToBucket(recallTimestampMs, nowMs);
            sum += decayBuckets[Math.min(bucket, decayBuckets.length - 1)];
            validSlots++;
        }

        if (validSlots == 0) {
            return -1.0f;
        }

        // Include initial encoding at creation time
        final int encodingBucket = PowerLawDecayKernel.ageToBucket(creationMs, nowMs);
        sum += decayBuckets[Math.min(encodingBucket, decayBuckets.length - 1)];

        // Algebraic identity: σ(ln(sum)) = sum / (sum + 1)
        return sum / (sum + 1.0f);
    }

    /**
     * Batch calculation of ring-buffer ACT-R bucket activations (Principle 3).
     */
    public static void computeBucketActivations(
            final int[][] relativeSeconds, final long[] creationMs, final long nowMs,
            final float[] decayBuckets, final float[] outActivations, final int count) {
        for (int i = 0; i < count; i++) {
            outActivations[i] = computeBucketActivation(relativeSeconds[i], creationMs[i], nowMs, decayBuckets);
        }
    }

    /**
     * Computes the ACT-R semantic fan factor:
     * <p>{@code fanFactor(d) = 1 / sqrt(d)}</p>
     * High-degree concept hubs dilute activation spreading across associations.
     *
     * @param degree concept node degree
     * @return attenuation multiplier in (0.0, 1.0]
     */
    public static float fanFactor(final int degree) {
        if (degree <= 0) {
            return 1.0f;
        }
        return (float) (1.0 / Math.sqrt(degree));
    }
}
