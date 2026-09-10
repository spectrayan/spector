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
 * Pure mathematical kernel for Wixted's power-law forgetting curve, Bahrick's permastore floors,
 * reconsolidation half-life doubling, and amygdala arousal decay resistance (ADR-0033 Domain 2).
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class PowerLawDecayKernel {

    public static final long HOUR_MS = 3_600_000L;
    public static final long DAY_MS = 24 * HOUR_MS;
    public static final long WEEK_MS = 7 * DAY_MS;
    public static final long MONTH_MS = 30 * DAY_MS;
    public static final long YEAR_MS = 365 * DAY_MS;

    private static final double[] MIDPOINT_HOURS = {
            0.5, 3.5, 15.0, 48.0, 120.0, 240.0, 504.0, 1080.0, 2160.0, 4320.0, 8760.0, 17520.0
    };

    public static final float DEFAULT_DECAY_EXPONENT = 0.15f;
    public static final float DEFAULT_PERMASTORE_FLOOR = 0.10f;
    public static final float[] DEFAULT_BUCKETS = computeBuckets(DEFAULT_DECAY_EXPONENT, DEFAULT_PERMASTORE_FLOOR);
    public static final int MAX_BUCKET = DEFAULT_BUCKETS.length - 1;

    /**
     * Precomputed arousal-based decay resistance multipliers.
     * Higher arousal = slower decay (up to 65% slower at extreme arousal).
     */
    public static final float[] AROUSAL_DECAY_MODIFIERS = {
            1.00f,  // arousal 0-63:    neutral   -> no change
            1.15f,  // arousal 64-127:  mild      -> 15% slower decay
            1.35f,  // arousal 128-191: moderate  -> 35% slower decay
            1.65f   // arousal 192-255: extreme   -> 65% slower decay
    };

    private PowerLawDecayKernel() {}

    /**
     * Precomputes discrete logarithmic time bucket decay multipliers.
     *
     * @param exponent decay power-law exponent (typically ~0.15)
     * @param floor    permastore floor (typically ~0.10)
     * @return 12-element normalized decay multiplier array
     */
    public static float[] computeBuckets(final float exponent, final float floor) {
        final float[] buckets = new float[MIDPOINT_HOURS.length];
        final double a = Math.pow(MIDPOINT_HOURS[0], exponent);
        for (int i = 0; i < MIDPOINT_HOURS.length; i++) {
            final double ret = a * Math.pow(MIDPOINT_HOURS[i], -exponent);
            buckets[i] = (float) Math.max(floor, Math.min(1.0, ret));
        }
        buckets[0] = 1.0f;
        return buckets;
    }

    /**
     * Maps memory creation timestamp and query reference time to a discrete logarithmic bucket index (0-11).
     *
     * @param timestampMs creation timestamp (epoch millis)
     * @param nowMs       current query timestamp (epoch millis)
     * @return bucket index in [0, 11]
     */
    public static int ageToBucket(final long timestampMs, final long nowMs) {
        final long ageMs = nowMs - timestampMs;
        if (ageMs < 0) {
            return 0; // future timestamp (clock skew) -> treat as fresh
        }

        if (ageMs < HOUR_MS)          return 0;   // < 1 hour
        if (ageMs < 6 * HOUR_MS)      return 1;   // 1-6 hours
        if (ageMs < DAY_MS)           return 2;   // 6-24 hours
        if (ageMs < 3 * DAY_MS)       return 3;   // 1-3 days
        if (ageMs < WEEK_MS)          return 4;   // 3-7 days
        if (ageMs < 4 * WEEK_MS)      return 5;   // 1-4 weeks
        if (ageMs < 3 * MONTH_MS)     return 6;   // 1-3 months
        if (ageMs < 6 * MONTH_MS)     return 7;   // 3-6 months
        if (ageMs < YEAR_MS)          return 8;   // 6-12 months
        if (ageMs < 2 * YEAR_MS)      return 9;   // 1-2 years
        if (ageMs < 5 * YEAR_MS)      return 10;  // 2-5 years
        return MAX_BUCKET;                        // 5+ years
    }

    /**
     * Adjusts the raw decay bucket for explicit agent reconsolidation (LTP) via bit-shift half-life doubling.
     *
     * @param rawBucket        initial age bucket
     * @param agentRecallCount number of explicit agent retrievals
     * @return adjusted bucket index
     */
    public static int adjustForReconsolidation(final int rawBucket, final int agentRecallCount) {
        final int shift = Math.min(agentRecallCount, 5);
        return rawBucket >> shift;
    }

    /**
     * Adjusts the decay bucket for internal passive auto-recall.
     *
     * @param bucket             current bucket index
     * @param passiveRecallCount number of passive auto-retrievals
     * @return adjusted bucket index clamped at 0
     */
    public static int adjustForAutoRecall(final int bucket, final int passiveRecallCount) {
        final int shift = Math.min(passiveRecallCount / 3, 2);
        return Math.max(0, bucket - shift);
    }

    /**
     * Returns the amygdala arousal-based decay resistance modifier.
     *
     * @param arousal signed byte interpreted as unsigned [0, 255]
     * @return resistance modifier in [1.0, 1.65]
     */
    public static float arousalModifier(final byte arousal) {
        final int unsigned = Byte.toUnsignedInt(arousal);
        final int bucket = Math.min(3, unsigned / 64);
        return AROUSAL_DECAY_MODIFIERS[bucket];
    }

    /**
     * Computes decay multiplier given timestamp, recall count, and bucket array.
     */
    public static float computeDecay(
            final long timestampMs, final long nowMs, final int agentRecallCount, final float[] buckets) {
        final int rawBucket = ageToBucket(timestampMs, nowMs);
        final int adjusted = adjustForReconsolidation(rawBucket, agentRecallCount);
        return buckets[Math.min(adjusted, buckets.length - 1)];
    }

    /**
     * Computes full decay multiplier including amygdala arousal modulation, clamped to [0.0, 1.0].
     */
    public static float computeDecayWithArousal(
            final long timestampMs, final long nowMs, final int agentRecallCount,
            final byte arousal, final float[] buckets) {
        final float baseDecay = computeDecay(timestampMs, nowMs, agentRecallCount, buckets);
        final float modifier = arousalModifier(arousal);
        return Math.min(1.0f, baseDecay * modifier);
    }

    /**
     * Batch decay calculation over structured arrays for scan acceleration (Principle 3).
     */
    public static void computeDecayBatch(
            final long[] timestampsMs, final int[] recallCounts, final byte[] arousals,
            final long nowMs, final float[] buckets, final float[] outDecays, final int count) {
        for (int i = 0; i < count; i++) {
            outDecays[i] = computeDecayWithArousal(timestampsMs[i], nowMs, recallCounts[i], arousals[i], buckets);
        }
    }
}
