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

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Filter specification for selecting candidate episodic sessions during a reflection sweep.
 *
 * @param sessionIds         optional explicit set of session IDs to process (null or empty = all sessions)
 * @param sessionIdAfter     exclusive watermark cursor; only sessions strictly after this ID will be considered
 * @param from               inclusive lower bound for session turn timestamp (null = unbounded)
 * @param to                 inclusive upper bound for session turn timestamp (null = unbounded)
 * @param partitionSeqs      optional partition sequence filter (null or empty = all partitions)
 * @param unconsolidatedOnly true to require that sessions contain unconsolidated turns (default true)
 * @since 1.5.0
 */
public record ReflectFilter(
        Set<Long> sessionIds,
        Long sessionIdAfter,
        Instant from,
        Instant to,
        Set<Integer> partitionSeqs,
        boolean unconsolidatedOnly
) {
    public ReflectFilter {
        sessionIds = (sessionIds != null && !sessionIds.isEmpty()) ? Set.copyOf(sessionIds) : null;
        partitionSeqs = (partitionSeqs != null && !partitionSeqs.isEmpty()) ? Set.copyOf(partitionSeqs) : null;
    }

    /**
     * Checks if an episodic session satisfies this filter's criteria.
     *
     * @param sessionId    the episodic session ID
     * @param timestampMs  the session timestamp in epoch milliseconds
     * @param partitionSeq the partition sequence ID
     * @return true if the session passes all active filter constraints
     */
    public boolean matches(long sessionId, long timestampMs, int partitionSeq) {
        if (sessionIds != null && !sessionIds.contains(sessionId)) {
            return false;
        }
        if (sessionIdAfter != null && sessionId <= sessionIdAfter) {
            return false;
        }
        if (partitionSeqs != null && !partitionSeqs.contains(partitionSeq)) {
            return false;
        }
        if (from != null && timestampMs < from.toEpochMilli()) {
            return false;
        }
        if (to != null && timestampMs > to.toEpochMilli()) {
            return false;
        }
        return true;
    }

    public static ReflectFilter all() {
        return new ReflectFilter(null, null, null, null, null, false);
    }

    public static ReflectFilter unconsolidated() {
        return new ReflectFilter(null, null, null, null, null, true);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Set<Long> sessionIds;
        private Long sessionIdAfter;
        private Instant from;
        private Instant to;
        private Set<Integer> partitionSeqs;
        private boolean unconsolidatedOnly = true;

        public Builder sessionIds(Set<Long> sessionIds) {
            this.sessionIds = sessionIds;
            return this;
        }

        public Builder sessionIdAfter(Long sessionIdAfter) {
            this.sessionIdAfter = sessionIdAfter;
            return this;
        }

        public Builder from(Instant from) {
            this.from = from;
            return this;
        }

        public Builder to(Instant to) {
            this.to = to;
            return this;
        }

        public Builder partitionSeqs(Set<Integer> partitionSeqs) {
            this.partitionSeqs = partitionSeqs;
            return this;
        }

        public Builder unconsolidatedOnly(boolean unconsolidatedOnly) {
            this.unconsolidatedOnly = unconsolidatedOnly;
            return this;
        }

        public ReflectFilter build() {
            return new ReflectFilter(sessionIds, sessionIdAfter, from, to, partitionSeqs, unconsolidatedOnly);
        }
    }
}
