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

import com.spectrayan.spector.kernel.storage.NamespacePathResolver;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Startup detector verifying that all tenanted namespaces have been migrated to the tenant-rooted
 * layout when {@code spector.namespace.tenant-rooted.enabled=true} (ADR-0033 D3=C, Task 4.5, Requirements R5.8).
 *
 * <p>Fails readiness by throwing an {@link IllegalStateException} if unmigrated tenanted namespaces
 * are detected. Automatically cleans up leftover {@code .migrating-*} staging directories on boot (R5.5).</p>
 */
@Component
public class TenantNamespaceStartupDetector {

    private static final Logger log = LoggerFactory.getLogger(TenantNamespaceStartupDetector.class);

    private final SynapseProperties synapseProps;
    private final AccountCatalog catalog;
    private final Path basePath;

    public TenantNamespaceStartupDetector(SynapseProperties synapseProps, AccountCatalog catalog) {
        this.synapseProps = Objects.requireNonNull(synapseProps, "synapseProps");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        // Must be the rememberer root, not dataDir() — otherwise the detector inspects a tree the
        // resolver never reads and always reports zero unmigrated namespaces (Req R3.1).
        this.basePath = synapseProps.remembererRoot();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Step 1: If the tenant-rooted layout is off, do nothing at all. Boot must not touch the
        // filesystem for a feature that is disabled — the previous ordering ran staging cleanup
        // first, so a disabled install still performed deletions under the rememberer root.
        boolean tenantRootedEnabled = synapseProps.getNamespace() != null
                && synapseProps.getNamespace().isTenantRootedEnabled();
        if (!tenantRootedEnabled) {
            log.debug("[TenantNamespaceStartupDetector] tenant-rooted layout is disabled; startup readiness check skipped");
            return;
        }

        // Step 2: Discard redundant staging copies from an interrupted migration (R5.5). Safe by the
        // migrator's invariant that staging is never the only copy of a namespace.
        TenantNamespaceMigrator.cleanupStagedDirectories(basePath);

        // Step 3: Count unmigrated tenanted namespaces
        int unmigrated = countUnmigratedNamespaces();
        if (unmigrated > 0) {
            log.error("[TenantNamespaceStartupDetector] Detected {} unmigrated tenanted namespace(s) under {}. "
                    + "Startup readiness failed. Run 'spector migrate-namespaces' to complete migration before enabling "
                    + "spector.namespace.tenant-rooted.enabled=true.", unmigrated, basePath);
            throw new IllegalStateException("Unmigrated tenanted namespaces detected: " + unmigrated
                    + ". Run 'spector migrate-namespaces' before starting Synapse with tenant-rooted layout enabled.");
        }

        log.info("[TenantNamespaceStartupDetector] All tenanted namespaces verified on tenant-rooted layout.");
    }

    public int countUnmigratedNamespaces() {
        if (!Files.isDirectory(basePath)) {
            return 0;
        }
        int count = 0;
        List<Account> tenantedAccounts = catalog.listTenantedAccounts();
        for (Account account : tenantedAccounts) {
            String tenantId = account.tenantId();
            String accountId = account.id();
            // Tombstoned namespaces are migrated (so a tenant wipe reaches them) but must not block
            // readiness: nothing can open one, so an unmigrated tombstone is not a correctness risk
            // at boot. Migration completeness is the migrator's concern, not the gate's.
            List<NamespaceRecord> records = catalog.listOwnedNamespaces(accountId).stream()
                    .filter(r -> r.status() != NamespaceStatus.TOMBSTONED)
                    .toList();
            for (NamespaceRecord record : records) {
                String nsId = record.namespaceId();
                Path src = NamespacePathResolver.resolve(basePath, null, nsId).dir();
                Path dst = NamespacePathResolver.resolve(basePath, tenantId, nsId).dir();

                // If layout A exists and layout B does not exist (or lacks namespace.json), it is unmigrated!
                if (Files.exists(src.resolve(StoragePaths.FILE_NAMESPACE))
                        && !Files.exists(dst.resolve(StoragePaths.FILE_NAMESPACE))) {
                    count++;
                }
            }
        }
        return count;
    }
}
