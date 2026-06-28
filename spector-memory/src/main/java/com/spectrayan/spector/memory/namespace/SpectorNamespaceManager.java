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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.memory.StorageLayout;
import com.spectrayan.spector.memory.migration.MigrationPipeline;

/**
 * Manages namespace lifecycle — creation, discovery, and quota enforcement.
 *
 * <h3>Design</h3>
 * <p>Each namespace is an isolated memory space at a directory path.
 * The manager discovers existing namespaces at startup and provides
 * create/get operations for runtime namespace management.</p>
 *
 * <h3>Disk Layout</h3>
 * <pre>
 *   persistence-path/namespaces/
 *   ├── agent-alpha/
 *   │   ├── namespace.json
 *   │   ├── global/
 *   │   ├── partitions/
 *   │   └── cross/
 *   └── agent-beta/
 *       └── ...
 * </pre>
 *
 * <h3>Extension Point</h3>
 * <p>Enterprise layers can extend this by subclassing or wrapping with
 * tenant-aware resolution, JWT-based namespace routing, and hierarchical
 * directory layouts. Core only manages flat namespace directories.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Uses {@link ConcurrentHashMap} for the namespace registry.
 * Each namespace is isolated — operations on one namespace do not
 * affect others.</p>
 *
 * @see NamespaceConfig
 * @see NamespaceQuotas
 * @see StorageLayout
 */
public class SpectorNamespaceManager {

    private static final Logger log = LoggerFactory.getLogger(SpectorNamespaceManager.class);

    private final Path basePath;
    private final boolean sharded;
    private final ConcurrentHashMap<String, NamespaceContext> namespaces;

    /** Optional migration pipeline — runs lazy migrations on namespace access. */
    private volatile MigrationPipeline migrationPipeline;

    /**
     * Creates a namespace manager rooted at the given persistence path.
     *
     * <p>On construction, discovers existing namespaces from the
     * {@code namespaces/} directory.</p>
     *
     * @param basePath root persistence path
     */
    public SpectorNamespaceManager(Path basePath) {
        this(basePath, false);
    }

    /**
     * Creates a namespace manager with optional directory sharding.
     *
     * <p>When {@code sharded=true}, namespaces are stored under
     * hash-sharded directories: {@code namespaces/XX/YY/id/}
     * (65,536 buckets). Discovery scans the two-level shard structure.</p>
     *
     * @param basePath root persistence path
     * @param sharded  if true, use hash-based directory sharding
     */
    public SpectorNamespaceManager(Path basePath, boolean sharded) {
        this.basePath = basePath;
        this.sharded = sharded;
        this.namespaces = new ConcurrentHashMap<>();

        // Discover existing namespaces
        Path namespacesDir = StorageLayout.namespacesDir(basePath);
        if (Files.isDirectory(namespacesDir)) {
            if (sharded) {
                discoverShardedNamespaces(namespacesDir);
            } else {
                discoverFlatNamespaces(namespacesDir);
            }
        }

        log.info("SpectorNamespaceManager initialized: {} namespaces at {} (sharded={})",
                namespaces.size(), basePath, sharded);
    }

    /**
     * Discovers flat (unsharded) namespaces: {@code namespaces/id/namespace.json}
     */
    private void discoverFlatNamespaces(Path namespacesDir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(namespacesDir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) continue;
                String nsId = entry.getFileName().toString();

                // Only recognize directories with namespace.json
                if (Files.exists(entry.resolve(StorageLayout.FILE_NAMESPACE))) {
                    NamespaceConfig config = loadConfig(nsId, entry);
                    namespaces.put(nsId, new NamespaceContext(config, entry));
                    log.info("Discovered namespace: {}", nsId);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan namespaces directory", e);
        }
    }

    /**
     * Discovers sharded namespaces: {@code namespaces/XX/YY/id/namespace.json}
     * Scans the two-level shard directory structure (256 × 256 = 65,536 buckets).
     */
    private void discoverShardedNamespaces(Path namespacesDir) {
        try (DirectoryStream<Path> l1Stream = Files.newDirectoryStream(namespacesDir)) {
            for (Path l1 : l1Stream) {
                if (!Files.isDirectory(l1)) continue;
                // L1 bucket (2-char hex prefix)
                String l1Name = l1.getFileName().toString();
                if (l1Name.length() != StorageLayout.SHARD_HEX_DIGITS) continue;

                try (DirectoryStream<Path> l2Stream = Files.newDirectoryStream(l1)) {
                    for (Path l2 : l2Stream) {
                        if (!Files.isDirectory(l2)) continue;
                        // L2 bucket (2-char hex prefix)
                        String l2Name = l2.getFileName().toString();
                        if (l2Name.length() != StorageLayout.SHARD_HEX_DIGITS) continue;

                        // Scan namespace dirs inside L2 bucket
                        try (DirectoryStream<Path> nsStream = Files.newDirectoryStream(l2)) {
                            for (Path nsDir : nsStream) {
                                if (!Files.isDirectory(nsDir)) continue;
                                String nsId = nsDir.getFileName().toString();

                                if (Files.exists(nsDir.resolve(StorageLayout.FILE_NAMESPACE))) {
                                    NamespaceConfig config = loadConfig(nsId, nsDir);
                                    namespaces.put(nsId, new NamespaceContext(config, nsDir));
                                    log.debug("Discovered sharded namespace: {} at {}/{}/{}",
                                            nsId, l1Name, l2Name, nsId);
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan sharded namespaces directory", e);
        }
    }

    /**
     * Creates a new namespace with the given configuration.
     *
     * @param config namespace configuration
     * @return the namespace context
     * @throws IllegalArgumentException if the namespace ID is invalid
     * @throws IllegalStateException    if the namespace already exists
     */
    public NamespaceContext createNamespace(NamespaceConfig config) {
        if (!config.isValidId()) {
            throw new IllegalArgumentException("Invalid namespace ID: " + config.id());
        }
        if (namespaces.containsKey(config.id())) {
            throw new IllegalStateException("Namespace already exists: " + config.id());
        }

        Path nsDir = resolveNamespacePath(config.id());
        try {
            Files.createDirectories(nsDir);
            Files.createDirectories(nsDir.resolve(StorageLayout.DIR_RUNTIME));
            Files.createDirectories(nsDir.resolve(StorageLayout.DIR_PARTITIONS));
            writeConfig(config, nsDir.resolve(StorageLayout.FILE_NAMESPACE));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create namespace: " + config.id(), e);
        }

        NamespaceContext ctx = new NamespaceContext(config, nsDir);
        namespaces.put(config.id(), ctx);
        log.info("Created namespace: {} at {}", config.id(), nsDir);
        return ctx;
    }

    /**
     * Returns the context for a namespace, or null if not found.
     *
     * @param namespaceId the namespace ID
     * @return the namespace context, or null
     */
    public NamespaceContext getNamespace(String namespaceId) {
        NamespaceContext ctx = namespaces.get(namespaceId);
        if (ctx != null) {
            migrateIfNeeded(ctx);
        }
        return ctx;
    }

    /**
     * Returns the context for a namespace, creating it with defaults if missing.
     *
     * @param namespaceId the namespace ID
     * @return the namespace context (never null)
     */
    public NamespaceContext getOrCreateNamespace(String namespaceId) {
        NamespaceContext existing = namespaces.get(namespaceId);
        if (existing != null) return existing;

        NamespaceConfig config = NamespaceConfig.unlimited(namespaceId);
        Path nsDir = resolveNamespacePath(config.id());
        try {
            Files.createDirectories(nsDir);
            Files.createDirectories(nsDir.resolve(StorageLayout.DIR_RUNTIME));
            Files.createDirectories(nsDir.resolve(StorageLayout.DIR_PARTITIONS));
            writeConfig(config, nsDir.resolve(StorageLayout.FILE_NAMESPACE));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create namespace: " + namespaceId, e);
        }

        NamespaceContext newCtx = new NamespaceContext(config, nsDir);
        NamespaceContext winner = namespaces.putIfAbsent(namespaceId, newCtx);
        if (winner != null) return winner; // another thread created it first

        // Write manifest for new namespace
        migrateIfNeeded(newCtx);

        log.info("Auto-created namespace: {}", namespaceId);
        return newCtx;
    }

    /**
     * Returns true if the namespace exists.
     */
    public boolean exists(String namespaceId) {
        return namespaces.containsKey(namespaceId);
    }

    /**
     * Returns the number of registered namespaces.
     */
    public int count() {
        return namespaces.size();
    }

    /**
     * Returns all namespace IDs.
     */
    public Collection<String> namespaceIds() {
        return namespaces.keySet();
    }

    /**
     * Returns all namespace contexts.
     */
    public Collection<NamespaceContext> allNamespaces() {
        return namespaces.values();
    }

    /**
     * Returns the base persistence path.
     */
    public Path basePath() {
        return basePath;
    }

    /** Returns whether directory sharding is enabled. */
    public boolean isSharded() {
        return sharded;
    }

    /**
     * Sets the migration pipeline for lazy schema migration.
     *
     * <p>When set, each namespace access ({@link #getNamespace},
     * {@link #getOrCreateNamespace}) triggers a migration check.
     * If the namespace's {@code manifest.json} is stale, pending
     * migrations run before the namespace is returned.</p>
     *
     * @param pipeline the migration pipeline, or null to disable
     */
    public void setMigrationPipeline(MigrationPipeline pipeline) {
        this.migrationPipeline = pipeline;
        log.info("[Namespace] Migration pipeline {}",
                pipeline != null ? "enabled (" + pipeline.migrationCount() + " migrations)" : "disabled");
    }

    /**
     * Runs the migration pipeline on a namespace if configured.
     */
    private void migrateIfNeeded(NamespaceContext ctx) {
        MigrationPipeline pipeline = this.migrationPipeline;
        if (pipeline != null) {
            pipeline.migrateIfNeeded(ctx.directory());
        }
    }

    /**
     * Resolves the namespace directory path, using sharding if enabled.
     *
     * @param namespaceId the namespace ID
     * @return flat or sharded path
     */
    protected Path resolveNamespacePath(String namespaceId) {
        if (sharded) {
            return StorageLayout.namespaceDirSharded(basePath, namespaceId);
        }
        return StorageLayout.namespaceDir(basePath, namespaceId);
    }

    // ── Internal helpers ──

    /**
     * Loads namespace configuration from the directory.
     * Subclasses (e.g., enterprise) can override for richer config parsing.
     */
    protected NamespaceConfig loadConfig(String nsId, Path nsDir) {
        log.debug("Loading namespace config: {}", nsDir.resolve(StorageLayout.FILE_NAMESPACE));
        return NamespaceConfig.unlimited(nsId);
    }

    /**
     * Writes namespace configuration to disk.
     * Subclasses can override to write additional metadata.
     */
    protected void writeConfig(NamespaceConfig config, Path path) throws IOException {
        String json = """
                {
                  "id": "%s",
                  "display_name": "%s",
                  "max_memories": %d,
                  "max_partitions": %d,
                  "max_storage_bytes": %d,
                  "read_only": %s,
                  "created_at": "%s"
                }
                """.formatted(
                config.id(),
                config.displayName(),
                config.maxMemories(),
                config.maxPartitions(),
                config.maxStorageBytes(),
                config.readOnly(),
                java.time.Instant.now().toString()
        );
        Files.writeString(path, json);
    }

    // ═══════════════════════════════════════════════════════════════
    // Namespace Context — groups config + quotas + path
    // ═══════════════════════════════════════════════════════════════

    /**
     * Context for a namespace: configuration, quotas, and path.
     */
    public static final class NamespaceContext {

        private final NamespaceConfig config;
        private final NamespaceQuotas quotas;
        private final Path directory;

        public NamespaceContext(NamespaceConfig config, Path directory) {
            this.config = config;
            this.quotas = new NamespaceQuotas(config);
            this.directory = directory;
        }

        /** Namespace configuration. */
        public NamespaceConfig config() { return config; }

        /** Quota tracker. */
        public NamespaceQuotas quotas() { return quotas; }

        /** Root directory for this namespace's data. */
        public Path directory() { return directory; }

        /** Path to runtime/ within this namespace (V3 layout). */
        public Path runtimeDir() { return directory.resolve(StorageLayout.DIR_RUNTIME); }

        /** @deprecated Use {@link #runtimeDir()}. Kept for migration. */
        @Deprecated(forRemoval = true)
        public Path globalDir() { return directory.resolve(StorageLayout.DIR_GLOBAL); }

        /** Path to partitions/ within this namespace. */
        public Path partitionsDir() { return directory.resolve(StorageLayout.DIR_PARTITIONS); }

        /** @deprecated V3 layout eliminates cross/. */
        @Deprecated(forRemoval = true)
        public Path crossDir() { return directory.resolve(StorageLayout.DIR_CROSS); }
    }
}
