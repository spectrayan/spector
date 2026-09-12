/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.replication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-follower bounded replication queue enforcing backpressure and degradation to full resync
 * (ADR-0034 §10.3, Invariant N8, Req R8.1–R8.4).
 *
 * <p>Invariant N8: Degrade to full resync, never to an unbounded queue.</p>
 */
public class BoundedFollowerQueue {

    private static final Logger log = LoggerFactory.getLogger(BoundedFollowerQueue.class);

    public static final int DEFAULT_CAPACITY = 10_000;

    private final String followerId;
    private final int capacity;
    private final BlockingQueue<ReplicationFrame> queue;

    private final AtomicBoolean lagging = new AtomicBoolean(false);
    private final AtomicBoolean tailStreamingStopped = new AtomicBoolean(false);
    private final AtomicBoolean fullResyncRequired = new AtomicBoolean(false);

    private final AtomicLong framesDropped = new AtomicLong(0);
    private final AtomicLong lastAckHwm = new AtomicLong(0);
    private final AtomicLong lastAckTimestampMs = new AtomicLong(System.currentTimeMillis());

    public BoundedFollowerQueue(String followerId) {
        this(followerId, DEFAULT_CAPACITY);
    }

    public BoundedFollowerQueue(String followerId, int capacity) {
        this.followerId = Objects.requireNonNull(followerId, "followerId must not be null");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, got: " + capacity);
        }
        this.capacity = capacity;
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * Attempts to enqueue a frame for the follower.
     * On queue overflow, stops WAL tail streaming, increments {@code frames.dropped},
     * and marks the follower as lagging (Req R8.1, R8.2, Invariant N8).
     *
     * @param frame replication frame to enqueue
     * @return true if accepted, false if dropped due to backpressure
     */
    public boolean offer(ReplicationFrame frame) {
        Objects.requireNonNull(frame, "frame must not be null");

        boolean accepted = queue.offer(frame);
        if (!accepted) {
            // Queue overflow: backpressure triggered!
            tailStreamingStopped.set(true);
            lagging.set(true);
            long dropped = framesDropped.incrementAndGet();

            log.warn("Follower '{}' replication queue full (cap={}). WAL tail stopped, follower marked lagging, frames dropped={}",
                    followerId, capacity, dropped);
            return false;
        }

        return true;
    }

    /**
     * Polls the next replication frame from the queue.
     *
     * @return next frame or null if queue is empty
     */
    public ReplicationFrame poll() {
        return queue.poll();
    }

    /**
     * Records an acknowledgment from the follower.
     *
     * @param hwm applied high-water mark reported by the follower
     * @param timestampMs acknowledgment timestamp in epoch milliseconds
     */
    public void acknowledge(long hwm, long timestampMs) {
        lastAckHwm.set(Math.max(lastAckHwm.get(), hwm));
        lastAckTimestampMs.set(Math.max(lastAckTimestampMs.get(), timestampMs));

        // If the queue has drained and follower caught up, clear lagging flag
        if (queue.isEmpty() && !fullResyncRequired.get()) {
            lagging.set(false);
            tailStreamingStopped.set(false);
        }
    }

    /**
     * Checks if the follower has exceeded the full resync threshold (Req R8.3).
     * If lag exceeds threshold, marks follower as requiring full resync.
     *
     * @param nowMs current epoch milliseconds
     * @param fullResyncLagThresholdMs threshold in milliseconds beyond which follower requires full resync
     * @return true if follower requires full resync
     */
    public boolean checkLag(long nowMs, long fullResyncLagThresholdMs) {
        long lagMs = nowMs - lastAckTimestampMs.get();
        if (lagMs >= fullResyncLagThresholdMs) {
            fullResyncRequired.set(true);
            tailStreamingStopped.set(true);
            lagging.set(true);
            log.warn("Follower '{}' lag ({}ms) exceeded threshold ({}ms). Driving to FULL resync at next snapshot (Req R8.3)",
                    followerId, lagMs, fullResyncLagThresholdMs);
            return true;
        }
        return fullResyncRequired.get();
    }

    /**
     * Resets the full resync state after a successful full snapshot apply.
     */
    public void resetFullResync() {
        fullResyncRequired.set(false);
        lagging.set(false);
        tailStreamingStopped.set(false);
        queue.clear();
        lastAckTimestampMs.set(System.currentTimeMillis());
        log.info("Follower '{}' full resync completed successfully. Resetting queue state.", followerId);
    }

    public String getFollowerId() {
        return followerId;
    }

    public int getCapacity() {
        return capacity;
    }

    public int size() {
        return queue.size();
    }

    public boolean isLagging() {
        return lagging.get();
    }

    public boolean isTailStreamingStopped() {
        return tailStreamingStopped.get();
    }

    public boolean isFullResyncRequired() {
        return fullResyncRequired.get();
    }

    public long getFramesDropped() {
        return framesDropped.get();
    }

    public long getLastAckHwm() {
        return lastAckHwm.get();
    }

    public long getLastAckTimestampMs() {
        return lastAckTimestampMs.get();
    }
}
