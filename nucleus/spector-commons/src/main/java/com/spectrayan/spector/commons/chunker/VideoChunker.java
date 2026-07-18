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
 * SPI for chunking video content into scene-based text segments.
 *
 * <p><b>Future extension point.</b> Current video processing is handled by
 * {@code FFmpegKeyframeExtractor} in the sensory package.</p>
 *
 * @see Chunker
 */
public interface VideoChunker extends Chunker {

    /**
     * Chunks a video file into scene-based text segments.
     *
     * @param source video file path
     * @param config chunking configuration
     * @return ordered list of chunks with timing/scene metadata
     * @throws IOException if the video file cannot be read
     */
    List<Chunk> chunkVideo(Path source, ChunkConfig config) throws IOException;
}
