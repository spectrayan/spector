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
package com.spectrayan.spector.kernel.score;

import com.spectrayan.spector.core.cognitive.PowerLawDecayKernel;

/**
 * SIMD-friendly bucket-based temporal decay with reconsolidation and arousal modulation.
 *
 * @deprecated Use {@link PowerLawDecayKernel} in {@code spector-core} instead.
 */
@Deprecated(since = "0.1.0-beta", forRemoval = true)
public final class DecayStrategy {

    private DecayStrategy() {}

    public static final float[] DECAY_BUCKETS = PowerLawDecayKernel.DEFAULT_BUCKETS;

    /** Maximum bucket index. */
    public static final int MAX_BUCKET = PowerLawDecayKernel.MAX_BUCKET;

    public static float[] computeBuckets(float d, float floor) {
        return PowerLawDecayKernel.computeBuckets(d, floor);
    }

    /**
     * Returns a defensive copy of the decay bucket multipliers.
     */
    public static float[] decayBuckets() {
        return DECAY_BUCKETS.clone();
    }

    /**
     * Maps a timestamp to a decay bucket index (0–11).
     */
    public static int ageToBucket(long timestampMs, long nowMs) {
        return PowerLawDecayKernel.ageToBucket(timestampMs, nowMs);
    }

    /**
     * Adjusts the raw decay bucket for reconsolidation (Long-Term Potentiation)
     * using exponential half-life doubling via bit-shift.
     *
     * <p>Each recall effectively halves the memory's perceived age by shifting
     * the bucket index right. This mirrors biological spaced repetition where
     * each successful retrieval doubles the memory's half-life.</p>
     *
     * <table>
     *   <tr><th>Recall Count</th><th>Shift</th><th>Effect</th></tr>
     *   <tr><td>0</td><td>0</td><td>No change</td></tr>
     *   <tr><td>1</td><td>÷2</td><td>bucket 6 → 3</td></tr>
     *   <tr><td>2</td><td>÷4</td><td>bucket 6 → 1</td></tr>
     *   <tr><td>3</td><td>÷8</td><td>bucket 7 → 0</td></tr>
     *   <tr><td>5+</td><td>÷32</td><td>effectively fresh</td></tr>
     * </table>
     *
     * @param rawBucket   original bucket from {@link #ageToBucket}
     * @param agentRecallCount number of times this memory has been recalled
     * @return adjusted bucket index (clamped to 0)
     */
    public static int adjustForReconsolidation(int rawBucket, int agentRecallCount) {
        return PowerLawDecayKernel.adjustForReconsolidation(rawBucket, agentRecallCount);
    }

    public static int adjustForAutoRecall(int bucket, int spectorRecallCount) {
        return PowerLawDecayKernel.adjustForAutoRecall(bucket, spectorRecallCount);
    }

    public static float decay(int bucket) {
        return DECAY_BUCKETS[Math.min(bucket, MAX_BUCKET)];
    }

    public static float computeDecay(long timestampMs, long nowMs, int agentRecallCount) {
        return PowerLawDecayKernel.computeDecay(timestampMs, nowMs, agentRecallCount, DECAY_BUCKETS);
    }

    public static float arousalModifier(byte arousal) {
        return PowerLawDecayKernel.arousalModifier(arousal);
    }

    public static float computeDecayWithArousal(long timestampMs, long nowMs,
                                                int agentRecallCount, byte arousal) {
        return PowerLawDecayKernel.computeDecayWithArousal(timestampMs, nowMs, agentRecallCount, arousal, DECAY_BUCKETS);
    }
}
