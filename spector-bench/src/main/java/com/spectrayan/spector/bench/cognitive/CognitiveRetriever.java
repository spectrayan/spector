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
package com.spectrayan.spector.bench.cognitive;

import java.util.List;

import com.spectrayan.spector.bench.cognitive.model.BenchmarkQuery;
import com.spectrayan.spector.bench.cognitive.model.ScoredResult;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoringMode;
import com.spectrayan.spector.memory.SpectorMemory;

/**
 * Cognitive retriever wrapping the full Spector Memory recall pipeline with
 * {@link RecallMode#OBSERVE} to prevent side effects during benchmarking.
 *
 * <p>This serves as the experimental condition in the benchmark: it exercises
 * the complete 6-phase cognitive scoring pipeline plus graph augmentation
 * (Hebbian, Temporal, Entity) without mutating memory state. Paired with
 * {@link BaselineRetriever} for comparative evaluation.</p>
 *
 * <h3>Pipeline Phases Exercised</h3>
 * <ol>
 *   <li>Tombstone Check — skips logically deleted records</li>
 *   <li>Synaptic Tag Gating — Bloom filter containment check</li>
 *   <li>Valence Filter — emotional range filtering</li>
 *   <li>Importance/Decay Pre-screen — eliminates low-value candidates</li>
 *   <li>SIMD L2 Distance — calibrated INT8 asymmetric quantization</li>
 *   <li>Fused Cognitive Score — alpha×similarity + beta×importance×decay</li>
 * </ol>
 *
 * <p>Plus graph augmentation: Hebbian spreading activation, temporal chain
 * traversal, and entity graph multi-hop discovery.</p>
 *
 * <h3>Edge Case Handling</h3>
 * <ul>
 *   <li>Empty synapticFilterTags → no tag filter applied (all tags pass)</li>
 *   <li>Null minValence → profile default applies (no override)</li>
 *   <li>Null maxValence → profile default applies (no override)</li>
 *   <li>DEFAULT_MODE_NETWORK profile → restricts to SEMANTIC and PROCEDURAL
 *       memory types (handled implicitly by {@link com.spectrayan.spector.memory.model.CognitiveProfile#applyTo})</li>
 * </ul>
 */
public final class CognitiveRetriever {

    private final SpectorMemory memory;
    /** null = per-query profiles, "NONE" = no profile, else a CognitiveProfile name. */
    private final String profileOverride;

    /**
     * Creates a new CognitiveRetriever backed by the given SpectorMemory instance.
     *
     * @param memory the memory instance to execute recall queries against;
     *               must not be null
     * @throws NullPointerException if memory is null
     */
    public CognitiveRetriever(SpectorMemory memory) {
        this(memory, null);
    }

    /**
     * Creates a new CognitiveRetriever with an optional profile override.
     *
     * @param memory          the memory instance for recall queries
     * @param profileOverride null = per-query profiles from dataset,
     *                        "NONE" = no profile (raw cognitive scoring with defaults),
     *                        or a CognitiveProfile enum name to force on all queries
     */
    public CognitiveRetriever(SpectorMemory memory, String profileOverride) {
        if (memory == null) {
            throw new NullPointerException("SpectorMemory instance must not be null");
        }
        this.memory = memory;
        this.profileOverride = profileOverride;
    }

    /**
     * Builds {@link RecallOptions} from a {@link BenchmarkQuery}, always using
     * {@link RecallMode#OBSERVE} to prevent state mutations during benchmarking.
     *
     * <p>Profile resolution order:
     * <ol>
     *   <li>If profileOverride is "NONE": no profile applied (default alpha=0.6, beta=0.4)</li>
     *   <li>If profileOverride is a CognitiveProfile name: that profile is used for all queries</li>
     *   <li>Otherwise: uses the query's own cognitiveProfile from the dataset</li>
     * </ol>
     *
     * @param query the benchmark query containing profile and filter parameters
     * @return configured RecallOptions ready for execution
     */
    RecallOptions buildOptions(BenchmarkQuery query) {
        RecallOptions.Builder builder = RecallOptions.builder()
                .topK(10)
                .recallMode(RecallMode.OBSERVE);

        // Apply profile based on override setting
        if ("NONE".equals(profileOverride)) {
            // No profile — use raw defaults (alpha=0.6, beta=0.4, full valence range)
            // This measures pure cognitive pipeline effect without profile tuning
        } else if (profileOverride != null) {
            // Override all queries to use a specific profile
            CognitiveProfile overrideProfile = CognitiveProfile.valueOf(profileOverride);
            builder.profile(overrideProfile);
        } else {
            // Default: use per-query profile from the dataset
            builder.profile(query.cognitiveProfile());
        }

        // Apply synaptic tag filter only when tags are present
        if (!query.synapticFilterTags().isEmpty()) {
            builder.synapticFilter(query.synapticFilterTags().toArray(String[]::new));
        }

        // Override profile valence defaults only when query specifies explicit bounds
        if (query.minValence() != null) {
            builder.minValence(query.minValence());
        }
        if (query.maxValence() != null) {
            builder.maxValence(query.maxValence());
        }

        // Pass pre-extracted entity hints for entity graph traversal
        if (query.entityHints() != null && !query.entityHints().isEmpty()) {
            builder.entityHints(query.entityHints());
        }

        return builder.build();
    }

    /**
     * Builds options with pipeline tracing enabled for diagnostic queries.
     */
    RecallOptions buildOptionsWithTrace(BenchmarkQuery query) {
        RecallOptions base = buildOptions(query);
        RecallOptions.Builder traceBuilder = RecallOptions.builder()
                .topK(base.topK())
                .recallMode(base.recallMode())
                .enableTrace(true)
                .entityHints(base.entityHints());

        // Re-apply profile the same way as buildOptions
        if ("NONE".equals(profileOverride)) {
            // no profile
        } else if (profileOverride != null) {
            traceBuilder.profile(CognitiveProfile.valueOf(profileOverride));
        } else {
            traceBuilder.profile(query.cognitiveProfile());
        }

        return traceBuilder.build();
    }

    /**
     * Executes a recall query through the full cognitive pipeline and maps
     * results to {@link ScoredResult} for metric computation.
     *
     * <p>Builds options from the query, executes recall via
     * {@link SpectorMemory#recall(String, RecallOptions)}, then maps each
     * {@link CognitiveResult} to a simplified {@link ScoredResult} containing
     * only the memory ID and final fused score.</p>
     *
     * @param queryText the text content of the query for embedding and retrieval
     * @param query     the benchmark query with profile and filter parameters
     * @return scored results ordered by descending cognitive score
     */
    public List<ScoredResult> retrieve(String queryText, BenchmarkQuery query) {
        RecallOptions options = buildOptions(query);
        List<CognitiveResult> results = memory.recall(queryText, options);
        return results.stream()
                .map(r -> new ScoredResult(r.id(), r.score()))
                .toList();
    }

    /**
     * Executes a recall query and returns the full {@link CognitiveResult} list
     * including score breakdowns, for subsystem contribution analysis.
     *
     * @param queryText the text content of the query for embedding and retrieval
     * @param query     the benchmark query with profile and filter parameters
     * @return full cognitive results with breakdown metadata
     */
    public List<CognitiveResult> retrieveWithBreakdown(String queryText, BenchmarkQuery query) {
        RecallOptions options = buildOptions(query);
        return memory.recall(queryText, options);
    }

    /**
     * Builds {@link RecallOptions} configured with {@link ScoringMode#SIMILARITY}.
     *
     * <p>Identical to {@link #buildOptions(BenchmarkQuery)} except scoring mode is
     * set to {@link ScoringMode#SIMILARITY}. This means the full pipeline runs
     * (HNSW index, Bloom filter tag gating, valence filter, importance threshold)
     * but the final score is pure cosine similarity — no importance weighting,
     * no temporal decay, no tag boost, no graph expansion.</p>
     *
     * <p>Use this to isolate the effect of the pipeline infrastructure
     * (filtering, candidate selection) from the cognitive scoring model.</p>
     *
     * @param query the benchmark query with profile and filter parameters
     * @return configured RecallOptions with SIMILARITY scoring mode
     */
    RecallOptions buildSimilarityOptions(BenchmarkQuery query) {
        RecallOptions.Builder builder = RecallOptions.builder()
                .topK(10)
                .recallMode(RecallMode.OBSERVE)
                .scoringMode(ScoringMode.SIMILARITY);

        // Apply same tag filters as cognitive — the point is to test
        // the pipeline with identical filtering but different scoring
        if (!query.synapticFilterTags().isEmpty()) {
            builder.synapticFilter(query.synapticFilterTags().toArray(String[]::new));
        }

        // Apply valence bounds from query (if specified)
        if (query.minValence() != null) {
            builder.minValence(query.minValence());
        }
        if (query.maxValence() != null) {
            builder.maxValence(query.maxValence());
        }

        // Entity hints still useful for candidate discovery even in SIMILARITY mode
        if (query.entityHints() != null && !query.entityHints().isEmpty()) {
            builder.entityHints(query.entityHints());
        }

        return builder.build();
    }

    /**
     * Executes a recall query using {@link ScoringMode#SIMILARITY} and returns
     * the full {@link CognitiveResult} list.
     *
     * <p>This is the third benchmark leg: full pipeline infrastructure (HNSW,
     * Bloom filter, valence filter) but pure similarity scoring. Comparing
     * this against baseline isolates filtering effects; comparing against
     * cognitive isolates scoring effects.</p>
     *
     * @param queryText the text content of the query for embedding and retrieval
     * @param query     the benchmark query with filter parameters
     * @return results ranked by pure cosine similarity through the pipeline
     */
    public List<CognitiveResult> retrieveWithSimilarityMode(String queryText, BenchmarkQuery query) {
        RecallOptions options = buildSimilarityOptions(query);
        return memory.recall(queryText, options);
    }
}
