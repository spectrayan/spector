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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Self-described cultural identity — used exclusively for self-relevance matching.
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><b>All fields are free-text</b> — never enums or checkboxes. Cultural identity
 *       is too complex and personal for constrained selection. A user may identify as
 *       "Japanese-American", "Afro-Caribbean", "Ashkenazi Jewish", or
 *       "secular Buddhist with Hindu family roots." Enums cannot capture this.</li>
 *   <li><b>Self-described only</b> — the system never infers or assigns cultural identity.
 *       Users explicitly choose what to share. Null/empty means "I choose not to share."</li>
 *   <li><b>Embedding-based matching</b> — cultural fields are combined into a single
 *       embedding at profile-save time. Matching is pure semantic similarity, never
 *       string matching or keyword detection.</li>
 * </ul>
 *
 * <h3>Anti-Discrimination Guardrail (Layer 4)</h3>
 * <p>Cultural identity fields produce <b>ONLY positive self-relevance boosts</b>. They
 * NEVER suppress, penalize, or differentially weight memories based on cultural content.
 * Implementation: {@code Math.max(1.0f, culturalBoost)} — cultural similarity can only
 * increase, never decrease, importance.</p>
 *
 * <p>Example: A Japanese-American user gets a self-relevance boost for memories about
 * Japanese culture. They NEVER get a penalty for memories about Korean or Nigerian culture.</p>
 *
 * <h3>Cross-Cultural Validity</h3>
 * <p>The Big Five personality model has been validated primarily in WEIRD populations
 * (Western, Educated, Industrialized, Rich, Democratic). Cultural identity fields
 * compensate for this limitation by allowing users to express collectivist values
 * (e.g., "filial piety", "community over individual") that may not map cleanly to
 * Big Five dimensions but are crucial for self-relevance matching.</p>
 *
 * @param ethnicity       self-described ethnicity (e.g., "Japanese-American", "Yoruba")
 * @param race            self-described race (e.g., "Asian", "Black", "Mixed-race")
 * @param religion        religious/spiritual identity (e.g., "Buddhist", "None", "Spiritual")
 * @param culturalHeritage narrative description (e.g., "Second-generation Korean immigrant")
 * @param culturalValues  core cultural values (e.g., ["filial piety", "community first"])
 * @param traditions      cultural traditions observed (e.g., ["Lunar New Year", "Diwali"])
 * @param primaryCulture  primary cultural identification for embedding (e.g., "Japanese")
 * @param cultureEmbedding pre-computed embedding of primaryCulture + values + heritage
 *                         (computed at profile-save time by enterprise layer)
 */
public record CulturalIdentity(
        String ethnicity,
        String race,
        String religion,
        String culturalHeritage,
        List<String> culturalValues,
        List<String> traditions,
        String primaryCulture,
        float[] cultureEmbedding
) {

    /**
     * Compact constructor — enforces immutability.
     */
    public CulturalIdentity {
        culturalValues = culturalValues != null
                ? Collections.unmodifiableList(culturalValues)
                : List.of();
        traditions = traditions != null
                ? Collections.unmodifiableList(traditions)
                : List.of();
        // Defensive copy of embedding
        if (cultureEmbedding != null) {
            cultureEmbedding = Arrays.copyOf(cultureEmbedding, cultureEmbedding.length);
        }
    }

    /**
     * Empty cultural identity — "I choose not to share."
     * Produces no scoring effect.
     */
    public static final CulturalIdentity NONE = new CulturalIdentity(
            null, null, null, null, List.of(), List.of(), null, null);

    /**
     * Returns true if this identity has any meaningful data.
     */
    public boolean isPresent() {
        return (ethnicity != null && !ethnicity.isBlank())
                || (race != null && !race.isBlank())
                || (religion != null && !religion.isBlank())
                || (culturalHeritage != null && !culturalHeritage.isBlank())
                || (primaryCulture != null && !primaryCulture.isBlank())
                || !culturalValues.isEmpty()
                || !traditions.isEmpty();
    }

    /**
     * Returns true if this identity has a pre-computed embedding for semantic matching.
     */
    public boolean hasEmbedding() {
        return cultureEmbedding != null && cultureEmbedding.length > 0;
    }

    /**
     * Returns the embedding dimensionality, or 0 if no embedding.
     */
    public int dimensions() {
        return cultureEmbedding != null ? cultureEmbedding.length : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CulturalIdentity other)) return false;
        return java.util.Objects.equals(ethnicity, other.ethnicity)
                && java.util.Objects.equals(race, other.race)
                && java.util.Objects.equals(religion, other.religion)
                && java.util.Objects.equals(culturalHeritage, other.culturalHeritage)
                && java.util.Objects.equals(culturalValues, other.culturalValues)
                && java.util.Objects.equals(traditions, other.traditions)
                && java.util.Objects.equals(primaryCulture, other.primaryCulture)
                && Arrays.equals(cultureEmbedding, other.cultureEmbedding);
    }

    @Override
    public int hashCode() {
        int result = java.util.Objects.hash(ethnicity, race, religion,
                culturalHeritage, culturalValues, traditions, primaryCulture);
        result = 31 * result + Arrays.hashCode(cultureEmbedding);
        return result;
    }

    @Override
    public String toString() {
        return "CulturalIdentity[primaryCulture=" + primaryCulture
                + ", ethnicity=" + ethnicity
                + ", values=" + culturalValues.size() + " items"
                + ", dims=" + dimensions() + "]";
    }
}
