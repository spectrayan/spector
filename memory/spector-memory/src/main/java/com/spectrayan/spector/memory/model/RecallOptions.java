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

import com.spectrayan.spector.memory.graph.ExtractedEntity;
import com.spectrayan.spector.memory.synapse.SynapticTagEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder for recall query configuration.
 *
 * <p>Controls how {@link SpectorMemory#recall} filters, scores, and returns
 * cognitive memories. Supports synaptic tag filtering, importance thresholds,
 * memory type selection, valence range filtering, and neurodivergent
 * cognitive profile mechanics (hyperfocus, lateral retrieval).</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 *   List<CognitiveResult> results = memory.recall("database lock timeout",
 *       RecallOptions.builder()
 *           .topK(5)
 *           .synapticFilter("debugging", "database")
 *           .minImportance(0.3f)
 *           .memoryTypes(MemoryType.SEMANTIC, MemoryType.EPISODIC)
 *           .maxValence((byte) -10)  // only negative-outcome memories
 *           .build());
 * }</pre>
 *
 * <h3>Neurodivergent Profiles</h3>
 * <pre>{@code
 *   // Hyperfocus: zero-decay, strict tag gate, pure similarity scoring
 *   RecallOptions opts = RecallOptions.builder()
 *       .profile(CognitiveProfile.HYPERFOCUS)
 *       .hyperfocusMask("database", "deadlock")
 *       .build();
 *
 *   // Lateral retrieval: cross-domain divergent thinking
 *   RecallOptions opts = RecallOptions.builder()
 *       .profile(CognitiveProfile.DIVERGENT)
 *       .lateralMode(true)
 *       .build();
 * }</pre>
 */
public record RecallOptions(
        int topK,
        long synapticTagMask,
        float minImportance,
        MemoryType[] memoryTypes,
        byte minValence,
        byte maxValence,
        float alpha,
        float beta,
        float tagRelevanceBoost,
        int semanticCandidateMultiplier,
        // â”€â”€ Neurodivergent: Hyperfocus â”€â”€
        long hyperfocusMask,
        float hyperfocusBoost,
        // â”€â”€ Neurodivergent: Lateral Retrieval â”€â”€
        boolean lateralMode,
        float lateralDistanceThreshold,
        int lateralMaxResults,
        float lateralMinTagOverlap,
        // â”€â”€ Enhanced Scoring â”€â”€
        float strictnessCoefficient,
        // â”€â”€ Valence Alignment (State-Dependent Recall) â”€â”€
        byte queryValence,
        boolean enableValenceAlignment,
        // â”€â”€ Two-Factor Memory (Bjork & Bjork) â”€â”€
        com.spectrayan.spector.memory.synapse.TwoFactorConfig twoFactorConfig,
        // â”€â”€ Recall Mode (Statefulness Control) â”€â”€
        RecallMode recallMode,
        // â”€â”€ Text Search (BM25 Hybrid) â”€â”€
        float gamma,
        boolean enableTextSearch,
        TextSearchMode textSearchMode,
        // â”€â”€ Scoring Mode â”€â”€
        ScoringMode scoringMode,
        // â”€â”€ Entity Hints (Pre-Extracted Entities) â”€â”€
        List<ExtractedEntity> entityHints,
        // â”€â”€ Pipeline Tracing â”€â”€
        boolean enableTrace,
        // â”€â”€ Temporal Gating â”€â”€
        Long minTimestamp,
        Long maxTimestamp,
        // â”€â”€ Graph Expansion Gating â”€â”€
        float graphExpansionThreshold,
        // â”€â”€ WAL Replay (Time-Travel) â”€â”€
        Instant replayTimestamp,
        int maxReplayEvents,
        // â”€â”€ Reranker (ColBERT v2) â”€â”€
        boolean enableReranker,
        int rerankerDepth,
        // â”€â”€ Auto-Profile Detection â”€â”€
        boolean autoProfile,
        // â”€â”€ Resolved Profile (for header stamping) â”€â”€
        CognitiveProfile resolvedProfile
) {

    /** Default options: top 10, no filters, balanced scoring. */
    public static final RecallOptions DEFAULT = builder().build();

    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // Composed sub-record accessors â€” progressive migration API
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /** Returns filter parameters as a composed {@link FilterOptions}. */
    public FilterOptions filter() {
        return new FilterOptions(synapticTagMask, minImportance, memoryTypes, minValence, maxValence);
    }

    /** Returns scoring parameters as a composed {@link ScoringOptions}. */
    public ScoringOptions scoring() {
        return new ScoringOptions(alpha, beta, tagRelevanceBoost, semanticCandidateMultiplier,
                strictnessCoefficient, queryValence, enableValenceAlignment, twoFactorConfig, scoringMode);
    }

    /** Returns text search parameters as a composed {@link TextSearchOptions}. */
    public TextSearchOptions textSearch() {
        return new TextSearchOptions(gamma, enableTextSearch, textSearchMode);
    }

    /** Returns neurodivergent parameters as a composed {@link NeurodivergentOptions}. */
    public NeurodivergentOptions nd() {
        return new NeurodivergentOptions(hyperfocusMask, hyperfocusBoost,
                lateralMode, lateralDistanceThreshold, lateralMaxResults, lateralMinTagOverlap);
    }

    /** Returns graph expansion parameters as a composed {@link GraphOptions}. */
    public GraphOptions graph() {
        return new GraphOptions(entityHints, graphExpansionThreshold);
    }

    /** Returns temporal parameters as a composed {@link TemporalOptions}. */
    public TemporalOptions temporal() {
        return new TemporalOptions(minTimestamp, maxTimestamp, replayTimestamp, maxReplayEvents);
    }

    /** Returns reranker parameters as a composed {@link RerankerOptions}. */
    public RerankerOptions reranker() {
        return new RerankerOptions(enableReranker, rerankerDepth);
    }

    /**
     * Returns the resolved CognitiveProfile that was applied to this options instance.
     *
     * <p>May be null if no profile was explicitly applied via the builder or autoProfile.
     * Used by RecallPipeline to write the profile ordinal to synaptic header byte 60.</p>
     */
    public CognitiveProfile profile() {
        return resolvedProfile;
    }


    /**
     * Builder for {@link RecallOptions}.
     */
    public static final class Builder {

        private int topK = 10;
        private long synapticTagMask = 0L;
        private float minImportance = 0.0f;
        private MemoryType[] memoryTypes = null; // null = all types
        private byte minValence = Byte.MIN_VALUE;
        private byte maxValence = Byte.MAX_VALUE;
        private float alpha = 0.6f;  // similarity weight
        private float beta = 0.4f;   // importance Ã— decay weight
        private float tagRelevanceBoost = 0.3f;  // weighted tag overlap boost
        private int semanticCandidateMultiplier = 3; // HNSW over-fetch for semantic

        // â”€â”€ Text Search (BM25 Hybrid) â”€â”€
        private float gamma = 0.3f;                             // BM25 weight in fused score
        private boolean enableTextSearch = true;                 // enable BM25 parallel path
        private TextSearchMode textSearchMode = TextSearchMode.HYBRID; // search mode

        // â”€â”€ Scoring Mode â”€â”€
        private ScoringMode scoringMode = ScoringMode.COGNITIVE; // default: full cognitive

        // â”€â”€ Entity Hints â”€â”€
        private List<ExtractedEntity> entityHints = List.of(); // default: empty (use EntityExtractor)

        // â”€â”€ Pipeline Tracing â”€â”€
        private boolean enableTrace = false; // default: off (no allocation overhead)

        // â”€â”€ Temporal Gating â”€â”€
        private Long minTimestamp = null;
        private Long maxTimestamp = null;

        // â”€â”€ Graph Expansion Gating â”€â”€
        private float graphExpansionThreshold = 0.40f; // default: expand when max similarity < 0.40

        // â”€â”€ WAL Replay (Time-Travel) â”€â”€
        private Instant replayTimestamp = null;    // null = disabled
        private int maxReplayEvents = 100_000;     // cap to prevent OOM on large WALs

        // â”€â”€ Reranker (ColBERT v2) â”€â”€
        private boolean enableReranker = false;     // default: off (requires TokenEmbeddingProvider)
        private int rerankerDepth = 50;             // rerank top-50 first-stage candidates

        // â”€â”€ Auto-Profile Detection â”€â”€
        private boolean autoProfile = false;         // default: off (use explicit profile)
        private CognitiveProfile resolvedProfile = null; // set when profile() is called

        // â”€â”€ Neurodivergent: Hyperfocus â”€â”€
        private long hyperfocusMask = 0L;       // 0 = disabled
        private float hyperfocusBoost = 1.0f;   // post-score multiplier

        // â”€â”€ Neurodivergent: Lateral Retrieval â”€â”€
        private boolean lateralMode = false;
        private float lateralDistanceThreshold = 1.2f;
        private int lateralMaxResults = -1;      // -1 = topK/3
        private float lateralMinTagOverlap = 0.5f;

        // â”€â”€ Enhanced Scoring â”€â”€
        private float strictnessCoefficient = 1.0f; // 1.0 = standard, 10.0 = Heaviside cliff

        // â”€â”€ Valence Alignment (State-Dependent Recall) â”€â”€
        private byte queryValence = 0;              // 0 = neutral
        private boolean enableValenceAlignment = false;

        // â”€â”€ Two-Factor Memory (Bjork & Bjork) â”€â”€
        private com.spectrayan.spector.memory.synapse.TwoFactorConfig twoFactorConfig
                = com.spectrayan.spector.memory.synapse.TwoFactorConfig.DEFAULT;

        // â”€â”€ Recall Mode â”€â”€
        private RecallMode recallMode = RecallMode.LEARN;

        /**
         * Applies a {@link CognitiveProfile} preset to this builder.
         *
         * <p>Sets alpha, beta, minValence, and maxValence from the profile.
         * Individual fields can be overridden after applying the profile.</p>
         *
         * @param profile the cognitive scoring profile to apply
         */
        public Builder profile(CognitiveProfile profile) {
            this.resolvedProfile = profile;
            return profile.applyTo(this);
        }

        /**
         * Applies a {@link SalienceProfile} to this builder.
         *
         * <p>Overrides alpha/beta if the salience profile has scoring overrides.
         * Applies the default cognitive profile if one is configured.
         * Individual fields can be further overridden after applying.</p>
         *
         * @param salience the salience profile to apply (null-safe)
         */
        public Builder salienceProfile(SalienceProfile salience) {
            if (salience != null) {
                if (salience.alpha() != null) this.alpha = salience.alpha();
                if (salience.beta() != null) this.beta = salience.beta();
                if (salience.defaultProfile() != null) {
                    salience.defaultProfile().applyTo(this);
                    // Re-apply salience overrides (profile may have reset alpha/beta)
                    if (salience.alpha() != null) this.alpha = salience.alpha();
                    if (salience.beta() != null) this.beta = salience.beta();
                }
            }
            return this;
        }

        /**
         * Maximum number of results to return.
         */
        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        /**
         * Synaptic tag filter using Bloom filter matching.
         * Only memories whose tags match ALL specified tags will be considered.
         */
        public Builder synapticFilter(String... tags) {
            this.synapticTagMask = SynapticTagEncoder.encode(tags);
            return this;
        }

        /**
         * Minimum importance threshold â€” memories below this are skipped.
         */
        public Builder minImportance(float minImportance) {
            this.minImportance = minImportance;
            return this;
        }

        /**
         * Restrict recall to specific memory types.
         * Pass null or omit to search all types.
         */
        public Builder memoryTypes(MemoryType... memoryTypes) {
            this.memoryTypes = memoryTypes;
            return this;
        }

        /**
         * Minimum valence (inclusive). Use for filtering to positive outcomes.
         */
        public Builder minValence(byte minValence) {
            this.minValence = minValence;
            return this;
        }

        /**
         * Maximum valence (inclusive). Use for filtering to negative outcomes (debugging).
         */
        public Builder maxValence(byte maxValence) {
            this.maxValence = maxValence;
            return this;
        }

        /**
         * Scoring weight for vector similarity (default: 0.6).
         */
        public Builder alpha(float alpha) {
            this.alpha = alpha;
            return this;
        }

        /**
         * Scoring weight for importance Ã— decay (default: 0.4).
         */
        public Builder beta(float beta) {
            this.beta = beta;
            return this;
        }

        /**
         * Boost factor for weighted tag relevance (default: 0.3).
         * Partial tag matches are scored as: score *= (1.0 + overlapRatio * tagRelevanceBoost).
         * Set to 0.0 to disable tag relevance boosting.
         */
        public Builder tagRelevanceBoost(float tagRelevanceBoost) {
            this.tagRelevanceBoost = tagRelevanceBoost;
            return this;
        }

        /**
         * Over-fetch multiplier for semantic HNSW search (default: 3).
         * Fetches topK * multiplier candidates from HNSW before cognitive re-ranking.
         */
        public Builder semanticCandidateMultiplier(int multiplier) {
            this.semanticCandidateMultiplier = multiplier;
            return this;
        }

        // â”€â”€ Neurodivergent: Hyperfocus â”€â”€

        /**
         * Sets the hyperfocus Bloom filter mask from raw long value.
         * Memories that don't match ALL bits in this mask are excluded (strict equality gate).
         * Set to 0L to disable hyperfocus (default).
         */
        public Builder hyperfocusMask(long mask) {
            this.hyperfocusMask = mask;
            return this;
        }

        /**
         * Sets the hyperfocus mask from synaptic tag strings.
         * Encodes tags into a Bloom filter mask for strict equality gating.
         */
        public Builder hyperfocusMask(String... tags) {
            this.hyperfocusMask = SynapticTagEncoder.encode(tags);
            return this;
        }

        /**
         * Post-score multiplier for hyperfocus-matched memories (default: 1.0).
         * Applied after the normalized base score is computed.
         */
        public Builder hyperfocusBoost(float boost) {
            this.hyperfocusBoost = boost;
            return this;
        }

        // â”€â”€ Neurodivergent: Lateral Retrieval â”€â”€

        /**
         * Enables lateral/orthogonal retrieval â€” finds tag-matched but semantically
         * distant memories for cross-domain insight (default: false).
         */
        public Builder lateralMode(boolean enabled) {
            this.lateralMode = enabled;
            return this;
        }

        /**
         * Minimum L2 distance for a memory to qualify as a lateral candidate (default: 1.2).
         * Higher values â†’ only very distant memories are considered lateral.
         */
        public Builder lateralDistanceThreshold(float threshold) {
            this.lateralDistanceThreshold = threshold;
            return this;
        }

        /**
         * Maximum number of lateral candidates in the final results (default: topK/3).
         * Set to -1 for auto (topK/3).
         */
        public Builder lateralMaxResults(int max) {
            this.lateralMaxResults = max;
            return this;
        }

        /**
         * Minimum tag overlap ratio for lateral candidates (default: 0.5).
         * Prevents Bloom filter false positives from producing spurious lateral results.
         */
        public Builder lateralMinTagOverlap(float minOverlap) {
            this.lateralMinTagOverlap = minOverlap;
            return this;
        }

        // â”€â”€ Enhanced Scoring â”€â”€

        /**
         * Strictness coefficient for the similarity function (default: 1.0).
         * Higher values create a steeper "cliff" â€” near-matches score well,
         * slightly vague matches plummet. Use 10.0 for SYSTEMATIZER / THE_EXECUTOR.
         */
        public Builder strictnessCoefficient(float k) {
            this.strictnessCoefficient = k;
            return this;
        }

        // â”€â”€ Valence Alignment (State-Dependent Recall) â”€â”€

        /**
         * Sets the query's emotional valence for state-dependent recall.
         * Memories with similar valence score higher. Enables valence alignment automatically.
         */
        public Builder queryValence(byte valence) {
            this.queryValence = valence;
            this.enableValenceAlignment = true;
            return this;
        }

        /**
         * Explicitly enables/disables valence alignment scoring.
         */
        public Builder enableValenceAlignment(boolean enabled) {
            this.enableValenceAlignment = enabled;
            return this;
        }

        // â”€â”€ Recall Mode â”€â”€

        /**
         * Sets the recall mode â€” controls whether recall mutates memory state.
         *
         * <ul>
         *   <li>{@link RecallMode#LEARN} (default): Full biological memory â€” recall
         *       fires LTP, Hebbian, habituation, ACT-R timestamps.</li>
         *   <li>{@link RecallMode#OBSERVE}: Pure read â€” no side effects.
         *       Same query always returns the same results.</li>
         * </ul>
         *
         * @param mode the recall mode
         */
        public Builder recallMode(RecallMode mode) {
            this.recallMode = mode;
            return this;
        }

        /**
         * Sets the target timestamp for REPLAY mode.
         *
         * <p>When {@link RecallMode#REPLAY} is used, this specifies the point-in-time
         * to reconstruct memory state from WAL events. All events after this timestamp
         * are ignored, and recall runs against the historical state.</p>
         *
         * @param timestamp the target point-in-time
         */
        public Builder replayTimestamp(Instant timestamp) {
            this.replayTimestamp = timestamp;
            return this;
        }

        /**
         * Maximum number of WAL events to replay (default: 100,000).
         *
         * <p>Safety cap to prevent excessive off-heap allocation when replaying
         * large WALs. If the WAL contains more events before the target timestamp,
         * only the first {@code maxReplayEvents} are processed.</p>
         *
         * @param max maximum events to replay
         */
        public Builder maxReplayEvents(int max) {
            this.maxReplayEvents = max;
            return this;
        }

        // â”€â”€ Text Search (BM25 Hybrid) â”€â”€

        /**
         * BM25 weight in the fused cognitive score (default: 0.3).
         * Higher values give more weight to keyword matches.
         */
        public Builder gamma(float gamma) {
            this.gamma = gamma;
            return this;
        }

        /**
         * Enables/disables the BM25 text search parallel path (default: true).
         */
        public Builder enableTextSearch(boolean enable) {
            this.enableTextSearch = enable;
            return this;
        }

        /**
         * Sets the text search mode (default: {@link TextSearchMode#HYBRID}).
         *
         * @see TextSearchMode
         */
        public Builder textSearchMode(TextSearchMode mode) {
            this.textSearchMode = mode;
            return this;
        }

        /**
         * Enables/disables the ColBERT v2 reranker (default: false).
         *
         * <p>When enabled, first-stage retrieval candidates are reranked using
         * ColBERT's token-level MaxSim scoring. Requires a configured
         * {@link com.spectrayan.spector.provider.embedding.TokenEmbeddingProvider}.</p>
         */
        public Builder enableReranker(boolean enable) {
            this.enableReranker = enable;
            return this;
        }

        /**
         * Sets the number of first-stage candidates to rerank (default: 50).
         *
         * <p>Higher values improve recall at the cost of latency (each candidate
         * requires per-token embedding computation).</p>
         *
         * @param depth number of candidates to rerank (typically 20-100)
         */
        public Builder rerankerDepth(int depth) {
            this.rerankerDepth = depth;
            return this;
        }

        // â”€â”€ Auto-Profile Detection â”€â”€

        /**
         * Enables automatic profile detection from recall context tags.
         *
         * <p>When enabled, the recall pipeline will use {@link CognitiveProfile#detect}
         * to automatically select the best profile based on the query's synaptic tags,
         * ignoring any explicitly set profile.</p>
         *
         * @param auto true to enable auto-detection
         */
        public Builder autoProfile(boolean auto) {
            this.autoProfile = auto;
            return this;
        }

        /**
         * Sets the scoring mode (default: {@link ScoringMode#COGNITIVE}).
         *
         * <p>Use {@link ScoringMode#SIMILARITY} for pure vector retrieval
         * benchmarks where cognitive scoring (importance, decay) would add noise.</p>
         */
        public Builder scoringMode(ScoringMode mode) {
            this.scoringMode = mode;
            return this;
        }

        // â”€â”€ Entity Hints â”€â”€

        /**
         * Pre-extracted entities for entity graph traversal at recall time.
         *
         * <p>When provided, these entities are used directly for graph traversal
         * in Step 5e of the recall pipeline, bypassing the live {@code EntityExtractor}.
         * This is essential for benchmarking (where no LLM is available for extraction)
         * and for MCP clients that already have entity data.</p>
         *
         * @param entities pre-extracted entities from the query
         */
        public Builder entityHints(List<ExtractedEntity> entities) {
            this.entityHints = entities != null ? entities : List.of();
            return this;
        }

        /**
         * Convenience overload for varargs entity hints.
         */
        public Builder entityHints(ExtractedEntity... entities) {
            this.entityHints = List.of(entities);
            return this;
        }

        // â”€â”€ Pipeline Tracing â”€â”€

        /**
         * Enables per-result pipeline scoring trace (default: false).
         *
         * <p>When enabled, each {@link CognitiveResult} will carry a
         * {@link RecallTrace} showing how its score evolved through every
         * phase of the recall pipeline. Useful for debugging and LLM-driven
         * dynamic query adjustment via MCP.</p>
         *
         * <p><b>Performance note:</b> Tracing adds allocation overhead per result.
         * Do not enable in production hot paths.</p>
         */
        public Builder enableTrace(boolean enable) {
            this.enableTrace = enable;
            return this;
        }

        // â”€â”€ Temporal Gating â”€â”€

        /**
         * Minimum timestamp (inclusive) â€” memories older than this are skipped.
         */
        public Builder minTimestamp(Long minTimestamp) {
            this.minTimestamp = minTimestamp;
            return this;
        }

        /**
         * Maximum timestamp (inclusive) â€” memories newer than this are skipped.
         */
        public Builder maxTimestamp(Long maxTimestamp) {
            this.maxTimestamp = maxTimestamp;
            return this;
        }

        // â”€â”€ Graph Expansion Gating â”€â”€

        /**
         * Maximum direct similarity score below which graph expansion is triggered (default: 0.40).
         * When the best direct result has similarity â‰¥ this threshold, graph expansion
         * (Hebbian, temporal, entity) is skipped to avoid diluting strong results.
         * Set to 0.0 to disable graph expansion entirely, or 1.0 to always expand.
         */
        public Builder graphExpansionThreshold(float threshold) {
            this.graphExpansionThreshold = threshold;
            return this;
        }

        public RecallOptions build() {
            int effectiveLateralMax = lateralMaxResults >= 0
                    ? lateralMaxResults
                    : Math.max(1, topK / 3);
            var options = new RecallOptions(topK, synapticTagMask, minImportance,
                    memoryTypes, minValence, maxValence, alpha, beta,
                    tagRelevanceBoost, semanticCandidateMultiplier,
                    hyperfocusMask, hyperfocusBoost,
                    lateralMode, lateralDistanceThreshold,
                    effectiveLateralMax, lateralMinTagOverlap,
                    strictnessCoefficient, queryValence, enableValenceAlignment,
                    twoFactorConfig, recallMode,
                    gamma, enableTextSearch, textSearchMode,
                    scoringMode, entityHints, enableTrace,
                    minTimestamp, maxTimestamp,
                    graphExpansionThreshold,
                    replayTimestamp, maxReplayEvents,
                    enableReranker, rerankerDepth,
                    autoProfile,
                    resolvedProfile);
            return options;
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // Validation â€” detect conflicting parameter combinations
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private static final Logger VALIDATION_LOG = LoggerFactory.getLogger(RecallOptions.class);

    /**
     * Validates this options instance for conflicting parameter combinations.
     *
     * <p>Returns a list of warning messages describing detected conflicts.
     * Also logs each warning via {@code java.util.logging}. The recall operation
     * proceeds regardless â€” these are advisory, not blocking.</p>
     *
     * <h4>Detected Conflicts</h4>
     * <ul>
     *   <li><b>lateralMode + high strictness</b>: Lateral mode finds semantically
     *       distant memories, but high strictness rejects vague matches. These
     *       two features fight each other.</li>
     *   <li><b>hyperfocusMask + lateralMode</b>: Hyperfocus narrows attention to a
     *       specific topic, while lateral mode broadens it. Use one or the other.</li>
     *   <li><b>Î± + Î² â‰  1.0</b>: Scoring weights should typically sum to 1.0.
     *       Other values produce unnormalized scores.</li>
     * </ul>
     *
     * @return list of warning messages (empty if no conflicts detected)
     */
    public List<String> validate() {
        List<String> warnings = new ArrayList<>();

        // 1. lateralMode + high strictness are contradictory
        if (lateralMode && strictnessCoefficient > 5.0f) {
            String msg = "lateralMode=true + strictnessCoefficient=" + strictnessCoefficient
                    + " is contradictory â€” lateral finds distant matches, high strictness rejects them. "
                    + "Consider using one or the other.";
            warnings.add(msg);
            VALIDATION_LOG.warn(msg);
        }

        // 2. hyperfocusMask + lateralMode are contradictory
        if (hyperfocusMask != 0 && lateralMode) {
            String msg = "hyperfocusMask is set + lateralMode=true â€” hyperfocus narrows attention "
                    + "to a specific topic, lateral mode broadens it. Consider using one or the other.";
            warnings.add(msg);
            VALIDATION_LOG.warn(msg);
        }

        // 3. Î± + Î² should sum to ~1.0
        float sum = alpha + beta;
        if (Math.abs(sum - 1.0f) > 0.01f) {
            String msg = "alpha (" + alpha + ") + beta (" + beta + ") = " + sum
                    + " â€” scoring weights don't sum to 1.0. Scores may be unnormalized.";
            warnings.add(msg);
            VALIDATION_LOG.warn(msg);
        }

        // 4. gamma out of useful range
        if (gamma < 0.0f || gamma > 2.0f) {
            String msg = "gamma (" + gamma + ") is outside the useful range [0.0, 2.0]. "
                    + "BM25 scoring may produce unexpected results.";
            warnings.add(msg);
            VALIDATION_LOG.warn(msg);
        }

        // 5. REPLAY mode requires replayTimestamp
        if (recallMode == RecallMode.REPLAY && replayTimestamp == null) {
            String msg = "recallMode=REPLAY requires replayTimestamp to be set. "
                    + "Use RecallOptions.builder().replayTimestamp(Instant.parse(...)).build()";
            warnings.add(msg);
            VALIDATION_LOG.warn(msg);
        }

        return warnings;
    }

    /**
     * Parses a profile name string (case-insensitive) into a {@link CognitiveProfile}.
     *
     * <p>Intended for MCP/REST integrations where profile arrives as a string.
     * Returns {@code null} if the name is null, empty, or doesn't match any profile.</p>
     *
     * @param profileName profile name (e.g., "DEBUGGING", "debugging", "Debugging")
     * @return the matching profile, or {@code null} if not found
     */
    public static CognitiveProfile parseProfile(String profileName) {
        if (profileName == null || profileName.isBlank()) return null;
        if ("AUTO".equalsIgnoreCase(profileName.strip())) return null;
        try {
            return CognitiveProfile.valueOf(profileName.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            VALIDATION_LOG.warn("Unknown CognitiveProfile: '" + profileName
                    + "'. Available: " + java.util.Arrays.toString(CognitiveProfile.values()));
            return null;
        }
    }

    /**
     * Checks if the given profile name represents the AUTO detection mode.
     *
     * <p>Use in combination with {@link #parseProfile} and {@link Builder#autoProfile}:
     * if this returns {@code true}, set {@code autoProfile(true)} on the builder
     * instead of applying a specific profile.</p>
     *
     * @param profileName profile name string (may be null)
     * @return true if the name is "AUTO" (case-insensitive)
     */
    public static boolean isAutoProfile(String profileName) {
        return profileName != null && "AUTO".equalsIgnoreCase(profileName.strip());
    }
}
