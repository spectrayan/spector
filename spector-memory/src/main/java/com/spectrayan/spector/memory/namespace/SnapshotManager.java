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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Point-in-time snapshot manager for namespace data.
 *
 * <h3>Design</h3>
 * <p>Snapshots are filesystem-level copies of a namespace's data directory.
 * Each snapshot captures the complete state of a namespace at a point in time,
 * enabling backup, migration, and disaster recovery.</p>
 *
 * <h3>Snapshot Layout</h3>
 * <pre>
 *   persistence-path/snapshots/
 *   └── agent-alpha/
 *       ├── 2026-06-14T01-00-00Z/
 *       │   ├── snapshot.json         ← metadata (timestamp, source, checksum)
 *       │   ├── global/              ← copied from namespace
 *       │   ├── partitions/
 *       │   └── cross/
 *       └── 2026-06-13T12-00-00Z/
 *           └── ...
 * </pre>
 *
 * <h3>Operations</h3>
 * <ul>
 *   <li>{@link #createSnapshot} — Copy namespace data → snapshot directory</li>
 *   <li>{@link #restoreSnapshot} — Copy snapshot data → namespace directory</li>
 *   <li>{@link #listSnapshots} — List all snapshots for a namespace</li>
 *   <li>{@link #deleteSnapshot} — Remove a snapshot and reclaim disk space</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>Snapshot operations are <b>not</b> atomic with respect to concurrent
 * writes. Callers should pause ingestion or use the WAL checkpoint mechanism
 * before creating a snapshot for full consistency.</p>
 *
 * @see StorageLayout#snapshotsDir(Path)
 * @see StorageLayout#snapshotDir(Path, String, String)
 */
public final class SnapshotManager {

    private static final Logger log = LoggerFactory.getLogger(SnapshotManager.class);

    private final Path basePath;

    /**
     * Creates a snapshot manager for the given persistence root.
     *
     * @param basePath root persistence path (same as SpectorMemory's persistence path)
     */
    public SnapshotManager(Path basePath) {
        this.basePath = basePath;
    }

    /**
     * Creates a point-in-time snapshot of a namespace.
     *
     * <p>Copies all files from the namespace directory to a timestamped
     * snapshot directory. Writes a {@code snapshot.json} metadata file
     * with the snapshot timestamp and source namespace.</p>
     *
     * @param namespaceId the namespace to snapshot
     * @return metadata about the created snapshot
     * @throws IllegalArgumentException if the namespace directory does not exist
     * @throws UncheckedIOException     if the copy fails
     */
    public SnapshotInfo createSnapshot(String namespaceId) {
        return createSnapshot(namespaceId, generateSnapshotId());
    }

    /**
     * Creates a snapshot with a specific ID.
     *
     * @param namespaceId the namespace to snapshot
     * @param snapshotId  the snapshot identifier (e.g., "pre-migration", "daily-backup")
     * @return metadata about the created snapshot
     */
    public SnapshotInfo createSnapshot(String namespaceId, String snapshotId) {
        Path nsDir = StorageLayout.namespaceDir(basePath, namespaceId);
        if (!Files.isDirectory(nsDir)) {
            throw new IllegalArgumentException(
                    "Namespace directory does not exist: " + namespaceId);
        }

        Path snapDir = StorageLayout.snapshotDir(basePath, namespaceId, snapshotId);
        if (Files.exists(snapDir)) {
            throw new IllegalStateException(
                    "Snapshot already exists: " + namespaceId + "/" + snapshotId);
        }

        Instant createdAt = Instant.now();
        long fileCount;

        try {
            Files.createDirectories(snapDir);

            // Copy the namespace data tree
            fileCount = copyDirectoryTree(nsDir, snapDir);

            // Write snapshot metadata
            writeSnapshotMeta(snapDir, namespaceId, snapshotId, createdAt, fileCount);

        } catch (IOException e) {
            // Cleanup partial snapshot on failure
            deleteDirectoryQuietly(snapDir);
            throw new UncheckedIOException(
                    "Failed to create snapshot: " + namespaceId + "/" + snapshotId, e);
        }

        long sizeBytes = directorySize(snapDir);
        log.info("Created snapshot: {}/{} ({} files, {} bytes)",
                namespaceId, snapshotId, fileCount, sizeBytes);

        return new SnapshotInfo(snapshotId, namespaceId, createdAt, fileCount, sizeBytes);
    }

    /**
     * Restores a namespace from a snapshot.
     *
     * <p><b>WARNING:</b> This replaces all current namespace data with
     * the snapshot contents. The current data is deleted before restore.
     * Callers should create a backup snapshot first if needed.</p>
     *
     * @param namespaceId the target namespace
     * @param snapshotId  the snapshot to restore from
     * @throws IllegalArgumentException if the snapshot does not exist
     * @throws UncheckedIOException     if the restore fails
     */
    public void restoreSnapshot(String namespaceId, String snapshotId) {
        Path snapDir = StorageLayout.snapshotDir(basePath, namespaceId, snapshotId);
        if (!Files.isDirectory(snapDir)) {
            throw new IllegalArgumentException(
                    "Snapshot does not exist: " + namespaceId + "/" + snapshotId);
        }

        Path nsDir = StorageLayout.namespaceDir(basePath, namespaceId);

        try {
            // Clear current namespace data (except namespace.json which will be overwritten)
            if (Files.isDirectory(nsDir)) {
                deleteDirectoryContents(nsDir);
            } else {
                Files.createDirectories(nsDir);
            }

            // Copy snapshot back to namespace (skip snapshot.json)
            copyDirectoryTree(snapDir, nsDir, StorageLayout.FILE_SNAPSHOT);

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to restore snapshot: " + namespaceId + "/" + snapshotId, e);
        }

        log.info("Restored namespace '{}' from snapshot '{}'", namespaceId, snapshotId);
    }

    /**
     * Lists all snapshots for a namespace, sorted by creation time (newest first).
     *
     * @param namespaceId the namespace to list snapshots for
     * @return list of snapshot metadata, or empty list if none exist
     */
    public List<SnapshotInfo> listSnapshots(String namespaceId) {
        Path nsSnapDir = StorageLayout.snapshotsDir(basePath).resolve(namespaceId);
        if (!Files.isDirectory(nsSnapDir)) {
            return List.of();
        }

        List<SnapshotInfo> snapshots = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(nsSnapDir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) continue;
                String snapId = entry.getFileName().toString();

                // Count files and compute size
                long fileCount = countFiles(entry);
                long sizeBytes = directorySize(entry);

                // Read creation time from snapshot.json or fall back to dir creation time
                Instant createdAt = readCreationTime(entry);

                snapshots.add(new SnapshotInfo(snapId, namespaceId, createdAt, fileCount, sizeBytes));
            }
        } catch (IOException e) {
            log.warn("Failed to list snapshots for namespace '{}': {}", namespaceId, e.getMessage());
            return List.of();
        }

        // Sort newest first
        snapshots.sort(Comparator.comparing(SnapshotInfo::createdAt).reversed());
        return snapshots;
    }

    /**
     * Deletes a snapshot, reclaiming disk space.
     *
     * @param namespaceId the namespace
     * @param snapshotId  the snapshot to delete
     * @return true if the snapshot was deleted, false if it didn't exist
     */
    public boolean deleteSnapshot(String namespaceId, String snapshotId) {
        Path snapDir = StorageLayout.snapshotDir(basePath, namespaceId, snapshotId);
        if (!Files.isDirectory(snapDir)) {
            return false;
        }

        deleteDirectoryQuietly(snapDir);
        log.info("Deleted snapshot: {}/{}", namespaceId, snapshotId);

        // Clean up empty namespace snapshot directory
        Path nsSnapDir = StorageLayout.snapshotsDir(basePath).resolve(namespaceId);
        try {
            if (Files.isDirectory(nsSnapDir) && isDirectoryEmpty(nsSnapDir)) {
                Files.delete(nsSnapDir);
            }
        } catch (IOException e) {
            log.debug("Could not clean up empty snapshot dir: {}", nsSnapDir);
        }

        return true;
    }

    /**
     * Returns the number of snapshots for a namespace.
     */
    public int snapshotCount(String namespaceId) {
        Path nsSnapDir = StorageLayout.snapshotsDir(basePath).resolve(namespaceId);
        if (!Files.isDirectory(nsSnapDir)) return 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(nsSnapDir)) {
            int count = 0;
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) count++;
            }
            return count;
        } catch (IOException e) {
            return 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Internal Helpers
    // ═══════════════════════════════════════════════════════════════

    /** Generates a timestamp-based snapshot ID. */
    private String generateSnapshotId() {
        return Instant.now().toString()
                .replace(':', '-')
                .replace('.', '-');
    }

    /** Copies a directory tree recursively. Returns the number of files copied. */
    private long copyDirectoryTree(Path source, Path target) throws IOException {
        return copyDirectoryTree(source, target, null);
    }

    /**
     * Copies a directory tree recursively, optionally skipping a file by name.
     * Returns the number of files copied.
     */
    private long copyDirectoryTree(Path source, Path target, String skipFileName)
            throws IOException {
        long[] count = {0};
        try (Stream<Path> walk = Files.walk(source)) {
            walk.forEach(src -> {
                try {
                    Path rel = source.relativize(src);
                    Path dest = target.resolve(rel);

                    // Skip specified file
                    if (skipFileName != null && src.getFileName().toString().equals(skipFileName)) {
                        return;
                    }

                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                        count[0]++;
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        return count[0];
    }

    /** Writes snapshot metadata JSON. */
    private void writeSnapshotMeta(Path snapDir, String namespaceId,
                                     String snapshotId, Instant createdAt,
                                     long fileCount) throws IOException {
        String json = """
                {
                  "snapshot_id": "%s",
                  "namespace_id": "%s",
                  "created_at": "%s",
                  "file_count": %d,
                  "spector_version": "1.0.0"
                }
                """.formatted(snapshotId, namespaceId, createdAt.toString(), fileCount);
        Files.writeString(snapDir.resolve(StorageLayout.FILE_SNAPSHOT), json);
    }

    /** Reads creation time from snapshot.json, falling back to file system time. */
    private Instant readCreationTime(Path snapDir) {
        Path metaFile = snapDir.resolve(StorageLayout.FILE_SNAPSHOT);
        if (Files.exists(metaFile)) {
            try {
                String content = Files.readString(metaFile);
                // Simple extraction: find "created_at": "..."
                int idx = content.indexOf("\"created_at\"");
                if (idx >= 0) {
                    int start = content.indexOf('"', idx + 13) + 1;
                    int end = content.indexOf('"', start);
                    if (start > 0 && end > start) {
                        return Instant.parse(content.substring(start, end));
                    }
                }
            } catch (Exception e) {
                log.debug("Could not parse snapshot metadata: {}", metaFile);
            }
        }
        // Fallback to file creation time
        try {
            return Files.getLastModifiedTime(snapDir).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    /** Counts the number of regular files in a directory tree. */
    private long countFiles(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Computes the total size of all files in a directory tree. */
    private long directorySize(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); }
                        catch (IOException e) { return 0; }
                    })
                    .sum();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Deletes all contents of a directory (but not the directory itself). */
    private void deleteDirectoryContents(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(dir))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); }
                        catch (IOException e) {
                            log.warn("Failed to delete: {}", p);
                        }
                    });
        }
    }

    /** Deletes a directory tree silently. */
    private void deleteDirectoryQuietly(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); }
                        catch (IOException e) {
                            log.warn("Failed to delete: {}", p);
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to walk directory for deletion: {}", dir);
        }
    }

    /** Returns true if a directory has no entries. */
    private boolean isDirectoryEmpty(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            return !stream.iterator().hasNext();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Snapshot Info Record
    // ═══════════════════════════════════════════════════════════════

    /**
     * Metadata about a namespace snapshot.
     *
     * @param snapshotId  unique identifier for the snapshot
     * @param namespaceId the source namespace
     * @param createdAt   when the snapshot was created
     * @param fileCount   number of files in the snapshot
     * @param sizeBytes   total size of the snapshot in bytes
     */
    public record SnapshotInfo(
            String snapshotId,
            String namespaceId,
            Instant createdAt,
            long fileCount,
            long sizeBytes
    ) {}
}
