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
package com.spectrayan.spector.cli.ingestion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.commons.chunker.StreamingChunker;
import com.spectrayan.spector.commons.chunker.Chunk;
import com.spectrayan.spector.commons.chunker.ChunkConfig;
import com.spectrayan.spector.commons.chunker.ChunkerRegistry;
import com.spectrayan.spector.commons.chunker.TextChunker;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorInternalException;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.provider.embedding.EmbedConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.ParallelEmbeddingPipeline;
import com.spectrayan.spector.provider.embedding.PipelineEmbeddingResult;

/**
 * Unified ingestion pipeline: chunk -> embed -> remember in SpectorMemory.
 *
 * <p>Configured via a {@link Builder} and exposes a single {@link #ingest}
 * entry point. The pipeline decides the strategy (direct, chunked, streaming)
 * based on builder configuration and content characteristics.</p>
 */
public class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);

    private final SpectorMemory memory;
    private final EmbeddingProvider embeddingProvider; // nullable for pre-embedded mode
    private final ParallelEmbeddingPipeline parallelPipeline; // nullable
    private final EmbedConfig embedConfig;              // configurable batch size
    private final TextChunker spiChunker; // nullable — SPI chunker
    private final ChunkConfig chunkConfig; // SPI chunk configuration
    private final int chunkThreshold;    // auto-chunk if content length exceeds this

    private IngestionPipeline(Builder builder) {
        this.memory = builder.memory;
        this.embeddingProvider = builder.embeddingProvider;
        this.spiChunker = builder.spiChunker;
        this.chunkConfig = builder.chunkConfig;
        this.chunkThreshold = builder.chunkThreshold;

        // Initialize parallel embedding pipeline if provider is available
        this.parallelPipeline = builder.embeddingProvider != null
                ? new ParallelEmbeddingPipeline(builder.embeddingProvider) : null;
        this.embedConfig = builder.embedConfig;

        String chunkerName = spiChunker != null ? spiChunker.name() : "none";
        log.info("IngestionPipeline created: chunker={}, chunkThreshold={}, hasEmbedder={}, memory={}",
                chunkerName,
                chunkThreshold,
                embeddingProvider != null,
                memory.getClass().getSimpleName());
    }

    /** Creates a new builder. */
    public static Builder builder() {
        return new Builder();
    }

    // ===============================================================
    // PUBLIC API — single ingest() method with overloads
    // ===============================================================

    /**
     * Ingests text content with auto-embedding.
     *
     * @param id      document ID
     * @param content text content
     * @return ingestion result
     * @throws SpectorValidationException if no embedding provider is configured
     */
    public IngestionResult ingest(String id, String content) {
        requireEmbeddingProvider();
        long start = System.nanoTime();

        if (shouldChunk(content)) {
            return chunkAndIngest(id, content, start);
        }
        return directIngest(id, content, start);
    }

    /**
     * Ingests text content with a pre-computed embedding vector.
     *
     * @param id      document ID
     * @param content text content
     * @param vector  pre-computed embedding vector
     * @return ingestion result
     */
    public IngestionResult ingest(String id, String content, float[] vector) {
        long start = System.nanoTime();

        memory.remember(id, content, vector, MemoryType.SEMANTIC, MemorySource.OBSERVED);

        long elapsed = (System.nanoTime() - start) / 1_000_000;
        return IngestionResult.single(id, elapsed);
    }

    /**
     * Ingests a file by streaming its content chunk-by-chunk.
     *
     * @param file       path to the text file
     * @param documentId parent document ID
     * @return ingestion result
     * @throws IOException if the file cannot be read
     */
    public IngestionResult ingest(Path file, String documentId) throws IOException {
        requireEmbeddingProvider();
        long start = System.nanoTime();

        int chunkSize = chunkConfig.maxChunkSize();
        int overlap = chunkConfig.overlap();

        int count = 0;
        List<String> failures = new ArrayList<>();

        try (var stream = StreamingChunker.chunkFile(file, documentId, chunkSize, overlap)) {
            var iter = stream.iterator();
            while (iter.hasNext()) {
                var chunk = iter.next();
                try {
                    float[] vector = embeddingProvider.embed(chunk.text()).vector();
                    memory.remember(chunk.chunkId(), chunk.text(), vector, MemoryType.SEMANTIC, MemorySource.OBSERVED);
                    count++;
                } catch (Exception e) {
                    failures.add(chunk.chunkId());
                    log.warn("Streaming ingestion failed for chunk '{}': {}",
                            chunk.chunkId(), e.getMessage());
                }
            }
        }

        long elapsed = (System.nanoTime() - start) / 1_000_000;
        log.info("Stream-ingested '{}' -> {} chunks ({} failed) in {}ms",
                file.getFileName(), count, failures.size(), elapsed);
        return IngestionResult.chunked(documentId, count, failures, elapsed);
    }

    // ===============================================================
    // INTERNAL STRATEGIES — selected by ingest() based on config
    // ===============================================================

    private IngestionResult directIngest(String id, String content, long startNanos) {
        float[] vector = embeddingProvider.embed(content).vector();
        memory.remember(id, content, vector, MemoryType.SEMANTIC, MemorySource.OBSERVED);

        long elapsed = (System.nanoTime() - startNanos) / 1_000_000;
        return IngestionResult.single(id, elapsed);
    }

    private IngestionResult chunkAndIngest(String id, String content, long startNanos) {
        var spiChunks = spiChunker != null ? spiChunker.chunk(id, content, chunkConfig) : List.<Chunk>of();
        List<String> texts = spiChunks.stream().map(Chunk::text).toList();
        List<String> chunkIds = spiChunks.stream().map(Chunk::chunkId).toList();

        // Parallel embedding using virtual threads (batch size from config)
        List<PipelineEmbeddingResult> embeddings = parallelPipeline.embed(texts, embedConfig);

        List<String> failures = new ArrayList<>();
        int stored = 0;

        for (int i = 0; i < texts.size(); i++) {
            var embedding = embeddings.get(i);

            if (embedding.success()) {
                memory.remember(chunkIds.get(i), texts.get(i), embedding.embedding(), MemoryType.SEMANTIC, MemorySource.OBSERVED);
                stored++;
            } else {
                failures.add(chunkIds.get(i));
                log.warn("Embedding failed for chunk '{}': {}", chunkIds.get(i), embedding.error());
            }
        }

        long elapsed = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("Ingested '{}' as {} chunks ({} failed) in {}ms",
                id, stored, failures.size(), elapsed);
        return IngestionResult.chunked(id, stored, failures, elapsed);
    }

    // ===============================================================
    // INTERNAL HELPERS
    // ===============================================================

    private boolean shouldChunk(String content) {
        return spiChunker != null && content.length() > chunkThreshold;
    }

    private void requireEmbeddingProvider() {
        if (embeddingProvider == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "No EmbeddingProvider configured. Use builder().embeddingProvider(provider) " + "or use ingest(id, content, vector) with a pre-computed vector.");
        }
    }

    /** Returns true if an embedding provider is configured. */
    public boolean hasEmbeddingProvider() {
        return embeddingProvider != null;
    }

    /** Returns the configured SPI chunker (nullable). */
    public TextChunker chunker() {
        return spiChunker;
    }

    // ===============================================================
    // BUILDER
    // ===============================================================

    public static final class Builder {
        private SpectorMemory memory;
        private EmbeddingProvider embeddingProvider;
        private TextChunker spiChunker;
        private ChunkConfig chunkConfig = ChunkConfig.DEFAULT;
        private int chunkThreshold = 800;
        private EmbedConfig embedConfig = EmbedConfig.DEFAULT;

        private Builder() {}

        /** Sets the SpectorMemory that receives ingested chunks. Required. */
        public Builder memory(SpectorMemory memory) {
            this.memory = memory;
            return this;
        }

        /** Sets the embedding provider for auto-embedding. */
        public Builder embeddingProvider(EmbeddingProvider embeddingProvider) {
            this.embeddingProvider = embeddingProvider;
            return this;
        }

        /** Sets the text chunker SPI implementation. */
        public Builder chunker(TextChunker chunker) {
            this.spiChunker = chunker;
            return this;
        }

        /** Sets chunk configuration (size, overlap, document format). */
        public Builder chunkConfig(ChunkConfig config) {
            this.chunkConfig = config != null ? config : ChunkConfig.DEFAULT;
            return this;
        }

        /** Sets the content length threshold for auto-chunking. */
        public Builder chunkThreshold(int threshold) {
            this.chunkThreshold = threshold;
            return this;
        }

        /** Sets the embedding pipeline configuration. */
        public Builder embedConfig(EmbedConfig config) {
            this.embedConfig = config != null ? config : EmbedConfig.DEFAULT;
            return this;
        }

        /** Builds the pipeline. */
        public IngestionPipeline build() {
            if (memory == null) {
                throw new SpectorInternalException(ErrorCode.ARGUMENT_NULL, "SpectorMemory");
            }
            return new IngestionPipeline(this);
        }
    }
}
