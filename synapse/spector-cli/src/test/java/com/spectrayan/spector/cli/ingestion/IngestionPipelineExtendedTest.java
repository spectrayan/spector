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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;

/**
 * Extended tests for {@link IngestionPipeline}.
 */
@DisplayName("IngestionPipeline -- Extended Coverage")
class IngestionPipelineExtendedTest {

    private SpectorMemory mockMemory;
    private EmbeddingProvider mockProvider;

    @BeforeEach
    void setUp() {
        mockMemory = mock(SpectorMemory.class);
        mockProvider = mock(EmbeddingProvider.class);
    }

    private EmbeddingResult stubEmbedding(int dims) {
        return EmbeddingResult.of(new float[dims], "test-model");
    }

    // ==============================================================
    // Builder
    // ==============================================================

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("rejects null memory")
        void rejectsNullMemory() {
            assertThatThrownBy(() -> IngestionPipeline.builder().build())
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("builds with memory only (no embedder)")
        void buildsWithMemoryOnly() {
            var pipeline = IngestionPipeline.builder()
                    .memory(mockMemory)
                    .build();
            assertThat(pipeline.hasEmbeddingProvider()).isFalse();
            assertThat(pipeline.chunker()).isNull();
        }

        @Test
        @DisplayName("builds with all options")
        void buildsWithAllOptions() {
            var pipeline = IngestionPipeline.builder()
                    .memory(mockMemory)
                    .embeddingProvider(mockProvider)
                    .chunker(new com.spectrayan.spector.commons.chunker.MarkdownChunker())
                    .chunkConfig(new com.spectrayan.spector.commons.chunker.ChunkConfig(500, 50, "text/plain", null, false, false, false))
                    .chunkThreshold(500)
                    .build();
            assertThat(pipeline.hasEmbeddingProvider()).isTrue();
            assertThat(pipeline.chunker()).isNotNull();
        }
    }

    // ==============================================================
    // Pre-embedded ingest
    // ==============================================================

    @Nested
    @DisplayName("pre-embedded ingest")
    class PreEmbeddedTests {

        @Test
        @DisplayName("ingest with pre-computed vector succeeds")
        void preEmbeddedIngest() {
            var pipeline = IngestionPipeline.builder()
                    .memory(mockMemory)
                    .build();

            float[] vector = {0.1f, 0.2f, 0.3f};
            var result = pipeline.ingest("doc-1", "Hello world", vector);

            assertThat(result.documentId()).isEqualTo("doc-1");
            assertThat(result.chunksStored()).isEqualTo(1);
            verify(mockMemory).remember("doc-1", "Hello world", vector, MemoryType.SEMANTIC, MemorySource.OBSERVED);
        }

        @Test
        @DisplayName("pre-embedded ingest returns timing >= 0")
        void preEmbeddedTiming() {
            var pipeline = IngestionPipeline.builder()
                    .memory(mockMemory)
                    .build();

            var result = pipeline.ingest("doc-1", "text", new float[]{1f});
            assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
        }
    }

    // ==============================================================
    // Direct ingest
    // ==============================================================

    @Nested
    @DisplayName("direct ingest")
    class DirectIngestTests {

        @Test
        @DisplayName("short content ingests directly without chunking")
        void directIngest() {
            when(mockProvider.embed(anyString())).thenReturn(stubEmbedding(3));

            var pipeline = IngestionPipeline.builder()
                    .memory(mockMemory)
                    .embeddingProvider(mockProvider)
                    .chunker(new com.spectrayan.spector.commons.chunker.MarkdownChunker())
                    .chunkConfig(new com.spectrayan.spector.commons.chunker.ChunkConfig(500, 50, "text/plain", null, false, false, false))
                    .chunkThreshold(500)
                    .build();

            var result = pipeline.ingest("doc-1", "Short text");

            assertThat(result.documentId()).isEqualTo("doc-1");
            assertThat(result.chunksStored()).isEqualTo(1);
            verify(mockMemory).remember(eq("doc-1"), eq("Short text"), any(float[].class), eq(MemoryType.SEMANTIC), eq(MemorySource.OBSERVED));
        }

        @Test
        @DisplayName("ingest without embedder throws validation exception")
        void noEmbedderThrows() {
            var pipeline = IngestionPipeline.builder()
                    .memory(mockMemory)
                    .build();

            assertThatThrownBy(() -> pipeline.ingest("doc-1", "text"))
                    .isInstanceOf(SpectorValidationException.class);
        }
    }

    // ==============================================================
    // Chunked ingest
    // ==============================================================

    @Nested
    @DisplayName("shouldChunk behavior")
    class ChunkingDecisionTests {

        @Test
        @DisplayName("long content without chunker falls through to direct path")
        void noChukerDirectPath() {
            when(mockProvider.embed(anyString())).thenReturn(stubEmbedding(3));

            var pipeline = IngestionPipeline.builder()
                    .memory(mockMemory)
                    .embeddingProvider(mockProvider)
                    .build();

            String longContent = "x".repeat(5000);
            var result = pipeline.ingest("doc-long", longContent);

            assertThat(result.documentId()).isEqualTo("doc-long");
            assertThat(result.chunksStored()).isEqualTo(1);
        }
    }
}
