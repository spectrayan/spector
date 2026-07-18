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
package com.spectrayan.spector.ingestion.sensory;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Extracts text from documents (PDF, DOCX, HTML, etc.) using Apache Tika.
 *
 * <h3>Supported Formats</h3>
 * <ul>
 *   <li>PDF ({@code application/pdf})</li>
 *   <li>Microsoft Word ({@code application/msword}, {@code application/vnd.openxmlformats-officedocument.wordprocessingml.document})</li>
 *   <li>HTML ({@code text/html})</li>
 *   <li>Plain text ({@code text/plain})</li>
 *   <li>Rich Text ({@code application/rtf})</li>
 *   <li>Markdown ({@code text/markdown})</li>
 *   <li>OpenDocument ({@code application/vnd.oasis.opendocument.text})</li>
 * </ul>
 *
 * <h3>Chunking Strategy</h3>
 * <p>Tika returns the full document text. This extractor splits it into chunks
 * of configurable size with overlap, similar to {@code TextChunker}. Each chunk
 * becomes an {@link ExtractionChunk} with metadata about the document.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Thread-safe. The underlying {@link Tika} instance is thread-safe.</p>
 */
public final class TikaTextExtractor implements SensoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(TikaTextExtractor.class);

    /** Supported MIME types. */
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/html",
            "text/plain",
            "text/markdown",
            "application/rtf",
            "application/vnd.oasis.opendocument.text"
    );

    /** Maximum content length Tika will extract (100 MB as text). */
    private static final int MAX_CONTENT_LENGTH = 100 * 1024 * 1024;

    private final Tika tika;
    private final int chunkSize;
    private final int chunkOverlap;

    /**
     * Creates an extractor with configurable chunk size.
     *
     * @param chunkSize    characters per chunk (default: 800)
     * @param chunkOverlap overlap between consecutive chunks (default: 100)
     */
    public TikaTextExtractor(int chunkSize, int chunkOverlap) {
        this.tika = new Tika();
        this.tika.setMaxStringLength(MAX_CONTENT_LENGTH);
        this.chunkSize = chunkSize > 0 ? chunkSize : 800;
        this.chunkOverlap = chunkOverlap >= 0 ? chunkOverlap : 100;
        log.info("TikaTextExtractor initialized: chunkSize={}, overlap={}", this.chunkSize, this.chunkOverlap);
    }

    /** Creates an extractor with default settings (800 char chunks, 100 overlap). */
    public TikaTextExtractor() {
        this(800, 100);
    }

    @Override
    public Stream<ExtractionChunk> extract(Path source, String mimeType) throws IOException {
        if (source == null || !Files.exists(source)) {
            throw new IOException("Source file does not exist: " + source);
        }
        if (!Files.isReadable(source)) {
            throw new IOException("Source file is not readable: " + source);
        }

        log.debug("Extracting text from {} (mimeType={})", source.getFileName(), mimeType);

        // Early return for empty files (Tika throws ZeroByteFileException)
        if (Files.size(source) == 0) {
            log.debug("Skipping empty file: {}", source.getFileName());
            return Stream.empty();
        }

        Metadata tikaMetadata = new Metadata();
        if (mimeType != null) {
            tikaMetadata.set(Metadata.CONTENT_TYPE, mimeType);
        }
        tikaMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, source.getFileName().toString());

        String fullText;
        try (InputStream is = Files.newInputStream(source)) {
            fullText = tika.parseToString(is, tikaMetadata, MAX_CONTENT_LENGTH);
        } catch (org.apache.tika.exception.ZeroByteFileException e) {
            log.debug("Zero-byte file: {}", source.getFileName());
            return Stream.empty();
        } catch (TikaException e) {
            throw new IOException("Tika failed to parse " + source.getFileName() + ": " + e.getMessage(), e);
        }

        if (fullText == null || fullText.isBlank()) {
            log.debug("No text extracted from {}", source.getFileName());
            return Stream.empty();
        }

        // Clean extracted text
        fullText = fullText.strip();

        // Build document-level metadata
        Map<String, String> docMetadata = buildDocumentMetadata(source, tikaMetadata, mimeType, fullText);

        log.info("Extracted {}B text from {} (title={}, type={})",
                fullText.length(), source.getFileName(),
                docMetadata.getOrDefault("title", "n/a"),
                docMetadata.getOrDefault("content_type", "n/a"));

        // Chunk the text
        return chunkText(fullText, source.getFileName().toString(), docMetadata);
    }

    @Override
    public Set<String> supportedMimeTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        // Also support subtype variants
        String lower = mimeType.toLowerCase();
        return SUPPORTED_TYPES.contains(lower)
                || lower.startsWith("application/vnd.openxmlformats-officedocument")
                || lower.startsWith("text/");
    }

    @Override
    public boolean isAvailable() {
        return true; // Tika is pure Java — always available
    }

    // ── Internal ──

    private Map<String, String> buildDocumentMetadata(Path source, Metadata tikaMetadata,
                                                       String mimeType, String fullText) {
        Map<String, String> meta = new HashMap<>();
        meta.put("modality", "TEXT");
        meta.put("source_uri", source.toUri().toString());
        meta.put("original_filename", source.getFileName().toString());
        meta.put("extractor", "tika");

        // Detected content type
        String detectedType = tikaMetadata.get(Metadata.CONTENT_TYPE);
        if (detectedType != null) {
            meta.put("content_type", detectedType);
        } else if (mimeType != null) {
            meta.put("content_type", mimeType);
        }

        // Document metadata from Tika
        String title = tikaMetadata.get(TikaCoreProperties.TITLE);
        if (title != null && !title.isBlank()) meta.put("title", title);

        String author = tikaMetadata.get(TikaCoreProperties.CREATOR);
        if (author != null && !author.isBlank()) meta.put("author", author);

        String pageCount = tikaMetadata.get("xmpTPg:NPages");
        if (pageCount == null) pageCount = tikaMetadata.get("meta:page-count");
        if (pageCount != null) meta.put("total_pages", pageCount);

        meta.put("total_chars", String.valueOf(fullText.length()));

        return meta;
    }

    /**
     * Splits text into overlapping chunks as a lazy stream.
     */
    private Stream<ExtractionChunk> chunkText(String text, String fileName, Map<String, String> docMetadata) {
        if (text.length() <= chunkSize) {
            // Single chunk — no splitting needed
            Map<String, String> chunkMeta = new HashMap<>(docMetadata);
            chunkMeta.put("chunk_index", "0");
            chunkMeta.put("total_chunks", "1");
            return Stream.of(new ExtractionChunk("chunk-0", text, chunkMeta));
        }

        // Build chunks with overlap
        var chunks = new java.util.ArrayList<ExtractionChunk>();
        int step = Math.max(1, chunkSize - chunkOverlap);
        int totalChunks = (int) Math.ceil((double) (text.length() - chunkOverlap) / step);
        if (totalChunks < 1) totalChunks = 1;

        int idx = 0;
        int pos = 0;
        while (pos < text.length()) {
            int end = Math.min(pos + chunkSize, text.length());
            String chunkText = text.substring(pos, end).strip();

            if (!chunkText.isEmpty()) {
                Map<String, String> chunkMeta = new HashMap<>(docMetadata);
                chunkMeta.put("chunk_index", String.valueOf(idx));
                chunkMeta.put("total_chunks", String.valueOf(totalChunks));
                chunks.add(new ExtractionChunk("chunk-" + idx, chunkText, chunkMeta));
                idx++;
            }

            pos += step;
        }

        log.debug("Chunked {} into {} chunks (chunkSize={}, overlap={})",
                fileName, chunks.size(), chunkSize, chunkOverlap);

        return chunks.stream();
    }
}
