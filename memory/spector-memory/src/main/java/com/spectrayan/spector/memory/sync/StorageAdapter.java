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
package com.spectrayan.spector.memory.sync;

import java.nio.ByteBuffer;

/**
 * Service Provider Interface for cloud storage backends.
 *
 * <p>Implementations provide WAL chunk upload/download for distributed
 * memory sync. Each agent uploads its WAL to a namespace-isolated path.</p>
 *
 * <h3>Built-in Implementations (V2+)</h3>
 * <ul>
 *   <li>Future: {@code S3StorageAdapter} — AWS S3</li>
 *   <li>Future: {@code GcsStorageAdapter} — Google Cloud Storage</li>
 *   <li>Future: {@code LocalStorageAdapter} — local filesystem (testing)</li>
 * </ul>
 *
 * @see MemoryWal
 */
public interface StorageAdapter extends AutoCloseable {

    /**
     * Uploads a WAL chunk to remote storage.
     *
     * @param namespace  agent namespace (isolation boundary)
     * @param chunkName  chunk identifier (e.g., "wal-000001.bin")
     * @param data       chunk data
     */
    void upload(String namespace, String chunkName, ByteBuffer data);

    /**
     * Downloads a WAL chunk from remote storage.
     *
     * @param namespace  agent namespace
     * @param chunkName  chunk identifier
     * @return chunk data, or null if not found
     */
    ByteBuffer download(String namespace, String chunkName);

    /**
     * Lists available WAL chunks for a namespace, ordered by name.
     *
     * @param namespace agent namespace
     * @return chunk names in order
     */
    java.util.List<String> listChunks(String namespace);

    /**
     * Lists all namespaces (agent IDs) with available WAL data.
     *
     * @return namespace identifiers
     */
    java.util.List<String> listNamespaces();

    /**
     * Checks if the adapter is connected and ready.
     */
    boolean isAvailable();

    /**
     * Default no-op close.
     */
    @Override
    default void close() {}
}
