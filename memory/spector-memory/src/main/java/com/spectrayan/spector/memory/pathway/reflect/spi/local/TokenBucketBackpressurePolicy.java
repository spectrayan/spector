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
package com.spectrayan.spector.memory.pathway.reflect.spi.local;

import com.spectrayan.spector.memory.pathway.reflect.SessionWorkItem;
import com.spectrayan.spector.memory.pathway.reflect.spi.ReflectBackpressurePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Token-bucket rate-limiting policy governing session consolidation throughput.
 *
 * @since 1.5.0
 */
public final class TokenBucketBackpressurePolicy implements ReflectBackpressurePolicy {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketBackpressurePolicy.class);

    private final double refillTokensPerMs;
    private final double capacity;
    private final int maxConsecutiveFailures;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final ReentrantLock lock = new ReentrantLock();

    private double availableTokens;
    private long lastRefillTimestampMs;

    public TokenBucketBackpressurePolicy(int sessionsPerMinute, int burstCapacity, int maxConsecutiveFailures) {
        if (sessionsPerMinute <= 0) {
            throw new IllegalArgumentException("sessionsPerMinute must be positive: " + sessionsPerMinute);
        }
        this.capacity = Math.max(1, burstCapacity);
        this.refillTokensPerMs = sessionsPerMinute / 60000.0;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.availableTokens = this.capacity;
        this.lastRefillTimestampMs = System.currentTimeMillis();
    }

    public static TokenBucketBackpressurePolicy perMinute(int sessionsPerMinute) {
        return new TokenBucketBackpressurePolicy(sessionsPerMinute, Math.max(1, sessionsPerMinute / 10), 5);
    }

    @Override
    public void beforeSession(SessionWorkItem item) throws InterruptedException {
        while (true) {
            long waitMs = 0;
            lock.lock();
            try {
                refill();
                if (availableTokens >= 1.0) {
                    availableTokens -= 1.0;
                    consecutiveFailures.set(0);
                    return;
                }
                double missing = 1.0 - availableTokens;
                waitMs = (long) Math.ceil(missing / refillTokensPerMs);
            } finally {
                lock.unlock();
            }

            if (waitMs > 0) {
                Thread.sleep(Math.min(waitMs, 1000L));
            }
        }
    }

    @Override
    public void onProviderFailure(Throwable t) {
        int failures = consecutiveFailures.incrementAndGet();
        log.warn("ReflectBackpressurePolicy: provider failure #{} recorded: {}", failures, t.getMessage());
    }

    @Override
    public boolean shouldAbortSweep() {
        return maxConsecutiveFailures > 0 && consecutiveFailures.get() >= maxConsecutiveFailures;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long elapsedMs = Math.max(0, now - lastRefillTimestampMs);
        if (elapsedMs > 0) {
            availableTokens = Math.min(capacity, availableTokens + (elapsedMs * refillTokensPerMs));
            lastRefillTimestampMs = now;
        }
    }
}
