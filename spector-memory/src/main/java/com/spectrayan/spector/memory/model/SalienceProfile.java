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

import com.spectrayan.spector.memory.neurodivergent.IcnuWeights;

import java.util.Collections;
import java.util.List;

/**
 * Salience profile — configures what matters to an entity (tenant, agent, or user).
 *
 * <h3>Biological Analog: Salience Network</h3>
 * <p>The brain's salience network (anterior insula + dorsal ACC) continuously
 * evaluates incoming stimuli against learned priorities. A firefighter's brain
 * instantly flags "smoke" as critical while suppressing "quarterly report."
 * SalienceProfile models this selective attention computationally.</p>
 *
 * <h3>Scope Hierarchy</h3>
 * <p>Profiles can be set at three levels with additive merge (tenant-authoritative):</p>
 * <pre>
 *   Tenant (org-wide defaults, authoritative)
 *     └─ Agent (per-agent tuning, additive)
 *         └─ User (individual preferences, additive)
 * </pre>
 *
 * <h3>How Interests Work</h3>
 * <p>Users express interests in <b>natural language</b>. The enterprise layer
 * pre-computes embedding vectors when the profile is saved. At ingestion time,
 * the core engine computes cosine similarity between the memory embedding and
 * each interest embedding — pure semantic matching, no keyword/tag matching.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 *   var profile = SalienceProfile.builder()
 *       .interest("database performance", InterestLevel.CRITICAL)
 *       .interest("Kubernetes orchestration", InterestLevel.HIGH)
 *       .interest("meeting notes", InterestLevel.IGNORE)
 *       .icnuWeights(new IcnuWeights(0.20f, 0.10f, 0.50f, 0.20f))
 *       .alpha(0.5f)
 *       .beta(0.5f)
 *       .build();
 *
 *   // At ingestion: memory "PostgreSQL query optimizer regression"
 *   // cosine("database performance", memory) = 0.82
 *   // boost = CRITICAL.multiplier() × 0.82 = 2.0 × 0.82 = 1.64
 *   // importance *= 1.64
 * }</pre>
 *
 * @param interests           topics to boost (semantic, embedding-based matching)
 * @param disinterests        topics to suppress (semantic, embedding-based matching)
 * @param icnuWeights         custom ICNU fusion weights (null = use system default)
 * @param alpha               similarity weight override (null = use profile default)
 * @param beta                importance weight override (null = use profile default)
 * @param defaultProfile      preferred cognitive profile (null = BALANCED)
 * @param flashbulbThreshold  z-score threshold for flashbulb memory (default: 3.0)
 * @param recencyWeight       recency preference multiplier (default: 1.0)
 * @param similarityThreshold minimum cosine similarity for interest matching (default: 0.5)
 */
public record SalienceProfile(
        List<InterestDomain> interests,
        List<InterestDomain> disinterests,
        IcnuWeights icnuWeights,
        Float alpha,
        Float beta,
        CognitiveProfile defaultProfile,
        float flashbulbThreshold,
        float recencyWeight,
        float similarityThreshold
) {

    /** Default similarity threshold for interest matching. */
    public static final float DEFAULT_SIMILARITY_THRESHOLD = 0.5f;

    /** Default flashbulb z-score threshold. */
    public static final float DEFAULT_FLASHBULB_THRESHOLD = 3.0f;

    /** Neutral profile — no effect on scoring. */
    public static final SalienceProfile NEUTRAL = new SalienceProfile(
            List.of(), List.of(), null, null, null, null,
            DEFAULT_FLASHBULB_THRESHOLD, 1.0f, DEFAULT_SIMILARITY_THRESHOLD);

    /**
     * Compact constructor — enforces immutability.
     */
    public SalienceProfile {
        interests = interests != null
                ? Collections.unmodifiableList(interests)
                : List.of();
        disinterests = disinterests != null
                ? Collections.unmodifiableList(disinterests)
                : List.of();
    }

    /**
     * Computes the cumulative topic boost for a memory embedding.
     *
     * <p>Iterates over all interest and disinterest domains. For each domain
     * that has a pre-computed embedding, computes cosine similarity against the
     * memory embedding. If similarity exceeds {@code similarityThreshold},
     * the domain's {@link InterestLevel#multiplier()} is applied, scaled by
     * the similarity value.</p>
     *
     * <h4>Algorithm</h4>
     * <pre>
     *   boost = 1.0
     *   for each interest:
     *     sim = cosine(memoryEmbedding, interest.embedding)
     *     if sim > threshold:
     *       boost = max(boost, level.multiplier × sim)
     *
     *   for each disinterest:
     *     sim = cosine(memoryEmbedding, disinterest.embedding)
     *     if sim > threshold:
     *       boost = min(boost, level.multiplier × sim)
     *
     *   return boost
     * </pre>
     *
     * <p>Performance: O(N × dims) where N = number of interests.
     * For 10 interests × 768 dims = 7,680 FLOPs — negligible.</p>
     *
     * @param memoryEmbedding the L2-normalized embedding of the incoming memory
     * @return multiplicative boost factor (1.0 = no effect)
     */
    public float computeTopicBoost(float[] memoryEmbedding) {
        if (memoryEmbedding == null || memoryEmbedding.length == 0) {
            return 1.0f;
        }
        if (interests.isEmpty() && disinterests.isEmpty()) {
            return 1.0f;
        }

        float boost = 1.0f;

        // Apply interest boosts (take max match)
        for (InterestDomain interest : interests) {
            if (!interest.hasEmbedding()) continue;
            float sim = cosineSimilarity(memoryEmbedding, interest.embedding());
            if (sim > similarityThreshold) {
                float candidateBoost = interest.level().multiplier() * sim;
                boost = Math.max(boost, candidateBoost);
            }
        }

        // Apply disinterest dampening (take min match — strongest suppression wins)
        for (InterestDomain disinterest : disinterests) {
            if (!disinterest.hasEmbedding()) continue;
            float sim = cosineSimilarity(memoryEmbedding, disinterest.embedding());
            if (sim > similarityThreshold) {
                float candidateDampen = disinterest.level().multiplier() * sim;
                // For dampeners (multiplier < 1.0), lower is stronger suppression
                boost = Math.min(boost, candidateDampen);
            }
        }

        return Math.max(0.01f, boost); // floor to prevent zero importance
    }

    /**
     * Computes cosine similarity between two L2-normalized vectors.
     *
     * <p>For L2-normalized vectors, cosine similarity equals the dot product.
     * This avoids the expensive magnitude computation.</p>
     */
    static float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            // Dimension mismatch — return 0 (no match) rather than throwing.
            // This can happen if the embedding model changes between profile save
            // and memory ingestion.
            return 0f;
        }
        float dot = 0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        // Clamp to [-1, 1] to handle floating-point imprecision
        return Math.clamp(dot, -1.0f, 1.0f);
    }

    /**
     * Returns true if this profile has any configured interests or disinterests.
     */
    public boolean hasInterests() {
        return !interests.isEmpty() || !disinterests.isEmpty();
    }

    /**
     * Returns true if this profile overrides ICNU weights.
     */
    public boolean hasIcnuOverride() {
        return icnuWeights != null;
    }

    /**
     * Returns true if this profile overrides alpha/beta scoring weights.
     */
    public boolean hasScoringOverride() {
        return alpha != null || beta != null;
    }

    /**
     * Returns true if this profile is effectively neutral (no effect).
     */
    public boolean isNeutral() {
        return !hasInterests()
                && !hasIcnuOverride()
                && !hasScoringOverride()
                && defaultProfile == null
                && flashbulbThreshold == DEFAULT_FLASHBULB_THRESHOLD
                && recencyWeight == 1.0f;
    }

    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link SalienceProfile}.
     */
    public static final class Builder {
        private java.util.List<InterestDomain> interests = new java.util.ArrayList<>();
        private java.util.List<InterestDomain> disinterests = new java.util.ArrayList<>();
        private IcnuWeights icnuWeights;
        private Float alpha;
        private Float beta;
        private CognitiveProfile defaultProfile;
        private float flashbulbThreshold = DEFAULT_FLASHBULB_THRESHOLD;
        private float recencyWeight = 1.0f;
        private float similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;

        /** Adds an interest domain. */
        public Builder interest(InterestDomain domain) {
            interests.add(domain);
            return this;
        }

        /** Convenience: adds an interest with topic, level, and embedding. */
        public Builder interest(String topic, InterestLevel level, float[] embedding) {
            return interest(new InterestDomain(topic, level, embedding));
        }

        /** Convenience: adds an interest without embedding (for tests/config). */
        public Builder interest(String topic, InterestLevel level) {
            return interest(new InterestDomain(topic, level));
        }

        /** Adds a disinterest domain (dampener). */
        public Builder disinterest(InterestDomain domain) {
            disinterests.add(domain);
            return this;
        }

        /** Convenience: adds a disinterest with topic, level, and embedding. */
        public Builder disinterest(String topic, InterestLevel level, float[] embedding) {
            return disinterest(new InterestDomain(topic, level, embedding));
        }

        /** Convenience: adds a disinterest without embedding. */
        public Builder disinterest(String topic, InterestLevel level) {
            return disinterest(new InterestDomain(topic, level));
        }

        /** Sets custom ICNU fusion weights. */
        public Builder icnuWeights(IcnuWeights weights) {
            this.icnuWeights = weights;
            return this;
        }

        /** Sets similarity weight override for recall scoring. */
        public Builder alpha(float alpha) {
            this.alpha = alpha;
            return this;
        }

        /** Sets importance weight override for recall scoring. */
        public Builder beta(float beta) {
            this.beta = beta;
            return this;
        }

        /** Sets the default cognitive profile. */
        public Builder defaultProfile(CognitiveProfile profile) {
            this.defaultProfile = profile;
            return this;
        }

        /** Sets the flashbulb z-score threshold. */
        public Builder flashbulbThreshold(float threshold) {
            this.flashbulbThreshold = threshold;
            return this;
        }

        /** Sets the recency weight multiplier. */
        public Builder recencyWeight(float weight) {
            this.recencyWeight = weight;
            return this;
        }

        /** Sets the minimum cosine similarity for interest matching. */
        public Builder similarityThreshold(float threshold) {
            this.similarityThreshold = threshold;
            return this;
        }

        /** Builds an immutable SalienceProfile. */
        public SalienceProfile build() {
            return new SalienceProfile(interests, disinterests, icnuWeights,
                    alpha, beta, defaultProfile, flashbulbThreshold,
                    recencyWeight, similarityThreshold);
        }
    }
}
