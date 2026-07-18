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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sentence-boundary text chunker backed by the legacy
 * {@link com.spectrayan.spector.commons.TextChunker}.
 *
 * <p>Wraps the existing {@code BreakIterator}-based sentence splitting
 * behind the new {@link TextChunker} SPI. Suitable for plain text content
 * where markdown structure is not present.</p>
 *
 * @see com.spectrayan.spector.commons.TextChunker
 */
public class SentenceChunker implements TextChunker {


    @Override
    public String name() {
        return "sentence";
    }

    @Override
    public Set<String> supportedContentTypes() {
        return Set.of("text/plain");
    }

    @Override
    public List<Chunk> chunk(String documentId, String content, ChunkConfig config) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        
        com.spectrayan.spector.commons.TextChunker legacyChunker = 
            new com.spectrayan.spector.commons.TextChunker(config.maxChunkSize(), config.overlap());
            
        List<com.spectrayan.spector.commons.TextChunker.Chunk> legacyChunks = 
            legacyChunker.chunk(documentId, content);
        
        return legacyChunks.stream()
            .map(c -> new Chunk(
                c.parentId(),
                c.chunkId(),
                c.index(),
                c.text(),
                c.startChar(),
                c.endChar(),
                Map.of("block_type", "prose")
            ))
            .collect(Collectors.toList());
    }
}
