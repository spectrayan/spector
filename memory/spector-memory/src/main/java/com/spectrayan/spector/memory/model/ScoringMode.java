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
package com.spectrayan.spector.memory.model;

/**
 * Controls how recall results are scored after retrieval.
 *
 * <p>This is orthogonal to {@link com.spectrayan.spector.config.model.TextSearchMode}, which controls <em>which</em>
 * retrieval signals to use (vector, keyword, both). {@code ScoringMode} controls
 * <em>how</em> the retrieved candidates are ranked.</p>
 *
 * <h3>Modes</h3>
 * <ul>
 *   <li><b>{@link #COGNITIVE}</b>: Full biologically-inspired scoring pipeline —
 *       importance weighting, temporal decay, tag overlap boosting, valence alignment.
 *       Use for interactive memory systems where recall quality depends on
 *       relevance + recency + emotional context.</li>
 *   <li><b>{@link #SIMILARITY}</b>: Pure vector similarity (cosine/dot product) —
 *       the HNSW score IS the final score. No importance, no decay, no tag boosting.
 *       Use for information retrieval benchmarks and document search where
 *       semantic similarity is the only ranking signal.</li>
 * </ul>
 *
 * @see RecallOptions
 * @see com.spectrayan.spector.config.model.TextSearchMode
 */
public enum ScoringMode {

    /**
     * Full cognitive scoring pipeline:
     * {@code alpha × similarity + beta × importance × decay},
     * with tag overlap boosting and optional valence alignment.
     */
    COGNITIVE,

    /**
     * Pure similarity scoring: HNSW cosine similarity is the final score.
     * Bypasses importance, decay, tag boosting, and valence alignment.
     * Ideal for information retrieval benchmarks.
     */
    SIMILARITY,

    /**
     * Associative scoring: tag-weighted contextual recall.
     *
     * <p>Biological analog: Hippocampal pattern completion. When the prefrontal
     * cortex struggles with top-down retrieval (as in executive dysfunction),
     * the hippocampus compensates with bottom-up associative activation —
     * recent context tags prime related memories for easier retrieval.</p>
     *
     * <p>Scoring combines standard cognitive scoring with enhanced tag overlap
     * boosting from recent recall history. Lateral retrieval is enabled to
     * surface tangentially related memories that share contextual tags.</p>
     *
     * @see CognitiveProfile#EXECUTIVE_DYSFUNCTION
     */
    ASSOCIATIVE
}
