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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BandwidthThrottler Window Accounting (ADR-0034 §11.2, Req R1.6, G61)")
class BandwidthThrottlerTest {

    @Test
    @DisplayName("G61: Transfer within rate limit does not sleep")
    void testTransferWithinRateLimitDoesNotSleep() {
        AtomicLong simulatedNanos = new AtomicLong(1_000_000_000L);
        List<Long> sleeps = new ArrayList<>();

        BandwidthThrottler throttler = new BandwidthThrottler(
                1000,
                simulatedNanos::get,
                sleeps::add
        );

        throttler.throttle(500);

        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("G61: Second transfer exceeding limit sleeps for exact remainder")
    void testSecondTransferExceedingLimitSleeps() {
        AtomicLong simulatedNanos = new AtomicLong(1_000_000_000L);
        List<Long> sleeps = new ArrayList<>();

        BandwidthThrottler throttler = new BandwidthThrottler(
                1000,
                simulatedNanos::get,
                nanos -> {
                    sleeps.add(nanos);
                    simulatedNanos.addAndGet(nanos);
                }
        );

        throttler.throttle(600);
        assertThat(sleeps).isEmpty();

        // Advance simulated time by 200ms
        simulatedNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(200));

        // Transfer another 600 bytes -> total 1200 bytes for 1000 B/s limit
        // Required time: 1.2s (1200ms). Elapsed so far: 200ms. Sleep should be 1000ms.
        throttler.throttle(600);

        assertThat(sleeps).hasSize(1);
        assertThat(sleeps.get(0)).isEqualTo(TimeUnit.MILLISECONDS.toNanos(1000));
    }

    @Test
    @DisplayName("G61: Single oversized payload consumes multiple windows and carries over remainder")
    void testOversizedPayloadConsumesMultipleWindows() {
        AtomicLong simulatedNanos = new AtomicLong(1_000_000_000L);
        List<Long> sleeps = new ArrayList<>();

        BandwidthThrottler throttler = new BandwidthThrottler(
                1000, // 1000 bytes per sec
                simulatedNanos::get,
                nanos -> {
                    sleeps.add(nanos);
                    simulatedNanos.addAndGet(nanos);
                }
        );

        // Send 5500 bytes (5.5x the rate limit)
        // Required time: 5.5s (5500ms). Sleep should be 5500ms.
        throttler.throttle(5500);

        assertThat(sleeps).hasSize(1);
        assertThat(sleeps.get(0)).isEqualTo(TimeUnit.MILLISECONDS.toNanos(5500));

        // Now, 500 bytes are carried over into the current window, and 500ms have elapsed (since 5.5s - 5.0s = 0.5s).
        // Sending another 600 bytes results in total 1100 bytes (> 1000 limit).
        // Required for 1100 bytes is 1.1s. Since 0.5s already elapsed, remaining sleep is 0.6s (600ms).
        throttler.throttle(600);
        assertThat(sleeps).hasSize(2);
        assertThat(sleeps.get(1)).isEqualTo(TimeUnit.MILLISECONDS.toNanos(600));
    }

    @Test
    @DisplayName("G61: Zero or negative bytes do not sleep")
    void testZeroOrNegativeBytesDoNotSleep() {
        AtomicLong simulatedNanos = new AtomicLong(1_000_000_000L);
        List<Long> sleeps = new ArrayList<>();

        BandwidthThrottler throttler = new BandwidthThrottler(
                1000,
                simulatedNanos::get,
                sleeps::add
        );

        throttler.throttle(0);
        throttler.throttle(-10);

        assertThat(sleeps).isEmpty();
    }
}
