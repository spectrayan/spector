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

/**
 * Educational background entry — represents a single degree or qualification.
 *
 * <h3>Cognitive Relevance</h3>
 * <p>Education field is a primary component of the self-schema. Expert memory
 * research (Chase &amp; Simon, 1973; Ericsson &amp; Kintsch, 1995) demonstrates
 * that domain expertise creates encoding scaffolding — a CS graduate encodes
 * technical content more deeply than non-technical content because existing
 * knowledge structures provide "hooks" for new information.</p>
 *
 * <h3>Schema Origin</h3>
 * <p>Mirrors {@code consciousness/identity/Education.yaml} with identical fields.</p>
 *
 * <h3>Scoring Usage</h3>
 * <p>The {@code degree} field is embedded at profile-save time and used for
 * self-relevance matching via cosine similarity in
 * {@link SalienceProfile#computeSelfRelevanceBoost}.</p>
 *
 * @param institution educational institution name (e.g., "Stanford University")
 * @param degree      degree obtained (e.g., "Bachelor of Science in Computer Science")
 * @param startYear   year education started
 * @param endYear     year education completed (nullable if ongoing)
 * @param description additional details (e.g., "Graduated with honors, GPA 3.8")
 */
public record Education(
        String institution,
        String degree,
        int startYear,
        Integer endYear,
        String description
) {

    /**
     * Compact constructor — validates required fields.
     */
    public Education {
        if (institution == null || institution.isBlank()) {
            throw new IllegalArgumentException("Education institution must not be null or blank");
        }
        if (degree == null || degree.isBlank()) {
            throw new IllegalArgumentException("Education degree must not be null or blank");
        }
        if (startYear < 1900 || startYear > 2100) {
            throw new IllegalArgumentException("Start year must be between 1900 and 2100");
        }
        if (endYear != null && endYear < startYear) {
            throw new IllegalArgumentException("End year must not be before start year");
        }
    }

    /**
     * Convenience constructor without description.
     */
    public Education(String institution, String degree, int startYear, Integer endYear) {
        this(institution, degree, startYear, endYear, null);
    }

    /**
     * Returns true if education is ongoing (no end year).
     */
    public boolean isOngoing() {
        return endYear == null;
    }
}
