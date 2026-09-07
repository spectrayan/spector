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
package com.spectrayan.spector.memory.aisme.enactment;

/**
 * Configuration parameters for the 4-cue self-recall subsystem in Persona Enactment (ADR-0032).
 */
public record RecallConfig(
        int semanticTopK,
        int episodicTopK,
        int proceduralTopK,
        int workingTopK,
        String defaultQuery
) {

    public static final int DEFAULT_SEMANTIC_TOP_K = 8;
    public static final int DEFAULT_EPISODIC_TOP_K = 5;
    public static final int DEFAULT_PROCEDURAL_TOP_K = 5;
    public static final int DEFAULT_WORKING_TOP_K = 5;
    public static final String DEFAULT_QUERY = "self";

    public RecallConfig {
        if (semanticTopK <= 0) {
            throw new IllegalArgumentException("semanticTopK must be positive, got: " + semanticTopK);
        }
        if (episodicTopK <= 0) {
            throw new IllegalArgumentException("episodicTopK must be positive, got: " + episodicTopK);
        }
        if (proceduralTopK <= 0) {
            throw new IllegalArgumentException("proceduralTopK must be positive, got: " + proceduralTopK);
        }
        if (workingTopK <= 0) {
            throw new IllegalArgumentException("workingTopK must be positive, got: " + workingTopK);
        }
        defaultQuery = (defaultQuery != null && !defaultQuery.isBlank()) ? defaultQuery : DEFAULT_QUERY;
    }

    public static RecallConfig defaultConfig() {
        return new RecallConfig(
                DEFAULT_SEMANTIC_TOP_K,
                DEFAULT_EPISODIC_TOP_K,
                DEFAULT_PROCEDURAL_TOP_K,
                DEFAULT_WORKING_TOP_K,
                DEFAULT_QUERY
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int semanticTopK = DEFAULT_SEMANTIC_TOP_K;
        private int episodicTopK = DEFAULT_EPISODIC_TOP_K;
        private int proceduralTopK = DEFAULT_PROCEDURAL_TOP_K;
        private int workingTopK = DEFAULT_WORKING_TOP_K;
        private String defaultQuery = DEFAULT_QUERY;

        public Builder semanticTopK(int semanticTopK) {
            this.semanticTopK = semanticTopK;
            return this;
        }

        public Builder episodicTopK(int episodicTopK) {
            this.episodicTopK = episodicTopK;
            return this;
        }

        public Builder proceduralTopK(int proceduralTopK) {
            this.proceduralTopK = proceduralTopK;
            return this;
        }

        public Builder workingTopK(int workingTopK) {
            this.workingTopK = workingTopK;
            return this;
        }

        public Builder defaultQuery(String defaultQuery) {
            this.defaultQuery = defaultQuery;
            return this;
        }

        public RecallConfig build() {
            return new RecallConfig(semanticTopK, episodicTopK, proceduralTopK, workingTopK, defaultQuery);
        }
    }
}
