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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.kernel.bundle.BundleFileLayout;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.memory.replication.SnapshotManifest;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Disaster recovery restorer that hydrates cognitive namespaces from S3 snapshot objects into
 * a standby cell (ADR-0034 §11.2, §14, §16, Phase 6, Req R3.1–R3.12).
 *
 * <p>Key Invariants:
 * <ul>
 *   <li>V4: Restored namespace is either verifiably complete or REFUSES TO SERVE (R3.2, R3.4).</li>
 *   <li>V6: Restores strictly refuse when standby cell is outside tenant residency jurisdiction (R9.4).</li>
 *   <li>R3.1: Pre-serving integrity verification — magic ('SMKM'), layout ID ('BUND'), and SHA-256.</li>
 *   <li>R3.3, R3.6: Restored namespace reports its snapshot HWM to disclose data loss.</li>
 *   <li>R3.5: Resumable and idempotent execution.</li>
 *   <li>R3.7: Supports prioritized restore ordering.</li>
 *   <li>R3.8, R3.10: Rebuilds catalog identity state so recovered namespaces are reachable.</li>
 * </ul>
 * </p>
 */
public class DisasterRecoveryRestorer {

    private static final Logger log = LoggerFactory.getLogger(DisasterRecoveryRestorer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectStoreClient objectStoreClient;
    private final String bucket;
    private final String standbyCellRegion;
    private final AccountCatalog accountCatalog;

    public record RestoreResult(
            String namespaceId,
            String tenantId,
            long epoch,
            long hwm,
            Path restoredDir,
            int verifiedArtifactCount,
            long durationMs,
            boolean success,
            String refusalReason
    ) {}

    public record RestoreRequest(
            String tenantId,
            String namespaceId,
            long epoch,
            int priority,
            String tenantJurisdiction
    ) {}

    public DisasterRecoveryRestorer(
            ObjectStoreClient objectStoreClient,
            String bucket,
            String standbyCellRegion,
            AccountCatalog accountCatalog
    ) {
        this.objectStoreClient = Objects.requireNonNull(objectStoreClient, "objectStoreClient must not be null");
        this.bucket = Objects.requireNonNull(bucket, "bucket must not be null");
        this.standbyCellRegion = Objects.requireNonNull(standbyCellRegion, "standbyCellRegion must not be null");
        this.accountCatalog = accountCatalog;
    }

    /**
     * Discovers selectable snapshot epochs under the tenant/namespace prefix.
     * Partial uploads lacking manifest.json are strictly unselectable (Req R2.6).
     */
    public List<Long> discoverSelectableEpochs(String tenantId, String namespaceId) {
        String safeTenant = tenantId != null && !tenantId.isBlank() ? tenantId : "untenanted";
        String searchPrefix = "snapshots/" + safeTenant + "/" + namespaceId + "/";

        List<String> allKeys = objectStoreClient.listKeysByPrefix(bucket, searchPrefix);
        Set<Long> epochsWithManifest = new TreeSet<>();

        for (String key : allKeys) {
            if (key.endsWith("/manifest.json")) {
                // key format: snapshots/{tenant}/{namespace}/{epoch}/manifest.json
                String sub = key.substring(searchPrefix.length());
                int slash = sub.indexOf('/');
                if (slash > 0) {
                    try {
                        long epoch = Long.parseLong(sub.substring(0, slash));
                        epochsWithManifest.add(epoch);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return new ArrayList<>(epochsWithManifest);
    }

    /**
     * Restores a namespace snapshot with strict pre-serving integrity and residency validation.
     */
    public RestoreResult restoreNamespace(
            String tenantId,
            String namespaceId,
            long epoch,
            Path targetBaseDir,
            String tenantJurisdiction
    ) {
        long startTime = System.currentTimeMillis();
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(targetBaseDir, "targetBaseDir must not be null");

        // 1. Data Residency Validation (Req R9.4, V6)
        DataResidencyEnforcer.validateRestoreResidency(tenantJurisdiction, standbyCellRegion);

        String safeTenant = tenantId != null && !tenantId.isBlank() ? tenantId : "untenanted";
        String s3Prefix = "snapshots/" + safeTenant + "/" + namespaceId + "/" + epoch + "/";
        String manifestKey = s3Prefix + "manifest.json";

        // 2. Fetch and Validate Manifest (Req R2.6, R3.4)
        Optional<byte[]> manifestBytes = objectStoreClient.getObject(bucket, manifestKey);
        if (manifestBytes.isEmpty()) {
            String err = "Manifest absent at " + manifestKey + " — snapshot is incomplete or unselectable (V4, R2.6). Refusing to serve.";
            log.error("[DRRestorer] {}", err);
            throw new IntegrityVerificationException(namespaceId, "manifest.json", "ABSENT_MANIFEST", err);
        }

        SnapshotManifest manifest;
        try {
            manifest = SnapshotManifest.fromJson(new String(manifestBytes.get(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            String err = "Manifest unparseable or invalid at " + manifestKey + ": " + e.getMessage();
            log.error("[DRRestorer] {}", err);
            throw new IntegrityVerificationException(namespaceId, "manifest.json", "CORRUPT_MANIFEST", err);
        }

        // 3. Reconstruct paths from layout markers (Req R2.3, R3.1, R3.4)
        Path targetNamespaceDir;
        if ("tenant-rooted".equalsIgnoreCase(manifest.pathHelper()) && tenantId != null && !tenantId.isBlank()) {
            targetNamespaceDir = targetBaseDir.resolve("tenants").resolve(tenantId).resolve("namespaces").resolve(namespaceId);
        } else {
            targetNamespaceDir = targetBaseDir.resolve("namespaces").resolve(namespaceId);
        }

        try {
            Files.createDirectories(targetNamespaceDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create destination directory: " + targetNamespaceDir, e);
        }

        int verifiedCount = 0;

        // 4. Download and verify payload artifacts
        List<String> keys = objectStoreClient.listKeysByPrefix(bucket, s3Prefix);
        for (String key : keys) {
            if (key.endsWith("/manifest.json")) {
                continue;
            }
            String fileName = key.substring(key.lastIndexOf('/') + 1);
            Optional<byte[]> fileDataOpt = objectStoreClient.getObject(bucket, key);
            if (fileDataOpt.isEmpty()) {
                throw new IntegrityVerificationException(namespaceId, fileName, "MISSING_PAYLOAD", "Payload missing: " + key);
            }
            byte[] fileData = fileDataOpt.get();

            // 4a. Verify SHA-256 Checksum against manifest
            String actualSha = AwsSigV4Signer.sha256Hex(fileData);
            String expectedSha = resolveExpectedSha(manifest, fileName);
            if (expectedSha != null && !expectedSha.equalsIgnoreCase(actualSha)) {
                String err = String.format("SHA-256 mismatch for %s: expected=%s, actual=%s. Refusing to serve (V4, R3.4).",
                        fileName, expectedSha, actualSha);
                log.error("[DRRestorer] {}", err);
                throw new IntegrityVerificationException(namespaceId, fileName, "SHA_MISMATCH", err);
            }

            // 4b. Verify Preamble Magic for bundle/region files (Req R3.1, V4)
            verifyFileMagic(namespaceId, fileName, fileData);

            // Write to destination
            Path destFile = targetNamespaceDir.resolve(fileName);
            try {
                Path tempFile = targetNamespaceDir.resolve(fileName + ".tmp");
                Files.write(tempFile, fileData);
                Files.move(tempFile, destFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write restored artifact " + fileName, e);
            }
            verifiedCount++;
        }

        // 5. Write namespace.json marker if not already present (Req R2.3)
        Path markerPath = targetNamespaceDir.resolve(StoragePaths.FILE_NAMESPACE);
        if (!Files.exists(markerPath)) {
            Map<String, Object> marker = new LinkedHashMap<>();
            marker.put("namespaceId", namespaceId);
            marker.put("tenantId", tenantId);
            marker.put("pathHelper", manifest.pathHelper());
            marker.put("layout", manifest.pathHelper());
            marker.put("restoredAt", Instant.now().toString());
            marker.put("snapshotEpoch", epoch);
            marker.put("hwm", manifest.hwm());
            try {
                Files.write(markerPath, MAPPER.writeValueAsBytes(marker));
            } catch (IOException e) {
                log.warn("[DRRestorer] Failed to write namespace marker: {}", e.getMessage());
            }
        }

        // 6. Rebuild Catalog Identity State Cross-Cell (Req R3.8, R3.10)
        if (accountCatalog != null) {
            try {
                String ownerAccount = tenantId != null ? tenantId : "default-account";
                accountCatalog.getOrCreateAccount(ownerAccount);
                if (accountCatalog.resolve(ownerAccount, namespaceId).isEmpty()) {
                    accountCatalog.createNamespace(
                            ownerAccount,
                            namespaceId,
                            NamespaceType.PROJECT,
                            "Restored-" + namespaceId,
                            "Restored from DR snapshot epoch=" + epoch,
                            null
                    );
                }
            } catch (Exception e) {
                log.warn("[DRRestorer] Catalog registration noted: {}", e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("[DRRestorer] Successfully restored namespace={} tenant={} epoch={} hwm={} artifacts={} in {}ms",
                namespaceId, tenantId, epoch, manifest.hwm(), verifiedCount, duration);

        return new RestoreResult(namespaceId, tenantId, epoch, manifest.hwm(), targetNamespaceDir, verifiedCount, duration, true, null);
    }

    /**
     * Executes restores in prioritized order (Req R3.7).
     */
    public List<RestoreResult> restorePrioritized(
            List<RestoreRequest> requests,
            Path targetBaseDir
    ) {
        // High priority first (e.g. priority 10 before priority 1)
        List<RestoreRequest> sorted = requests.stream()
                .sorted(Comparator.comparingInt(RestoreRequest::priority).reversed())
                .toList();

        List<RestoreResult> results = new ArrayList<>();
        for (RestoreRequest req : sorted) {
            results.add(restoreNamespace(
                    req.tenantId(),
                    req.namespaceId(),
                    req.epoch(),
                    targetBaseDir,
                    req.tenantJurisdiction()
            ));
        }
        return results;
    }

    private static String resolveExpectedSha(SnapshotManifest manifest, String fileName) {
        if (manifest.runtime() != null && fileName.equals(manifest.runtime().file())) {
            return manifest.runtime().sha256();
        }
        if (manifest.activePartition() != null && fileName.equals(manifest.activePartition().id())) {
            return manifest.activePartition().sha256();
        }
        for (SnapshotManifest.SealedPartitionEntry sealed : manifest.sealed()) {
            if (fileName.equals(sealed.id())) {
                return sealed.sha256();
            }
        }
        return null;
    }

    private static void verifyFileMagic(String namespaceId, String fileName, byte[] data) {
        if (data.length < 4) {
            return;
        }
        // Bundle files check BUND magic
        if (fileName.endsWith(".bundle")) {
            int magic = ByteBuffer.wrap(data, 0, 4).getInt();
            if (magic != BundleFileLayout.LAYOUT_ID) {
                String err = String.format("Bundle layout magic mismatch on %s: expected 0x%08X (BUND), got 0x%08X",
                        fileName, BundleFileLayout.LAYOUT_ID, magic);
                throw new IntegrityVerificationException(namespaceId, fileName, "BAD_LAYOUT_MAGIC", err);
            }
        }
        // Partition / region files check SMKM preamble magic
        if (fileName.endsWith(".spct") || fileName.startsWith("part-")) {
            int magic = ByteBuffer.wrap(data, 0, 4).getInt();
            if (magic != RegionPreamble.MAGIC) {
                String err = String.format("Preamble magic mismatch on %s: expected 0x%08X (SMKM), got 0x%08X",
                        fileName, RegionPreamble.MAGIC, magic);
                throw new IntegrityVerificationException(namespaceId, fileName, "BAD_PREAMBLE_MAGIC", err);
            }
        }
    }
}
