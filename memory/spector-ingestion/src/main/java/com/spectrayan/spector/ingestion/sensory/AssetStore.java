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
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;

/**
 * SPI for storing and retrieving multimodal assets (images, audio, video).
 *
 * <p>Spector's cognitive engine only stores text (captions, transcripts) and
 * vectors in its off-heap tiers. The raw binary assets are delegated to an
 * {@code AssetStore} implementation, which returns a URI pointer that gets
 * stored in the memory's metadata.</p>
 *
 * <h3>Built-in Implementations</h3>
 * <ul>
 *   <li>{@link LocalAssetStore} — local filesystem under {@code .spector/assets/}</li>
 * </ul>
 *
 * <h3>Future Adapters</h3>
 * <ul>
 *   <li>S3AssetStore — Amazon S3</li>
 *   <li>GcsAssetStore — Google Cloud Storage</li>
 *   <li>AzureBlobAssetStore — Azure Blob Storage</li>
 * </ul>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Implementations must be thread-safe</li>
 *   <li>{@link #store} must return a stable URI that can be resolved by {@link #retrieve}</li>
 *   <li>{@link #delete} is idempotent — deleting a non-existent asset does not throw</li>
 * </ul>
 */
public interface AssetStore {

    /**
     * Stores an asset and returns a URI that can be used to retrieve it.
     *
     * @param source   path to the source file
     * @param memoryId the memory ID this asset is associated with
     * @param mimeType MIME type of the asset
     * @return a URI pointing to the stored asset
     * @throws IOException if the file cannot be read or stored
     */
    URI store(Path source, String memoryId, String mimeType) throws IOException;

    /**
     * Retrieves a previously stored asset.
     *
     * @param assetUri the URI returned by {@link #store}
     * @return an input stream to the asset content
     * @throws IOException if the asset cannot be found or read
     */
    InputStream retrieve(URI assetUri) throws IOException;

    /**
     * Checks if an asset exists.
     *
     * @param assetUri the URI returned by {@link #store}
     * @return true if the asset exists
     */
    boolean exists(URI assetUri);

    /**
     * Deletes a previously stored asset.
     *
     * <p>Idempotent — does not throw if the asset doesn't exist.</p>
     *
     * @param assetUri the URI returned by {@link #store}
     * @throws IOException if a deletion error occurs (other than not-found)
     */
    void delete(URI assetUri) throws IOException;
}
