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
        String defaultQuery,
        java.util.List<String> constitutionTags,
        java.util.List<String> scarTags,
        java.util.List<String> livedTags,
        java.util.List<String> habitTags,
        java.util.List<String> stateTags,
        byte scarMaxValence
) {

    public static final int DEFAULT_SEMANTIC_TOP_K = 8;
    public static final int DEFAULT_EPISODIC_TOP_K = 5;
    public static final int DEFAULT_PROCEDURAL_TOP_K = 5;
    public static final int DEFAULT_WORKING_TOP_K = 5;
    public static final String DEFAULT_QUERY = "self";

    public static final java.util.List<String> DEFAULT_CONSTITUTION_TAGS = java.util.List.of("dogma", "value", "belief", "constraint", "soul");
    public static final java.util.List<String> DEFAULT_SCAR_TAGS = java.util.List.of("scar", "incident", "failure", "outage", "vulnerability");
    public static final java.util.List<String> DEFAULT_LIVED_TAGS = java.util.List.of("lived", "reaction", "decision", "waking", "enactment");
    public static final java.util.List<String> DEFAULT_HABIT_TAGS = java.util.List.of("habit", "playbook", "procedure");
    public static final java.util.List<String> DEFAULT_STATE_TAGS = java.util.List.of("affect", "stance", "open_loop", "active_task");
    public static final byte DEFAULT_SCAR_MAX_VALENCE = (byte) -10;

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
        constitutionTags = (constitutionTags != null) ? java.util.List.copyOf(constitutionTags) : DEFAULT_CONSTITUTION_TAGS;
        scarTags = (scarTags != null) ? java.util.List.copyOf(scarTags) : DEFAULT_SCAR_TAGS;
        livedTags = (livedTags != null) ? java.util.List.copyOf(livedTags) : DEFAULT_LIVED_TAGS;
        habitTags = (habitTags != null) ? java.util.List.copyOf(habitTags) : DEFAULT_HABIT_TAGS;
        stateTags = (stateTags != null) ? java.util.List.copyOf(stateTags) : DEFAULT_STATE_TAGS;
    }

    public RecallConfig(int semanticTopK, int episodicTopK, int proceduralTopK, int workingTopK, String defaultQuery) {
        this(
                semanticTopK,
                episodicTopK,
                proceduralTopK,
                workingTopK,
                defaultQuery,
                DEFAULT_CONSTITUTION_TAGS,
                DEFAULT_SCAR_TAGS,
                DEFAULT_LIVED_TAGS,
                DEFAULT_HABIT_TAGS,
                DEFAULT_STATE_TAGS,
                DEFAULT_SCAR_MAX_VALENCE
        );
    }

    public static RecallConfig defaultConfig() {
        return new RecallConfig(
                DEFAULT_SEMANTIC_TOP_K,
                DEFAULT_EPISODIC_TOP_K,
                DEFAULT_PROCEDURAL_TOP_K,
                DEFAULT_WORKING_TOP_K,
                DEFAULT_QUERY,
                DEFAULT_CONSTITUTION_TAGS,
                DEFAULT_SCAR_TAGS,
                DEFAULT_LIVED_TAGS,
                DEFAULT_HABIT_TAGS,
                DEFAULT_STATE_TAGS,
                DEFAULT_SCAR_MAX_VALENCE
        );
    }

    /**
     * Returns a lightweight copy of this configuration with scaled-down top-K values for low-intensity queries.
     *
     * @param maxTopK the maximum top-k allowed for any individual cue
     * @return lightweight RecallConfig
     */
    public RecallConfig lightweight(int maxTopK) {
        return new RecallConfig(
                Math.max(1, Math.min(this.semanticTopK, maxTopK)),
                Math.max(1, Math.min(this.episodicTopK, Math.max(1, maxTopK / 2))),
                Math.max(1, Math.min(this.proceduralTopK, Math.max(1, maxTopK / 2))),
                Math.max(1, Math.min(this.workingTopK, Math.max(1, maxTopK / 2))),
                this.defaultQuery,
                this.constitutionTags,
                this.scarTags,
                this.livedTags,
                this.habitTags,
                this.stateTags,
                this.scarMaxValence
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
        private java.util.List<String> constitutionTags = DEFAULT_CONSTITUTION_TAGS;
        private java.util.List<String> scarTags = DEFAULT_SCAR_TAGS;
        private java.util.List<String> livedTags = DEFAULT_LIVED_TAGS;
        private java.util.List<String> habitTags = DEFAULT_HABIT_TAGS;
        private java.util.List<String> stateTags = DEFAULT_STATE_TAGS;
        private byte scarMaxValence = DEFAULT_SCAR_MAX_VALENCE;

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

        public Builder constitutionTags(java.util.List<String> tags) {
            this.constitutionTags = tags;
            return this;
        }

        public Builder scarTags(java.util.List<String> tags) {
            this.scarTags = tags;
            return this;
        }

        public Builder livedTags(java.util.List<String> tags) {
            this.livedTags = tags;
            return this;
        }

        public Builder habitTags(java.util.List<String> tags) {
            this.habitTags = tags;
            return this;
        }

        public Builder stateTags(java.util.List<String> tags) {
            this.stateTags = tags;
            return this;
        }

        public Builder scarMaxValence(byte valence) {
            this.scarMaxValence = valence;
            return this;
        }

        public RecallConfig build() {
            return new RecallConfig(
                    semanticTopK,
                    episodicTopK,
                    proceduralTopK,
                    workingTopK,
                    defaultQuery,
                    constitutionTags,
                    scarTags,
                    livedTags,
                    habitTags,
                    stateTags,
                    scarMaxValence
            );
        }
    }
}
