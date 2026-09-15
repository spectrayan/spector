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
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Shared configuration for a named {@link CircuitBreaker}.
 *
 * <p>Defines failure thresholds, recovery cooldown, half-open trial concurrency and successes,
 * and the specific {@link FaultKind} set that trips the circuit.</p>
 */
public final class CircuitBreakerConfig {

    public static final int DEFAULT_FAILURE_THRESHOLD = 5;
    public static final Duration DEFAULT_COOLDOWN = Duration.ofSeconds(30);
    public static final int DEFAULT_HALF_OPEN_PROBES = 1;
    public static final int DEFAULT_HALF_OPEN_SUCCESSES = 1;
    public static final Set<FaultKind> DEFAULT_TRIP_ON = Collections.unmodifiableSet(
            EnumSet.of(FaultKind.TRANSIENT, FaultKind.DOWNSTREAM));

    private final int failureThreshold;
    private final Duration cooldown;
    private final int halfOpenProbes;
    private final int halfOpenSuccesses;
    private final Set<FaultKind> tripOn;

    private CircuitBreakerConfig(final Builder builder) {
        this.failureThreshold = builder.failureThreshold;
        this.cooldown = builder.cooldown;
        this.halfOpenProbes = builder.halfOpenProbes;
        this.halfOpenSuccesses = builder.halfOpenSuccesses;
        this.tripOn = Collections.unmodifiableSet(EnumSet.copyOf(builder.tripOn));
    }

    public int failureThreshold() {
        return failureThreshold;
    }

    public Duration cooldown() {
        return cooldown;
    }

    public int halfOpenProbes() {
        return halfOpenProbes;
    }

    public int halfOpenSuccesses() {
        return halfOpenSuccesses;
    }

    public Set<FaultKind> tripOn() {
        return tripOn;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CircuitBreakerConfig inProcess() {
        return builder()
                .failureThreshold(20)
                .cooldown(Duration.ofSeconds(5))
                .build();
    }

    public static CircuitBreakerConfig remote() {
        return builder()
                .failureThreshold(DEFAULT_FAILURE_THRESHOLD)
                .cooldown(DEFAULT_COOLDOWN)
                .build();
    }

    public static CircuitBreakerConfig defaultConfig() {
        return remote();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof CircuitBreakerConfig that)) return false;
        return failureThreshold == that.failureThreshold
                && halfOpenProbes == that.halfOpenProbes
                && halfOpenSuccesses == that.halfOpenSuccesses
                && Objects.equals(cooldown, that.cooldown)
                && Objects.equals(tripOn, that.tripOn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(failureThreshold, cooldown, halfOpenProbes, halfOpenSuccesses, tripOn);
    }

    @Override
    public String toString() {
        return "CircuitBreakerConfig{" +
                "failureThreshold=" + failureThreshold +
                ", cooldown=" + cooldown +
                ", halfOpenProbes=" + halfOpenProbes +
                ", halfOpenSuccesses=" + halfOpenSuccesses +
                ", tripOn=" + tripOn +
                '}';
    }

    public static final class Builder {
        private int failureThreshold = DEFAULT_FAILURE_THRESHOLD;
        private Duration cooldown = DEFAULT_COOLDOWN;
        private int halfOpenProbes = DEFAULT_HALF_OPEN_PROBES;
        private int halfOpenSuccesses = DEFAULT_HALF_OPEN_SUCCESSES;
        private Set<FaultKind> tripOn = EnumSet.of(FaultKind.TRANSIENT, FaultKind.DOWNSTREAM);

        private Builder() {}

        public Builder failureThreshold(final int failureThreshold) {
            if (failureThreshold <= 0) {
                throw new IllegalArgumentException("failureThreshold must be positive");
            }
            this.failureThreshold = failureThreshold;
            return this;
        }

        public Builder cooldown(final Duration cooldown) {
            this.cooldown = Objects.requireNonNull(cooldown, "cooldown cannot be null");
            if (cooldown.isNegative() || cooldown.isZero()) {
                throw new IllegalArgumentException("cooldown must be positive");
            }
            return this;
        }

        public Builder halfOpenProbes(final int halfOpenProbes) {
            if (halfOpenProbes <= 0) {
                throw new IllegalArgumentException("halfOpenProbes must be positive");
            }
            this.halfOpenProbes = halfOpenProbes;
            return this;
        }

        public Builder halfOpenSuccesses(final int halfOpenSuccesses) {
            if (halfOpenSuccesses <= 0) {
                throw new IllegalArgumentException("halfOpenSuccesses must be positive");
            }
            this.halfOpenSuccesses = halfOpenSuccesses;
            return this;
        }

        public Builder tripOn(final Set<FaultKind> tripOn) {
            Objects.requireNonNull(tripOn, "tripOn cannot be null");
            if (tripOn.isEmpty()) {
                throw new IllegalArgumentException("tripOn cannot be empty");
            }
            this.tripOn = EnumSet.copyOf(tripOn);
            return this;
        }

        public Builder tripOn(final FaultKind first, final FaultKind... rest) {
            this.tripOn = EnumSet.of(first, rest);
            return this;
        }

        public CircuitBreakerConfig build() {
            return new CircuitBreakerConfig(this);
        }
    }
}
