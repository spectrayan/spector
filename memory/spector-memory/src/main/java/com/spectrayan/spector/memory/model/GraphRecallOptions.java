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
package com.spectrayan.spector.memory.model;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;

/**
 * Immutable options for graph traversal queries with a builder pattern.
 * <p>
 * Example usage:
 * <pre>
 * GraphRecallOptions options = GraphRecallOptions.builder()
 *     .startEntity("John Doe")
 *     .maxHops(2)
 *     .topPaths(5)
 *     .build();
 * </pre>
 */
public record GraphRecallOptions(
    String startEntity,
    String query,
    String targetEntity,
    int maxHops,
    int topPaths,
    Set<String> entityTypeFilters,
    Set<String> relationTypeFilters,
    boolean includeMemories,
    Instant asOf,
    boolean includeSuperseded
) {
    public GraphRecallOptions {
        if (entityTypeFilters != null) {
            entityTypeFilters = Set.copyOf(entityTypeFilters);
        } else {
            entityTypeFilters = null;
        }
        
        if (relationTypeFilters != null) {
            relationTypeFilters = Set.copyOf(relationTypeFilters);
        } else {
            relationTypeFilters = null;
        }
    }

    public static final GraphRecallOptions DEFAULT = new Builder().build();

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String startEntity = null;
        private String query = null;
        private String targetEntity = null;
        private int maxHops = 3;
        private int topPaths = 10;
        private Set<String> entityTypeFilters = null;
        private Set<String> relationTypeFilters = null;
        private boolean includeMemories = true;
        private Instant asOf = null;
        private boolean includeSuperseded = false;

        public Builder startEntity(String startEntity) {
            this.startEntity = startEntity;
            return this;
        }

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder targetEntity(String targetEntity) {
            this.targetEntity = targetEntity;
            return this;
        }

        public Builder maxHops(int maxHops) {
            this.maxHops = maxHops;
            return this;
        }

        public Builder topPaths(int topPaths) {
            this.topPaths = topPaths;
            return this;
        }

        public Builder entityTypeFilters(Set<String> entityTypeFilters) {
            this.entityTypeFilters = entityTypeFilters;
            return this;
        }

        public Builder relationTypeFilters(Set<String> relationTypeFilters) {
            this.relationTypeFilters = relationTypeFilters;
            return this;
        }

        public Builder includeMemories(boolean includeMemories) {
            this.includeMemories = includeMemories;
            return this;
        }

        public Builder asOf(Instant asOf) {
            this.asOf = asOf;
            return this;
        }

        public Builder includeSuperseded(boolean includeSuperseded) {
            this.includeSuperseded = includeSuperseded;
            return this;
        }

        public GraphRecallOptions build() {
            int clampedMaxHops = Math.max(1, Math.min(this.maxHops, 5));
            int clampedTopPaths = Math.max(1, Math.min(this.topPaths, 50));

            return new GraphRecallOptions(
                this.startEntity,
                this.query,
                this.targetEntity,
                clampedMaxHops,
                clampedTopPaths,
                this.entityTypeFilters,
                this.relationTypeFilters,
                this.includeMemories,
                this.asOf,
                this.includeSuperseded
            );
        }
    }
}
