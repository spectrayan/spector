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
 * Base SPI for content chunking.
 *
 * <p>All chunker types extend this marker interface. Sub-interfaces define
 * the contract for specific media types:</p>
 * <ul>
 *   <li>{@link TextChunker} — text/markdown/HTML content</li>
 *   <li>{@link AudioChunker} — audio transcription chunking (future)</li>
 *   <li>{@link VideoChunker} — video scene chunking (future)</li>
 * </ul>
 *
 * <h3>Discovery</h3>
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}
 * and can be looked up through {@link ChunkerRegistry}.</p>
 *
 * @see TextChunker
 * @see ChunkerRegistry
 */
public interface Chunker {

    /**
     * Returns the unique name of this chunker.
     *
     * <p>Used for configuration and registry lookup (e.g., "markdown",
     * "sentence", "recursive").</p>
     *
     * @return chunker name (never null or blank)
     */
    String name();

    /**
     * Returns a human-readable display name.
     *
     * @return display name (e.g., "Markdown-Aware Chunker")
     */
    default String displayName() {
        return name();
    }
}
