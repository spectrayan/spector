package com.spectrayan.spector.memory.habituation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Session-level result diversity penalty to prevent recall fixation.
 *
 * <h3>Biological Analog: Sensory Habituation</h3>
 * <p>Repeated exposure to the same stimulus decreases neural response. You stop
 * hearing the clock ticking after a few minutes. The brain deprioritizes repetitive
 * input to make room for novel information.</p>
 *
 * <h3>Anti-Filter-Bubble Mechanism</h3>
 * <p>Tracks how many times each memory has been returned in the current session.
 * Applies a diminishing multiplier to frequently-returned memories, forcing the
 * agent to consider alternative information.</p>
 *
 * <pre>
 *   1st return → 1.0x (no penalty)
 *   5th return → 0.5x
 *   10th return → 0.33x
 *   20th return → 0.2x
 * </pre>
 *
 * <h3>Thread Safety</h3>
 * <p>Fully concurrent via {@link ConcurrentHashMap} + {@link AtomicInteger}.</p>
 */
public final class HabituationPenalty {

    /** Habituation decay rate. Higher = faster habituation. */
    private final float decayRate;

    /** Per-memory return counts for this session. */
    private final ConcurrentHashMap<String, AtomicInteger> returnCounts = new ConcurrentHashMap<>();

    /**
     * Creates a habituation penalty calculator.
     *
     * @param decayRate habituation strength (default: 0.2, higher = faster habituation)
     */
    public HabituationPenalty(float decayRate) {
        this.decayRate = decayRate;
    }

    /**
     * Creates a habituation penalty with default rate (0.2).
     */
    public HabituationPenalty() {
        this(0.2f);
    }

    /**
     * Records that a memory was returned in a recall result and computes the
     * habituation multiplier.
     *
     * @param memoryId the memory that was returned
     * @return habituation multiplier (1.0 = first time, decreasing for repeats)
     */
    public float recordAndComputePenalty(String memoryId) {
        int timesReturned = returnCounts
                .computeIfAbsent(memoryId, k -> new AtomicInteger(0))
                .incrementAndGet();
        return computePenalty(timesReturned);
    }

    /**
     * Computes the habituation penalty without recording a return.
     *
     * @param memoryId the memory to check
     * @return current habituation multiplier
     */
    public float currentPenalty(String memoryId) {
        AtomicInteger count = returnCounts.get(memoryId);
        if (count == null) return 1.0f;
        return computePenalty(count.get());
    }

    /**
     * Computes the penalty for a given return count.
     * Formula: 1.0 / (1.0 + timesReturned * decayRate)
     */
    private float computePenalty(int timesReturned) {
        return 1.0f / (1.0f + (timesReturned - 1) * decayRate);
    }

    /**
     * Returns the number of unique memories tracked.
     */
    public int trackedCount() {
        return returnCounts.size();
    }

    /**
     * Clears all habituation data (typically at session end).
     */
    public void clear() {
        returnCounts.clear();
    }

    /**
     * Batch penalty computation — records all IDs and returns their penalties.
     *
     * <p>Minimizes ConcurrentHashMap contention by processing all results
     * in a tight loop. Particularly effective when called from a single
     * recall thread (no cross-thread CHM contention).</p>
     *
     * @param memoryIds array of memory IDs to record
     * @return array of habituation multipliers (1.0 = first time, decreasing for repeats)
     */
    public float[] recordAndComputeBatch(String[] memoryIds) {
        float[] penalties = new float[memoryIds.length];
        for (int i = 0; i < memoryIds.length; i++) {
            penalties[i] = recordAndComputePenalty(memoryIds[i]);
        }
        return penalties;
    }
}
