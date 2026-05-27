package com.spectrayan.spector.memory;

/**
 * Builder for recall query configuration.
 *
 * <p>Controls how {@link SpectorMemory#recall} filters, scores, and returns
 * cognitive memories. Supports synaptic tag filtering, importance thresholds,
 * memory type selection, and valence range filtering.</p>
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
 */
public record RecallOptions(
        int topK,
        long synapticTagMask,
        float minImportance,
        MemoryType[] memoryTypes,
        byte minValence,
        byte maxValence,
        float alpha,
        float beta
) {

    /** Default options: top 10, no filters, balanced scoring. */
    public static final RecallOptions DEFAULT = builder().build();

    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
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
        private float beta = 0.4f;   // importance × decay weight

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
            this.synapticTagMask = com.spectrayan.spector.memory.synapse.SynapticTagEncoder.encode(tags);
            return this;
        }

        /**
         * Minimum importance threshold — memories below this are skipped.
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
         * Scoring weight for importance × decay (default: 0.4).
         */
        public Builder beta(float beta) {
            this.beta = beta;
            return this;
        }

        public RecallOptions build() {
            return new RecallOptions(topK, synapticTagMask, minImportance,
                    memoryTypes, minValence, maxValence, alpha, beta);
        }
    }
}
