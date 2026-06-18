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
package com.spectrayan.spector.memory.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers and chains namespace migration steps to upgrade stale namespaces.
 *
 * <p>On namespace load, the pipeline reads {@code manifest.json} from the
 * namespace directory, extracts component versions (schema, wal, index,
 * encryption), and runs any pending migrations to bring each component
 * up to {@link SchemaVersion#CURRENT}.</p>
 *
 * <h3>Manifest Format</h3>
 * <pre>{@code
 * {
 *   "schemaVersion": "2.0.0",
 *   "walVersion": "1.0.0",
 *   "indexVersion": "1.0.0",
 *   "encryptionVersion": "1.0.0",
 *   "lastMigration": "2026-06-17T10:00:00Z",
 *   "migrationHistory": [
 *     {"from": "1.0.0", "to": "1.1.0", "timestamp": "...", "description": "..."}
 *   ]
 * }
 * }</pre>
 *
 * <h3>Thread Safety</h3>
 * <p>Migration is synchronized on the namespace directory path to prevent
 * concurrent migrations of the same namespace.</p>
 */
public class MigrationPipeline {

    private static final Logger log = LoggerFactory.getLogger(MigrationPipeline.class);

    /** Manifest file name within each namespace directory. */
    public static final String MANIFEST_FILE = "manifest.json";

    /** Pattern to extract schemaVersion from manifest JSON. */
    private static final Pattern SCHEMA_VERSION_PATTERN =
            Pattern.compile("\"schemaVersion\"\\s*:\\s*\"([^\"]+)\"");

    /** Registered migrations, sorted by fromVersion. */
    private final List<NamespaceMigration> migrations;

    /** Target version to migrate to. */
    private final SchemaVersion targetVersion;

    /** Lock objects per namespace path to prevent concurrent migration. */
    private final Map<String, Object> migrationLocks = new WeakHashMap<>();

    /**
     * Creates a migration pipeline with the given migrations targeting CURRENT.
     *
     * @param migrations available migration steps
     */
    public MigrationPipeline(List<NamespaceMigration> migrations) {
        this(migrations, SchemaVersion.CURRENT);
    }

    /**
     * Creates a migration pipeline targeting a specific version.
     *
     * @param migrations    available migration steps
     * @param targetVersion the version to migrate namespaces to
     */
    public MigrationPipeline(List<NamespaceMigration> migrations, SchemaVersion targetVersion) {
        this.migrations = new ArrayList<>(migrations);
        this.migrations.sort(Comparator.comparing(NamespaceMigration::fromVersion));
        this.targetVersion = targetVersion;
    }

    /**
     * Checks if a namespace needs migration and runs pending steps if so.
     *
     * <p>This is the main entry point, called on namespace load. It is
     * idempotent and thread-safe per namespace.</p>
     *
     * @param namespaceDir the namespace directory
     * @return true if any migrations were applied
     */
    public boolean migrateIfNeeded(Path namespaceDir) {
        SchemaVersion currentVersion = readSchemaVersion(namespaceDir);

        if (!currentVersion.needsMigration(targetVersion)) {
            return false;
        }

        // Synchronize on namespace path to prevent concurrent migration
        Object lock;
        synchronized (migrationLocks) {
            lock = migrationLocks.computeIfAbsent(
                    namespaceDir.toAbsolutePath().toString(), k -> new Object());
        }

        synchronized (lock) {
            // Re-read inside lock (another thread may have migrated)
            currentVersion = readSchemaVersion(namespaceDir);
            if (!currentVersion.needsMigration(targetVersion)) {
                return false;
            }

            return executeMigrationChain(namespaceDir, currentVersion);
        }
    }

    /**
     * Finds and executes the migration chain from current to target version.
     */
    private boolean executeMigrationChain(Path namespaceDir, SchemaVersion fromVersion) {
        List<NamespaceMigration> chain = findMigrationChain(fromVersion, targetVersion);

        if (chain.isEmpty()) {
            log.warn("[Migration] No migration path from {} to {} for {}",
                    fromVersion, targetVersion, namespaceDir);
            return false;
        }

        log.info("[Migration] Migrating namespace {} from {} to {} ({} steps)",
                namespaceDir.getFileName(), fromVersion, targetVersion, chain.size());

        SchemaVersion current = fromVersion;
        List<Map<String, String>> history = new ArrayList<>();

        for (NamespaceMigration migration : chain) {
            try {
                log.info("[Migration]   Step: {} -> {} ({})",
                        migration.fromVersion(), migration.toVersion(), migration.description());

                migration.migrate(namespaceDir);

                history.add(Map.of(
                        "from", migration.fromVersion().toString(),
                        "to", migration.toVersion().toString(),
                        "timestamp", Instant.now().toString(),
                        "description", migration.description()));

                current = migration.toVersion();

                // Write intermediate version in case of failure on next step
                writeSchemaVersion(namespaceDir, current, history);

            } catch (IOException e) {
                log.error("[Migration] Failed at step {} -> {}: {}",
                        migration.fromVersion(), migration.toVersion(), e.getMessage(), e);
                // Write whatever version we reached
                writeSchemaVersion(namespaceDir, current, history);
                return !history.isEmpty(); // partial migration
            }
        }

        log.info("[Migration] Successfully migrated {} to version {}",
                namespaceDir.getFileName(), current);
        return true;
    }

    /**
     * Finds the shortest chain of migrations from source to target.
     */
    List<NamespaceMigration> findMigrationChain(SchemaVersion from, SchemaVersion to) {
        List<NamespaceMigration> chain = new ArrayList<>();
        SchemaVersion current = from;

        while (current.isOlderThan(to)) {
            SchemaVersion searchVersion = current;
            Optional<NamespaceMigration> next = migrations.stream()
                    .filter(m -> m.fromVersion().equals(searchVersion))
                    .findFirst();

            if (next.isEmpty()) {
                break; // no migration path available
            }

            chain.add(next.get());
            current = next.get().toVersion();
        }

        return chain;
    }

    /**
     * Reads the schema version from the namespace's manifest.json.
     * Returns V1_0_0 if no manifest exists (pre-versioning namespace).
     */
    public SchemaVersion readSchemaVersion(Path namespaceDir) {
        Path manifestPath = namespaceDir.resolve(MANIFEST_FILE);
        if (!Files.exists(manifestPath)) {
            return SchemaVersion.V1_0_0; // pre-versioning
        }

        try {
            String content = Files.readString(manifestPath);
            Matcher matcher = SCHEMA_VERSION_PATTERN.matcher(content);
            if (matcher.find()) {
                return SchemaVersion.parse(matcher.group(1));
            }
        } catch (Exception e) {
            log.warn("[Migration] Could not read manifest from {}: {}",
                    manifestPath, e.getMessage());
        }

        return SchemaVersion.V1_0_0;
    }

    /**
     * Writes the schema version and migration history to manifest.json.
     */
    void writeSchemaVersion(Path namespaceDir, SchemaVersion version,
                            List<Map<String, String>> history) {
        Path manifestPath = namespaceDir.resolve(MANIFEST_FILE);
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"schemaVersion\": \"").append(version).append("\",\n");
            sb.append("  \"walVersion\": \"1.0.0\",\n");
            sb.append("  \"indexVersion\": \"1.0.0\",\n");
            sb.append("  \"encryptionVersion\": \"1.0.0\",\n");
            sb.append("  \"lastMigration\": \"").append(Instant.now()).append("\",\n");
            sb.append("  \"migrationHistory\": [\n");
            for (int i = 0; i < history.size(); i++) {
                Map<String, String> entry = history.get(i);
                sb.append("    {");
                sb.append("\"from\": \"").append(entry.get("from")).append("\", ");
                sb.append("\"to\": \"").append(entry.get("to")).append("\", ");
                sb.append("\"timestamp\": \"").append(entry.get("timestamp")).append("\", ");
                sb.append("\"description\": \"").append(entry.get("description")).append("\"");
                sb.append("}");
                if (i < history.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ]\n");
            sb.append("}\n");
            Files.writeString(manifestPath, sb.toString());
        } catch (IOException e) {
            log.error("[Migration] Failed to write manifest to {}: {}", manifestPath, e.getMessage());
        }
    }

    /**
     * Returns the number of registered migrations.
     */
    public int migrationCount() {
        return migrations.size();
    }

    /**
     * Returns the target version.
     */
    public SchemaVersion targetVersion() {
        return targetVersion;
    }
}
