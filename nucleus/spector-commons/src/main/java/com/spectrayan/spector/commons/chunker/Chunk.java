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

import java.util.Map;

/**
 * A chunk of content produced by a {@link Chunker}.
 *
 * <p>Carries the chunk text, positional information within the source document,
 * and optional metadata. The {@code metadata} map conveys per-chunk context:</p>
 * <ul>
 *   <li>{@code block_type} — what the chunk contains: {@code "prose"},
 *       {@code "code:java"}, {@code "table"}, {@code "mermaid"},
 *       {@code "blockquote"}, {@code "mixed"}</li>
 *   <li>{@code heading_context} — breadcrumb of containing headings
 *       (e.g., {@code "## Architecture > ### Data Flow"})</li>
 *   <li>{@code content_type} — MIME type hint for the chunk content</li>
 *   <li>{@code chunk_index}, {@code total_chunks} — positional info</li>
 * </ul>
 *
 * @param parentId   parent document/media identifier
 * @param chunkId    unique chunk identifier (e.g., {@code "doc-1::chunk-0"})
 * @param index      zero-based chunk index within the parent
 * @param text       the chunk text content
 * @param startChar  character offset in the original content ({@code -1} if N/A)
 * @param endChar    end character offset (exclusive) in the original content ({@code -1} if N/A)
 * @param metadata   per-chunk metadata (block_type, heading_context, etc.)
 */
public record Chunk(
        String parentId,
        String chunkId,
        int index,
        String text,
        int startChar,
        int endChar,
        Map<String, String> metadata
) {

    /**
     * Convenience constructor without metadata.
     */
    public Chunk(String parentId, String chunkId, int index, String text,
                 int startChar, int endChar) {
        this(parentId, chunkId, index, text, startChar, endChar, Map.of());
    }

    /**
     * Compact constructor — enforces non-null text and defensive metadata copy.
     */
    public Chunk {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        if (metadata == null) {
            metadata = Map.of();
        } else {
            metadata = Map.copyOf(metadata);
        }
    }

    /**
     * Returns the character length of this chunk.
     *
     * @return number of characters in the chunk text
     */
    public int length() {
        return text.length();
    }
}
