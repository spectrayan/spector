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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Compliance erasure service that unlinks namespace directories from local storage,
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
    private final ReplicaErasureDispatcher replicaErasureDispatcher;

    public TenantErasureService(
            AccountCatalog accountCatalog,
            ObjectStoreClient objectStoreClient,
            String drBucket,
            Path remembererBasePath
    ) {
        this(accountCatalog, objectStoreClient, drBucket, remembererBasePath, (ReplicaErasureDispatcher) null);
    }

    public TenantErasureService(
            AccountCatalog accountCatalog,
            ObjectStoreClient objectStoreClient,
            String drBucket,
            Path remembererBasePath,
            ReplicaErasureDispatcher replicaErasureDispatcher
    ) {
        this.accountCatalog = Objects.requireNonNull(accountCatalog, "accountCatalog must not be null");
        this.objectStoreClient = objectStoreClient;
        this.drBucket = drBucket;
        this.remembererBasePath = remembererBasePath;
        this.replicaErasureDispatcher = replicaErasureDispatcher;
    }

    /**
     * Executes unlinking and deletion of a namespace across local disk, cloud object stores, and replicas.
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

        // 1. Verify Legal Hold and fail closed if unresolvable (Req R6.7, Invariant V8, G24)
        Optional<NamespaceRecord> recordOpt = accountCatalog.resolve(accountId, slugOrId);
        if (recordOpt.isEmpty()) {
            log.error("[Erasure] Refusing erasure for unresolvable namespace '{}' in account '{}' (fail-closed, G24)",
                    slugOrId, accountId);
            throw new IllegalStateException("Cannot verify legal hold for unresolvable namespace: " + slugOrId);
        }
        NamespaceRecord record = recordOpt.get();
        if (record.legalHold()) {
            String nsId = record.namespaceId();
            log.warn("[Erasure] Refusing erasure for namespace '{}' under active legal hold (Req R6.7, V8)", nsId);
            throw new NamespaceLegalHoldException(nsId);
        }
        String namespaceId = record.namespaceId();

        // 1b. Tombstone / deregister from Catalog BEFORE file deletion (G24)
        // If tombstone fails (e.g. concurrent legal hold set), no data is destroyed
        accountCatalog.tombstone(accountId, slugOrId);

        // 2. Local File Deletion via staged atomic rename (Req R6.2, G25)
        AtomicInteger deletedFilesCount = new AtomicInteger(0);
        AtomicLong deletedBytesCount = new AtomicLong(0);

        if (remembererBasePath != null && Files.isDirectory(remembererBasePath)) {
            Path targetDir = findNamespaceDir(namespaceId);
            if (targetDir != null && Files.exists(targetDir)) {
                Path stagedDir = targetDir.resolveSibling(targetDir.getFileName().toString() + ".deleting." + System.currentTimeMillis());
                try {
                    Files.move(targetDir, stagedDir, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                    deleteLocalDirectory(stagedDir, deletedFilesCount, deletedBytesCount);
                } catch (IOException e) {
                    log.warn("[Erasure] Staged atomic rename failed for {}, falling back to direct purge: {}", targetDir, e.getMessage());
                    deleteLocalDirectory(targetDir, deletedFilesCount, deletedBytesCount);
                }
            }
            // G25: Clean up any namespace-specific WAL directory outside the main namespace tree
            Path walDir = remembererBasePath.resolve(StoragePaths.DIR_WAL).resolve(namespaceId);
            if (Files.isDirectory(walDir)) {
                deleteLocalDirectory(walDir, deletedFilesCount, deletedBytesCount);
            }
        }

        // 3. Object-Store Prefix Deletion in DR Bucket (Req R6.3, G25)
        int objectStoreKeysDeleted = 0;
        if (objectStoreClient != null && drBucket != null && !drBucket.isBlank()) {
            // Delete prefixes: snapshots/{accountId}/{namespaceId}/ and snapshots/untenanted/{namespaceId}/
            objectStoreKeysDeleted += objectStoreClient.deleteByPrefix(drBucket, "snapshots/" + accountId + "/" + namespaceId + "/");
            objectStoreKeysDeleted += objectStoreClient.deleteByPrefix(drBucket, "snapshots/untenanted/" + namespaceId + "/");
            // G25: Also sweep all prefixes matching /{namespaceId}/
            try {
                List<String> matchingKeys = objectStoreClient.listKeysByPrefix(drBucket, "snapshots/");
                for (String key : matchingKeys) {
                    if (key.contains("/" + namespaceId + "/")
                            && !key.startsWith("snapshots/" + accountId + "/" + namespaceId + "/")
                            && !key.startsWith("snapshots/untenanted/" + namespaceId + "/")) {
                        objectStoreClient.deleteObject(drBucket, key);
                        objectStoreKeysDeleted++;
                    }
                }
            } catch (Exception e) {
                log.debug("[Erasure] Prefix sweep for {} encountered: {}", namespaceId, e.getMessage());
            }

            // G32: Write erasure tombstone marker so DR restore refuses to resurrect erased data
            try {
                byte[] markerBytes = ("{\"erasedAt\":\"" + Instant.now() + "\",\"namespaceId\":\"" + namespaceId + "\",\"accountId\":\"" + accountId + "\"}").getBytes(StandardCharsets.UTF_8);
                objectStoreClient.putObject(drBucket, "snapshots/.tombstones/" + namespaceId,
                        markerBytes, "application/json", Map.of("erased", "true"));
            } catch (Exception e) {
                log.warn("[Erasure] Failed to write erasure marker in DR bucket for {}: {}", namespaceId, e.getMessage());
            }
        }

        // 4. Propagate to Replica Disks (Req R6.3, G37)
        boolean replicaDispatched = false;
        int replicaAcks = 0;
        List<String> unreachableReplicas = List.of();
        if (propagateReplicas) {
            if (replicaErasureDispatcher != null) {
                try {
                    ReplicaErasureDispatcher.ReplicaErasureResult result = replicaErasureDispatcher.dispatchErasure(namespaceId);
                    replicaDispatched = true;
                    replicaAcks = result.successfulAcks();
                    unreachableReplicas = result.unreachableReplicas();
                    if (!result.isComplete()) {
                        log.warn("[Erasure] Replica erasure incomplete for {}: {} failed, unreachable: {}",
                                namespaceId, result.failedAcks(), unreachableReplicas);
                    }
                } catch (Exception e) {
                    log.warn("[Erasure] Failed to propagate erasure to replica disks for {}: {}", namespaceId, e.getMessage());
                }
            } else {
                log.warn("[Erasure] Replica propagation requested for '{}' but no replicaErasureDispatcher configured (G37)",
                        namespaceId);
            }
        }

        ErasureAuditReport report = ErasureAuditReport.create(
                accountId,
                namespaceId,
                operator,
                deletedFilesCount.get(),
                deletedBytesCount.get(),
                objectStoreKeysDeleted,
                replicaDispatched,
                replicaAcks,
                unreachableReplicas
        );

        log.info("[Erasure] Completed unlinking and deletion of namespace={} localFiles={} localBytes={} cloudKeys={} replicaAcks={}",
                namespaceId, report.localFilesDeleted(), report.localBytesDeleted(), report.objectStoreKeysDeleted(), replicaAcks);

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
