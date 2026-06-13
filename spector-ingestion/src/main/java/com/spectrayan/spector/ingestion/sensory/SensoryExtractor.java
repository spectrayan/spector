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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * SPI for extracting text representations from non-text content.
 *
 * <p>Implementations transform binary content (images, audio, video) into
 * text chunks suitable for embedding and cognitive storage. Each chunk carries
 * its source modality, original asset URI, and arbitrary metadata.</p>
 *
 * <h3>Implementations</h3>
 * <ul>
 *   <li>{@code OllamaVisionExtractor} — image captioning via Ollama VLM</li>
 *   <li>Future: {@code WhisperExtractor} — audio transcription</li>
 *   <li>Future: {@code FFmpegVideoExtractor} — video frame captioning</li>
 * </ul>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Implementations must be thread-safe</li>
 *   <li>The returned {@link Stream} may be lazy — callers must close it</li>
 *   <li>{@link #extract} must not throw for unsupported MIME types —
 *       return an empty stream instead</li>
 * </ul>
 *
 * @see ExtractionChunk
 */
public interface SensoryExtractor {

    /**
     * A single text chunk extracted from non-text content.
     *
     * @param chunkId   unique identifier for this chunk
     * @param text      the extracted text (caption, transcript, OCR, etc.)
     * @param metadata  key-value metadata (modality, source_uri, model, page, etc.)
     */
    record ExtractionChunk(
            String chunkId,
            String text,
            Map<String, String> metadata
    ) {
        /** Compact constructor — enforces non-null metadata. */
        public ExtractionChunk {
            if (chunkId == null || chunkId.isBlank()) {
                throw new IllegalArgumentException("chunkId must not be null or blank");
            }
            if (text == null) {
                throw new IllegalArgumentException("text must not be null");
            }
            if (metadata == null) {
                metadata = Map.of();
            }
        }
    }

    /**
     * Extracts text chunks from a source file.
     *
     * <p>The returned stream is lazy — implementations should not load
     * the entire file into memory. Callers must close the stream.</p>
     *
     * <p>Returns an empty stream for unsupported MIME types (never throws).</p>
     *
     * @param source   path to the source file
     * @param mimeType MIME type of the file (e.g., "image/jpeg", "audio/mp3")
     * @return a stream of extraction chunks
     * @throws IOException if the file cannot be read
     */
    Stream<ExtractionChunk> extract(Path source, String mimeType) throws IOException;

    /**
     * Returns the set of MIME types this extractor supports.
     *
     * @return supported MIME types (e.g., {"image/jpeg", "image/png"})
     */
    Set<String> supportedMimeTypes();

    /**
     * Returns true if this extractor supports the given MIME type.
     */
    default boolean supports(String mimeType) {
        return mimeType != null && supportedMimeTypes().contains(mimeType.toLowerCase());
    }

    /**
     * Returns true if the extractor's backing service is available.
     *
     * <p>Implementations should perform a lightweight health check
     * (e.g., ping the Ollama server, check if FFmpeg is on PATH).</p>
     */
    default boolean isAvailable() {
        return true;
    }
}
