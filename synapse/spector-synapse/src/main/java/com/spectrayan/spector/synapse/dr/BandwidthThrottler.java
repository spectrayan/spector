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

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Token-bucket bandwidth rate limiter to throttle DR snapshot exports and prevent starvations
 * of replication links (ADR-0034 §11.2, §14, Req R1.6, W10).
 *
 * <p>Invariant: Window accounting accounts for multi-window allocations and carries over
 * deficit bytes to prevent single oversized payloads from blowing the limit (G61).</p>
 */
public class BandwidthThrottler {

    @FunctionalInterface
    public interface Sleeper {
        void sleepNanos(long nanos) throws InterruptedException;
    }

    public static final Sleeper DEFAULT_SLEEPER = nanos -> {
        if (nanos > 0) {
            TimeUnit.NANOSECONDS.sleep(nanos);
        }
    };

    private final long maxBytesPerSecond;
    private final LongSupplier nanoClock;
    private final Sleeper sleeper;
    private final AtomicLong transferredInWindow;
    private final AtomicLong windowStartTime;

    public BandwidthThrottler(long maxBytesPerSecond) {
        this(maxBytesPerSecond, System::nanoTime, DEFAULT_SLEEPER);
    }

    public BandwidthThrottler(long maxBytesPerSecond, LongSupplier nanoClock, Sleeper sleeper) {
        this.maxBytesPerSecond = maxBytesPerSecond;
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock must not be null");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper must not be null");
        this.transferredInWindow = new AtomicLong(0);
        this.windowStartTime = new AtomicLong(nanoClock.getAsLong());
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

        long now = nanoClock.getAsLong();
        long start = windowStartTime.get();
        long elapsedNanos = now - start;
        long oneSecondNanos = TimeUnit.SECONDS.toNanos(1);

        if (elapsedNanos >= oneSecondNanos) {
            long fullWindowsElapsed = elapsedNanos / oneSecondNanos;
            windowStartTime.addAndGet(fullWindowsElapsed * oneSecondNanos);
            transferredInWindow.set(0);
            elapsedNanos = now - windowStartTime.get();
        }

        long total = transferredInWindow.addAndGet(bytes);
        if (total > maxBytesPerSecond) {
            // G61: Accurately compute duration required for total transferred bytes
            long fullSeconds = total / maxBytesPerSecond;
            long remainderBytes = total % maxBytesPerSecond;
            long requiredNanos = fullSeconds * oneSecondNanos + (remainderBytes * oneSecondNanos) / maxBytesPerSecond;

            long sleepNanos = requiredNanos - elapsedNanos;
            if (sleepNanos > 0) {
                try {
                    sleeper.sleepNanos(sleepNanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Advance window start time by the full seconds consumed by this allocation
            long fullWindowsConsumed = requiredNanos / oneSecondNanos;
            windowStartTime.addAndGet(fullWindowsConsumed * oneSecondNanos);

            // Carry over any residual bytes into the new current window
            long carriedOver = total - (fullWindowsConsumed * maxBytesPerSecond);
            transferredInWindow.set(Math.max(0, carriedOver));
        }
    }

    public long getMaxBytesPerSecond() {
        return maxBytesPerSecond;
    }
}

