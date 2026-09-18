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
package com.spectrayan.spector.synapse.replication;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Replication metrics registry (ADR-0034 §15.7, Req R11.1, R11.3, R11.4, R11.5).
 */
public class ReplicationMetrics {

    public static final String METRIC_FRAMES_REJECTED = "spector.replication.frames.rejected";
    public static final String METRIC_FRAMES_DROPPED = "spector.replication.frames.dropped";
    public static final String METRIC_HWM_LAG = "spector.replication.hwm.lag";
    public static final String METRIC_FULL_RESYNC_COUNT = "spector.replication.full_resync.count";
    public static final String METRIC_VERIFICATION_FAILURES = "spector.replication.verification.failures";

    private final AtomicLong framesRejected = new AtomicLong(0);
    private final AtomicLong framesDropped = new AtomicLong(0);
    private final AtomicLong fullResyncCount = new AtomicLong(0);
    private final AtomicLong verificationFailures = new AtomicLong(0);

    // Per-follower per-namespace HWM lag: key -> "follower:tenant:namespace"
    private final Map<String, AtomicLong> hwmLag = new ConcurrentHashMap<>();

    public void recordFrameRejected() {
        framesRejected.incrementAndGet();
    }

    public void recordFrameDropped() {
        framesDropped.incrementAndGet();
    }

    public void recordFullResync() {
        fullResyncCount.incrementAndGet();
    }

    public void recordVerificationFailure() {
        verificationFailures.incrementAndGet();
    }

    public void recordHwmLag(String followerId, String namespaceId, long lag) {
        String key = followerId + ":" + namespaceId;
        hwmLag.computeIfAbsent(key, k -> new AtomicLong(0)).set(Math.max(0, lag));
    }

    public long getFramesRejected() {
        return framesRejected.get();
    }

    public long getFramesDropped() {
        return framesDropped.get();
    }

    public long getFullResyncCount() {
        return fullResyncCount.get();
    }

    public long getVerificationFailures() {
        return verificationFailures.get();
    }

    public long getHwmLag(String followerId, String namespaceId) {
        String key = followerId + ":" + namespaceId;
        AtomicLong val = hwmLag.get(key);
        return val != null ? val.get() : 0L;
    }
}
