/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility to migrate existing flat namespace directories to the sharded layout.
 *
 * <p>Scans flat namespace directories and moves each to its sharded location.
 * The migration is idempotent -- directories already in their correct sharded
 * location are skipped.</p>
 */
public class ShardedNamespaceMigrator {

    private static final Logger log = LoggerFactory.getLogger(ShardedNamespaceMigrator.class);

    private ShardedNamespaceMigrator() { /* utility class */ }

    /**
     * Migrates all flat namespace directories to sharded layout.
     *
     * @param basePath root persistence path (parent of namespaces/)
     * @return number of namespaces successfully migrated
     */
    public static int migrateToSharded(Path basePath) {
        Path namespacesDir = StorageLayout.namespacesDir(basePath);
        if (!Files.isDirectory(namespacesDir)) {
            log.info("[Migration] No namespaces directory found at {}. Nothing to migrate.", namespacesDir);
            return 0;
        }

        AtomicInteger migrated = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(namespacesDir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) continue;

                String dirName = entry.getFileName().toString();

                // Skip shard bucket directories (2-char hex names like "a3", "f7")
                if (dirName.length() == StorageLayout.SHARD_HEX_DIGITS
                        && dirName.chars().allMatch(c -> "0123456789abcdef".indexOf(c) >= 0)) {
                    continue;
                }

                // Check if this is a flat namespace (has namespace.json)
                if (!Files.exists(entry.resolve(StorageLayout.FILE_NAMESPACE))) {
                    continue;
                }

                // Compute sharded target path
                Path shardedTarget = StorageLayout.namespaceDirSharded(basePath, dirName);

                if (Files.exists(shardedTarget)) {
                    log.warn("[Migration] Sharded target already exists for '{}': {} - skipping",
                            dirName, shardedTarget);
                    skipped.incrementAndGet();
                    continue;
                }

                try {
                    // Create parent shard directories
                    Files.createDirectories(shardedTarget.getParent());

                    // Move atomically if supported, otherwise fallback to regular move
                    try {
                        Files.move(entry, shardedTarget, StandardCopyOption.ATOMIC_MOVE);
                    } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                        Files.move(entry, shardedTarget);
                    }

                    migrated.incrementAndGet();
                    log.info("[Migration] Migrated namespace '{}': {} -> {}",
                            dirName, entry, shardedTarget);
                } catch (IOException e) {
                    errors.incrementAndGet();
                    log.error("[Migration] Failed to migrate namespace '{}': {}",
                            dirName, e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan namespaces directory for migration", e);
        }

        log.info("[Migration] Complete: migrated={}, skipped={}, errors={}",
                migrated.get(), skipped.get(), errors.get());
        return migrated.get();
    }
}
