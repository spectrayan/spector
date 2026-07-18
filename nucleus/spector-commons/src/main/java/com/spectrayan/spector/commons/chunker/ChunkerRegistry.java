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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers and manages {@link Chunker} implementations via {@link ServiceLoader}.
 *
 * <p>Provides lookup by name or by content type, with fallback to the default
 * sentence-based chunker when no specialized implementation is available.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   var registry = ChunkerRegistry.discover();
 *   TextChunker chunker = registry.forContentType("text/markdown")
 *       .orElseGet(SentenceChunker::new);
 *   List<Chunk> chunks = chunker.chunk("doc-1", content, ChunkConfig.markdown(800, 100));
 * }</pre>
 *
 * <h3>Thread Safety</h3>
 * <p>Instances are safe for concurrent reads after construction. Registration
 * is not thread-safe and should be done during initialization only.</p>
 */
public final class ChunkerRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChunkerRegistry.class);

    private final Map<String, TextChunker> textChunkers = new LinkedHashMap<>();
    private TextChunker defaultChunker;

    /**
     * Discovers all {@link TextChunker} implementations via {@link ServiceLoader}.
     *
     * @return populated registry
     */
    public static ChunkerRegistry discover() {
        var registry = new ChunkerRegistry();
        ServiceLoader.load(TextChunker.class).forEach(chunker -> {
            registry.register(chunker);
            log.debug("[ChunkerRegistry] Discovered TextChunker: {} ({})",
                    chunker.name(), chunker.supportedContentTypes());
        });
        if (registry.textChunkers.isEmpty()) {
            log.warn("[ChunkerRegistry] No TextChunker implementations found via ServiceLoader");
        } else {
            log.info("[ChunkerRegistry] Discovered {} TextChunker implementation(s): {}",
                    registry.textChunkers.size(), registry.textChunkerNames());
        }
        return registry;
    }

    /**
     * Creates an empty registry for programmatic construction.
     */
    public ChunkerRegistry() {
        // empty
    }

    /**
     * Creates a registry pre-populated with the given chunkers.
     *
     * @param chunkers chunkers to register
     */
    public ChunkerRegistry(TextChunker... chunkers) {
        for (var c : chunkers) {
            register(c);
        }
    }

    /**
     * Registers a text chunker. The first registered chunker with name
     * "sentence" becomes the default fallback.
     *
     * @param chunker chunker to register
     */
    public void register(TextChunker chunker) {
        textChunkers.put(chunker.name(), chunker);
        if (defaultChunker == null || "sentence".equals(chunker.name())) {
            defaultChunker = chunker;
        }
    }

    /**
     * Returns the best {@link TextChunker} for the given content type.
     *
     * <p>Searches all registered chunkers for one that explicitly supports
     * the given content type. Falls back to the default chunker if none match.</p>
     *
     * @param contentType MIME content type (e.g., "text/markdown")
     * @return matching chunker, or default chunker if no specific match
     */
    public Optional<TextChunker> forContentType(String contentType) {
        return textChunkers.values().stream()
                .filter(c -> c.supportedContentTypes().contains(contentType))
                .findFirst()
                .or(() -> Optional.ofNullable(defaultChunker));
    }

    /**
     * Returns a chunker by its unique name.
     *
     * @param name chunker name (e.g., "markdown", "sentence")
     * @return the matching chunker, if registered
     */
    public Optional<TextChunker> byName(String name) {
        return Optional.ofNullable(textChunkers.get(name));
    }

    /**
     * Returns the default text chunker, if any.
     *
     * @return default chunker
     */
    public Optional<TextChunker> defaultChunker() {
        return Optional.ofNullable(defaultChunker);
    }

    /**
     * Returns all registered text chunker names.
     *
     * @return immutable set of chunker names
     */
    public Set<String> textChunkerNames() {
        return Set.copyOf(textChunkers.keySet());
    }

    /**
     * Returns whether the registry has any text chunkers.
     *
     * @return true if at least one TextChunker is registered
     */
    public boolean isEmpty() {
        return textChunkers.isEmpty();
    }
}
