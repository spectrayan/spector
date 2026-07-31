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
package com.spectrayan.spector.memory.pipeline;

/**
 * Controls when and how graph expansion is triggered during recall.
 *
 * <p>The mode determines whether the similarity-gated threshold check is applied
 * before performing Hebbian, temporal, and entity graph expansion.</p>
 *
 * <ul>
 *   <li>{@link #GATED} — Default production mode. Graph expansion fires only when
 *       the best direct similarity result is <em>below</em> the configured threshold,
 *       avoiding dilution of already-strong direct results.</li>
 *   <li>{@link #ALWAYS} — Diagnostic mode. Graph expansion fires unconditionally
 *       on every query, ignoring the threshold. <strong>Not recommended for production</strong>
 *       as it can severely degrade ranking quality by flooding the candidate pool.</li>
 *   <li>{@link #ENTITY_ONLY} — Selective mode. Graph expansion fires only for queries
 *       that contain at least one resolvable entity hint or entity mention. Useful for
 *       benchmarking entity-graph contribution without affecting non-entity queries.</li>
 * </ul>
 */
public enum GraphExpansionMode {

    /**
     * Default: expand only when direct similarity is below the configured threshold.
     */
    GATED,

    /**
     * Diagnostic: always expand, ignoring the threshold. Use only for benchmarking.
     */
    ALWAYS,

    /**
     * Selective: expand only when the query has resolvable entity hints.
     */
    ENTITY_ONLY;

    /** System property key for configuring the graph expansion mode at runtime. */
    public static final String SYSTEM_PROPERTY = "spector.memory.graphExpansionMode";

    /**
     * Resolves the expansion mode from the system property, defaulting to {@link #GATED}.
     *
     * @return the configured or default expansion mode
     */
    public static GraphExpansionMode resolve() {
        String value = System.getProperty(SYSTEM_PROPERTY);
        if (value == null || value.isBlank()) {
            return GATED;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GATED;
        }
    }
}
