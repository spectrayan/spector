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
package com.spectrayan.spector.synapse.dr;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token-bucket bandwidth rate limiter to throttle DR snapshot exports and prevent starvations
 * of replication links (ADR-0034 §11.2, §14, Req R1.6, W10).
 */
public class BandwidthThrottler {

    private final long maxBytesPerSecond;
    private final AtomicLong transferredInWindow = new AtomicLong(0);
    private final AtomicLong windowStartTime = new AtomicLong(System.nanoTime());

    public BandwidthThrottler(long maxBytesPerSecond) {
        this.maxBytesPerSecond = maxBytesPerSecond;
    }

    /**
     * Records byte transfer and pauses the calling thread if bandwidth limit is exceeded.
     *
     * @param bytes number of bytes to transfer
     */
    public void throttle(long bytes) {
        if (maxBytesPerSecond <= 0 || bytes <= 0) {
            return;
        }

        long now = System.nanoTime();
        long start = windowStartTime.get();
        long elapsedNanos = now - start;

        if (elapsedNanos >= TimeUnit.SECONDS.toNanos(1)) {
            windowStartTime.set(now);
            transferredInWindow.set(bytes);
            return;
        }

        long total = transferredInWindow.addAndGet(bytes);
        if (total > maxBytesPerSecond) {
            long remainingNanosInWindow = TimeUnit.SECONDS.toNanos(1) - elapsedNanos;
            if (remainingNanosInWindow > 0) {
                try {
                    TimeUnit.NANOSECONDS.sleep(remainingNanosInWindow);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            windowStartTime.set(System.nanoTime());
            transferredInWindow.set(0);
        }
    }

    public long getMaxBytesPerSecond() {
        return maxBytesPerSecond;
    }
}
