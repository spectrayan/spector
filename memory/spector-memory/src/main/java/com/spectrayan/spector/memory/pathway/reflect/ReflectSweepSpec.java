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
package com.spectrayan.spector.memory.pathway.reflect;

import com.spectrayan.spector.memory.pathway.reflect.spi.ReflectBackpressurePolicy;
import com.spectrayan.spector.memory.pathway.reflect.spi.local.NoopBackpressurePolicy;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Specification defining parameters, limits, and behavior for a reflection sweep execution.
 *
 * @param sweepId             unique identifier for this sweep execution
 * @param filter              filter determining candidate episodic sessions to consolidate
 * @param sessionLimit        maximum number of sessions to process (<= 0 means unlimited)
 * @param maxTurnsPerSession  maximum turns permitted per session before truncation/sampling (<= 0 means unlimited)
 * @param timeBudget          hard execution deadline for the sweep (null means unlimited)
 * @param runCompanionRelays  whether to execute downstream biological maintenance relays (e.g. Hebbian decay, pruning)
 * @param backpressure        rate-limiting and error-handling policy between sessions (null defaults to Noop)
 * @since 1.5.0
 */
public record ReflectSweepSpec(
        String sweepId,
        ReflectFilter filter,
        int sessionLimit,
        int maxTurnsPerSession,
        Duration timeBudget,
        boolean runCompanionRelays,
        ReflectBackpressurePolicy backpressure
) {
    public static final int DEFAULT_MAX_TURNS_PER_SESSION = 100;

    public ReflectSweepSpec {
        sweepId = (sweepId != null && !sweepId.isBlank()) ? sweepId : "sweep-" + UUID.randomUUID();
        filter = (filter != null) ? filter : ReflectFilter.unconsolidated();
        maxTurnsPerSession = (maxTurnsPerSession > 0) ? maxTurnsPerSession : DEFAULT_MAX_TURNS_PER_SESSION;
        backpressure = (backpressure != null) ? backpressure : NoopBackpressurePolicy.INSTANCE;
    }

    /**
     * Creates a spec representing a standard biological sleep cycle (unbounded sessions, all relays active).
     */
    public static ReflectSweepSpec fullCycle() {
        return new ReflectSweepSpec(
                "full-cycle",
                ReflectFilter.unconsolidated(),
                0,
                DEFAULT_MAX_TURNS_PER_SESSION,
                null,
                true,
                NoopBackpressurePolicy.INSTANCE
        );
    }

    /**
     * Creates a spec for a lightweight consolidation-only tick (e.g. circadian or continuous background sweep).
     *
     * @param sessionLimit maximum number of sessions to consolidate in this tick
     */
    public static ReflectSweepSpec consolidationOnly(int sessionLimit) {
        return new ReflectSweepSpec(
                "consolidation-tick",
                ReflectFilter.unconsolidated(),
                sessionLimit,
                DEFAULT_MAX_TURNS_PER_SESSION,
                null,
                false,
                NoopBackpressurePolicy.INSTANCE
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String sweepId;
        private ReflectFilter filter = ReflectFilter.unconsolidated();
        private int sessionLimit = 0;
        private int maxTurnsPerSession = DEFAULT_MAX_TURNS_PER_SESSION;
        private Duration timeBudget;
        private boolean runCompanionRelays = true;
        private ReflectBackpressurePolicy backpressure = NoopBackpressurePolicy.INSTANCE;

        public Builder sweepId(String sweepId) {
            this.sweepId = sweepId;
            return this;
        }

        public Builder filter(ReflectFilter filter) {
            this.filter = filter;
            return this;
        }

        public Builder sessionLimit(int sessionLimit) {
            this.sessionLimit = sessionLimit;
            return this;
        }

        public Builder maxTurnsPerSession(int maxTurnsPerSession) {
            this.maxTurnsPerSession = maxTurnsPerSession;
            return this;
        }

        public Builder timeBudget(Duration timeBudget) {
            this.timeBudget = timeBudget;
            return this;
        }

        public Builder runCompanionRelays(boolean runCompanionRelays) {
            this.runCompanionRelays = runCompanionRelays;
            return this;
        }

        public Builder backpressure(ReflectBackpressurePolicy backpressure) {
            this.backpressure = backpressure;
            return this;
        }

        public ReflectSweepSpec build() {
            return new ReflectSweepSpec(sweepId, filter, sessionLimit, maxTurnsPerSession, timeBudget, runCompanionRelays, backpressure);
        }
    }
}
