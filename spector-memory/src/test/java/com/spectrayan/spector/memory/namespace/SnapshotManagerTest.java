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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SnapshotManager}.
 */
class SnapshotManagerTest {

    @TempDir
    Path tempDir;

    private SnapshotManager snapshotManager;
    private SpectorNamespaceManager nsManager;

    @BeforeEach
    void setUp() {
        nsManager = new SpectorNamespaceManager(tempDir);
        snapshotManager = new SnapshotManager(tempDir);
    }

    @Test
    void createSnapshot_copies_namespace_data() throws IOException {
        // Create a namespace with some data
        var ctx = nsManager.createNamespace(NamespaceConfig.unlimited("test-ns"));
        Files.writeString(ctx.globalDir().resolve("data.json"), "{\"key\": \"value\"}");
        Files.createDirectories(ctx.partitionsDir().resolve("000_12345"));
        Files.writeString(ctx.partitionsDir().resolve("000_12345").resolve("semantic.mem"), "binary-data");

        // Snapshot it
        var info = snapshotManager.createSnapshot("test-ns", "backup-1");

        assertThat(info.snapshotId()).isEqualTo("backup-1");
        assertThat(info.namespaceId()).isEqualTo("test-ns");
        assertThat(info.createdAt()).isNotNull();
        assertThat(info.fileCount()).isGreaterThanOrEqualTo(3); // namespace.json + data.json + semantic.mem

        // Verify snapshot directory exists
        Path snapDir = StorageLayout.snapshotDir(tempDir, "test-ns", "backup-1");
        assertThat(Files.isDirectory(snapDir)).isTrue();
        assertThat(Files.exists(snapDir.resolve(StorageLayout.FILE_SNAPSHOT))).isTrue();

        // Verify data was copied
        assertThat(Files.readString(snapDir.resolve(StorageLayout.DIR_GLOBAL).resolve("data.json")))
                .isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    void createSnapshot_rejects_nonexistent_namespace() {
        assertThatThrownBy(() -> snapshotManager.createSnapshot("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void createSnapshot_rejects_duplicate_snapshot_id() {
        nsManager.createNamespace(NamespaceConfig.unlimited("test-ns"));
        snapshotManager.createSnapshot("test-ns", "snap-1");

        assertThatThrownBy(() -> snapshotManager.createSnapshot("test-ns", "snap-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("snap-1");
    }

    @Test
    void restoreSnapshot_replaces_namespace_data() throws IOException {
        // Create namespace with original data
        var ctx = nsManager.createNamespace(NamespaceConfig.unlimited("test-ns"));
        Files.writeString(ctx.globalDir().resolve("state.txt"), "original");

        // Snapshot
        snapshotManager.createSnapshot("test-ns", "checkpoint");

        // Modify namespace data
        Files.writeString(ctx.globalDir().resolve("state.txt"), "modified");
        Files.writeString(ctx.globalDir().resolve("new-file.txt"), "new");

        // Verify modification
        assertThat(Files.readString(ctx.globalDir().resolve("state.txt"))).isEqualTo("modified");

        // Restore from snapshot
        snapshotManager.restoreSnapshot("test-ns", "checkpoint");

        // Verify restored data
        assertThat(Files.readString(ctx.globalDir().resolve("state.txt"))).isEqualTo("original");
        // new-file.txt should be gone (restore clears the directory first)
        assertThat(Files.exists(ctx.globalDir().resolve("new-file.txt"))).isFalse();
    }

    @Test
    void restoreSnapshot_rejects_nonexistent_snapshot() {
        nsManager.createNamespace(NamespaceConfig.unlimited("test-ns"));

        assertThatThrownBy(() -> snapshotManager.restoreSnapshot("test-ns", "nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void listSnapshots_returns_sorted_newest_first() throws InterruptedException {
        nsManager.createNamespace(NamespaceConfig.unlimited("test-ns"));

        snapshotManager.createSnapshot("test-ns", "snap-a");
        Thread.sleep(50); // Ensure different timestamps
        snapshotManager.createSnapshot("test-ns", "snap-b");

        var snapshots = snapshotManager.listSnapshots("test-ns");
        assertThat(snapshots).hasSize(2);
        // Newest first
        assertThat(snapshots.get(0).snapshotId()).isEqualTo("snap-b");
        assertThat(snapshots.get(1).snapshotId()).isEqualTo("snap-a");
    }

    @Test
    void listSnapshots_returns_empty_for_no_snapshots() {
        assertThat(snapshotManager.listSnapshots("nonexistent")).isEmpty();
    }

    @Test
    void deleteSnapshot_removes_directory() {
        nsManager.createNamespace(NamespaceConfig.unlimited("test-ns"));
        snapshotManager.createSnapshot("test-ns", "to-delete");

        assertThat(snapshotManager.snapshotCount("test-ns")).isEqualTo(1);

        boolean deleted = snapshotManager.deleteSnapshot("test-ns", "to-delete");
        assertThat(deleted).isTrue();
        assertThat(snapshotManager.snapshotCount("test-ns")).isZero();

        // Verify directory gone
        Path snapDir = StorageLayout.snapshotDir(tempDir, "test-ns", "to-delete");
        assertThat(Files.exists(snapDir)).isFalse();
    }

    @Test
    void deleteSnapshot_returns_false_for_nonexistent() {
        assertThat(snapshotManager.deleteSnapshot("test-ns", "missing")).isFalse();
    }

    @Test
    void snapshotCount_tracks_snapshots() {
        nsManager.createNamespace(NamespaceConfig.unlimited("test-ns"));

        assertThat(snapshotManager.snapshotCount("test-ns")).isZero();

        snapshotManager.createSnapshot("test-ns", "snap-1");
        assertThat(snapshotManager.snapshotCount("test-ns")).isEqualTo(1);

        snapshotManager.createSnapshot("test-ns", "snap-2");
        assertThat(snapshotManager.snapshotCount("test-ns")).isEqualTo(2);

        snapshotManager.deleteSnapshot("test-ns", "snap-1");
        assertThat(snapshotManager.snapshotCount("test-ns")).isEqualTo(1);
    }

    @Test
    void multiple_namespaces_have_isolated_snapshots() {
        nsManager.createNamespace(NamespaceConfig.unlimited("ns-a"));
        nsManager.createNamespace(NamespaceConfig.unlimited("ns-b"));

        snapshotManager.createSnapshot("ns-a", "snap-1");
        snapshotManager.createSnapshot("ns-b", "snap-1");

        assertThat(snapshotManager.snapshotCount("ns-a")).isEqualTo(1);
        assertThat(snapshotManager.snapshotCount("ns-b")).isEqualTo(1);

        snapshotManager.deleteSnapshot("ns-a", "snap-1");
        assertThat(snapshotManager.snapshotCount("ns-a")).isZero();
        assertThat(snapshotManager.snapshotCount("ns-b")).isEqualTo(1); // unaffected
    }
}
