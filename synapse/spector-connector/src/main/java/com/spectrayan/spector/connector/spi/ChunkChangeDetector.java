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
package com.spectrayan.spector.connector.spi;

/**
 * SPI for chunk-level content change detection (delta upserts).
 *
 * <p>Implementations track per-chunk content hashes so that only changed chunks
 * trigger re-embedding during document re-ingestion. The default implementation
 * is {@code ChunkHashManifest} in the persistence module.</p>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>{@link #hasChunkChanged} returns {@code true} for new or modified chunks</li>
 *   <li>{@link #trackChunk} records the hash + memory ID after successful ingestion</li>
 * </ul>
 *
 * @see com.spectrayan.spector.connector.sink.SpectorIngestionSink
 */
public interface ChunkChangeDetector {

    /**
     * Checks if a chunk's content has changed since the last tracking.
     *
     * @param pipelineId the pipeline identifier
     * @param documentId the document identifier
     * @param chunkIndex the chunk position within the document (0-based)
     * @param content    the chunk's current text content
     * @return true if the content is new or changed, false if unchanged
     */
    boolean hasChunkChanged(String pipelineId, String documentId, int chunkIndex, String content);

    /**
     * Records a chunk's content hash and associated memory ID after successful ingestion.
     *
     * @param pipelineId the pipeline identifier
     * @param documentId the document identifier
     * @param chunkIndex the chunk position within the document (0-based)
     * @param content    the chunk text content
     * @param memoryId   the Spector memory ID created for this chunk
     */
    void trackChunk(String pipelineId, String documentId, int chunkIndex, String content, String memoryId);
}
