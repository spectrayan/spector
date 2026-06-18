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
package com.spectrayan.spector.memory.namespace;

import com.spectrayan.spector.memory.StorageLayout;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ShardedNamespaceMigrator}.
 */
class ShardedNamespaceMigratorTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("migrator-test-");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            }
        }
    }

    private void createFlatNamespace(String nsId) throws IOException {
        Path nsDir = StorageLayout.namespaceDir(tempDir, nsId);
        Files.createDirectories(nsDir);
        Files.writeString(nsDir.resolve(StorageLayout.FILE_NAMESPACE),
                "{\"id\": \"" + nsId + "\"}");
        // Create subdirs like real namespaces
        Files.createDirectories(nsDir.resolve(StorageLayout.DIR_GLOBAL));
        Files.createDirectories(nsDir.resolve(StorageLayout.DIR_PARTITIONS));
    }

    @Test
    @DisplayName("Migrates flat namespaces to sharded layout")
    void migratesFlatToSharded() throws IOException {
        createFlatNamespace("agent-alpha");
        createFlatNamespace("agent-beta");

        int migrated = ShardedNamespaceMigrator.migrateToSharded(tempDir);

        assertThat(migrated).isEqualTo(2);

        // Flat dirs should no longer exist
        assertThat(Files.exists(StorageLayout.namespaceDir(tempDir, "agent-alpha"))).isFalse();
        assertThat(Files.exists(StorageLayout.namespaceDir(tempDir, "agent-beta"))).isFalse();

        // Sharded dirs should exist
        assertThat(Files.exists(StorageLayout.namespaceDirSharded(tempDir, "agent-alpha"))).isTrue();
        assertThat(Files.exists(StorageLayout.namespaceDirSharded(tempDir, "agent-beta"))).isTrue();

        // Content should be preserved
        assertThat(Files.exists(StorageLayout.namespaceDirSharded(tempDir, "agent-alpha")
                .resolve(StorageLayout.FILE_NAMESPACE))).isTrue();
        assertThat(Files.exists(StorageLayout.namespaceDirSharded(tempDir, "agent-alpha")
                .resolve(StorageLayout.DIR_GLOBAL))).isTrue();
    }

    @Test
    @DisplayName("Skips directories without namespace.json")
    void skipsNonNamespaceDirs() throws IOException {
        // Create a directory without namespace.json
        Path randomDir = StorageLayout.namespacesDir(tempDir).resolve("random-dir");
        Files.createDirectories(randomDir);

        int migrated = ShardedNamespaceMigrator.migrateToSharded(tempDir);
        assertThat(migrated).isZero();
    }

    @Test
    @DisplayName("Skips already-sharded directories (hex bucket names)")
    void skipsShardBucketDirs() throws IOException {
        // Pre-create a sharded namespace
        Path shardedDir = StorageLayout.namespaceDirSharded(tempDir, "agent-x");
        Files.createDirectories(shardedDir);
        Files.writeString(shardedDir.resolve(StorageLayout.FILE_NAMESPACE),
                "{\"id\": \"agent-x\"}");

        int migrated = ShardedNamespaceMigrator.migrateToSharded(tempDir);
        assertThat(migrated).isZero();
    }

    @Test
    @DisplayName("Skips if sharded target already exists")
    void skipsIfTargetExists() throws IOException {
        createFlatNamespace("agent-alpha");

        // Pre-create the sharded target
        Path target = StorageLayout.namespaceDirSharded(tempDir, "agent-alpha");
        Files.createDirectories(target);
        Files.writeString(target.resolve(StorageLayout.FILE_NAMESPACE),
                "{\"id\": \"agent-alpha\", \"migrated\": true}");

        int migrated = ShardedNamespaceMigrator.migrateToSharded(tempDir);
        assertThat(migrated).isZero();

        // Original flat dir should still exist (not moved)
        assertThat(Files.exists(StorageLayout.namespaceDir(tempDir, "agent-alpha"))).isTrue();
    }

    @Test
    @DisplayName("Returns 0 when no namespaces directory exists")
    void returnsZeroWhenNoDirExists() throws IOException {
        Path emptyDir = Files.createTempDirectory("empty-");
        int migrated = ShardedNamespaceMigrator.migrateToSharded(emptyDir);
        assertThat(migrated).isZero();

        // Cleanup
        Files.delete(emptyDir);
    }

    @Test
    @DisplayName("Migrated namespaces are discoverable by sharded manager")
    void migratedNamespacesDiscoverable() throws IOException {
        createFlatNamespace("agent-alpha");
        createFlatNamespace("agent-beta");

        ShardedNamespaceMigrator.migrateToSharded(tempDir);

        // Sharded manager should discover both
        var mgr = new SpectorNamespaceManager(tempDir, true);
        assertThat(mgr.count()).isEqualTo(2);
        assertThat(mgr.exists("agent-alpha")).isTrue();
        assertThat(mgr.exists("agent-beta")).isTrue();
    }

    @Test
    @DisplayName("Is idempotent — running twice has no effect")
    void idempotent() throws IOException {
        createFlatNamespace("agent-alpha");

        int first = ShardedNamespaceMigrator.migrateToSharded(tempDir);
        assertThat(first).isEqualTo(1);

        // Second run — nothing to migrate
        int second = ShardedNamespaceMigrator.migrateToSharded(tempDir);
        assertThat(second).isZero();
    }
}
