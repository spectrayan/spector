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
package com.spectrayan.spector.engine;

import com.spectrayan.spector.config.SpectorConfig;
import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.index.VectorIndex;
import com.spectrayan.spector.query.ranking.Reranker;
import com.spectrayan.spector.storage.DocumentStore;
import com.spectrayan.spector.storage.VectorStore;

/**
 * Administrative interface for the Spector Engine.
 *
 * <p>Provides access to internal subsystems (document store, vector store,
 * vector index, embedding provider, re-ranker) for operational monitoring,
 * tuning, and advanced integrations.</p>
 *
 * <p>This interface is <b>not intended for typical SDK consumers</b>.
 * Use {@link SpectorEngine} for the public API (ingest, search, etc.).
 * Access this via {@link SpectorEngine#admin()}.</p>
 *
 * @since 1.0.0
 * @see SpectorEngine
 */
public interface SpectorEngineAdmin {

    /** Returns the engine configuration. */
    SpectorConfig config();

    /** Returns the document store. */
    DocumentStore documentStore();

    /** Returns the vector store. */
    VectorStore vectorStore();

    /** Returns the underlying vector index (for ANN pre-filtering by Memory). */
    VectorIndex index();

    /** Returns the embedding provider, or null if none configured. */
    EmbeddingProvider embeddingProvider();

    /** Returns the active re-ranker, or null if none configured. */
    Reranker reranker();

    /** Returns the engine's ingestion target for use with the unified IngestionPipeline. */
    EngineIngestionTarget target();
}
