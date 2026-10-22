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
package com.spectrayan.spector.memory.neuromod.dopamine;

import com.spectrayan.spector.core.cognitive.DopaminergicSurpriseKernel;
import com.spectrayan.spector.core.similarity.EuclideanDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Adaptive surprise detection engine — automatically assigns importance at ingestion time.
 *
 * <h3>Biological Analog: Dopamine Prediction Error Signaling</h3>
 * <p>The brain is a prediction engine. If you eat a normal breakfast, you forget it in
 * an hour. If the toaster catches fire, a dopamine spike sears the event into your
 * brain forever. The brain scales memory strength based on <em>Prediction Error</em> —
 * the gap between what was expected and what actually happened.</p>
 *
 * <h3>Why Not Fixed L2 Thresholds?</h3>
 * <p>Fixed thresholds (e.g., {@code L2 < 0.1 = boring}) are embedding-model-dependent.
 * {@code nomic-embed-text} (768-dim) produces very different L2 ranges than
 * {@code all-MiniLM-L6-v2} (384-dim). Z-score normalization adapts to any model
 * automatically.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>This class is thread-safe. The internal {@link WelfordStats} uses {@link java.util.concurrent.locks.ReentrantLock}
 * updates, and reads are volatile.</p>
 */
public final class SurpriseDetector {

    private static final Logger log = LoggerFactory.getLogger(SurpriseDetector.class);

    private final WelfordStats stats;

    /** Default warmup sample count before adaptive z-score importance activates. */
    public static final int DEFAULT_WARMUP_SAMPLES = 20;

    /** Minimum samples required before z-score-based importance kicks in. */
    private final int warmupSamples;

    /**
     * Importance of a memory with no neighbour at all: novelty 1.0 on the 0–10 scale.
     * An empty store has no distance to learn from, so the sample is not recorded.
     */
    public static final float FULL_NOVELTY_IMPORTANCE = DopaminergicSurpriseKernel.MAX_IMPORTANCE;

    /**
     * Prior for L2 distances between L2-normalised embeddings (mean 1, sd 0.25).
     * Warm-up blends this prior with the distances seen so far, weighted by how many
     * samples are still missing, and caps the z-score at the flashbulb boundary.
     */
    static final double PRIOR_MEAN = 1.0;
    static final double PRIOR_SD = 0.25;
    static final double WARMUP_Z_CAP = DopaminergicSurpriseKernel.DEFAULT_FLASHBULB_THRESHOLD;

    /** Recent ingested vectors kept so episodic writes (no slab vector) still count. */
    static final int RECENT_VECTORS = 256;

    private final ReentrantLock recentLock = new ReentrantLock();
    private final float[][] recent = new float[RECENT_VECTORS][];
    private int recentSize;
    private int recentNext;

    /**
     * Creates a new surprise detector.
     *
     * @param warmupSamples minimum observations before adaptive importance activates (default: 20)
     */
    public SurpriseDetector(int warmupSamples) {
        this.stats = new WelfordStats();
        this.warmupSamples = warmupSamples;
    }

    /**
     * Creates a surprise detector with default warmup (20 samples).
     */
    public SurpriseDetector() {
        this(DEFAULT_WARMUP_SAMPLES);
    }

    /**
     * Computes the surprise score for a new memory based on its distance to existing content.
     *
     * <p>Call this during ingestion: compute the L2 distance from the new vector to
     * the nearest existing centroid or cluster center, then pass that distance here.</p>
     *
     * @param distanceToNearest L2 distance from new vector to nearest existing memory/centroid.
     *                          A non-finite value means the reference set is empty.
     * @return importance value (about 0.05 = mundane, 10.0 = fully novel or extreme surprise)
     */
    public float computeImportance(float distanceToNearest) {
        Scored scored = score(distanceToNearest, true);
        if (scored.importance() >= 5.0f) {
            log.debug("Dopamine spike! z-score={}, importance={}", scored.z(), scored.importance());
        }
        return scored.importance();
    }

    /**
     * Same mapping as {@link #computeImportance} without recording the distance.
     *
     * @param distanceToNearest L2 distance, or a non-finite value when nothing is stored
     * @return importance in [{@link DopaminergicSurpriseKernel#MIN_IMPORTANCE}, {@link #FULL_NOVELTY_IMPORTANCE}]
     */
    public float peekImportance(float distanceToNearest) {
        return score(distanceToNearest, false).importance();
    }

    /**
     * Importance and the z-score that produced it. A warm update records the distance first,
     * matching the historical outlier behaviour; a cold update scores against the prior first.
     *
     * @param distanceToNearest L2 distance, or a non-finite value when the reference set is empty
     * @param update            whether to record the distance
     * @return the paired score
     */
    public Scored score(float distanceToNearest, boolean update) {
        if (isEmptyReference(distanceToNearest)) {
            return new Scored(FULL_NOVELTY_IMPORTANCE, 0.0);
        }
        if (update && stats.count() >= warmupSamples) {
            stats.update(distanceToNearest);
            double z = stats.zScore(distanceToNearest);
            return new Scored(zScoreToImportance(z), z);
        }
        double z = blendedZ(distanceToNearest);
        if (update) {
            stats.update(distanceToNearest);
        }
        return new Scored(zScoreToImportance(z), z);
    }

    /**
     * Paired novelty importance and z-score.
     *
     * @param importance importance in [0.05, 10]
     * @param z          z-score used to produce it (0 when the store is empty)
     */
    public record Scored(float importance, double z) {}

    /**
     * Z-score used for importance. During warm-up the running mean and variance are blended
     * with the normalised-embedding prior, and the result is capped at the flashbulb boundary
     * so a cold store cannot pin a flashbulb. Once warm, this is the plain Welford z-score.
     *
     * @param distanceToNearest L2 distance
     * @return z-score, or 0 when the reference set is empty
     */
    public double blendedZ(float distanceToNearest) {
        if (isEmptyReference(distanceToNearest)) {
            return 0.0;
        }
        long n = stats.count();
        if (n >= warmupSamples) {
            return stats.zScore(distanceToNearest);
        }
        double missing = warmupSamples - n;
        double sampleMean = n == 0 ? 0.0 : stats.mean();
        double sampleSd = n < 2 ? 0.0 : stats.stddev();
        double total = n + missing;
        double mean = (n * sampleMean + missing * PRIOR_MEAN) / total;
        double var = (n * sampleSd * sampleSd + missing * PRIOR_SD * PRIOR_SD) / total;
        double sd = Math.sqrt(Math.max(var, 1e-12));
        double z = (distanceToNearest - mean) / sd;
        if (!Double.isFinite(z)) {
            return 0.0;
        }
        return Math.min(z, WARMUP_Z_CAP);
    }

    /**
     * A missing neighbour. {@link Float#MAX_VALUE} is what an empty slab scan returns.
     *
     * @param distance candidate nearest distance
     * @return {@code true} when there is nothing to compare against
     */
    public static boolean isEmptyReference(float distance) {
        return !Float.isFinite(distance) || distance < 0f || distance >= Float.MAX_VALUE / 2f;
    }

    /**
     * Remembers an ingested embedding for later nearest-neighbour checks.
     *
     * @param vector L2-normalised embedding; ignored when {@code null} or empty
     */
    public void observe(float[] vector) {
        if (vector == null || vector.length == 0) {
            return;
        }
        float[] copy = Arrays.copyOf(vector, vector.length);
        recentLock.lock();
        try {
            recent[recentNext] = copy;
            recentNext = (recentNext + 1) % RECENT_VECTORS;
            if (recentSize < RECENT_VECTORS) {
                recentSize++;
            }
        } finally {
            recentLock.unlock();
        }
    }

    /**
     * L2 distance to the closest vector passed to {@link #observe}.
     *
     * @param query candidate embedding
     * @return nearest distance, or {@link Float#POSITIVE_INFINITY} when none were observed
     */
    public float nearestObserved(float[] query) {
        if (query == null || query.length == 0) {
            return Float.POSITIVE_INFINITY;
        }
        recentLock.lock();
        try {
            if (recentSize == 0) {
                return Float.POSITIVE_INFINITY;
            }
            float best = Float.POSITIVE_INFINITY;
            int start = recentSize < RECENT_VECTORS ? 0 : recentNext;
            int length = query.length;
            for (int i = 0; i < recentSize; i++) {
                float[] seen = recent[(start + i) % RECENT_VECTORS];
                if (seen == null || seen.length != length) {
                    continue;
                }
                float dist = EuclideanDistance.compute(query, seen);
                if (dist < best) {
                    best = dist;
                }
            }
            return best;
        } finally {
            recentLock.unlock();
        }
    }

    /**
     * Estimates the surprise z-score for a query without updating the running distribution.
     *
     * <p>Used during recall to dynamically adapt retrieval temperature based on
     * prediction error. If the detector has not seen enough samples (&lt; warmupSamples),
     * returns 0.0 (neutral baseline).</p>
     *
     * @param distanceToNearest L2 distance from query vector to nearest memory/centroid
     * @return surprise z-score (0.0 if not warmed up)
     */
    public double querySurpriseZScore(float distanceToNearest) {
        if (!stats.isWarm(warmupSamples)) {
            return 0.0;
        }
        return stats.zScore(distanceToNearest);
    }

    /**
     * Maps a z-score to an importance value using a continuous sigmoid.
     *
     * <p>Produces a smooth, unique importance value for each memory instead of
     * the previous 5-level step function that collapsed 95% of memories to
     * the same value. The sigmoid is centered at z=1.0 (moderate novelty
     * threshold) with steepness 1.2, mapping to the [0.05, 10.0] range.</p>
     *
     * <pre>
     *   z ≪ 0  → near 0.05 (very similar to known memories)
     *   z ≈ 1  → ~5.0 (midpoint — moderate novelty)
     *   z ≫ 3  → near 10.0 (extreme outlier — dopamine spike!)
     * </pre>
     */
    public static float zScoreToImportance(double zScore) {
        return DopaminergicSurpriseKernel.zScoreToImportance(zScore);
    }

    /**
     * Returns the warmup samples threshold required before adaptive scoring activates.
     */
    public int warmupSamples() {
        return warmupSamples;
    }

    /**
     * Returns the underlying statistics for introspection.
     */
    public WelfordStats stats() {
        return stats;
    }

    // ── V2: Dual Surprise Signal (Spatial + Temporal) ──

    private final WelfordStats temporalStats = new WelfordStats();
    private final java.util.concurrent.ConcurrentHashMap<Long, Long> lastSeenByTags =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Computes dual surprise: spatial novelty + temporal recurrence.
     *
     * <p>Spatial surprise measures how far a new vector is from known clusters.
     * Temporal surprise measures how long since we saw something with similar tags —
     * a recurrence after a long gap is itself surprising (e.g., "the database crashed
     * again" is semantically familiar but temporally novel).</p>
     *
     * @param distanceToNearest L2 distance from new vector to nearest existing memory
     * @param synapticTags      Bloom filter tags of the new memory
     * @param spatialWeight     weight for spatial surprise (default: 0.6)
     * @param temporalWeight    weight for temporal surprise (default: 0.4)
     * @return importance value (0.1 to 10.0)
     */
    public float computeDualImportance(float distanceToNearest, long synapticTags,
                                        float spatialWeight, float temporalWeight) {
        // Spatial surprise
        stats.update(distanceToNearest);
        double spatialZ = !stats.isWarm(warmupSamples) ? 0.0 : stats.zScore(distanceToNearest);

        // Temporal surprise: time since last memory with overlapping tags
        long nowMs = System.currentTimeMillis();
        Long lastSeen = lastSeenByTags.put(synapticTags, nowMs);
        double temporalZ = 0.0;

        if (lastSeen != null) {
            float hoursSinceLast = (nowMs - lastSeen) / (1000f * 3600f);
            temporalStats.update(hoursSinceLast);
            if (temporalStats.isWarm(warmupSamples)) {
                temporalZ = temporalStats.zScore(hoursSinceLast);
            }
        }

        double combinedZ = spatialWeight * spatialZ + temporalWeight * temporalZ;
        float importance = zScoreToImportance(combinedZ);

        if (importance >= 5.0f) {
            log.debug("Dual dopamine spike! spatialZ={}, temporalZ={}, combined={}, importance={}",
                    spatialZ, temporalZ, combinedZ, importance);
        }

        return importance;
    }

    /**
     * Convenience: dual surprise with default weights (0.6 spatial, 0.4 temporal).
     */
    public float computeDualImportance(float distanceToNearest, long synapticTags) {
        return computeDualImportance(distanceToNearest, synapticTags, 0.6f, 0.4f);
    }

    /**
     * Resets the detector, clearing all learned baseline statistics.
     */
    public void reset() {
        stats.reset();
        temporalStats.reset();
        lastSeenByTags.clear();
        recentLock.lock();
        try {
            Arrays.fill(recent, null);
            recentSize = 0;
            recentNext = 0;
        } finally {
            recentLock.unlock();
        }
    }
}
