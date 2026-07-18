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
package com.spectrayan.spector.commons.chunker;

import java.util.List;
import java.util.Set;

/**
 * SPI for splitting text content into semantic chunks.
 *
 * <p>Implementations should produce chunks that preserve semantic coherence —
 * respecting sentence boundaries, paragraph structure, and format-specific
 * elements (headings, code blocks, tables, diagrams).</p>
 *
 * <h3>Implementations</h3>
 * <ul>
 *   <li>{@code MarkdownChunker} — markdown-aware, respects headings/tables/code blocks/mermaid</li>
 *   <li>{@code SentenceChunker} — sentence-boundary splitting via {@link java.text.BreakIterator}</li>
 * </ul>
 *
 * <h3>Discovery</h3>
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} and
 * registered in {@code META-INF/services/com.spectrayan.spector.commons.chunker.TextChunker}.</p>
 *
 * @see Chunker
 * @see ChunkConfig
 * @see Chunk
 * @see ChunkerRegistry
 */
public interface TextChunker extends Chunker {

    /**
     * Splits text content into ordered chunks.
     *
     * <p>The returned list is ordered by document position. Each chunk carries
     * metadata including {@code block_type} and {@code heading_context} when
     * available.</p>
     *
     * @param documentId parent document identifier
     * @param content    text content to split
     * @param config     chunking configuration
     * @return ordered list of chunks (never null, may be empty for blank input)
     */
    List<Chunk> chunk(String documentId, String content, ChunkConfig config);

    /**
     * Convenience overload using {@link ChunkConfig#DEFAULT}.
     *
     * @param documentId parent document identifier
     * @param content    text content to split
     * @return ordered list of chunks
     */
    default List<Chunk> chunk(String documentId, String content) {
        return chunk(documentId, content, ChunkConfig.DEFAULT);
    }

    /**
     * Returns the content types this chunker handles optimally.
     *
     * <p>Used by {@link ChunkerRegistry} to select the best chunker for
     * a given document format. Implementations should return the set of
     * MIME types they are specifically designed for.</p>
     *
     * @return supported content types (e.g., {@code {"text/markdown"}})
     */
    default Set<String> supportedContentTypes() {
        return Set.of("text/plain");
    }
}
