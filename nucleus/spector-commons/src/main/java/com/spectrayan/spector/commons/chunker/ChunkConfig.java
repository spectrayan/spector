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

/**
 * Configuration for chunk splitting behavior.
 *
 * <p>Controls how a {@link TextChunker} splits content: maximum chunk size,
 * overlap between chunks, document format hints, and structural preservation
 * options.</p>
 *
 * <h3>Document Format vs Block Detection</h3>
 * <ul>
 *   <li>{@code documentFormat} — the format of the input text, driving
 *       the parsing strategy (e.g., {@code "text/markdown"} enables heading
 *       and fenced-block detection)</li>
 *   <li>{@code sourceMimeType} — the original document MIME type before
 *       extraction (e.g., {@code "application/pdf"}) — informational only,
 *       flows into chunk metadata for provenance</li>
 *   <li>{@code detectEmbeddedBlocks} — when {@code true}, heuristically
 *       detects JSON, XML, and code blocks even in plain text (useful for
 *       text extracted from Word/PDF documents)</li>
 * </ul>
 *
 * @param maxChunkSize         maximum characters per chunk
 * @param overlap              overlap characters between consecutive chunks
 * @param documentFormat       format of the input text — drives parsing strategy
 * @param sourceMimeType       original document MIME type before extraction (nullable)
 * @param preserveBlocks       if {@code true}, never split inside structural blocks
 * @param detectEmbeddedBlocks if {@code true}, heuristically detect JSON/XML/code in plain text
 * @param stripHeaders         if {@code true}, move heading text to metadata only
 */
public record ChunkConfig(
        int maxChunkSize,
        int overlap,
        String documentFormat,
        String sourceMimeType,
        boolean preserveBlocks,
        boolean detectEmbeddedBlocks,
        boolean stripHeaders,
        boolean parentChildLinking
) {

    public ChunkConfig(int maxChunkSize, int overlap, String documentFormat,
                       String sourceMimeType, boolean preserveBlocks,
                       boolean detectEmbeddedBlocks, boolean stripHeaders) {
        this(maxChunkSize, overlap, documentFormat, sourceMimeType, preserveBlocks, detectEmbeddedBlocks, stripHeaders, false);
    }

    /** Default configuration: 800 chars, 100 overlap, plain text, all detection enabled. */
    public static final ChunkConfig DEFAULT =
            new ChunkConfig(800, 100, "text/plain", null, true, true, false, false);

    /**
     * Creates a markdown-optimized configuration.
     *
     * @param maxChunkSize maximum characters per chunk
     * @param overlap      overlap characters between consecutive chunks
     * @return markdown chunk config
     */
    public static ChunkConfig markdown(int maxChunkSize, int overlap) {
        return new ChunkConfig(maxChunkSize, overlap, "text/markdown", null, true, true, false, false);
    }

    /**
     * Creates a markdown-optimized configuration with parent-child linking enabled.
     *
     * @param maxChunkSize maximum characters per chunk
     * @param overlap      overlap characters between consecutive chunks
     * @return markdown parent-child chunk config
     */
    public static ChunkConfig markdownParentChild(int maxChunkSize, int overlap) {
        return new ChunkConfig(maxChunkSize, overlap, "text/markdown", null, true, true, false, true);
    }

    /**
     * Creates a plain text configuration.
     *
     * @param maxChunkSize maximum characters per chunk
     * @param overlap      overlap characters between consecutive chunks
     * @return plain text chunk config
     */
    public static ChunkConfig plainText(int maxChunkSize, int overlap) {
        return new ChunkConfig(maxChunkSize, overlap, "text/plain", null, true, false, false, false);
    }

    /**
     * Creates a configuration for text extracted from a binary document.
     *
     * <p>Enables embedded block detection since the original structure may have
     * been lost during extraction (e.g., Word/PDF → plain text via Tika).</p>
     *
     * @param maxChunkSize   maximum characters per chunk
     * @param overlap        overlap characters between consecutive chunks
     * @param sourceMimeType original document MIME type (e.g., "application/pdf")
     * @return extracted document chunk config
     */
    public static ChunkConfig forExtractedDocument(int maxChunkSize, int overlap,
                                                    String sourceMimeType) {
        return new ChunkConfig(maxChunkSize, overlap, "text/plain", sourceMimeType,
                true, true, false, false);
    }

    /**
     * Compact constructor — validates bounds and applies defaults.
     */
    public ChunkConfig {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("maxChunkSize must be > 0, got: " + maxChunkSize);
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("overlap must be >= 0, got: " + overlap);
        }
        if (overlap >= maxChunkSize) {
            throw new IllegalArgumentException(
                    "overlap (" + overlap + ") must be < maxChunkSize (" + maxChunkSize + ")");
        }
        if (documentFormat == null) {
            documentFormat = "text/plain";
        }
    }
}
