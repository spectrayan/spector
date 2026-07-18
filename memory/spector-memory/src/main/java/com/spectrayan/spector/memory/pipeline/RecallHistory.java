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
package com.spectrayan.spector.memory.pipeline;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Thread-safe circular buffer tracking recent recall context tags.
 *
 * <h3>Biological Analog: Hippocampal Replay Buffer</h3>
 * <p>During rest and sleep, the hippocampus replays recent experiences to
 * consolidate them into long-term cortical memory. Crucially, recent
 * experiences are replayed more frequently than older ones — a temporal
 * weighting that strengthens fresh associations while allowing stale ones
 * to fade.</p>
 *
 * <p>This class models that replay buffer for the recall pipeline. Each
 * time a recall query fires, its context tags are recorded into a fixed-size
 * ring buffer with timestamps. The {@link #weightedRecentTags} method
 * applies exponential decay from the most recent entry, simulating the
 * hippocampus's preference for recent experiences.</p>
 *
 * <h3>Executive Dysfunction Context</h3>
 * <p>For the {@code EXECUTIVE_DYSFUNCTION} cognitive profile, this buffer
 * enables associative recall by surfacing tags from the user's recent
 * recall context. When the user struggles to formulate a precise query,
 * the system can augment the query with recently active tags — simulating
 * the "it was related to that thing I was just looking at" pattern common
 * in executive dysfunction.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>All public methods are guarded by a {@link ReentrantLock} to ensure
 * safe concurrent access from multiple recall threads.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   var history = new RecallHistory();
 *
 *   // Record tags from each recall
 *   history.record(new String[]{"database", "timeout"});
 *   history.record(new String[]{"cache", "redis"});
 *
 *   // Get recent tags (flat, no weighting)
 *   String[] recent = history.recentTags(5);
 *
 *   // Get weighted tags (exponential decay from most recent)
 *   Map<String, Float> weighted = history.weightedRecentTags(5, 0.8f);
 *   // → {"cache"=1.0, "redis"=1.0, "database"=0.8, "timeout"=0.8}
 * }</pre>
 */
public final class RecallHistory {

    private static final Logger log = LoggerFactory.getLogger(RecallHistory.class);

    /** Default ring buffer capacity — tracks the last 20 recall contexts. */
    public static final int DEFAULT_CAPACITY = 20;

    private final ReentrantLock lock = new ReentrantLock();
    private final String[][] ringBuffer;
    private final long[] timestamps;
    private final int capacity;
    private int head;   // next write position
    private int count;  // number of entries stored (≤ capacity)

    /**
     * Creates a recall history with the {@link #DEFAULT_CAPACITY}.
     */
    public RecallHistory() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a recall history with the specified capacity.
     *
     * @param capacity maximum number of recall contexts to retain
     * @throws IllegalArgumentException if capacity is less than 1
     */
    public RecallHistory(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be ≥ 1, got: " + capacity);
        }
        this.capacity = capacity;
        this.ringBuffer = new String[capacity][];
        this.timestamps = new long[capacity];
        this.head = 0;
        this.count = 0;
    }

    /**
     * Records a set of context tags from a recall query.
     *
     * <p>Overwrites the oldest entry when the buffer is full.</p>
     *
     * @param tags the context tags from this recall (null or empty is ignored)
     */
    public void record(String[] tags) {
        if (tags == null || tags.length == 0) return;

        lock.lock();
        try {
            ringBuffer[head] = tags.clone(); // defensive copy
            timestamps[head] = System.currentTimeMillis();
            head = (head + 1) % capacity;
            if (count < capacity) count++;
        } finally {
            lock.unlock();
        }

        if (log.isTraceEnabled()) {
            log.trace("Recorded {} tags into recall history (count={})", tags.length, count);
        }
    }

    /**
     * Returns the most recent unique tags, up to {@code maxTags}.
     *
     * <p>Iterates from most recent to oldest. Each tag appears at most once
     * in the result. Stops when {@code maxTags} unique tags are collected
     * or the buffer is exhausted.</p>
     *
     * @param maxTags maximum number of unique tags to return
     * @return array of unique recent tags, most-recent first
     */
    public String[] recentTags(int maxTags) {
        if (maxTags <= 0) return new String[0];

        lock.lock();
        try {
            // Use LinkedHashMap for insertion-order uniqueness
            var seen = new LinkedHashMap<String, Boolean>(maxTags * 2);
            for (int i = 0; i < count && seen.size() < maxTags; i++) {
                int idx = ((head - 1 - i) % capacity + capacity) % capacity;
                String[] tags = ringBuffer[idx];
                if (tags == null) continue;
                for (String tag : tags) {
                    if (tag != null && !tag.isBlank()) {
                        seen.putIfAbsent(tag, Boolean.TRUE);
                        if (seen.size() >= maxTags) break;
                    }
                }
            }
            return seen.keySet().toArray(new String[0]);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns recently active tags with exponential decay weighting.
     *
     * <p>Iterates from most recent to oldest entry. The most recent entry
     * receives weight 1.0, the next receives {@code decayFactor}, then
     * {@code decayFactor²}, etc. If a tag appears in multiple entries,
     * the maximum weight is kept (simulating hippocampal potentiation —
     * repeated recent activation strengthens the tag).</p>
     *
     * @param maxTags     maximum number of unique tags to return
     * @param decayFactor decay multiplier per entry (0.0–1.0); e.g., 0.8 means
     *                    each older entry has 80% of the previous entry's weight
     * @return map of tag → weight, ordered by descending weight
     */
    public Map<String, Float> weightedRecentTags(int maxTags, float decayFactor) {
        if (maxTags <= 0) return Map.of();

        lock.lock();
        try {
            // Collect all tags with their max weight
            var tagWeights = new LinkedHashMap<String, Float>(maxTags * 2);
            float weight = 1.0f;

            for (int i = 0; i < count; i++) {
                int idx = ((head - 1 - i) % capacity + capacity) % capacity;
                String[] tags = ringBuffer[idx];
                if (tags == null) {
                    weight *= decayFactor;
                    continue;
                }
                for (String tag : tags) {
                    if (tag != null && !tag.isBlank()) {
                        tagWeights.merge(tag, weight, Math::max);
                    }
                }
                weight *= decayFactor;
            }

            // Sort by weight descending and take top maxTags
            var result = new LinkedHashMap<String, Float>(maxTags * 2);
            tagWeights.entrySet().stream()
                    .sorted(Map.Entry.<String, Float>comparingByValue().reversed())
                    .limit(maxTags)
                    .forEach(e -> result.put(e.getKey(), e.getValue()));

            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the number of entries currently stored in the buffer.
     *
     * @return entry count (0 to {@link #capacity()})
     */
    public int size() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the maximum capacity of this buffer.
     *
     * @return the ring buffer capacity
     */
    public int capacity() {
        return capacity;
    }
}
