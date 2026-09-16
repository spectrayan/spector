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
package com.spectrayan.spector.commons.pathway;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration for retry behavior on a stage decorator.
 *
 * <p>Rules (ADR-0036 §8):
 * <ol>
 *   <li>Default {@code retryOn = {TRANSIENT}}</li>
 *   <li>Never retry VALIDATION, CONTRACT, CONTROL, INTERNAL</li>
 *   <li>DOWNSTREAM is opt-in only</li>
 *   <li>Backoff: {@code initial × 2^(attempt-1)} plus jitter {@code ± jitter × backoff}</li>
 *   <li>Sleep uses {@code LockSupport.parkNanos} on the current virtual thread</li>
 *   <li>If a timeout wrapper is present, each attempt gets a fresh timeout</li>
 *   <li>Relay must implement {@link IdempotentRelay}</li>
 * </ol>
 */
public final class RetryPolicy {

    /** Set of FaultKinds that are never retried regardless of configuration. */
    private static final Set<FaultKind> NEVER_RETRY = Collections.unmodifiableSet(
            EnumSet.of(FaultKind.VALIDATION, FaultKind.CONTRACT, FaultKind.CONTROL,
                    FaultKind.INTERNAL, FaultKind.INTERRUPTED));

    private static final RetryPolicy NONE = new RetryPolicy(1, Duration.ZERO, 0.0,
            EnumSet.noneOf(FaultKind.class));

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final double jitter;
    private final Set<FaultKind> retryOn;

    private RetryPolicy(int maxAttempts, Duration initialBackoff, double jitter,
                         EnumSet<FaultKind> retryOn) {
        this.maxAttempts = maxAttempts;
        this.initialBackoff = Objects.requireNonNull(initialBackoff);
        this.jitter = jitter;
        this.retryOn = Collections.unmodifiableSet(EnumSet.copyOf(retryOn));
    }

    /**
     * Returns a no-retry policy (1 attempt, no backoff).
     */
    public static RetryPolicy none() {
        return NONE;
    }

    /**
     * Creates a retry policy for the specified fault kinds.
     *
     * @param maxAttempts    total attempts including the first (1 = no retry)
     * @param backoff        initial backoff duration
     * @param kinds          fault kinds to retry on
     * @return retry policy
     * @throws IllegalArgumentException if maxAttempts < 1 or any kind is in the never-retry set
     */
    public static RetryPolicy of(int maxAttempts, Duration backoff, FaultKind... kinds) {
        return of(maxAttempts, backoff, 0.15, kinds);
    }

    /**
     * Creates a retry policy with explicit jitter.
     *
     * @param maxAttempts    total attempts including the first
     * @param backoff        initial backoff duration
     * @param jitter         jitter factor (0.0–1.0)
     * @param kinds          fault kinds to retry on
     * @return retry policy
     */
    public static RetryPolicy of(int maxAttempts, Duration backoff, double jitter,
                                  FaultKind... kinds) {
        if (maxAttempts < 1) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "maxAttempts", maxAttempts);
        }
        if (jitter < 0.0 || jitter > 1.0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_OUT_OF_RANGE, "jitter", jitter, "0.0", "1.0");
        }
        EnumSet<FaultKind> set = EnumSet.noneOf(FaultKind.class);
        for (FaultKind k : kinds) {
            if (NEVER_RETRY.contains(k)) {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "retryOn", k + " is never retryable");
            }
            set.add(k);
        }
        return new RetryPolicy(maxAttempts, backoff, jitter, set);
    }

    public int maxAttempts() { return maxAttempts; }
    public Duration initialBackoff() { return initialBackoff; }
    public double jitter() { return jitter; }
    public Set<FaultKind> retryOn() { return retryOn; }

    /**
     * Returns true if this policy would retry the given fault kind.
     */
    public boolean shouldRetry(FaultKind kind) {
        return kind != null && retryOn.contains(kind) && !NEVER_RETRY.contains(kind);
    }

    /**
     * Computes the backoff duration for the given attempt number (1-based).
     */
    public Duration backoffFor(int attempt) {
        if (attempt <= 1 || initialBackoff.isZero()) {
            return Duration.ZERO;
        }
        long baseNanos = initialBackoff.toNanos() * (1L << (attempt - 2));
        if (jitter > 0) {
            double jitterAmount = baseNanos * jitter * (2 * Math.random() - 1);
            baseNanos = Math.max(0, baseNanos + (long) jitterAmount);
        }
        return Duration.ofNanos(baseNanos);
    }

    @Override
    public String toString() {
        return "RetryPolicy{maxAttempts=" + maxAttempts + ", backoff=" + initialBackoff
                + ", jitter=" + jitter + ", retryOn=" + retryOn + '}';
    }
}
