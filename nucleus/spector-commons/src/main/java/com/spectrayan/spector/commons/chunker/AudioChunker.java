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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * SPI for chunking audio content into timed text segments.
 *
 * <p>Implementations transcribe audio and split into time-aligned chunks
 * suitable for embedding and cognitive storage.</p>
 *
 * <p><b>Future extension point.</b> Current audio processing is handled by
 * {@code OllamaAudioExtractor} in the sensory package.</p>
 *
 * @see Chunker
 */
public interface AudioChunker extends Chunker {

    /**
     * Chunks an audio file into timed text segments.
     *
     * @param source audio file path
     * @param config chunking configuration
     * @return ordered list of chunks with timing metadata
     * @throws IOException if the audio file cannot be read
     */
    List<Chunk> chunkAudio(Path source, ChunkConfig config) throws IOException;
}
