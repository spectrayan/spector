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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Local filesystem asset store.
 *
 * <p>Stores assets under a configurable base directory, organized by memory ID:</p>
 * <pre>
 *   {baseDir}/
 *     {memoryId}/
 *       {filename}
 * </pre>
 *
 * <p>By default, the base directory is {@code .spector/assets/} relative to the
 * working directory. This can be overridden via constructor.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Thread-safe — uses atomic file operations and creates directories lazily.</p>
 */
public final class LocalAssetStore implements AssetStore {

    private static final Logger log = LoggerFactory.getLogger(LocalAssetStore.class);

    /** Default base directory for asset storage. */
    private static final String DEFAULT_BASE_DIR = ".spector/assets";

    private final Path baseDir;

    /**
     * Creates a local asset store with a custom base directory.
     *
     * @param baseDir the base directory for asset storage
     */
    public LocalAssetStore(Path baseDir) {
        this.baseDir = baseDir != null ? baseDir : Path.of(DEFAULT_BASE_DIR);
        log.info("LocalAssetStore initialized: baseDir={}", this.baseDir.toAbsolutePath());
    }

    /**
     * Creates a local asset store with the default base directory.
     */
    public LocalAssetStore() {
        this(Path.of(DEFAULT_BASE_DIR));
    }

    /**
     * Creates a local asset store with a string path.
     */
    public static LocalAssetStore create(String baseDirPath) {
        return new LocalAssetStore(Path.of(baseDirPath));
    }

    @Override
    public URI store(Path source, String memoryId, String mimeType) throws IOException {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        if (memoryId == null || memoryId.isBlank()) {
            throw new IllegalArgumentException("memoryId must not be null or blank");
        }
        if (!Files.exists(source)) {
            throw new IOException("Source file not found: " + source);
        }

        // Sanitize memoryId for filesystem safety
        String safeId = memoryId.replaceAll("[^a-zA-Z0-9._-]", "_");
        String filename = source.getFileName().toString();

        Path targetDir = baseDir.resolve(safeId);
        Files.createDirectories(targetDir);

        Path targetFile = targetDir.resolve(filename);
        Files.copy(source, targetFile, StandardCopyOption.REPLACE_EXISTING);

        URI assetUri = targetFile.toAbsolutePath().toUri();
        log.debug("Asset stored: {} → {}", source.getFileName(), assetUri);
        return assetUri;
    }

    @Override
    public InputStream retrieve(URI assetUri) throws IOException {
        if (assetUri == null) throw new IllegalArgumentException("assetUri must not be null");

        Path path = Path.of(assetUri);
        if (!Files.exists(path)) {
            throw new IOException("Asset not found: " + assetUri);
        }
        return Files.newInputStream(path);
    }

    @Override
    public boolean exists(URI assetUri) {
        if (assetUri == null) return false;
        try {
            return Files.exists(Path.of(assetUri));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void delete(URI assetUri) throws IOException {
        if (assetUri == null) return;
        try {
            Path path = Path.of(assetUri);
            Files.deleteIfExists(path);

            // Clean up empty parent directory
            Path parent = path.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                try (var entries = Files.list(parent)) {
                    if (entries.findFirst().isEmpty()) {
                        Files.deleteIfExists(parent);
                    }
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            // Silently ignore non-IO errors (malformed URI, etc.)
            log.debug("Asset delete failed for {}: {}", assetUri, e.getMessage());
        }
    }

    /** Returns the base directory used by this store. */
    public Path baseDir() {
        return baseDir;
    }
}
