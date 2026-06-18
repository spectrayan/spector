/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.spectrayan.spector.memory.migration.migrations;

import com.spectrayan.spector.memory.migration.NamespaceMigration;
import com.spectrayan.spector.memory.migration.SchemaVersion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Migration from V1.0.0 to V1.1.0: adds encryption readiness marker.
 *
 * <p>Pre-encryption namespaces (V1.0.0) have no encryption metadata.
 * This migration creates a {@code .encryption-ready} marker file that
 * indicates the namespace has been scanned and is ready for encryption
 * to be applied when enabled at the tenant level.</p>
 *
 * <p>This migration is idempotent -- if the marker already exists,
 * it is a no-op.</p>
 */
public class V1_0_to_V1_1_EncryptionMarker implements NamespaceMigration {

    private static final Logger log = LoggerFactory.getLogger(V1_0_to_V1_1_EncryptionMarker.class);

    private static final String MARKER_FILE = ".encryption-ready";

    @Override
    public SchemaVersion fromVersion() {
        return SchemaVersion.V1_0_0;
    }

    @Override
    public SchemaVersion toVersion() {
        return SchemaVersion.V1_1_0;
    }

    @Override
    public String description() {
        return "Add encryption readiness marker for pre-encryption namespaces";
    }

    @Override
    public void migrate(Path namespaceDir) throws IOException {
        Path markerPath = namespaceDir.resolve(MARKER_FILE);
        if (Files.exists(markerPath)) {
            log.debug("[V1.0->1.1] Marker already exists for {}, skipping", namespaceDir);
            return;
        }

        // Create marker file with metadata
        String content = String.format(
                "{\"migratedAt\": \"%s\", \"encryptionEnabled\": false, \"reason\": \"lazy-migration\"}\n",
                java.time.Instant.now());
        Files.writeString(markerPath, content);

        log.info("[V1.0->1.1] Created encryption marker for {}", namespaceDir.getFileName());
    }
}
