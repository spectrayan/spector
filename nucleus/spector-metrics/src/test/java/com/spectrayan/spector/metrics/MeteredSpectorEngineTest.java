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
package com.spectrayan.spector.metrics;

import com.spectrayan.spector.config.SpectorConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.engine.EngineIngestionTarget;
import com.spectrayan.spector.engine.SpectorEngine;
import com.spectrayan.spector.index.VectorIndex;
import com.spectrayan.spector.query.SearchQuery;
import com.spectrayan.spector.query.SearchResponse;
import com.spectrayan.spector.query.ranking.Reranker;
import com.spectrayan.spector.storage.DocumentStore;
import com.spectrayan.spector.storage.VectorStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MeteredSpectorEngine}.
 */
class MeteredSpectorEngineTest {

    @Test
    void searchRecordsMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        SpectorEngine stub = new DummySpectorEngine() {
            @Override
            public SearchResponse search(SearchQuery query) {
                return new SearchResponse(new com.spectrayan.spector.index.ScoredResult[0], 0, 0L, SearchQuery.SearchMode.KEYWORD);
            }
        };

        MeteredSpectorEngine metered = new MeteredSpectorEngine(stub, registry);
        SearchQuery query = SearchQuery.keyword("hello", 10);
        SearchResponse response = metered.search(query);

        assertThat(response).isNotNull();
        assertThat(registry.get(MeteredSpectorEngine.METRIC_SEARCH_TOTAL).counter().count()).isEqualTo(1.0);
        assertThat(registry.get(MeteredSpectorEngine.METRIC_SEARCH_DURATION).timer().count()).isEqualTo(1L);
    }

    @Test
    void ingestRecordsMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        SpectorEngine stub = new DummySpectorEngine();

        MeteredSpectorEngine metered = new MeteredSpectorEngine(stub, registry);
        metered.ingest("id-1", "content-1", new float[]{0.1f});

        assertThat(registry.get(MeteredSpectorEngine.METRIC_INGEST_TOTAL).counter().count()).isEqualTo(1.0);
        assertThat(registry.get(MeteredSpectorEngine.METRIC_INGEST_DURATION).timer().count()).isEqualTo(1L);
    }

    @Test
    void testAllDelegationMethods() throws Exception {
        MeterRegistry registry = new SimpleMeterRegistry();
        SpectorEngine stub = new DummySpectorEngine() {
            @Override public int ingestFile(Path path, String documentId, Function<String, float[]> vectorProvider, int chunkSize, int overlap) throws IOException { return 42; }
            @Override public int ingestFileAuto(Path path, String documentId, int chunkSize, int overlap) throws IOException { return 43; }
        };

        MeteredSpectorEngine metered = new MeteredSpectorEngine(stub, registry);

        assertThat(metered.unwrap()).isSameAs(stub);

        metered.ingest("id-1", "title-1", "content-1", new float[]{0.1f});
        metered.ingestBatch(new String[]{"id-2"}, new String[]{"content-2"}, new float[][]{{0.2f}});
        assertThat(metered.delete("id-1")).isTrue();

        metered.ingestChunked("id-3", "content-3", s -> new float[]{0.3f});
        metered.ingestChunked("id-4", "content-4", s -> new float[]{0.4f}, null);
        metered.ingestStructured("id-5", "content-5", new float[]{0.5f});

        Path tempFile = Path.of("nonexistent-file-xyz.txt");
        try {
            metered.ingestFile(tempFile, "doc-1", s -> new float[]{0.1f}, 10, 2);
        } catch (IOException ignored) {}

        try {
            metered.ingestFileAuto(tempFile, "doc-1", 10, 2);
        } catch (IOException ignored) {}

        metered.ingestTokenChunked("id-6", "content-6", s -> new float[]{0.6f}, 10, 2);
        metered.ingest("id-7", "content-7");
        metered.ingest("id-8", "title-8", "content-8");
        metered.ingestChunkedAuto("id-9", "content-9");

        metered.keywordSearch("query", 10);
        metered.vectorSearch(new float[]{0.1f}, 10);
        metered.hybridSearch("query", new float[]{0.1f}, 10);
        metered.search("query", 10);

        metered.batchCosineSimilarity(new float[0], new float[0], 0, 0);
        assertThat(metered.isGpuActive()).isFalse();

        metered.admin();
        metered.config();
        assertThat(metered.documentCount()).isEqualTo(0);
        metered.documentStore();
        metered.vectorStore();
        metered.index();
        metered.embeddingProvider();
        assertThat(metered.hasEmbeddingProvider()).isFalse();
        metered.reranker();
        assertThat(metered.isRerankerActive()).isFalse();
        metered.target();
        metered.close();
    }

    static class DummySpectorEngine implements SpectorEngine {
        @Override public void ingest(String id, String content, float[] vector) {}
        @Override public void ingest(String id, String title, String content, float[] vector) {}
        @Override public void ingestBatch(String[] ids, String[] contents, float[][] vectors) {}
        @Override public boolean delete(String id) { return true; }
        @Override public int ingestChunked(String id, String content, Function<String, float[]> vectorProvider) { return 1; }
        @Override public int ingestChunked(String id, String content, Function<String, float[]> vectorProvider, com.spectrayan.spector.commons.TextChunker chunker) { return 1; }
        @Override public void ingestStructured(String id, String content, float[] vector) {}
        @Override public int ingestFile(Path path, String documentId, Function<String, float[]> vectorProvider, int chunkSize, int overlap) throws IOException { return 1; }
        @Override public int ingestTokenChunked(String id, String content, Function<String, float[]> vectorProvider, int maxTokens, int overlapTokens) { return 1; }
        @Override public void ingest(String id, String content) {}
        @Override public void ingest(String id, String title, String content) {}
        @Override public int ingestChunkedAuto(String id, String content) { return 1; }
        @Override public int ingestFileAuto(Path path, String documentId, int chunkSize, int overlap) throws IOException { return 1; }
        @Override public SearchResponse search(SearchQuery query) { return null; }
        @Override public SearchResponse keywordSearch(String text, int topK) { return null; }
        @Override public SearchResponse vectorSearch(float[] vector, int topK) { return null; }
        @Override public SearchResponse hybridSearch(String text, float[] vector, int topK) { return null; }
        @Override public SearchResponse search(String text, int topK) { return null; }
        @Override public float[] batchCosineSimilarity(float[] query, float[] database, int n, int dims) { return null; }
        @Override public boolean isGpuActive() { return false; }
        @Override public SpectorConfig config() { return null; }
        @Override public int documentCount() { return 0; }
        @Override public DocumentStore documentStore() { return null; }
        @Override public VectorStore vectorStore() { return null; }
        @Override public VectorIndex index() { return null; }
        @Override public EmbeddingProvider embeddingProvider() { return null; }
        @Override public boolean hasEmbeddingProvider() { return false; }
        @Override public Reranker reranker() { return null; }
        @Override public boolean isRerankerActive() { return false; }
        @Override public EngineIngestionTarget target() { return null; }
        @Override public com.spectrayan.spector.engine.SpectorEngineAdmin admin() { return null; }
        @Override public void close() {}
    }
}
