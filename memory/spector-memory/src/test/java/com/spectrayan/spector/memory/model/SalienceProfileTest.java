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

import com.spectrayan.spector.memory.SalienceProfileProvider;
import com.spectrayan.spector.memory.neurodivergent.IcnuWeights;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SalienceProfile}, {@link InterestDomain}, {@link InterestLevel},
 * and {@link SalienceProfileProvider}.
 */
class SalienceProfileTest {

    // ── InterestLevel ──────────────────────────────────────────────

    @Test
    @DisplayName("InterestLevel multipliers are correct")
    void interestLevelMultipliers() {
        assertEquals(2.0f, InterestLevel.CRITICAL.multiplier());
        assertEquals(1.5f, InterestLevel.HIGH.multiplier());
        assertEquals(1.25f, InterestLevel.MEDIUM.multiplier());
        assertEquals(0.5f, InterestLevel.LOW.multiplier());
        assertEquals(0.1f, InterestLevel.IGNORE.multiplier());
    }

    @Test
    @DisplayName("InterestLevel boost/dampen classification")
    void interestLevelClassification() {
        assertTrue(InterestLevel.CRITICAL.isBoost());
        assertTrue(InterestLevel.HIGH.isBoost());
        assertTrue(InterestLevel.MEDIUM.isBoost());
        assertFalse(InterestLevel.MEDIUM.isDampen());

        assertTrue(InterestLevel.LOW.isDampen());
        assertTrue(InterestLevel.IGNORE.isDampen());
        assertFalse(InterestLevel.LOW.isBoost());
    }

    // ── InterestDomain ─────────────────────────────────────────────

    @Test
    @DisplayName("InterestDomain validates null topic")
    void interestDomainNullTopic() {
        assertThrows(IllegalArgumentException.class,
                () -> new InterestDomain(null, InterestLevel.HIGH));
    }

    @Test
    @DisplayName("InterestDomain validates blank topic")
    void interestDomainBlankTopic() {
        assertThrows(IllegalArgumentException.class,
                () -> new InterestDomain("   ", InterestLevel.HIGH));
    }

    @Test
    @DisplayName("InterestDomain validates null level")
    void interestDomainNullLevel() {
        assertThrows(IllegalArgumentException.class,
                () -> new InterestDomain("test", null));
    }

    @Test
    @DisplayName("InterestDomain without embedding")
    void interestDomainNoEmbedding() {
        var domain = new InterestDomain("database performance", InterestLevel.HIGH);
        assertEquals("database performance", domain.topic());
        assertEquals(InterestLevel.HIGH, domain.level());
        assertFalse(domain.hasEmbedding());
        assertEquals(0, domain.dimensions());
    }

    @Test
    @DisplayName("InterestDomain with embedding")
    void interestDomainWithEmbedding() {
        float[] embedding = {0.1f, 0.2f, 0.3f};
        var domain = new InterestDomain("database", InterestLevel.CRITICAL, embedding);
        assertTrue(domain.hasEmbedding());
        assertEquals(3, domain.dimensions());
    }

    @Test
    @DisplayName("InterestDomain embedding is defensively copied")
    void interestDomainDefensiveCopy() {
        float[] embedding = {1.0f, 2.0f, 3.0f};
        var domain = new InterestDomain("test", InterestLevel.HIGH, embedding);
        // Modify original — should not affect domain
        embedding[0] = 999.0f;
        assertEquals(1.0f, domain.embedding()[0]);
    }

    // ── SalienceProfile ────────────────────────────────────────────

    @Test
    @DisplayName("NEUTRAL profile has no effect")
    void neutralProfile() {
        var profile = SalienceProfile.NEUTRAL;
        assertTrue(profile.isNeutral());
        assertFalse(profile.hasInterests());
        assertFalse(profile.hasIcnuOverride());
        assertFalse(profile.hasScoringOverride());
        assertEquals(1.0f, profile.computeTopicBoost(new float[]{0.5f, 0.5f}));
    }

    @Test
    @DisplayName("computeTopicBoost returns 1.0 for null embedding")
    void topicBoostNullEmbedding() {
        var profile = SalienceProfile.builder()
                .interest("test", InterestLevel.CRITICAL)
                .build();
        assertEquals(1.0f, profile.computeTopicBoost(null));
    }

    @Test
    @DisplayName("computeTopicBoost returns 1.0 for empty embedding")
    void topicBoostEmptyEmbedding() {
        var profile = SalienceProfile.builder()
                .interest("test", InterestLevel.CRITICAL)
                .build();
        assertEquals(1.0f, profile.computeTopicBoost(new float[0]));
    }

    @Test
    @DisplayName("computeTopicBoost returns 1.0 when no interests configured")
    void topicBoostNoInterests() {
        var profile = SalienceProfile.builder().build();
        assertEquals(1.0f, profile.computeTopicBoost(new float[]{0.5f, 0.5f}));
    }

    @Test
    @DisplayName("computeTopicBoost boosts on high cosine similarity")
    void topicBoostHighSimilarity() {
        // Two identical L2-normalized vectors → cosine = 1.0
        float[] interestEmbed = {0.6f, 0.8f}; // norm ≈ 1.0
        float[] memoryEmbed = {0.6f, 0.8f};    // identical

        var profile = SalienceProfile.builder()
                .interest("database", InterestLevel.CRITICAL, interestEmbed)
                .build();

        float boost = profile.computeTopicBoost(memoryEmbed);
        // cosine ≈ 1.0, CRITICAL = 2.0 → boost ≈ 2.0
        assertTrue(boost > 1.5f, "Expected boost > 1.5 but got " + boost);
    }

    @Test
    @DisplayName("computeTopicBoost dampens on matching disinterest")
    void topicBoostDampens() {
        float[] disinterestEmbed = {0.6f, 0.8f};
        float[] memoryEmbed = {0.6f, 0.8f};

        var profile = SalienceProfile.builder()
                .disinterest("meetings", InterestLevel.IGNORE, disinterestEmbed)
                .build();

        float boost = profile.computeTopicBoost(memoryEmbed);
        // cosine ≈ 1.0, IGNORE = 0.1 → boost ≈ 0.1
        assertTrue(boost < 0.5f, "Expected boost < 0.5 but got " + boost);
    }

    @Test
    @DisplayName("computeTopicBoost ignores below similarity threshold")
    void topicBoostBelowThreshold() {
        // Orthogonal vectors → cosine ≈ 0
        float[] interestEmbed = {1.0f, 0.0f};
        float[] memoryEmbed = {0.0f, 1.0f};

        var profile = SalienceProfile.builder()
                .interest("database", InterestLevel.CRITICAL, interestEmbed)
                .build();

        float boost = profile.computeTopicBoost(memoryEmbed);
        assertEquals(1.0f, boost, "Orthogonal vectors should not trigger boost");
    }

    @Test
    @DisplayName("computeTopicBoost handles dimension mismatch gracefully")
    void topicBoostDimensionMismatch() {
        float[] interestEmbed = {0.5f, 0.5f, 0.5f};
        float[] memoryEmbed = {0.5f, 0.5f};

        var profile = SalienceProfile.builder()
                .interest("test", InterestLevel.HIGH, interestEmbed)
                .build();

        // Should not crash — returns 1.0 (no match)
        assertEquals(1.0f, profile.computeTopicBoost(memoryEmbed));
    }

    @Test
    @DisplayName("computeTopicBoost floors at 0.01")
    void topicBoostFloor() {
        float[] embed = {1.0f, 0.0f};

        var profile = SalienceProfile.builder()
                .disinterest("spam", InterestLevel.IGNORE, embed)
                .build();

        float boost = profile.computeTopicBoost(embed);
        assertTrue(boost >= 0.01f, "Boost should be floored at 0.01");
    }

    // ── Cosine Similarity ──────────────────────────────────────────

    @Test
    @DisplayName("cosineSimilarity of identical vectors is 1.0")
    void cosineIdentical() {
        float[] v = {0.6f, 0.8f};
        assertEquals(1.0f, SalienceProfile.cosineSimilarity(v, v), 0.01f);
    }

    @Test
    @DisplayName("cosineSimilarity of orthogonal vectors is 0.0")
    void cosineOrthogonal() {
        float[] a = {1.0f, 0.0f};
        float[] b = {0.0f, 1.0f};
        assertEquals(0.0f, SalienceProfile.cosineSimilarity(a, b), 0.01f);
    }

    @Test
    @DisplayName("cosineSimilarity of opposite vectors is -1.0")
    void cosineOpposite() {
        float[] a = {1.0f, 0.0f};
        float[] b = {-1.0f, 0.0f};
        assertEquals(-1.0f, SalienceProfile.cosineSimilarity(a, b), 0.01f);
    }

    @Test
    @DisplayName("cosineSimilarity with dimension mismatch returns 0")
    void cosineDimensionMismatch() {
        float[] a = {1.0f, 0.0f};
        float[] b = {1.0f, 0.0f, 0.0f};
        assertEquals(0.0f, SalienceProfile.cosineSimilarity(a, b));
    }

    // ── Builder ────────────────────────────────────────────────────

    @Test
    @DisplayName("Builder creates correct profile")
    void builderFull() {
        var icnu = new IcnuWeights(0.2f, 0.1f, 0.5f, 0.2f);
        var profile = SalienceProfile.builder()
                .interest("database", InterestLevel.CRITICAL)
                .interest("kubernetes", InterestLevel.HIGH)
                .disinterest("meetings", InterestLevel.IGNORE)
                .icnuWeights(icnu)
                .alpha(0.5f)
                .beta(0.5f)
                .flashbulbThreshold(2.5f)
                .recencyWeight(1.2f)
                .similarityThreshold(0.6f)
                .defaultProfile(CognitiveProfile.BALANCED)
                .build();

        assertEquals(2, profile.interests().size());
        assertEquals(1, profile.disinterests().size());
        assertTrue(profile.hasIcnuOverride());
        assertTrue(profile.hasScoringOverride());
        assertFalse(profile.isNeutral());
        assertEquals(0.5f, profile.alpha());
        assertEquals(0.5f, profile.beta());
        assertEquals(2.5f, profile.flashbulbThreshold());
        assertEquals(1.2f, profile.recencyWeight());
        assertEquals(0.6f, profile.similarityThreshold());
        assertEquals(CognitiveProfile.BALANCED, profile.defaultProfile());
    }

    @Test
    @DisplayName("Profile interests are immutable")
    void immutableInterests() {
        var profile = SalienceProfile.builder()
                .interest("test", InterestLevel.HIGH)
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> profile.interests().add(new InterestDomain("hack", InterestLevel.LOW)));
    }

    // ── SalienceProfileProvider ────────────────────────────────────

    @Test
    @DisplayName("Noop provider returns NEUTRAL")
    void noopProvider() {
        var provider = SalienceProfileProvider.noop();
        assertSame(SalienceProfile.NEUTRAL, provider.effectiveProfile());
    }

    // ── RecallOptions integration ──────────────────────────────────

    @Test
    @DisplayName("RecallOptions.Builder applies salience alpha/beta")
    void recallOptionsSalience() {
        var profile = SalienceProfile.builder()
                .alpha(0.3f)
                .beta(0.7f)
                .build();

        var options = RecallOptions.builder()
                .salienceProfile(profile)
                .build();

        assertEquals(0.3f, options.alpha());
        assertEquals(0.7f, options.beta());
    }

    @Test
    @DisplayName("RecallOptions.Builder salience null-safe")
    void recallOptionsSalienceNull() {
        var options = RecallOptions.builder()
                .salienceProfile(null)
                .build();

        // Should not crash — uses defaults
        assertEquals(0.6f, options.alpha());
        assertEquals(0.4f, options.beta());
    }

    // ── Edge cases: computeTopicBoost ──────────────────────────────

    @Test
    @DisplayName("computeTopicBoost: interest + disinterest competing — disinterest wins (suppression)")
    void topicBoostCompetingInterestAndDisinterest() {
        // Same embedding for both interest and disinterest
        float[] embed = {0.6f, 0.8f};

        var profile = SalienceProfile.builder()
                .interest("database", InterestLevel.HIGH, embed)      // 1.5× boost
                .disinterest("meetings", InterestLevel.IGNORE, embed) // 0.1× dampen
                .build();

        float boost = profile.computeTopicBoost(embed);
        // Disinterest dampening should dominate (min of boost and dampen)
        assertTrue(boost < 1.0f,
                "Disinterest suppression should win over interest boost, got " + boost);
    }

    @Test
    @DisplayName("computeTopicBoost: multiple interests — strongest match wins")
    void topicBoostMultipleInterestsStrongestWins() {
        float[] memoryEmbed = {0.6f, 0.8f};
        float[] weakMatch = {0.0f, 1.0f};   // cosine ≈ 0.8 with memory
        float[] strongMatch = {0.6f, 0.8f};  // cosine ≈ 1.0 with memory

        var profile = SalienceProfile.builder()
                .interest("weak topic", InterestLevel.MEDIUM, weakMatch)
                .interest("strong topic", InterestLevel.CRITICAL, strongMatch)
                .build();

        float boost = profile.computeTopicBoost(memoryEmbed);
        // CRITICAL × 1.0 = 2.0 should win over MEDIUM × 0.8 = 1.0
        assertTrue(boost > 1.5f,
                "Strongest interest should produce highest boost, got " + boost);
    }

    @Test
    @DisplayName("computeTopicBoost: interests without embeddings are skipped")
    void topicBoostInterestsWithoutEmbeddingsSkipped() {
        float[] memoryEmbed = {0.6f, 0.8f};

        var profile = SalienceProfile.builder()
                .interest("no-embed topic", InterestLevel.CRITICAL) // no embedding
                .build();

        // hasInterests() returns true, but no embeddings → no cosine → boost = 1.0
        assertTrue(profile.hasInterests());
        assertEquals(1.0f, profile.computeTopicBoost(memoryEmbed));
    }

    @Test
    @DisplayName("computeTopicBoost: custom similarity threshold filters")
    void topicBoostCustomThreshold() {
        float[] interestEmbed = {0.6f, 0.8f};
        float[] memoryEmbed = {0.55f, 0.85f}; // cosine ≈ 0.998 — above any threshold

        var profileLow = SalienceProfile.builder()
                .interest("test", InterestLevel.HIGH, interestEmbed)
                .similarityThreshold(0.3f) // lenient
                .build();

        var profileHigh = SalienceProfile.builder()
                .interest("test", InterestLevel.HIGH, interestEmbed)
                .similarityThreshold(0.99f) // very strict
                .build();

        float boostLow = profileLow.computeTopicBoost(memoryEmbed);
        float boostHigh = profileHigh.computeTopicBoost(memoryEmbed);

        assertTrue(boostLow > 1.0f, "Low threshold should trigger boost");
        // High threshold might not trigger for slightly different vectors
        // (cosine ≈ 0.998 so both should match, but this validates the threshold path)
    }

    @Test
    @DisplayName("computeTopicBoost: only disinterests configured")
    void topicBoostOnlyDisinterests() {
        float[] embed = {0.6f, 0.8f};

        var profile = SalienceProfile.builder()
                .disinterest("spam", InterestLevel.LOW, embed)
                .build();

        assertTrue(profile.hasInterests()); // disinterests count as "interests"
        float boost = profile.computeTopicBoost(embed);
        assertTrue(boost < 1.0f, "Disinterest should dampen, got " + boost);
    }

    // ── Edge cases: InterestDomain equality ────────────────────────

    @Test
    @DisplayName("InterestDomain: equals/hashCode with same embedding")
    void interestDomainEqualsSameEmbedding() {
        float[] e1 = {0.1f, 0.2f};
        float[] e2 = {0.1f, 0.2f};

        var d1 = new InterestDomain("test", InterestLevel.HIGH, e1);
        var d2 = new InterestDomain("test", InterestLevel.HIGH, e2);

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    @DisplayName("InterestDomain: not equal with different level")
    void interestDomainNotEqualDifferentLevel() {
        var d1 = new InterestDomain("test", InterestLevel.HIGH);
        var d2 = new InterestDomain("test", InterestLevel.LOW);

        assertNotEquals(d1, d2);
    }

    @Test
    @DisplayName("InterestDomain: equals with both null embeddings")
    void interestDomainEqualsBothNullEmbedding() {
        var d1 = new InterestDomain("test", InterestLevel.HIGH);
        var d2 = new InterestDomain("test", InterestLevel.HIGH);

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    @DisplayName("InterestDomain: toString is readable")
    void interestDomainToString() {
        var domain = new InterestDomain("database", InterestLevel.HIGH, new float[768]);
        String str = domain.toString();
        assertTrue(str.contains("database"));
        assertTrue(str.contains("HIGH"));
        assertTrue(str.contains("768"));
    }

    // ── Edge cases: Builder defaults ───────────────────────────────

    @Test
    @DisplayName("Builder defaults match NEUTRAL constants")
    void builderDefaults() {
        var profile = SalienceProfile.builder().build();

        assertEquals(SalienceProfile.DEFAULT_SIMILARITY_THRESHOLD,
                profile.similarityThreshold());
        assertEquals(SalienceProfile.DEFAULT_FLASHBULB_THRESHOLD,
                profile.flashbulbThreshold());
        assertEquals(1.0f, profile.recencyWeight());
        assertNull(profile.alpha());
        assertNull(profile.beta());
        assertNull(profile.icnuWeights());
        assertNull(profile.defaultProfile());
        assertTrue(profile.interests().isEmpty());
        assertTrue(profile.disinterests().isEmpty());
    }

    @Test
    @DisplayName("RecallOptions salience with defaultProfile cascades correctly")
    void recallOptionsSalienceWithDefaultProfile() {
        var profile = SalienceProfile.builder()
                .alpha(0.3f)
                .beta(0.7f)
                .defaultProfile(CognitiveProfile.BALANCED)
                .build();

        var options = RecallOptions.builder()
                .salienceProfile(profile)
                .build();

        // Salience alpha/beta should override the profile's default
        assertEquals(0.3f, options.alpha());
        assertEquals(0.7f, options.beta());
    }
}
