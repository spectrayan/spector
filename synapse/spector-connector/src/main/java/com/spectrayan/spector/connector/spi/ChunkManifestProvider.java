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

import java.util.List;
import java.util.Map;

/**
 * SPI for chunk manifest operations needed by document versioning.
 *
 * <p>Extends {@link ChunkChangeDetector} with manifest retrieval and
 * chunk removal capabilities needed for the full upsert flow.</p>
 *
 * @see ChunkChangeDetector
 */
public interface ChunkManifestProvider extends ChunkChangeDetector {

    /**
     * A single chunk's tracked metadata.
     *
     * @param chunkIndex  position within the document (0-based)
     * @param contentHash content hash of the chunk
     * @param memoryId    the Spector memory ID (nullable)
     */
    record ChunkInfo(int chunkIndex, String contentHash, String memoryId) {}

    /**
     * Returns the full chunk manifest for a document.
     *
     * @param pipelineId the pipeline
     * @param documentId the document
     * @return map of chunkIndex → ChunkInfo
     */
    Map<Integer, ChunkInfo> getChunkManifest(String pipelineId, String documentId);

    /**
     * Removes chunks at and beyond a given index and returns their memory IDs.
     *
     * @param pipelineId the pipeline
     * @param documentId the document
     * @param fromIndex  remove chunks at this index and above
     * @return list of memory IDs of removed chunks
     */
    List<String> removeChunksFrom(String pipelineId, String documentId, int fromIndex);
}
