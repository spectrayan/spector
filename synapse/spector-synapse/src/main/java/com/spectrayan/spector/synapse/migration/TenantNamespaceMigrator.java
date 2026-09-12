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

    /** Prefix for transient staging directories used on cross-filesystem migrations. */
    static final String STAGING_PREFIX = ".migrating-";

    private final Path basePath;
    private final AccountCatalog catalog;
    private final Predicate<String> leaseGuard;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    @Autowired
    public TenantNamespaceMigrator(
            SynapseProperties synapseProps,
            AccountCatalog catalog,
            org.springframework.beans.factory.ObjectProvider<
                    com.spectrayan.spector.synapse.memory.MemoryRegistry> registryProvider,
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Autowired(required = false) ObjectMapper objectMapper) {
        this(resolveBasePath(synapseProps), catalog,
                inProcessLeaseGuard(registryProvider), meterRegistry, objectMapper);
    }

    /**
     * Builds the in-process guard that refuses to relocate a namespace this JVM currently has open
     * or leased (Req R5.4).
     *
     * <p>Resolved lazily: the registry is not necessarily initialised when this bean is constructed,
     * and passing {@code null} here — as the original wiring did — left every production path with no
     * guard whatsoever.</p>
     *
     * <p>Only covers this JVM. Cross-process protection is {@link RemembererRootLock}.</p>
     */
    private static Predicate<String> inProcessLeaseGuard(
            org.springframework.beans.factory.ObjectProvider<
                    com.spectrayan.spector.synapse.memory.MemoryRegistry> registryProvider) {
        if (registryProvider == null) {
            return null;
        }
        return nsId -> {
            var registry = registryProvider.getIfAvailable();
            if (registry == null) {
                return false;
            }
            var resolver = registry.namespaceResolver();
            if (resolver == null) {
                return false;
            }
            // "Open" matters as much as "leased": an mmap'd directory must not be moved even when no
            // request currently holds a lease on it.
            return resolver.isNamespaceOpen(nsId) || resolver.isNamespaceLeased(nsId);
        };
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

    /**
     * Resolves the rememberer root the migrator must operate on.
     *
     * <p>This MUST be {@link SynapseProperties#remembererRoot()} — the same root
     * {@code NamespaceResolver} opens. Using {@code dataDir()} here made the migrator scan a tree
     * the resolver never reads, so every namespace fell through the "source does not exist" branch
     * and was silently counted as skipped (Req R3.1).</p>
     */
    private static Path resolveBasePath(SynapseProperties props) {
        return props != null
                ? props.remembererRoot()
                : Path.of(SynapseProperties.DEFAULT_DATA_DIR);
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

        // Cross-process guard (Req R5.4). A CLI-local lease predicate cannot see mappings held by a
        // running server, so before touching anything verify no other JVM owns this root. A dry run
        // only reads, so it is exempt.
        RemembererRootLock.Attempt attempt = dryRun
                ? new RemembererRootLock.Attempt(RemembererRootLock.Outcome.ACQUIRED, null)
                : RemembererRootLock.tryAcquire(basePath);
        if (!dryRun && !attempt.mayMutateRoot()) {
            log.error("[TenantNamespaceMigrator] Refusing to migrate: another process holds the rememberer "
                    + "root {} (lock outcome {}). Stop the running Spector instance before migrating — "
                    + "relocating a directory it has memory-mapped is undefined behaviour.",
                    basePath, attempt.outcome());
            return new MigrationSummary(0, 0, 1);
        }

        try (RemembererRootLock held = attempt.lock()) {
            if (!dryRun) {
                cleanupStagedDirectories(basePath);
            }
            return migrateLocked(basePath, catalog, leaseGuard, meterRegistry, mapper, dryRun);
        }
    }

    private static MigrationSummary migrateLocked(
            Path basePath,
            AccountCatalog catalog,
            Predicate<String> leaseGuard,
            MeterRegistry meterRegistry,
            ObjectMapper mapper,
            boolean dryRun) {

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
                // Owner-scoped and status-inclusive. listAccessible is an authorization view: it hides
                // tombstoned namespaces and includes ones the account only holds a grant on, so using
                // it left tombstoned directories stranded on the flat layout, outside the tenant
                // prefix a wipe would delete (Req R9.1).
                records = catalog.listOwnedNamespaces(accountId);
            } catch (Exception e) {
                log.error("[TenantNamespaceMigrator] Failed to list namespaces for account '{}': {}", accountId, e.getMessage(), e);
                errors.incrementAndGet();
                continue;
            }

            for (NamespaceRecord record : records) {
                String nsId = record.namespaceId();
                Path src = NamespacePathResolver.resolve(basePath, null, nsId).dir();
                Path dst = NamespacePathResolver.resolve(basePath, tenantId, nsId).dir();

                boolean targetPopulated = Files.exists(dst) && isNonEmptyDirectory(dst);

                // Check 1: Target already populated. Never merge, never overwrite (R5.2). If the
                // source also survives, a previous run was interrupted after the target rename but
                // before the source was reclaimed; repair the marker and report the leftover instead
                // of deleting data on the operator's behalf.
                if (targetPopulated) {
                    if (!dryRun) {
                        try {
                            writeLayoutMarker(dst, tenantId, nsId, mapper);
                        } catch (IOException e) {
                            log.error("[TenantNamespaceMigrator] Namespace '{}' is present at {} but its layout "
                                    + "marker could not be repaired: {}", nsId, dst, e.getMessage(), e);
                            errors.incrementAndGet();
                            continue;
                        }
                    }
                    if (Files.exists(src)) {
                        log.warn("[TenantNamespaceMigrator] Namespace '{}' is already migrated to {} but a stale "
                                + "source copy remains at {}. The target is authoritative; remove the source "
                                + "manually once you have confirmed it. Not deleting it automatically.",
                                nsId, dst, src);
                    } else {
                        log.debug("[TenantNamespaceMigrator] Namespace '{}' already migrated to {}", nsId, dst);
                    }
                    skipped.incrementAndGet();
                    continue;
                }

                // Check 2: Nothing to migrate
                if (!Files.exists(src)) {
                    log.debug("[TenantNamespaceMigrator] Namespace '{}' at layout A ({}) does not exist; skipping (never created)",
                            nsId, src);
                    skipped.incrementAndGet();
                    continue;
                }

                // Check 3: Sanity gate on namespace.json
                if (!Files.exists(src.resolve(StoragePaths.FILE_NAMESPACE))) {
                    log.warn("[TenantNamespaceMigrator] Namespace directory exists at {} but lacks namespace.json; skipping", src);
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

                // Execute the move (R5.5).
                //
                // Invariant: the source is never removed until the target is durably in place, and a
                // staging directory is never the only copy of the data. The previous implementation
                // violated both — it moved (or copied-then-deleted) the source into staging first, so
                // a crash before the final rename left the data reachable only from `.migrating-*`,
                // which cleanupStagedDirectories then deleted on the next boot.
                //
                // Two paths, both crash-safe:
                //   same filesystem  -> one atomic rename src -> dst, then repair the marker at dst.
                //                       A crash before the marker write is healed by Check 1 on the
                //                       next run.
                //   cross filesystem -> copy src -> staging, marker into staging, rename staging ->
                //                       dst, and only then reclaim src. The source is intact for the
                //                       whole window, so an orphaned staging copy is always safe to
                //                       discard.
                Path staging = dst.getParent().resolve(STAGING_PREFIX + nsId);
                try {
                    if (Files.exists(staging)) {
                        deleteRecursively(staging);
                    }
                    Files.createDirectories(dst.getParent());

                    boolean movedInPlace;
                    try {
                        Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
                        movedInPlace = true;
                    } catch (AtomicMoveNotSupportedException e) {
                        copyRecursively(src, staging);
                        writeLayoutMarker(staging, tenantId, nsId, mapper);
                        try {
                            Files.move(staging, dst, StandardCopyOption.ATOMIC_MOVE);
                        } catch (AtomicMoveNotSupportedException e2) {
                            Files.move(staging, dst);
                        }
                        movedInPlace = false;
                    }

                    if (movedInPlace) {
                        writeLayoutMarker(dst, tenantId, nsId, mapper);
                    } else {
                        // Target is durable; the source copy can now be reclaimed.
                        deleteRecursively(src);
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
     * Discards leftover {@code .migrating-*} staging directories from interrupted moves (R5.5).
     *
     * <p>Safe only because of the invariant established in {@link #migrate}: a staging directory is
     * always a <em>copy</em> made while the source is still intact, never the sole copy of a
     * namespace. Anything found here is therefore redundant by construction.</p>
     *
     * <p>Do not weaken that invariant. An earlier version moved the source into staging before
     * renaming it into place, which made this method delete the only surviving copy whenever a
     * migration was interrupted mid-flight.</p>
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
                    .filter(p -> p.getFileName().toString().startsWith(STAGING_PREFIX))
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
        // Both fields carry the resolver's own identity, matching what NamespaceResolver and
        // FileAccountCatalog write. Two earlier values were wrong here: "TENANT_SHA256" is the enum
        // name rather than the layout id, and "tenantNamespaceDirSharded" names the *deprecated*
        // helper, which shards tenants under namespaces/ and is incompatible with this layout. A
        // Phase 3 replica reading that manifest would pick the wrong resolver — exactly the failure
        // ADR-0033 KI-3 warns about (Req R8.1, R8.3).
        String layoutId = NamespacePathResolver.Layout.TENANT_SHA256.id();
        manifest.put("layout", layoutId);
        manifest.put("pathHelper", layoutId);
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
