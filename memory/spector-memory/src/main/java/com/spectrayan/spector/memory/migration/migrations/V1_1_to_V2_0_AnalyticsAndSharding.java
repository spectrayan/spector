/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
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
 * Migration from V1.1.0 to V2.0.0: adds analytics metadata and sharding readiness.
 *
 * <p>V2.0.0 namespaces include an analytics metadata directory and are
 * compatible with the sharded directory layout. This migration:</p>
 * <ul>
 *   <li>Creates {@code analytics/} subdirectory for CDC event metadata</li>
 *   <li>Creates {@code .shard-compatible} marker indicating the namespace
 *       data is ready for relocation to a sharded path</li>
 * </ul>
 */
public class V1_1_to_V2_0_AnalyticsAndSharding implements NamespaceMigration {

    private static final Logger log = LoggerFactory.getLogger(V1_1_to_V2_0_AnalyticsAndSharding.class);

    @Override
    public SchemaVersion fromVersion() {
        return SchemaVersion.V1_1_0;
    }

    @Override
    public SchemaVersion toVersion() {
        return SchemaVersion.V2_0_0;
    }

    @Override
    public String description() {
        return "Add analytics metadata directory and shard-compatibility marker";
    }

    @Override
    public void migrate(Path namespaceDir) throws IOException {
        // 1. Create analytics directory
        Path analyticsDir = namespaceDir.resolve("analytics");
        if (!Files.exists(analyticsDir)) {
            Files.createDirectories(analyticsDir);
            log.info("[V1.1->2.0] Created analytics/ for {}", namespaceDir.getFileName());
        }

        // 2. Create shard-compatible marker
        Path shardMarker = namespaceDir.resolve(".shard-compatible");
        if (!Files.exists(shardMarker)) {
            Files.writeString(shardMarker, String.format(
                    "{\"migratedAt\": \"%s\", \"shardReady\": true}\n",
                    java.time.Instant.now()));
            log.info("[V1.1->2.0] Created shard-compatibility marker for {}",
                    namespaceDir.getFileName());
        }
    }
}
