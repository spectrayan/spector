/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.core.cognitive;

/**
 * Pure mathematical kernel for Spector's unified 6-phase cognitive score fusion formula
 * (ADR-0033 Domain 14, #34).
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class CognitiveScoreFusionKernel {

    private CognitiveScoreFusionKernel() {}

    /**
     * Immutable parameter carrier for query-scoped cognitive fusion weights and modes.
     */
    public record FusionParams(
            float strictness,
            float beta,
            float alpha,
            float sExponent,
            float tagRelevanceBoost,
            float hyperfocusBoost,
            float associativePriorDelta,
            float lambda,
            boolean additiveMode,
            boolean twoFactorEnabled,
            boolean pureSimilarity,
            boolean valenceAlign) {

        public static final FusionParams DEFAULT = new FusionParams(
                1.0f,   // strictness
                0.5f,   // beta
                0.7f,   // alpha
                0.3f,   // sExponent
                0.2f,   // tagRelevanceBoost
                1.5f,   // hyperfocusBoost
                0.3f,   // associativePriorDelta
                1.0f,   // lambda
                false,  // additiveMode
                true,   // twoFactorEnabled
                false,  // pureSimilarity
                false   // valenceAlign
        );
    }

    // ── Phase 1: Vector Distance to Similarity ──

    public static float similarity(final float l2dist, final float strictness) {
        return 1.0f / (1.0f + l2dist * strictness);
    }

    // ── Phase 2: Importance, Recency Decay & Storage Strength ──

    public static float importanceDecayFactor(
            final float importance, final float beta, final float decay, final float storageBoost) {
        final float importanceNorm = importance / 10.0f;
        return 1.0f + beta * importanceNorm * decay * storageBoost;
    }

    // ── Phase 3: Mood-Congruent Valence Alignment ──

    public static float applyValenceAlignment(
            final float score, final boolean valenceAlign, final byte queryValence, final byte valence) {
        if (!valenceAlign) {
            return score;
        }
        final float valenceMultiplier = 1.0f - (Math.abs(queryValence - valence) / 255.0f);
        return score * valenceMultiplier;
    }

    // ── Phase 4: Synaptic Tag Overlap Relevance ──

    public static float applyTagRelevance(
            final float score, final float tagOverlap, final float tagRelevanceBoost, final boolean additive) {
        if (additive) {
            return score;
        }
        return score * (1.0f + tagOverlap * tagRelevanceBoost);
    }

    // ── Phase 5: Attentional Hyperfocus Modulation ──

    public static float applyHyperfocus(
            final float score, final boolean focusMatch, final float hyperfocusBoost) {
        if (focusMatch && hyperfocusBoost != 1.0f) {
            return score * hyperfocusBoost;
        }
        return score;
    }

    // ── Phase 6: Graph Associative Prior Injection ──

    public static float applyAssociativePrior(
            final float score, final float prior, final float delta, final boolean additive) {
        if (additive) {
            return score + delta * prior;
        }
        return score * (1.0f + delta * prior);
    }

    // ── Linear-Blend Variant (partition-aware semantic re-ranking) ──

    /**
     * Computes the linear-blend fused score used by partition-aware semantic re-ranking.
     *
     * <p>This is a <b>distinct formula</b> from the 6-phase {@link #computeFusedScore}, not a
     * special case of it. Where the 6-phase form applies importance and decay as a multiplicative
     * factor on similarity ({@code sim * (1 + beta * impNorm * decay * storageBoost)}), this form
     * blends them <b>additively</b>:</p>
     * <pre>
     *   base  = alpha * similarity + beta * (importance / 10) * decay
     *   score = base * (1 + tagOverlap * tagRelevanceBoost)
     * </pre>
     *
     * <p>It is kept here rather than inline at the call site so that every fusion formula in the
     * product has exactly one implementation (ADR-0033 §5.1). Behaviour is bit-identical to the
     * pre-migration {@code SemanticRecallStrategy} inline expression — do not "unify" it with the
     * 6-phase form, as that would change recall rankings.</p>
     *
     * @param similarity        normalized similarity in [0, 1]
     * @param importance        raw importance in [0, 10]
     * @param decay             mass-dilated decay multiplier in [0, 1]
     * @param tagOverlap        synaptic tag overlap ratio in [0, 1]
     * @param alpha             similarity blend weight
     * @param beta              importance-decay blend weight
     * @param tagRelevanceBoost multiplicative tag relevance boost coefficient
     * @return fused linear-blend score
     */
    public static float computeLinearBlendScore(
            final float similarity, final float importance, final float decay,
            final float tagOverlap, final float alpha, final float beta,
            final float tagRelevanceBoost) {
        final float base = alpha * similarity + beta * (importance / 10.0f) * decay;
        return base * (1.0f + tagOverlap * tagRelevanceBoost);
    }

    /**
     * Batch linear-blend fused scoring across candidate records (Principle 3).
     *
     * <p>Struct-of-arrays counterpart to
     * {@link #computeLinearBlendScore(float, float, float, float, float, float, float)}.
     * Null checks are hoisted out of the loop.</p>
     *
     * @param similarities      per-candidate normalized similarities (required)
     * @param importances       per-candidate raw importances (required)
     * @param decays            per-candidate decay multipliers (required)
     * @param tagOverlaps       per-candidate tag overlap ratios, or {@code null} for all-zero
     * @param alpha             similarity blend weight
     * @param beta              importance-decay blend weight
     * @param tagRelevanceBoost multiplicative tag relevance boost coefficient
     * @param outScores         destination array for fused scores
     * @param count             number of candidates to process
     */
    public static void computeLinearBlendScores(
            final float[] similarities, final float[] importances, final float[] decays,
            final float[] tagOverlaps, final float alpha, final float beta,
            final float tagRelevanceBoost, final float[] outScores, final int count) {

        if (outScores == null || similarities == null || importances == null || decays == null || count <= 0) {
            return;
        }
        final int limit = Math.min(count, Math.min(outScores.length,
                Math.min(similarities.length, Math.min(importances.length, decays.length))));
        final boolean hasTagOverlaps = tagOverlaps != null && tagOverlaps.length >= limit;

        for (int i = 0; i < limit; i++) {
            outScores[i] = computeLinearBlendScore(
                    similarities[i], importances[i], decays[i],
                    hasTagOverlaps ? tagOverlaps[i] : 0.0f,
                    alpha, beta, tagRelevanceBoost);
        }
    }

    /**
     * Computes the complete 6-phase fused cognitive score for a candidate memory.
     */
    public static float computeFusedScore(
            final float l2dist,
            final long timestampMs,
            final long nowMs,
            final float cognitiveMass,
            final byte arousal,
            final float storageStrength,
            final boolean hasStorageStrength,
            final int recallCount,
            final float importance,
            final float tagOverlap,
            final byte valence,
            final byte queryValence,
            final boolean focusMatch,
            final boolean zeroTimeDecay,
            final float associativePrior,
            final FusionParams params) {

        final FusionParams p = (params != null) ? params : FusionParams.DEFAULT;

        final float sim = similarity(l2dist, p.strictness());
        if (p.pureSimilarity()) {
            return sim;
        }

        final float decay = MassDilatedDecayKernel.compute(
                timestampMs, nowMs, cognitiveMass, arousal, recallCount, zeroTimeDecay, p.lambda());

        float storageBoost = 1.0f;
        if (hasStorageStrength && p.twoFactorEnabled() && storageStrength > 1.0f) {
            storageBoost = CognitiveMassKernel.fastStorageBoost(storageStrength, p.sExponent());
        }

        final float impDecayFactor = importanceDecayFactor(importance, p.beta(), decay, storageBoost);

        float baseScore;
        if (p.additiveMode()) {
            final float baseSimilarity = p.alpha() * sim + (1.0f - p.alpha()) * tagOverlap;
            baseScore = baseSimilarity * impDecayFactor;
        } else {
            baseScore = sim * impDecayFactor;
        }

        if (p.valenceAlign()) {
            baseScore = applyValenceAlignment(baseScore, true, queryValence, valence);
        }

        float finalScore = applyTagRelevance(baseScore, tagOverlap, p.tagRelevanceBoost(), p.additiveMode());

        finalScore = applyHyperfocus(finalScore, focusMatch, p.hyperfocusBoost());

        if (associativePrior != 0.0f) {
            finalScore = applyAssociativePrior(finalScore, associativePrior, p.associativePriorDelta(), p.additiveMode());
        }

        return finalScore;
    }

    /**
     * Batch fused cognitive score computation across candidate records (Principle 3).
     */
    public static void computeFusedScores(
            final float[] l2dists,
            final long[] timestampsMs,
            final float[] cognitiveMasses,
            final byte[] arousals,
            final float[] storageStrengths,
            final boolean[] hasStorageStrength,
            final int[] recallCounts,
            final float[] importances,
            final float[] tagOverlaps,
            final byte[] valences,
            final boolean[] focusMatches,
            final boolean[] zeroTimeDecays,
            final float[] associativePriors,
            final long nowMs,
            final byte queryValence,
            final FusionParams params,
            final float[] outScores,
            final int count) {

        if (outScores == null || count <= 0) {
            return;
        }

        final FusionParams p = (params != null) ? params : FusionParams.DEFAULT;
        final int limit = Math.min(count, outScores.length);

        if (p.pureSimilarity()) {
            final float strictness = p.strictness();
            if (l2dists != null && l2dists.length >= limit) {
                for (int i = 0; i < limit; i++) {
                    outScores[i] = 1.0f / (1.0f + l2dists[i] * strictness);
                }
            } else {
                for (int i = 0; i < limit; i++) {
                    final float d = (l2dists != null && i < l2dists.length) ? l2dists[i] : 0.0f;
                    outScores[i] = 1.0f / (1.0f + d * strictness);
                }
            }
            return;
        }

        final boolean allArraysPresent = l2dists != null && l2dists.length >= limit
                && timestampsMs != null && timestampsMs.length >= limit
                && cognitiveMasses != null && cognitiveMasses.length >= limit
                && arousals != null && arousals.length >= limit
                && storageStrengths != null && storageStrengths.length >= limit
                && hasStorageStrength != null && hasStorageStrength.length >= limit
                && recallCounts != null && recallCounts.length >= limit
                && importances != null && importances.length >= limit
                && tagOverlaps != null && tagOverlaps.length >= limit
                && valences != null && valences.length >= limit
                && focusMatches != null && focusMatches.length >= limit
                && zeroTimeDecays != null && zeroTimeDecays.length >= limit
                && associativePriors != null && associativePriors.length >= limit;

        if (allArraysPresent) {
            // Fast path: zero bounds checks and zero null checks per iteration
            for (int i = 0; i < limit; i++) {
                outScores[i] = computeFusedScore(
                        l2dists[i], timestampsMs[i], nowMs, cognitiveMasses[i], arousals[i],
                        storageStrengths[i], hasStorageStrength[i], recallCounts[i],
                        importances[i], tagOverlaps[i], valences[i], queryValence,
                        focusMatches[i], zeroTimeDecays[i], associativePriors[i], p);
            }
            return;
        }

        // Fallback for ragged or partially populated arrays
        for (int i = 0; i < limit; i++) {
            final float l2dist = (l2dists != null && i < l2dists.length) ? l2dists[i] : 0.0f;
            final long timestampMs = (timestampsMs != null && i < timestampsMs.length) ? timestampsMs[i] : nowMs;
            final float cognitiveMass = (cognitiveMasses != null && i < cognitiveMasses.length) ? cognitiveMasses[i] : 0.0f;
            final byte arousal = (arousals != null && i < arousals.length) ? arousals[i] : 0;
            final float storageStrength = (storageStrengths != null && i < storageStrengths.length) ? storageStrengths[i] : 1.0f;
            final boolean hasStorage = (hasStorageStrength != null && i < hasStorageStrength.length) && hasStorageStrength[i];
            final int recallCount = (recallCounts != null && i < recallCounts.length) ? recallCounts[i] : 0;
            final float importance = (importances != null && i < importances.length) ? importances[i] : 5.0f;
            final float tagOverlap = (tagOverlaps != null && i < tagOverlaps.length) ? tagOverlaps[i] : 0.0f;
            final byte valence = (valences != null && i < valences.length) ? valences[i] : 0;
            final boolean focusMatch = (focusMatches != null && i < focusMatches.length) && focusMatches[i];
            final boolean zeroTimeDecay = (zeroTimeDecays != null && i < zeroTimeDecays.length) && zeroTimeDecays[i];
            final float prior = (associativePriors != null && i < associativePriors.length) ? associativePriors[i] : 0.0f;

            outScores[i] = computeFusedScore(
                    l2dist, timestampMs, nowMs, cognitiveMass, arousal, storageStrength,
                    hasStorage, recallCount, importance, tagOverlap, valence, queryValence,
                    focusMatch, zeroTimeDecay, prior, p);
        }
    }
}
