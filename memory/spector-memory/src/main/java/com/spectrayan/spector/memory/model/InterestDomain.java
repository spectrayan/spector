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

/**
 * A semantic interest domain — a topic the user/agent/tenant cares about.
 *
 * <h3>Biological Analog: Attentional Salience</h3>
 * <p>The brain's salience network (anterior insula + dorsal anterior cingulate cortex)
 * filters incoming stimuli based on learned relevance. An ER doctor's brain fires
 * strongly on "cardiac arrest" but barely notices "quarterly budget review."
 * InterestDomain models this selective attention computationally.</p>
 *
 * <h3>How It Works</h3>
 * <p>The user expresses interest in natural language ("database performance").
 * The enterprise layer pre-computes the embedding vector when the profile is saved.
 * At ingestion time, the core engine computes cosine similarity between the memory's
 * embedding and this interest's embedding — no string matching, pure semantic.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 *   // User says: "I care about database performance"
 *   var interest = new InterestDomain("database performance", InterestLevel.HIGH, embedding);
 *
 *   // Memory arrives: "PostgreSQL query optimizer regression after v16 upgrade"
 *   // cosine(memoryEmbed, interestEmbed) = 0.82 → HIGH match → 1.5× boost
 * }</pre>
 *
 * @param topic     natural language description of the interest
 * @param level     intensity level (CRITICAL, HIGH, MEDIUM, LOW, IGNORE)
 * @param embedding pre-computed embedding vector for semantic matching (nullable for tests)
 */
public record InterestDomain(String topic, InterestLevel level, float[] embedding) {

    /**
     * Compact constructor — validates inputs.
     */
    public InterestDomain {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Interest topic must not be null or blank");
        }
        if (level == null) {
            throw new IllegalArgumentException("Interest level must not be null");
        }
        // Defensive copy of embedding
        if (embedding != null) {
            embedding = Arrays.copyOf(embedding, embedding.length);
        }
    }

    /**
     * Convenience constructor without embedding — for tests and config files.
     * Semantic matching is disabled; only tag-based keyword matching is used.
     */
    public InterestDomain(String topic, InterestLevel level) {
        this(topic, level, null);
    }

    /**
     * Returns true if this interest has a pre-computed embedding for semantic matching.
     */
    public boolean hasEmbedding() {
        return embedding != null && embedding.length > 0;
    }

    /**
     * Returns the embedding dimensionality, or 0 if no embedding.
     */
    public int dimensions() {
        return embedding != null ? embedding.length : 0;
    }

    @Override
    public String toString() {
        return "InterestDomain[topic=" + topic + ", level=" + level
                + ", dims=" + dimensions() + "]";
    }

    // Override equals/hashCode to use Arrays.equals for float[]
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InterestDomain other)) return false;
        return topic.equals(other.topic)
                && level == other.level
                && Arrays.equals(embedding, other.embedding);
    }

    @Override
    public int hashCode() {
        int result = topic.hashCode();
        result = 31 * result + level.hashCode();
        result = 31 * result + Arrays.hashCode(embedding);
        return result;
    }
}
