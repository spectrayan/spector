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
package com.spectrayan.spector.memory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs an idempotent, versioned relocation of a legacy flat memory layout into the
 * default user's per-user namespace.
 *
 * <h3>Design</h3>
 * <p>Before multi-user support, a memory data root held its state in a flat layout:
 * {@code runtime/} and {@code partitions/} directly beneath the data root. When
 * authentication is enabled, each user's data instead lives under a single-level sharded
 * per-user directory resolved by {@link StorageLayout#namespaceDirSharded(Path, String)}
 * (i.e. {@code namespaces/AA/BB/{userId}/}). This migrator relocates the flat layout into
 * the {@code defaultUserId} namespace so an existing single-user deployment can be enabled
 * for multi-user without data loss.</p>
 *
 * <h3>Safety guarantees</h3>
 * <ul>
 *   <li><b>Idempotent</b> — running it twice equals running it once. Once
 *       {@link DataLayoutVersion#read(Path)} reports a version at or above
 *       {@link DataLayoutVersion#CURRENT}, the call is a no-op.</li>
 *   <li><b>Monotonic</b> — the stored layout version never decreases; it is only ever
 *       advanced to {@link DataLayoutVersion#CURRENT} after verification succeeds.</li>
 *   <li><b>Copy-then-verify</b> — files are <em>copied</em> (not moved) into the new
 *       layout; the original flat {@code runtime/} and {@code partitions/} directories are
 *       retained until every regular file has a byte-identical destination.</li>
 *   <li><b>Fail-closed</b> — on any I/O error or verification mismatch, the stored version
 *       is left unchanged, the originals are retained, and an unchecked exception is
 *       surfaced to the caller.</li>
 * </ul>
 *
 * <p>This class is a stateless utility holding no native resources; it is not
 * {@link AutoCloseable}.</p>
 *
 * @see DataLayoutVersion
 * @see StorageLayout#namespaceDirSharded(Path, String)
 */
public final class LayoutMigrator {

    private static final Logger log = LoggerFactory.getLogger(LayoutMigrator.class);

    private LayoutMigrator() {}

    /**
     * Idempotently relocates a legacy flat layout at {@code dataRoot} into the default
     * user's per-user namespace, advancing the stored layout version only once the new
     * layout is verified byte-for-byte.
     *
     * <p>Postconditions:</p>
     * <ul>
     *   <li>If {@link DataLayoutVersion#read(Path)} is already at or above
     *       {@link DataLayoutVersion#CURRENT}, this is a no-op: no filesystem changes are
     *       made and the stored version is left unchanged.</li>
     *   <li>Otherwise the existing flat {@code runtime/} and {@code partitions/}
     *       directories are copied under
     *       {@code namespaceDirSharded(dataRoot, defaultUserId)} (i.e.
     *       {@code namespaces/AA/BB/{defaultUserId}/runtime|partitions}), verified so that
     *       every regular file under the originals has a destination file with identical
     *       byte length and byte content, and only then is the stored layout version set
     *       to {@link DataLayoutVersion#CURRENT}.</li>
     *   <li>The original flat directories are retained until verification succeeds and are
     *       never deleted by this method.</li>
     * </ul>
     *
     * @param dataRoot      the memory persistence root directory
     * @param defaultUserId the user id whose namespace the flat layout is relocated into;
     *                      this value is itself the namespace id (no {@code user-} prefix)
     * @throws NullPointerException     if {@code dataRoot} is {@code null}
     * @throws IllegalArgumentException if {@code defaultUserId} is not a valid namespace id
     *                                  (see {@link StorageLayout#namespaceDirSharded(Path, String)})
     * @throws UncheckedIOException     if an I/O error prevents copying or verification
     * @throws IllegalStateException    if the copied layout fails byte-for-byte verification
     */
    public static void migrateIfNeeded(Path dataRoot, String defaultUserId) {
        Objects.requireNonNull(dataRoot, "dataRoot");

        int currentVersion = DataLayoutVersion.read(dataRoot);
        if (currentVersion >= DataLayoutVersion.CURRENT) {
            log.debug("Layout at {} already at version {} (>= {}); migration is a no-op",
                    dataRoot, currentVersion, DataLayoutVersion.CURRENT);
            return;
        }

        // Resolves and validates the namespace id; the id IS the defaultUserId (no prefix).
        Path namespaceDir = StorageLayout.namespaceDirSharded(dataRoot, defaultUserId);

        Path flatRuntime = StorageLayout.runtimeDir(dataRoot);
        Path flatPartitions = StorageLayout.partitionsDir(dataRoot);
        Path destRuntime = namespaceDir.resolve(StorageLayout.DIR_RUNTIME);
        Path destPartitions = namespaceDir.resolve(StorageLayout.DIR_PARTITIONS);

        log.info("Migrating flat layout at {} (version {}) into namespace {} for user {}",
                dataRoot, currentVersion, namespaceDir, defaultUserId);

        try {
            Files.createDirectories(namespaceDir);
            // Copy first (originals retained), then verify byte-for-byte before committing.
            copyTree(flatRuntime, destRuntime);
            copyTree(flatPartitions, destPartitions);
            verifyTree(flatRuntime, destRuntime);
            verifyTree(flatPartitions, destPartitions);
        } catch (IOException e) {
            // Fail-closed: leave the stored version and originals unchanged, surface the error.
            throw new UncheckedIOException(
                    "Layout migration failed for data root " + dataRoot
                            + "; stored version left unchanged at " + currentVersion, e);
        }

        // Verified: commit by advancing the stored layout version (monotonic).
        DataLayoutVersion.write(dataRoot, DataLayoutVersion.CURRENT);
        log.info("Layout migration complete for {}; stored version advanced to {}",
                dataRoot, DataLayoutVersion.CURRENT);
    }

    /**
     * Recursively copies every regular file under {@code src} into {@code dst}, preserving
     * the relative directory structure. Existing destination files are replaced so that a
     * re-run after a partial failure converges to an identical tree. If {@code src} does not
     * exist, nothing is copied.
     */
    private static void copyTree(Path src, Path dst) throws IOException {
        if (!Files.exists(src)) {
            return;
        }
        try (var stream = Files.walk(src)) {
            for (Path source : (Iterable<Path>) stream::iterator) {
                Path target = dst.resolve(src.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else if (Files.isRegularFile(source)) {
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(source, target,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    /**
     * Verifies that every regular file under {@code src} has a corresponding destination
     * file under {@code dst} with identical byte length and identical byte content. If
     * {@code src} does not exist, verification trivially passes.
     *
     * @throws IllegalStateException if a destination file is missing or differs from its source
     */
    private static void verifyTree(Path src, Path dst) throws IOException {
        if (!Files.exists(src)) {
            return;
        }
        try (var stream = Files.walk(src)) {
            for (Path source : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(source)) {
                    continue;
                }
                Path target = dst.resolve(src.relativize(source).toString());
                if (!Files.isRegularFile(target)) {
                    throw new IllegalStateException(
                            "Layout migration verification failed: missing destination file "
                                    + target + " for source " + source);
                }
                // Files.mismatch returns -1 when the files are byte-for-byte identical,
                // and detects length differences as well as content differences.
                long mismatch = Files.mismatch(source, target);
                if (mismatch != -1L) {
                    throw new IllegalStateException(
                            "Layout migration verification failed: content mismatch at byte "
                                    + mismatch + " between " + source + " and " + target);
                }
            }
        }
    }
}
