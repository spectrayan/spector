/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.migration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.kernel.storage.NamespacePathResolver;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Migrates existing flat namespace directories (layout A) to the tenant-rooted layout (layout B)
 * for tenanted accounts (ADR-0033, Tasks 4.1–4.3, Requirements R5.1–R5.8).
 *
 * <p>Key properties:
 * <ul>
 *   <li><b>Catalog-driven discovery:</b> Queries the catalog for accounts where {@code tenantId != null},
 *       rather than crawling the filesystem (R5.1).</li>
 *   <li><b>Idempotent:</b> Re-running is a safe no-op. Non-empty targets are skipped with warning,
 *       never merged or overwritten (R5.2).</li>
 *   <li><b>Never dual-writes:</b> Operates on disk exclusively during migration (I6).</li>
 *   <li><b>Lease guard:</b> Refuses to migrate namespaces that are currently mapped or leased (R5.4).</li>
 *   <li><b>Crash safe:</b> Stages through {@code .migrating-{nsId}} sibling before moving into place (R5.5).</li>
 * </ul>
 */
@Component
public class TenantNamespaceMigrator {

    private static final Logger log = LoggerFactory.getLogger(TenantNamespaceMigrator.class);

    private final Path basePath;
    private final AccountCatalog catalog;
    private final Predicate<String> leaseGuard;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    @Autowired
    public TenantNamespaceMigrator(
            SynapseProperties synapseProps,
            AccountCatalog catalog,
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Autowired(required = false) ObjectMapper objectMapper) {
        this(resolveBasePath(synapseProps), catalog, null, meterRegistry, objectMapper);
    }

    public TenantNamespaceMigrator(
            Path basePath,
            AccountCatalog catalog,
            Predicate<String> leaseGuard,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {
        this.basePath = Objects.requireNonNull(basePath, "basePath must not be null");
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.leaseGuard = leaseGuard;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    private static Path resolveBasePath(SynapseProperties props) {
        String dataDir = props != null ? props.dataDir() : null;
        if (dataDir == null || dataDir.isBlank()) {
            dataDir = "./spector-data";
        }
        return Path.of(dataDir);
    }

    /**
     * Executes migration across all tenanted accounts.
     *
     * @param dryRun if true, logs actions and returns planned counts without modifying disk
     * @return structured summary of the operation
     */
    public MigrationSummary migrate(boolean dryRun) {
        return migrate(this.basePath, this.catalog, this.leaseGuard, this.meterRegistry, this.objectMapper, dryRun);
    }

    /**
     * Static driver for standalone and test execution.
     */
    public static MigrationSummary migrate(
            Path basePath,
            AccountCatalog catalog,
            Predicate<String> leaseGuard,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper,
            boolean dryRun) {

        Objects.requireNonNull(basePath, "basePath must not be null");
        Objects.requireNonNull(catalog, "catalog must not be null");
        ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper();

        if (!Files.isDirectory(basePath)) {
            log.info("[TenantNamespaceMigrator] Base directory does not exist: {}. Nothing to migrate.", basePath);
            return new MigrationSummary(0, 0, 0);
        }

        if (!dryRun) {
            cleanupStagedDirectories(basePath);
        }

        AtomicInteger migrated = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        List<Account> tenantedAccounts = catalog.listTenantedAccounts();
        log.info("[TenantNamespaceMigrator] Found {} tenanted account(s) in catalog (dryRun={})",
                tenantedAccounts.size(), dryRun);

        for (Account account : tenantedAccounts) {
            String tenantId = account.tenantId();
            String accountId = account.id();
            List<NamespaceRecord> records;
            try {
                records = catalog.listAccessible(accountId).stream()
                        .filter(r -> accountId.equals(r.ownerAccountId()))
                        .toList();
            } catch (Exception e) {
                log.error("[TenantNamespaceMigrator] Failed to list namespaces for account '{}': {}", accountId, e.getMessage(), e);
                errors.incrementAndGet();
                continue;
            }

            for (NamespaceRecord record : records) {
                String nsId = record.namespaceId();
                Path src = NamespacePathResolver.resolve(basePath, null, nsId).dir();
                Path dst = NamespacePathResolver.resolve(basePath, tenantId, nsId).dir();

                // Check 1: Already migrated or never created on disk
                if (!Files.exists(src)) {
                    log.debug("[TenantNamespaceMigrator] Namespace '{}' at layout A ({}) does not exist; skipping (already migrated or never created)",
                            nsId, src);
                    skipped.incrementAndGet();
                    continue;
                }

                // Check 2: Sanity gate on namespace.json
                if (!Files.exists(src.resolve(StoragePaths.FILE_NAMESPACE))) {
                    log.warn("[TenantNamespaceMigrator] Namespace directory exists at {} but lacks namespace.json; skipping", src);
                    skipped.incrementAndGet();
                    continue;
                }

                // Check 3: Target already exists and non-empty -> skip with warning, never merge or overwrite (R5.2)
                if (Files.exists(dst) && isNonEmptyDirectory(dst)) {
                    log.warn("[TenantNamespaceMigrator] Sharded target already exists and is non-empty for namespace '{}' at {}. Skipping without merge or overwrite.",
                            nsId, dst);
                    skipped.incrementAndGet();
                    continue;
                }

                // Check 4: Lease guard (R5.4) -> refuse to move mapped or leased namespace
                if (leaseGuard != null && leaseGuard.test(nsId)) {
                    log.error("[TenantNamespaceMigrator] Refusing to migrate namespace '{}': namespace is currently leased or active", nsId);
                    errors.incrementAndGet();
                    continue;
                }

                // Check 5: Dry-run mode (R5.7)
                if (dryRun) {
                    log.info("[TenantNamespaceMigrator] [DRY RUN] Would migrate namespace '{}' (tenant '{}'): {} -> {}",
                            nsId, tenantId, src, dst);
                    migrated.incrementAndGet();
                    continue;
                }

                // Execute move with crash-safe staging (R5.5)
                Path staging = dst.getParent().resolve(".migrating-" + nsId);
                try {
                    if (Files.exists(staging)) {
                        deleteRecursively(staging);
                    }
                    Files.createDirectories(dst.getParent());

                    // Move to staging directory first
                    try {
                        Files.move(src, staging, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException e) {
                        copyRecursively(src, staging);
                        deleteRecursively(src);
                    }

                    // Update layout marker in staging before final rename
                    writeLayoutMarker(staging, tenantId, nsId, mapper);

                    // Final atomic rename into target
                    try {
                        Files.move(staging, dst, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException e) {
                        Files.move(staging, dst);
                    }

                    migrated.incrementAndGet();
                    log.info("[TenantNamespaceMigrator] Successfully migrated namespace '{}' (tenant '{}'): {} -> {}",
                            nsId, tenantId, src, dst);
                } catch (Exception e) {
                    errors.incrementAndGet();
                    log.error("[TenantNamespaceMigrator] Failed to migrate namespace '{}' (tenant '{}'): {}",
                            nsId, tenantId, e.getMessage(), e);
                }
            }
        }

        int migratedCount = migrated.get();
        int skippedCount = skipped.get();
        int errorsCount = errors.get();

        if (meterRegistry != null && !dryRun) {
            try {
                Counter.builder("spector.namespace.migration.migrated")
                        .description("Count of namespaces migrated to tenant-rooted layout")
                        .register(meterRegistry)
                        .increment(migratedCount);
                Counter.builder("spector.namespace.migration.skipped")
                        .description("Count of namespaces skipped during tenant migration")
                        .register(meterRegistry)
                        .increment(skippedCount);
                Counter.builder("spector.namespace.migration.errors")
                        .description("Count of namespace migration errors")
                        .register(meterRegistry)
                        .increment(errorsCount);
            } catch (Exception e) {
                log.warn("[TenantNamespaceMigrator] Failed to update micrometer counters: {}", e.getMessage());
            }
        }

        log.info("[TenantNamespaceMigrator] Migration complete: migrated={}, skipped={}, errors={}",
                migratedCount, skippedCount, errorsCount);

        return new MigrationSummary(migratedCount, skippedCount, errorsCount);
    }

    /**
     * Cleans up leftover {@code .migrating-*} directories from interrupted moves (R5.5).
     *
     * @param root base directory to scan
     * @return number of cleaned directories
     */
    public static int cleanupStagedDirectories(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return 0;
        }
        AtomicInteger cleaned = new AtomicInteger(0);
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> migratingDirs = stream
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith(".migrating-"))
                    .toList();
            for (Path dir : migratingDirs) {
                try {
                    deleteRecursively(dir);
                    cleaned.incrementAndGet();
                    log.info("[TenantNamespaceMigrator] Cleaned up leftover staging directory: {}", dir);
                } catch (IOException e) {
                    log.warn("[TenantNamespaceMigrator] Failed to clean up staging directory {}: {}", dir, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("[TenantNamespaceMigrator] Error scanning for leftover staging directories in {}: {}", root, e.getMessage());
        }
        return cleaned.get();
    }

    private static boolean isNonEmptyDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            return Files.exists(dir);
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            return ds.iterator().hasNext();
        } catch (IOException e) {
            return true;
        }
    }

    private static void copyRecursively(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            List<Path> paths = stream.toList();
            for (Path s : paths) {
                Path rel = source.relativize(s);
                Path dest = target.resolve(rel);
                if (Files.isDirectory(s)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(s, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static void writeLayoutMarker(Path dir, String tenantId, String namespaceId, ObjectMapper objectMapper) throws IOException {
        Path nsJson = dir.resolve(StoragePaths.FILE_NAMESPACE);
        Map<String, Object> manifest = new LinkedHashMap<>();
        if (Files.exists(nsJson)) {
            try {
                manifest = objectMapper.readValue(nsJson.toFile(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
                manifest = new LinkedHashMap<>();
            }
        }
        manifest.put("layout", "TENANT_SHA256");
        manifest.put("pathHelper", "tenantNamespaceDirSharded");
        manifest.put("tenantId", tenantId);
        manifest.put("namespaceId", namespaceId);
        if (!manifest.containsKey("createdAt")) {
            manifest.put("createdAt", Instant.now().toString());
        }

        Path tmp = dir.resolve(StoragePaths.FILE_NAMESPACE + ".tmp");
        objectMapper.writeValue(tmp.toFile(), manifest);
        Files.move(tmp, nsJson, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Structured result summary for migration executions.
     */
    public record MigrationSummary(int migrated, int skipped, int errors) {
        public boolean hasErrors() {
            return errors > 0;
        }
    }
}
