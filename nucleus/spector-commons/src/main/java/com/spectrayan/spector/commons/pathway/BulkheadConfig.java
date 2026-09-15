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

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for a named bulkhead (concurrency isolation).
 *
 * <p>Per ADR-0036 §10, a bulkhead limits concurrent in-flight calls to prevent Dream
 * from enqueuing 200 Remember conducts on top of live Recall. Virtual threads make this
 * a logical cap, not a platform-thread cap.</p>
 *
 * @see BulkheadRelay
 * @see BulkheadRegistry
 */
public final class BulkheadConfig {

    public static final int DEFAULT_MAX_IN_FLIGHT = 8;
    public static final Duration DEFAULT_WAIT = Duration.ZERO;
    public static final OnReject DEFAULT_ON_REJECT = OnReject.FAIL;

    private final int maxInFlight;
    private final Duration wait;
    private final OnReject onReject;

    private BulkheadConfig(int maxInFlight, Duration wait, OnReject onReject) {
        if (maxInFlight < 1) {
            throw new IllegalArgumentException("maxInFlight must be >= 1, got " + maxInFlight);
        }
        this.maxInFlight = maxInFlight;
        this.wait = Objects.requireNonNull(wait, "wait cannot be null");
        this.onReject = Objects.requireNonNull(onReject, "onReject cannot be null");
    }

    /**
     * Creates a bulkhead configuration with the given parameters.
     *
     * @param maxInFlight maximum concurrent permits
     * @param wait        wait duration for permit acquisition (0 = non-blocking)
     * @param onReject    action when permit cannot be acquired
     * @return bulkhead config
     */
    public static BulkheadConfig of(int maxInFlight, Duration wait, OnReject onReject) {
        return new BulkheadConfig(maxInFlight, wait, onReject);
    }

    /**
     * Creates a bulkhead with non-blocking rejection.
     *
     * @param maxInFlight maximum concurrent permits
     * @return bulkhead config
     */
    public static BulkheadConfig of(int maxInFlight) {
        return of(maxInFlight, DEFAULT_WAIT, DEFAULT_ON_REJECT);
    }

    /**
     * Creates a bulkhead with specified wait and rejection mode.
     *
     * @param maxInFlight maximum concurrent permits
     * @param waitMs      wait in milliseconds
     * @param onReject    action on rejection
     * @return bulkhead config
     */
    public static BulkheadConfig of(int maxInFlight, long waitMs, OnReject onReject) {
        return of(maxInFlight, Duration.ofMillis(waitMs), onReject);
    }

    public int maxInFlight() { return maxInFlight; }
    public Duration waitDuration() { return wait; }
    public OnReject onReject() { return onReject; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BulkheadConfig that)) return false;
        return maxInFlight == that.maxInFlight
                && wait.equals(that.wait)
                && onReject == that.onReject;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxInFlight, wait, onReject);
    }

    @Override
    public String toString() {
        return "BulkheadConfig{maxInFlight=" + maxInFlight + ", wait=" + wait
                + ", onReject=" + onReject + '}';
    }
}
