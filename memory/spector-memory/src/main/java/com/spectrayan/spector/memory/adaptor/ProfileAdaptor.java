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
package com.spectrayan.spector.memory.adaptor;

import com.spectrayan.spector.memory.model.CognitiveProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Contextual bandit that learns the optimal {@link CognitiveProfile} per tag context.
 *
 * <h3>Biological Analog: Basal Ganglia Contextual Bandit</h3>
 * <p>The basal ganglia select motor programs (actions) based on contextual cues
 * from the cortex. When an action produces a reward, dopamine reinforces the
 * context→action mapping. Over time, the basal ganglia learn which action to
 * select in each context without explicit instruction — a biological
 * multi-armed bandit.</p>
 *
 * <p>This class implements the same principle for cognitive profiles. Each
 * unique tag context (a set of synaptic tags) maps to a set of candidate
 * profiles. Reinforcement signals update the exponential moving average (EMA)
 * of each profile's success rate in that context. The {@link #suggest} method
 * uses ε-greedy selection: 90% exploit (best EMA), 10% explore (random).</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Reads use {@link ConcurrentHashMap} lock-free access. Writes are
 * serialized by a {@link ReentrantLock}. {@link RunningStats} is immutable
 * (copy-on-write), so readers never see torn state.</p>
 *
 * <h3>Persistence</h3>
 * <p>Bandit statistics are persisted inside the CoActivationTracker's COAX v2
 * binary format. Use {@link #statsSnapshot()} to export and
 * {@link #loadStats(Map)} to import.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   var adaptor = new ProfileAdaptor(CognitiveProfile.BALANCED);
 *
 *   // After a successful recall with DEBUGGING profile in "database,timeout" context:
 *   adaptor.recordOutcome(CognitiveProfile.DEBUGGING,
 *       new String[]{"database", "timeout"}, true);
 *
 *   // Next recall in the same context — suggest the best profile:
 *   CognitiveProfile suggested = adaptor.suggest("database", "timeout");
 *   // → DEBUGGING (if enough positive signals accumulated)
 * }</pre>
 *
 * @see RunningStats
 * @see CognitiveProfile
 */
public final class ProfileAdaptor {

    private static final Logger log = LoggerFactory.getLogger(ProfileAdaptor.class);

    /** Minimum signals before trusting the EMA (cold start threshold). */
    static final int MIN_SIGNALS = 10;

    /** Exploration rate: probability of choosing a random profile. */
    static final float EPSILON = 0.10f;

    /** EMA smoothing factor for reinforcement signals. */
    static final float EMA_ALPHA = 0.15f;

    /** Available profiles for exploration (all enum values). */
    private static final CognitiveProfile[] ALL_PROFILES = CognitiveProfile.values();

    /**
     * Bandit statistics: context hash → per-profile running stats.
     * ConcurrentHashMap for lock-free reads; writes guarded by {@link #writeLock}.
     */
    private final ConcurrentHashMap<Long, EnumMap<CognitiveProfile, RunningStats>> stats =
            new ConcurrentHashMap<>();

    /** Write lock for mutating the stats map. */
    private final ReentrantLock writeLock = new ReentrantLock();

    /** Fallback profile from SalienceProfile.defaultProfile() — may be null. */
    private final CognitiveProfile salienceDefault;

    /**
     * Creates a profile adaptor with the given fallback profile.
     *
     * @param salienceDefault the fallback profile from SalienceProfile, or null
     *                        for no fallback (caller falls back to BALANCED)
     */
    public ProfileAdaptor(CognitiveProfile salienceDefault) {
        this.salienceDefault = salienceDefault;
    }

    /**
     * Creates a profile adaptor with no fallback profile.
     */
    public ProfileAdaptor() {
        this(null);
    }

    // ══════════════════════════════════════════════════════════════
    // Recording outcomes
    // ══════════════════════════════════════════════════════════════

    /**
     * Records a reinforcement outcome for a (context, profile) pair.
     *
     * <p>Updates the EMA of the profile's success rate in the given tag context.
     * This is the core learning signal — positive reinforcement strengthens
     * the context→profile mapping, negative weakens it.</p>
     *
     * @param profile  the cognitive profile that was used
     * @param tags     the context tags from the recall query
     * @param positive whether the outcome was positive (user accepted result)
     */
    public void recordOutcome(CognitiveProfile profile, String[] tags, boolean positive) {
        if (profile == null || tags == null || tags.length == 0) return;

        long ctxHash = contextHash(tags);
        writeLock.lock();
        try {
            EnumMap<CognitiveProfile, RunningStats> contextStats =
                    stats.computeIfAbsent(ctxHash, _ -> new EnumMap<>(CognitiveProfile.class));
            RunningStats current = contextStats.getOrDefault(profile, RunningStats.EMPTY);
            contextStats.put(profile, current.update(positive, EMA_ALPHA));
        } finally {
            writeLock.unlock();
        }

        if (log.isTraceEnabled()) {
            log.trace("ProfileAdaptor: recorded {} outcome for {} in context hash={}",
                    positive ? "positive" : "negative", profile, ctxHash);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Suggestion (ε-greedy)
    // ══════════════════════════════════════════════════════════════

    /**
     * Suggests the best cognitive profile for the given tag context.
     *
     * <p>Uses ε-greedy selection:</p>
     * <ul>
     *   <li><b>10% explore</b>: returns a random profile from the full set</li>
     *   <li><b>90% exploit</b>: returns the profile with the highest EMA in
     *       this context, if sufficient signals have been collected</li>
     * </ul>
     *
     * <p>Returns {@code null} (caller falls back to BALANCED) when:</p>
     * <ul>
     *   <li>No data exists for this context AND no salienceDefault is set</li>
     *   <li>The best profile has fewer than {@link #MIN_SIGNALS} signals</li>
     * </ul>
     *
     * @param tags context tags from the recall query
     * @return the suggested profile, or null if insufficient data
     */
    public CognitiveProfile suggest(String... tags) {
        if (tags == null || tags.length == 0) return salienceDefault;

        // ε-greedy: explore with probability EPSILON
        if (ThreadLocalRandom.current().nextFloat() < EPSILON) {
            CognitiveProfile random = ALL_PROFILES[
                    ThreadLocalRandom.current().nextInt(ALL_PROFILES.length)];
            log.trace("ProfileAdaptor: exploring random profile {}", random);
            return random;
        }

        // Exploit: find best EMA for this context
        long ctxHash = contextHash(tags);
        EnumMap<CognitiveProfile, RunningStats> contextStats = stats.get(ctxHash);
        if (contextStats == null || contextStats.isEmpty()) {
            return salienceDefault;
        }

        CognitiveProfile best = null;
        float bestEma = -1.0f;
        for (Map.Entry<CognitiveProfile, RunningStats> entry : contextStats.entrySet()) {
            RunningStats rs = entry.getValue();
            if (rs.totalSignals() >= MIN_SIGNALS && rs.ema() > bestEma) {
                bestEma = rs.ema();
                best = entry.getKey();
            }
        }

        if (best != null) {
            log.trace("ProfileAdaptor: suggesting {} (EMA={}) for context hash={}",
                    best, bestEma, ctxHash);
        }
        return best != null ? best : salienceDefault;
    }

    // ══════════════════════════════════════════════════════════════
    // Context Hashing
    // ══════════════════════════════════════════════════════════════

    /**
     * Computes a deterministic hash for a set of tags (order-independent).
     *
     * <p>Sorts tags alphabetically, joins with ":", then applies FNV-1a 64-bit
     * hashing — the same algorithm used by {@code CoActivationTracker.hashTag()}.</p>
     *
     * @param tags the context tags
     * @return the 64-bit context hash
     */
    static long contextHash(String... tags) {
        String[] sorted = tags.clone();
        Arrays.sort(sorted);
        String joined = String.join(":", sorted);

        // FNV-1a 64-bit — same algorithm as CoActivationTracker.hashTag()
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < joined.length(); i++) {
            hash ^= joined.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash == 0 ? 1 : hash;
    }

    // ══════════════════════════════════════════════════════════════
    // Persistence
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns an unmodifiable snapshot of all bandit statistics.
     *
     * <p>Used by CoActivationTracker to persist bandit stats in the COAX v2 format.</p>
     *
     * @return unmodifiable map of context hash → per-profile stats
     */
    public Map<Long, EnumMap<CognitiveProfile, RunningStats>> statsSnapshot() {
        return Collections.unmodifiableMap(stats);
    }

    /**
     * Bulk-loads bandit statistics from persistence.
     *
     * <p>Called during CoActivationTracker load to restore bandit state from
     * the COAX v2 file. Replaces any existing in-memory stats.</p>
     *
     * @param loaded the persisted statistics map
     */
    public void loadStats(Map<Long, EnumMap<CognitiveProfile, RunningStats>> loaded) {
        if (loaded == null) return;
        writeLock.lock();
        try {
            stats.clear();
            for (Map.Entry<Long, EnumMap<CognitiveProfile, RunningStats>> entry : loaded.entrySet()) {
                stats.put(entry.getKey(), new EnumMap<>(entry.getValue()));
            }
            log.info("ProfileAdaptor: loaded {} context entries from persistence", loaded.size());
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Returns the total number of tracked contexts.
     *
     * @return context count
     */
    public int contextCount() {
        return stats.size();
    }
}
