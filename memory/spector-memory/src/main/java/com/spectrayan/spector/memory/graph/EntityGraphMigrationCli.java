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
package com.spectrayan.spector.memory.graph;

import com.spectrayan.spector.memory.kernel.StorageLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * One-shot migration utility that converts a legacy {@code entity.graph} file
 * into the graduated {@code entity-directory.edir} + sidecar format (ADR-0003 #457, P3).
 *
 * <p>The migration performs a non-destructive extract: the original {@code entity.graph}
 * is preserved as {@code entity.graph.bak}. The migrator loads the legacy file via
 * {@link EntityGraphMemory#load}, creates a fresh {@link EntityDirectory}, calls
 * {@link EntityDirectory#deriveFrom}, and persists the directory to its own container.
 * A post-migration fidelity check asserts entity-count equality and name-index
 * round-trip.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Migrate a single namespace:
 * EntityGraphMigrationCli.migrate(namespacePath);
 *
 * // Or as a standalone tool:
 * java -cp spector-memory.jar EntityGraphMigrationCli /path/to/namespace
 * }</pre>
 *
 * @see EntityDirectory
 * @see EntityGraphMemory
 */
public final class EntityGraphMigrationCli {

    private static final Logger log = LoggerFactory.getLogger(EntityGraphMigrationCli.class);

    /** Default entity capacity when creating a fresh directory. */
    private static final int DEFAULT_ENTITY_CAP = 4096;
    /** Default edge capacity for loading the legacy graph. */
    private static final int DEFAULT_EDGE_CAP = 8192;

    private EntityGraphMigrationCli() {} // utility class

    /**
     * Migrates the legacy {@code entity.graph} in the given namespace directory
     * to the graduated {@code entity-directory.edir} format.
     *
     * @param namespacePath root namespace directory (e.g., {@code ~/.spector/namespaces/default})
     * @return a {@link MigrationResult} describing the outcome
     * @throws MigrationException if the migration or fidelity check fails
     */
    public static MigrationResult migrate(Path namespacePath) {
        return migrate(namespacePath, null);
    }

    /**
     * Migrates the legacy entity graph with optional encryption support.
     *
     * @param namespacePath root namespace directory
     * @param encryptor     optional data encryptor (may be {@code null})
     * @return a {@link MigrationResult}
     * @throws MigrationException if the migration fails
     */
    public static MigrationResult migrate(Path namespacePath, com.spectrayan.spector.memory.DataEncryptor encryptor) {
        Path runtimeDir = StorageLayout.runtimeDir(namespacePath);
        Path legacyFile = runtimeDir.resolve(StorageLayout.FILE_ENTITY);
        Path edirFile = runtimeDir.resolve(StorageLayout.FILE_ENTITY_DIRECTORY);

        // Guard: no source file
        if (!Files.exists(legacyFile)) {
            log.info("EntityGraphMigration: no legacy entity.graph found at {} — skipping", legacyFile);
            return new MigrationResult(MigrationResult.Status.SKIPPED, 0, 0,
                    "No entity.graph found");
        }

        // Guard: already migrated
        if (Files.exists(edirFile)) {
            log.info("EntityGraphMigration: entity-directory.edir already exists at {} — skipping", edirFile);
            return new MigrationResult(MigrationResult.Status.ALREADY_MIGRATED, 0, 0,
                    "entity-directory.edir already present");
        }

        log.info("EntityGraphMigration: starting migration of {} ...", legacyFile);

        // 1. Load the legacy graph
        EntityGraphMemory legacyGraph;
        try {
            legacyGraph = EntityGraphMemory.load(legacyFile, DEFAULT_ENTITY_CAP, DEFAULT_EDGE_CAP, encryptor);
        } catch (Exception e) {
            throw new MigrationException("Failed to load legacy entity.graph: " + legacyFile, e);
        }

        int legacyCount = legacyGraph.entityCount();
        int legacyNames = legacyGraph.nameIndex().size();
        log.info("EntityGraphMigration: loaded {} entities, {} names from legacy graph", legacyCount, legacyNames);

        // 2. Create a fresh EntityDirectory and derive from the legacy graph
        TypeRegistryMemory entityTypeRegistry = legacyGraph.entityTypeRegistry();
        EntityDirectory directory = new EntityDirectory(edirFile, DEFAULT_ENTITY_CAP, entityTypeRegistry);
        directory.setDataEncryptor(encryptor);

        int derived = directory.deriveFrom(legacyGraph);
        log.info("EntityGraphMigration: derived {} entities into EntityDirectory", derived);

        // 3. Save the directory (creates entity-directory.edir + sidecar)
        try {
            directory.save(edirFile, encryptor);
        } catch (Exception e) {
            throw new MigrationException("Failed to save entity-directory.edir: " + edirFile, e);
        }

        // 4. Fidelity checks
        assertFidelity(legacyGraph, directory, legacyFile);

        // 5. Backup the legacy file (entity.graph → entity.graph.bak)
        Path backupFile = runtimeDir.resolve(StorageLayout.FILE_ENTITY + ".bak");
        try {
            Files.copy(legacyFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("EntityGraphMigration: backed up legacy graph to {}", backupFile);
        } catch (IOException e) {
            throw new MigrationException("Failed to create backup: " + backupFile, e);
        }

        // 6. Close resources
        legacyGraph.close();
        directory.close();

        log.info("EntityGraphMigration: ✓ migration complete — {} entities migrated, legacy backed up to {}",
                derived, backupFile);

        return new MigrationResult(MigrationResult.Status.MIGRATED, legacyCount, derived, null);
    }

    /**
     * Asserts round-trip fidelity: entity count, name index match, memory-ref equality.
     */
    private static void assertFidelity(EntityGraphMemory legacy, EntityDirectory directory, Path legacyFile) {
        // Entity count
        int legacyCount = legacy.entityCount();
        int dirCount = directory.entityCount();
        if (legacyCount != dirCount) {
            throw new MigrationException(String.format(
                    "Entity count mismatch: legacy=%d, directory=%d (%s)", legacyCount, dirCount, legacyFile));
        }

        // Name index round-trip
        for (var entry : legacy.nameIndex().entrySet()) {
            int dirId = directory.findEntity(entry.getKey());
            if (dirId != entry.getValue()) {
                throw new MigrationException(String.format(
                        "Name index mismatch for '%s': legacy=%d, directory=%d (%s)",
                        entry.getKey(), entry.getValue(), dirId, legacyFile));
            }
        }

        // Memory-ref count equality (spot-check first 100 entities)
        int checkLimit = Math.min(legacyCount, 100);
        for (int e = 0; e < checkLimit; e++) {
            int legacyRefCount = legacy.memoryRefCount(e);
            int dirRefCount = directory.memoryRefCount(e);
            if (legacyRefCount != dirRefCount) {
                throw new MigrationException(String.format(
                        "Memory ref count mismatch for entity %d: legacy=%d, directory=%d (%s)",
                        e, legacyRefCount, dirRefCount, legacyFile));
            }
        }

        log.info("EntityGraphMigration: fidelity check passed — {} entities, {} names, ref counts verified",
                legacyCount, legacy.nameIndex().size());
    }

    // ── Result / Exception types ──

    /**
     * Result of a migration operation.
     */
    public record MigrationResult(Status status, int legacyEntityCount, int migratedEntityCount,
                                  String message) {
        public enum Status { MIGRATED, SKIPPED, ALREADY_MIGRATED }
    }

    /**
     * Exception thrown when migration fails.
     */
    public static class MigrationException extends RuntimeException {
        public MigrationException(String message) { super(message); }
        public MigrationException(String message, Throwable cause) { super(message, cause); }
    }

    // ── CLI entry point ──

    /**
     * Standalone entry point for offline migration of a single namespace.
     *
     * @param args expects a single argument: the namespace directory path
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: EntityGraphMigrationCli <namespace-directory>");
            System.exit(1);
        }
        Path namespacePath = Path.of(args[0]);
        if (!Files.isDirectory(namespacePath)) {
            System.err.println("Error: not a directory: " + namespacePath);
            System.exit(1);
        }
        try {
            MigrationResult result = migrate(namespacePath);
            System.out.printf("Migration result: %s (%d entities)%n",
                    result.status(), result.migratedEntityCount());
        } catch (MigrationException e) {
            System.err.println("Migration failed: " + e.getMessage());
            if (e.getCause() != null) {
                e.getCause().printStackTrace(System.err);
            }
            System.exit(2);
        }
    }
}
