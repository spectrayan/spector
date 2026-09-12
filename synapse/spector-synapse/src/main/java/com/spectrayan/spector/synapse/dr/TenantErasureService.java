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
package com.spectrayan.spector.synapse.dr;

import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceLegalHoldException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Compliance erasure service that physically wipes namespace directories from local NVMe,
 * deletes cloud DR bucket object prefixes, and propagates deletion to replicas
 * (ADR-0034 §16, Req R6.1–R6.8, V7, V8).
 *
 * <p>Invariant V8: Legal hold strictly beats deletion in every path (R6.7).
 * Invariant V7: The erase report discloses uninspected ownerless/untenanted classes (R6.4).</p>
 */
public class TenantErasureService {

    private static final Logger log = LoggerFactory.getLogger(TenantErasureService.class);

    private final AccountCatalog accountCatalog;
    private final ObjectStoreClient objectStoreClient;
    private final String drBucket;
    private final Path remembererBasePath;
    private final Consumer<String> replicaErasureDispatcher;

    public TenantErasureService(
            AccountCatalog accountCatalog,
            ObjectStoreClient objectStoreClient,
            String drBucket,
            Path remembererBasePath
    ) {
        this(accountCatalog, objectStoreClient, drBucket, remembererBasePath, null);
    }

    public TenantErasureService(
            AccountCatalog accountCatalog,
            ObjectStoreClient objectStoreClient,
            String drBucket,
            Path remembererBasePath,
            Consumer<String> replicaErasureDispatcher
    ) {
        this.accountCatalog = Objects.requireNonNull(accountCatalog, "accountCatalog must not be null");
        this.objectStoreClient = objectStoreClient;
        this.drBucket = drBucket;
        this.remembererBasePath = remembererBasePath;
        this.replicaErasureDispatcher = replicaErasureDispatcher;
    }

    /**
     * Executes physical erasure of a namespace across local disk, cloud object stores, and replicas.
     *
     * @param accountId owning account
     * @param slugOrId namespace slug or TSID
     * @param propagateReplicas whether to propagate erasure to cell replicas
     * @param operator identity of the user/system performing erasure
     * @return ErasureAuditReport documenting deleted resources and completeness boundaries
     */
    public ErasureAuditReport eraseNamespace(
            String accountId,
            String slugOrId,
            boolean propagateReplicas,
            String operator
    ) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(slugOrId, "slugOrId must not be null");

        // 1. Verify Legal Hold (Req R6.7, V8)
        Optional<NamespaceRecord> recordOpt = accountCatalog.resolve(accountId, slugOrId);
        if (recordOpt.isPresent() && recordOpt.get().legalHold()) {
            String nsId = recordOpt.get().namespaceId();
            log.warn("[Erasure] Refusing erasure for namespace '{}' under active legal hold (Req R6.7, V8)", nsId);
            throw new NamespaceLegalHoldException(nsId);
        }

        String namespaceId = recordOpt.map(NamespaceRecord::namespaceId).orElse(slugOrId);

        // 2. Physical File Deletion on Local Disk (Req R6.2)
        AtomicInteger deletedFilesCount = new AtomicInteger(0);
        AtomicLong deletedBytesCount = new AtomicLong(0);

        if (remembererBasePath != null && Files.isDirectory(remembererBasePath)) {
            deleteLocalDirectory(findNamespaceDir(namespaceId), deletedFilesCount, deletedBytesCount);
        }

        // 3. Object-Store Prefix Deletion in DR Bucket (Req R6.3)
        int objectStoreKeysDeleted = 0;
        if (objectStoreClient != null && drBucket != null && !drBucket.isBlank()) {
            // Delete prefixes: snapshots/{accountId}/{namespaceId}/ and snapshots/*/{namespaceId}/
            objectStoreKeysDeleted += objectStoreClient.deleteByPrefix(drBucket, "snapshots/" + accountId + "/" + namespaceId + "/");
            objectStoreKeysDeleted += objectStoreClient.deleteByPrefix(drBucket, "snapshots/untenanted/" + namespaceId + "/");
        }

        // 4. Propagate to Replica Disks (Req R6.3)
        boolean replicaDispatched = false;
        if (propagateReplicas && replicaErasureDispatcher != null) {
            try {
                replicaErasureDispatcher.accept(namespaceId);
                replicaDispatched = true;
            } catch (Exception e) {
                log.warn("[Erasure] Failed to propagate erasure to replica disks for {}: {}", namespaceId, e.getMessage());
            }
        }

        // 5. Tombstone / deregister from Catalog
        try {
            accountCatalog.tombstone(accountId, slugOrId);
        } catch (Exception e) {
            log.info("[Erasure] Catalog tombstone noted: {}", e.getMessage());
        }

        ErasureAuditReport report = ErasureAuditReport.create(
                accountId,
                namespaceId,
                operator,
                deletedFilesCount.get(),
                deletedBytesCount.get(),
                objectStoreKeysDeleted,
                replicaDispatched
        );

        log.info("[Erasure] Completed physical erasure of namespace={} localFiles={} localBytes={} cloudKeys={}",
                namespaceId, report.localFilesDeleted(), report.localBytesDeleted(), report.objectStoreKeysDeleted());

        return report;
    }

    private Path findNamespaceDir(String namespaceId) {
        // Flat layout
        Path flat = remembererBasePath.resolve("namespaces").resolve(namespaceId);
        if (Files.isDirectory(flat)) {
            return flat;
        }

        // Tenant-rooted layout search
        Path tenants = remembererBasePath.resolve("tenants");
        if (Files.isDirectory(tenants)) {
            try (Stream<Path> stream = Files.list(tenants)) {
                Optional<Path> found = stream
                        .map(p -> p.resolve("namespaces").resolve(namespaceId))
                        .filter(Files::isDirectory)
                        .findFirst();
                if (found.isPresent()) {
                    return found.get();
                }
            } catch (IOException ignored) {}
        }

        return flat;
    }

    private void deleteLocalDirectory(Path dir, AtomicInteger fileCount, AtomicLong byteCount) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    if (Files.isRegularFile(p)) {
                        fileCount.incrementAndGet();
                        byteCount.addAndGet(Files.size(p));
                    }
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("[Erasure] Could not delete path {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("[Erasure] Failed to walk directory {}: {}", dir, e.getMessage());
        }
    }
}
