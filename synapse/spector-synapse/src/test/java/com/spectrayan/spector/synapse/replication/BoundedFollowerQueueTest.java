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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for BoundedFollowerQueue verifying backpressure overflow, frames.dropped counting,
 * tail stopping, lagging marking, full resync trigger, and independent follower queues
 * (ADR-0034 §10.3, Invariant N8, Req R8.1–R8.4).
 */
@DisplayName("Task 5.6–5.8: Bounded Follower Queue & Backpressure Tests")
class BoundedFollowerQueueTest {

    @Test
    @DisplayName("R8.1 & R8.2: Queue overflow stops WAL tail, marks lagging, and increments frames.dropped")
    void testQueueOverflowBackpressure() {
        int capacity = 5;
        BoundedFollowerQueue queue = new BoundedFollowerQueue("follower-laggy", capacity);

        assertThat(queue.isLagging()).isFalse();
        assertThat(queue.isTailStreamingStopped()).isFalse();
        assertThat(queue.getFramesDropped()).isEqualTo(0L);

        // Fill queue to capacity
        for (int i = 0; i < capacity; i++) {
            ReplicationFrame frame = new ReplicationFrame(ReplicationFrame.TYPE_WAL_TAIL, ("event-" + i).getBytes(StandardCharsets.UTF_8));
            boolean accepted = queue.offer(frame);
            assertThat(accepted).isTrue();
        }

        assertThat(queue.size()).isEqualTo(capacity);
        assertThat(queue.isLagging()).isFalse();
        assertThat(queue.isTailStreamingStopped()).isFalse();

        // Push 6th frame -> should overflow and trigger backpressure
        ReplicationFrame overflowFrame1 = new ReplicationFrame(ReplicationFrame.TYPE_WAL_TAIL, "overflow-1".getBytes(StandardCharsets.UTF_8));
        boolean accepted1 = queue.offer(overflowFrame1);
        assertThat(accepted1).isFalse();

        // Invariant N8: overflow stops tail, counts frames.dropped, marks lagging
        assertThat(queue.isLagging()).isTrue();
        assertThat(queue.isTailStreamingStopped()).isTrue();
        assertThat(queue.getFramesDropped()).isEqualTo(1L);

        // Push 7th frame -> dropped as well
        ReplicationFrame overflowFrame2 = new ReplicationFrame(ReplicationFrame.TYPE_WAL_TAIL, "overflow-2".getBytes(StandardCharsets.UTF_8));
        boolean accepted2 = queue.offer(overflowFrame2);
        assertThat(accepted2).isFalse();
        assertThat(queue.getFramesDropped()).isEqualTo(2L);
    }

    @Test
    @DisplayName("R8.3: Follower exceeding fullResyncLagThreshold is driven to FULL resync")
    void testFollowerDrivenToFullResyncOnThresholdExceeded() {
        BoundedFollowerQueue queue = new BoundedFollowerQueue("follower-stalled", 100);

        long now = System.currentTimeMillis();
        long thresholdMs = 300_000L; // 5 minutes (ADR knob)

        // Currently fresh
        assertThat(queue.checkLag(now, thresholdMs)).isFalse();
        assertThat(queue.isFullResyncRequired()).isFalse();

        // Fast-forward time past threshold (e.g. 6 minutes without ack)
        long futureTime = now + 360_000L;
        boolean requiresFullResync = queue.checkLag(futureTime, thresholdMs);

        assertThat(requiresFullResync).isTrue();
        assertThat(queue.isFullResyncRequired()).isTrue();
        assertThat(queue.isLagging()).isTrue();
        assertThat(queue.isTailStreamingStopped()).isTrue();

        // Reset after full resync
        queue.resetFullResync();
        assertThat(queue.isFullResyncRequired()).isFalse();
        assertThat(queue.isLagging()).isFalse();
        assertThat(queue.isTailStreamingStopped()).isFalse();
    }

    @Test
    @DisplayName("R8.4: A full resync or backpressured queue must not stall healthy followers")
    void testIndependentFollowerQueuesDoNotStallHealthyFollowers() {
        BoundedFollowerQueue slowFollower = new BoundedFollowerQueue("follower-slow", 2);
        BoundedFollowerQueue fastFollower = new BoundedFollowerQueue("follower-fast", 100);

        // Fill slow follower to trigger backpressure
        slowFollower.offer(new ReplicationFrame(ReplicationFrame.TYPE_WAL_TAIL, new byte[]{1}));
        slowFollower.offer(new ReplicationFrame(ReplicationFrame.TYPE_WAL_TAIL, new byte[]{2}));
        boolean dropped = !slowFollower.offer(new ReplicationFrame(ReplicationFrame.TYPE_WAL_TAIL, new byte[]{3}));

        assertThat(dropped).isTrue();
        assertThat(slowFollower.isLagging()).isTrue();
        assertThat(slowFollower.isTailStreamingStopped()).isTrue();
        assertThat(slowFollower.getFramesDropped()).isEqualTo(1L);

        // Fast follower continues without interruption or dropped frames
        for (int i = 0; i < 50; i++) {
            boolean fastAccepted = fastFollower.offer(new ReplicationFrame(ReplicationFrame.TYPE_WAL_TAIL, new byte[]{(byte) i}));
            assertThat(fastAccepted).isTrue();
        }

        assertThat(fastFollower.isLagging()).isFalse();
        assertThat(fastFollower.isTailStreamingStopped()).isFalse();
        assertThat(fastFollower.getFramesDropped()).isEqualTo(0L);
        assertThat(fastFollower.size()).isEqualTo(50);
    }
}
