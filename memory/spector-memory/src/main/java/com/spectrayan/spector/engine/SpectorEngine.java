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
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.index.VectorIndex;
import com.spectrayan.spector.query.SearchQuery;
import com.spectrayan.spector.query.SearchResponse;
import com.spectrayan.spector.query.ranking.Reranker;
import com.spectrayan.spector.storage.DocumentStore;
import com.spectrayan.spector.storage.VectorStore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Primary interface for the Spector engine.
 *
 * <p>Provides a unified API for document ingestion, search, and lifecycle
 * management. Implementations include {@link DefaultSpectorEngine} (the
 * standard implementation) and metered decorators for observability.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   SpectorEngine engine = DefaultSpectorEngine.builder()
 *       .dimensions(384)
 *       .capacity(100_000)
 *       .build();
 *
 *   engine.ingest("doc-1", "Hello world", vectorData);
 *   SearchResponse response = engine.search(SearchQuery.keyword("hello", 10));
 * }</pre>
 *
 * @see DefaultSpectorEngine
 */
public interface SpectorEngine extends AutoCloseable {

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ Ingestion â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Ingests a single document with its text content and vector embedding. */
    void ingest(String id, String content, float[] vector);

    /** Ingests a document with title, content, and vector. */
    void ingest(String id, String title, String content, float[] vector);

    /** Ingests a batch of documents. */
    void ingestBatch(String[] ids, String[] contents, float[][] vectors);

    /** Deletes a document by ID from all indexes. */
    boolean delete(String id);

    /** Ingests a large document by splitting it into overlapping chunks. */
    int ingestChunked(String id, String content,
                      Function<String, float[]> vectorProvider);

    /** Ingests a large document with a custom chunker configuration. */
    int ingestChunked(String id, String content,
                      Function<String, float[]> vectorProvider,
                      com.spectrayan.spector.commons.TextChunker chunker);

    /** Ingests structured content (XML, JSON, Java objects) by extracting text. */
    void ingestStructured(String id, String content, float[] vector);

    /** Ingests a large file using streaming chunking with bounded memory. */
    int ingestFile(Path path, String documentId,
                   Function<String, float[]> vectorProvider,
                   int chunkSize, int overlap) throws IOException;

    /** Ingests a large document using token-level chunking. */
    int ingestTokenChunked(String id, String content,
                           Function<String, float[]> vectorProvider,
                           int maxTokens, int overlapTokens);

    /** Ingests a document with automatic embedding generation. */
    void ingest(String id, String content);

    /** Ingests a document with title and automatic embedding. */
    void ingest(String id, String title, String content);

    /** Auto-embed chunked ingestion for large documents. */
    int ingestChunkedAuto(String id, String content);

    /** Auto-embed file ingestion with streaming. */
    int ingestFileAuto(Path path, String documentId,
                       int chunkSize, int overlap) throws IOException;

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ Search â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Executes a search query. */
    SearchResponse search(SearchQuery query);

    /** Convenience: keyword search. */
    SearchResponse keywordSearch(String text, int topK);

    /** Convenience: vector search. */
    SearchResponse vectorSearch(float[] vector, int topK);

    /** Convenience: hybrid search. */
    SearchResponse hybridSearch(String text, float[] vector, int topK);

    /** Auto-embed search: embeds the query text and performs hybrid search. */
    SearchResponse search(String text, int topK);

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ GPU-Accelerated Batch Operations â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Computes batch cosine similarities using GPU if available, CPU SIMD otherwise. */
    float[] batchCosineSimilarity(float[] query, float[] database, int n, int dims);

    /** Returns whether GPU acceleration is active. */
    boolean isGpuActive();

    /** Returns the number of indexed documents. */
    int documentCount();

    /** Returns true if an embedding provider is configured. */
    boolean hasEmbeddingProvider();

    /** Returns true if LLM re-ranking is active. */
    boolean isRerankerActive();

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ Admin Interface â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Returns the administrative interface for accessing internal subsystems.
     *
     * <p>Typical SDK consumers should not need this â€” it provides access to
     * document store, vector store, vector index, embedding provider, and
     * re-ranker for operational monitoring, tuning, and advanced integrations.</p>
     *
     * @return the admin interface (never null)
     * @since 1.0.0
     */
    SpectorEngineAdmin admin();

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ Deprecated Accessors (use admin() instead) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** @deprecated Use {@link #admin()}.{@link SpectorEngineAdmin#config() config()} instead. */
    @Deprecated(since = "1.0.0", forRemoval = true) SpectorConfig config();
    /** @deprecated Use {@link #admin()}.{@link SpectorEngineAdmin#documentStore() documentStore()} instead. */
    @Deprecated(since = "1.0.0", forRemoval = true) DocumentStore documentStore();
    /** @deprecated Use {@link #admin()}.{@link SpectorEngineAdmin#vectorStore() vectorStore()} instead. */
    @Deprecated(since = "1.0.0", forRemoval = true) VectorStore vectorStore();
    /** @deprecated Use {@link #admin()}.{@link SpectorEngineAdmin#index() index()} instead. */
    @Deprecated(since = "1.0.0", forRemoval = true) VectorIndex index();
    /** @deprecated Use {@link #admin()}.{@link SpectorEngineAdmin#embeddingProvider() embeddingProvider()} instead. */
    @Deprecated(since = "1.0.0", forRemoval = true) EmbeddingProvider embeddingProvider();
    /** @deprecated Use {@link #admin()}.{@link SpectorEngineAdmin#reranker() reranker()} instead. */
    @Deprecated(since = "1.0.0", forRemoval = true) Reranker reranker();
    /** @deprecated Use {@link #admin()}.{@link SpectorEngineAdmin#target() target()} instead. */
    @Deprecated(since = "1.0.0", forRemoval = true) EngineIngestionTarget target();

    /** Closes the engine and releases all resources. */
    @Override
    void close();

    /** Returns a new fluent {@link DefaultSpectorEngine.Builder} for constructing an engine. */
    static DefaultSpectorEngine.Builder builder() {
        return DefaultSpectorEngine.builder();
    }
}
