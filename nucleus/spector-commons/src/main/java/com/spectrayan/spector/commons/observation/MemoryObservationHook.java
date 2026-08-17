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
package com.spectrayan.spector.commons.observation;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Lightweight, zero-dependency observation SPI for cognitive memory operations.
 *
 * <p>Enables low-level core and memory components to fire observation spans and lifecycle
 * timings without taking a compile-time dependency on Micrometer or OpenTelemetry.</p>
 */
@FunctionalInterface
public interface MemoryObservationHook {

    // Observation Name Suffix constants
    String EMBEDDING = "embedding";
    String LLM_INFERENCE = "llm";
    String VECTOR_SEARCH = "vector_search";
    String BM25_SEARCH = "bm25_search";
    String SPLADE_SEARCH = "splade_search";
    String SCORING = "scoring";
    String GRAPH_EXPANSION = "graph_expansion";
    String CONTRADICTION = "contradiction";
    String TAG_EXTRACTION = "tag_extraction";
    String CHUNKING = "chunking";
    String ENTITY_EXTRACTION = "entity_extraction";
    String GRAPH_SYNC = "graph_sync";
    
    // Scoring sub-operations
    String SCORING_COGNITIVE = "cognitive";
    String SCORING_HABITUATION = "habituation";
    String SCORING_STDP = "stdp";
    String SCORING_HEBBIAN = "hebbian";
    String SCORING_VALENCE = "valence";
    String SCORING_TOPK = "topk";

    // Tag Key constants
    String TAG_TIER = "spector.tier";
    String TAG_NAMESPACE = "spector.namespace";
    String TAG_STATUS = "spector.status";
    String TAG_PROVIDER = "spector.provider";
    String TAG_INDEX_TYPE = "spector.index_type";
    String TAG_SEARCH_MODE = "spector.search_mode";
    String TAG_MODEL_ID = "spector.model_id";
    String TAG_CANDIDATES = "spector.candidates";
    String TAG_CHUNKS = "spector.chunks";
    String TAG_TOKENS_IN = "spector.tokens_in";
    String TAG_TOKENS_OUT = "spector.tokens_out";
    String TAG_MEMORY_ID = "spector.memory_id";
    String TAG_SESSION_ID = "spector.session_id";
    String TAG_QUERY = "spector.query";
    String TAG_TASK_ID = "spector.task_id";

    /**
     * No-op implementation that does nothing and returns an empty AutoCloseable.
     */
    MemoryObservationHook NOOP = new NoOpHook();

    /**
     * Starts an observation span for the given operation name and contextual tags.
     *
     * @param observationSuffix operation name suffix (e.g. "recall", "chunking")
     * @param tags contextual low-cardinality and high-cardinality tags
     * @return an AutoCloseable handle whose {@link AutoCloseable#close()} method stops the observation
     */
    AutoCloseable start(String observationSuffix, Map<String, String> tags);

    default <T> T observe(String suffix, Map<String, String> tags, Supplier<T> work) {
        try (AutoCloseable ac = start(suffix, tags)) {
            return work.get();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    default <T> T observe(String suffix, Supplier<Map<String, String>> tagSupplier, Supplier<T> work) {
        try (AutoCloseable ac = start(suffix, tagSupplier.get())) {
            return work.get();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    default void observe(String suffix, Map<String, String> tags, Runnable work) {
        try (AutoCloseable ac = start(suffix, tags)) {
            work.run();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    default void observe(String suffix, Supplier<Map<String, String>> tagSupplier, Runnable work) {
        try (AutoCloseable ac = start(suffix, tagSupplier.get())) {
            work.run();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
