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
package com.spectrayan.spector.memory.pathway.pipeline;

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

    /**
     * Parses a mode string into a {@code GraphExpansionMode}, defaulting to {@link #GATED}.
     *
     * @param modeStr the mode string (case-insensitive), may be {@code null}
     * @return the parsed mode, or {@link #GATED} if {@code modeStr} is null, blank, or invalid
     */
    public static GraphExpansionMode fromString(String modeStr) {
        if (modeStr != null && !modeStr.isBlank()) {
            try {
                return valueOf(modeStr.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return GATED;
    }
}
