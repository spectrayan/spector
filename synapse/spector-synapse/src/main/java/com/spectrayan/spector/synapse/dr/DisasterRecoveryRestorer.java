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
import java.util.stream.Stream;

/**
 * Disaster recovery restorer that hydrates cognitive namespaces from object storage
 * into local NVMe file hierarchy (ADR-0034 §11.2, §14, §16, Phase 6, Req R3.1–R3.12).
 *
 * <p>Key Invariants:
 * <ul>
 *   <li>V4: Pre-serving integrity verification — corrupt snapshots refuse to serve (R3.4).</li>
 *   <li>V6: Data residency validation — cannot restore into non-compliant region (R9.4).</li>
 *   <li>G26: Downloads and verifies into an isolated staging directory; atomically moves into place
 *       only after complete validation; writes {@code restore.failed} sentinel on error.</li>
 *   <li>G28, G33: Iterates manifest-declared artifacts and refuses unmanifested payloads ({@code UNMANIFESTED_ARTIFACT}).</li>
 *   <li>G32: Refuses to restore erased namespaces marked with {@code .erased}; restores {@code NamespaceRecord} verbatim.</li>
 *   <li>G33: Journaled/resumable download skipping verified files; error isolation in prioritized restore.</li>
 *   <li>G35: Discovers immutable {@code {epoch}-{runId}/} snapshot directories and selects latest complete run.</li>
 * </ul>
 * </p>
 */
public class DisasterRecoveryRestorer {

    private static final Logger log = LoggerFactory.getLogger(DisasterRecoveryRestorer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

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
            long elapsedMs,
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
     * Erased namespaces are unselectable (G32).
     */
    public List<Long> discoverSelectableEpochs(String tenantId, String namespaceId) {
        String safeTenant = tenantId != null && !tenantId.isBlank() ? tenantId : "untenanted";
        String searchPrefix = "snapshots/" + safeTenant + "/" + namespaceId + "/";

        // G32: If namespace was erased, it is unselectable
        if (isNamespaceErased(safeTenant, namespaceId)) {
            log.warn("[DRRestorer] Namespace {} has been erased; discoverSelectableEpochs returning empty", namespaceId);
            return List.of();
        }

        List<String> allKeys = objectStoreClient.listKeysByPrefix(bucket, searchPrefix);
        Set<Long> epochsWithManifest = new TreeSet<>();

        for (String key : allKeys) {
            if (key.endsWith("/manifest.json")) {
                // key format: snapshots/{tenant}/{namespace}/{epoch}/manifest.json
                // or immutable format: snapshots/{tenant}/{namespace}/{epoch}-{runId}/manifest.json
                String sub = key.substring(searchPrefix.length());
                int slash = sub.indexOf('/');
                if (slash > 0) {
                    String epochSegment = sub.substring(0, slash);
                    try {
                        long epoch = epochSegment.contains("-")
                                ? Long.parseLong(epochSegment.substring(0, epochSegment.indexOf('-')))
                                : Long.parseLong(epochSegment);
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
        String searchPrefix = "snapshots/" + safeTenant + "/" + namespaceId + "/";

        // G32: Refuse restore if namespace has an erasure marker
        if (isNamespaceErased(safeTenant, namespaceId)) {
            String err = "Namespace " + namespaceId + " has been erased and cannot be restored from DR (G32).";
            log.error("[DRRestorer] {}", err);
            throw new IntegrityVerificationException(namespaceId, "erased.marker", "NAMESPACE_ALREADY_ERASED", err);
        }

        // G35: Resolve latest manifest-complete snapshot run prefix for the requested epoch
        String chosenPrefix = resolveSnapshotPrefix(searchPrefix, namespaceId, epoch);
        String manifestKey = chosenPrefix + "manifest.json";

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

        // 3. Resolve destination paths (Req R2.3, R3.1, R3.4)
        Path targetNamespaceDir;
        if ("tenant-rooted".equalsIgnoreCase(manifest.pathHelper()) && tenantId != null && !tenantId.isBlank()) {
            targetNamespaceDir = targetBaseDir.resolve("tenants").resolve(tenantId).resolve("namespaces").resolve(namespaceId);
        } else {
            targetNamespaceDir = targetBaseDir.resolve("namespaces").resolve(namespaceId);
        }

        // G26: Download and verify into an isolated staging directory
        Path stagingDir = targetNamespaceDir.resolveSibling(namespaceId + ".restoring");
        try {
            Files.createDirectories(stagingDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create staging directory: " + stagingDir, e);
        }

        int verifiedCount = 0;

        try {
            // Build map of expected artifacts from manifest (G28, G33)
            Map<String, String> expectedArtifacts = new LinkedHashMap<>();
            if (manifest.files() != null && !manifest.files().isEmpty()) {
                for (SnapshotManifest.FileEntry fe : manifest.files()) {
                    expectedArtifacts.put(fe.path(), fe.sha256());
                }
            }
            if (manifest.runtime() != null) {
                expectedArtifacts.putIfAbsent(manifest.runtime().file(), manifest.runtime().sha256());
            }
            if (manifest.activePartition() != null) {
                expectedArtifacts.putIfAbsent(manifest.activePartition().id(), manifest.activePartition().sha256());
            }
            for (SnapshotManifest.SealedPartitionEntry s : manifest.sealed()) {
                expectedArtifacts.putIfAbsent(s.id(), s.sha256());
            }

            // G33: Validate S3 contents against manifest — detect missing or unmanifested objects
            List<String> s3Keys = objectStoreClient.listKeysByPrefix(bucket, chosenPrefix);
            Set<String> s3RelPaths = new HashSet<>();
            for (String key : s3Keys) {
                if (key.endsWith("/manifest.json")) {
                    continue;
                }
                String relPath = key.substring(chosenPrefix.length());
                s3RelPaths.add(relPath);

                // G28: Check that this object is manifested
                String expectedSha = resolveExpectedSha(manifest, relPath);
                if (expectedSha == null) {
                    String err = "Unmanifested artifact found in object store: " + relPath + ". Refusing unverified payload (G28).";
                    log.error("[DRRestorer] {}", err);
                    throw new IntegrityVerificationException(namespaceId, relPath, "UNMANIFESTED_ARTIFACT", err);
                }
            }

            // Verify all manifested files are present in S3
            for (String expPath : expectedArtifacts.keySet()) {
                String expFileName = expPath.contains("/") ? expPath.substring(expPath.lastIndexOf('/') + 1) : expPath;
                boolean presentInS3 = s3RelPaths.contains(expPath) || s3RelPaths.contains(expFileName);
                if (!presentInS3) {
                    String err = "Manifested artifact missing in object store: " + expPath + ". Refusing incomplete snapshot (G33).";
                    log.error("[DRRestorer] {}", err);
                    throw new IntegrityVerificationException(namespaceId, expPath, "MISSING_PAYLOAD", err);
                }
            }

            // 4. Download and verify payload artifacts into stagingDir (G26, G33)
            for (Map.Entry<String, String> entry : expectedArtifacts.entrySet()) {
                String relPath = entry.getKey();
                String expectedSha = entry.getValue();
                String fileName = relPath.contains("/") ? relPath.substring(relPath.lastIndexOf('/') + 1) : relPath;

                Path destFile = stagingDir.resolve(relPath);
                Files.createDirectories(destFile.getParent());

                // G33: Resumability check — if already verified in staging dir, skip re-download
                if (Files.isRegularFile(destFile)) {
                    byte[] existing = Files.readAllBytes(destFile);
                    if (AwsSigV4Signer.sha256Hex(existing).equalsIgnoreCase(expectedSha)) {
                        log.debug("[DRRestorer] Artifact {} already verified in staging journal, skipping download", relPath);
                        verifiedCount++;
                        continue;
                    }
                }

                // Download from S3 (trying relative path first, then bare filename)
                Optional<byte[]> fileDataOpt = objectStoreClient.getObject(bucket, chosenPrefix + relPath);
                if (fileDataOpt.isEmpty()) {
                    fileDataOpt = objectStoreClient.getObject(bucket, chosenPrefix + fileName);
                }
                if (fileDataOpt.isEmpty()) {
                    throw new IntegrityVerificationException(namespaceId, relPath, "MISSING_PAYLOAD", "Payload missing: " + relPath);
                }
                byte[] fileData = fileDataOpt.get();

                // 4a. Verify SHA-256 Checksum against manifest
                String actualSha = AwsSigV4Signer.sha256Hex(fileData);
                if (!expectedSha.equalsIgnoreCase(actualSha)) {
                    String err = String.format("SHA-256 mismatch for %s: expected=%s, actual=%s. Refusing to serve (V4, R3.4).",
                            relPath, expectedSha, actualSha);
                    log.error("[DRRestorer] {}", err);
                    throw new IntegrityVerificationException(namespaceId, relPath, "SHA_MISMATCH", err);
                }

                // 4b. Verify Preamble Magic for bundle/region files (Req R3.1, V4)
                verifyFileMagic(namespaceId, fileName, fileData);

                // Write to destination in stagingDir
                Path tempFile = destFile.resolveSibling(destFile.getFileName().toString() + ".tmp");
                Files.write(tempFile, fileData);
                Files.move(tempFile, destFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                verifiedCount++;
            }

            // 5. Write namespace.json marker inside stagingDir before atomic promotion (Req R2.3)
            Path markerPath = stagingDir.resolve(StoragePaths.FILE_NAMESPACE);
            if (!Files.exists(markerPath)) {
                Map<String, Object> marker = new LinkedHashMap<>();
                marker.put("namespaceId", namespaceId);
                marker.put("tenantId", tenantId);
                marker.put("pathHelper", manifest.pathHelper());
                marker.put("layout", manifest.pathHelper());
                marker.put("restoredAt", Instant.now().toString());
                marker.put("snapshotEpoch", epoch);
                marker.put("hwm", manifest.hwm());
                Files.write(markerPath, MAPPER.writeValueAsBytes(marker));
            }

            // 6. Rebuild Catalog Identity State Cross-Cell (Req R3.8, R3.10, G32)
            if (accountCatalog != null) {
                try {
                    if (manifest.namespaceMetadataJson() != null && !manifest.namespaceMetadataJson().isBlank()) {
                        NamespaceRecord record = MAPPER.readValue(manifest.namespaceMetadataJson(), NamespaceRecord.class);
                        accountCatalog.getOrCreateAccount(record.ownerAccountId());
                        accountCatalog.importNamespace(record);
                        log.info("[DRRestorer] Imported authoritative catalog record for {} (slug={}, legalHold={})",
                                namespaceId, record.slug(), record.legalHold());
                    } else {
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
                    }
                } catch (Exception e) {
                    String err = "Catalog registration failed for namespace " + namespaceId + ": " + e.getMessage();
                    log.error("[DRRestorer] {}", err, e);
                    throw new IntegrityVerificationException(namespaceId, "catalog", "CATALOG_REGISTRATION_FAILED", err);
                }
            }

            // G26: Promote staging directory to live serving directory with single atomic move
            if (Files.exists(targetNamespaceDir)) {
                Path oldDir = targetNamespaceDir.resolveSibling(namespaceId + ".old." + System.currentTimeMillis());
                try {
                    Files.move(targetNamespaceDir, oldDir, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    deleteDirectoryRecursively(targetNamespaceDir);
                }
                Files.move(stagingDir, targetNamespaceDir, StandardCopyOption.ATOMIC_MOVE);
                deleteDirectoryRecursively(oldDir);
            } else {
                Files.createDirectories(targetNamespaceDir.getParent());
                Files.move(stagingDir, targetNamespaceDir, StandardCopyOption.ATOMIC_MOVE);
            }

            // Remove any legacy restore.failed sentinel if present
            Path sentinel = targetNamespaceDir.resolve("restore.failed");
            Files.deleteIfExists(sentinel);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[DRRestorer] Successfully restored namespace={} tenant={} epoch={} hwm={} artifacts={} in {}ms",
                    namespaceId, tenantId, epoch, manifest.hwm(), verifiedCount, duration);

            return new RestoreResult(namespaceId, tenantId, epoch, manifest.hwm(), targetNamespaceDir, verifiedCount, duration, true, null);

        } catch (Exception e) {
            // G26: On failure, write a restore.failed sentinel so the namespace refuses to serve
            try {
                deleteDirectoryRecursively(stagingDir);
                Files.createDirectories(targetNamespaceDir);
                Files.writeString(targetNamespaceDir.resolve("restore.failed"),
                        "Restore failed at " + Instant.now() + " for epoch=" + epoch + ": " + e.getMessage());
            } catch (Exception writeSentinelEx) {
                log.warn("[DRRestorer] Could not write restore.failed sentinel: {}", writeSentinelEx.getMessage());
            }

            if (e instanceof IntegrityVerificationException ive) {
                throw ive;
            }
            throw new RuntimeException("Disaster recovery restore failed: " + e.getMessage(), e);
        }
    }

    /**
     * Executes restores in prioritized order with per-namespace error isolation (Req R3.7, G33).
     */
    public List<RestoreResult> restorePrioritized(
            List<RestoreRequest> requests,
            Path targetBaseDir
    ) {
        // High priority first (e.g. priority 100 before priority 1)
        List<RestoreRequest> sorted = requests.stream()
                .sorted(Comparator.comparingInt(RestoreRequest::priority).reversed())
                .toList();

        List<RestoreResult> results = new ArrayList<>();
        for (RestoreRequest req : sorted) {
            try {
                results.add(restoreNamespace(
                        req.tenantId(),
                        req.namespaceId(),
                        req.epoch(),
                        targetBaseDir,
                        req.tenantJurisdiction()
                ));
            } catch (Exception e) {
                // G33: Error isolation — collect per-namespace failure and continue
                log.error("[DRRestorer] Prioritized restore failed for namespace={}: {}", req.namespaceId(), e.getMessage());
                Path failedDir = targetBaseDir.resolve("namespaces").resolve(req.namespaceId());
                results.add(new RestoreResult(
                        req.namespaceId(),
                        req.tenantId(),
                        req.epoch(),
                        0L,
                        failedDir,
                        0,
                        0L,
                        false,
                        e.getMessage()
                ));
            }
        }
        return results;
    }

    private boolean isNamespaceErased(String tenantId, String namespaceId) {
        if (objectStoreClient.getObject(bucket, "snapshots/.tombstones/" + namespaceId).isPresent()) {
            return true;
        }
        String key1 = "snapshots/" + tenantId + "/" + namespaceId + "/.erased";
        if (objectStoreClient.getObject(bucket, key1).isPresent()) {
            return true;
        }
        String key2 = "snapshots/untenanted/" + namespaceId + "/.erased";
        return objectStoreClient.getObject(bucket, key2).isPresent();
    }

    private String resolveSnapshotPrefix(String searchPrefix, String namespaceId, long epoch) {
        List<String> allKeys = objectStoreClient.listKeysByPrefix(bucket, searchPrefix);
        List<String> candidates = allKeys.stream()
                .filter(k -> k.endsWith("/manifest.json"))
                .map(k -> k.substring(searchPrefix.length()))
                .filter(sub -> {
                    int slash = sub.indexOf('/');
                    if (slash <= 0) return false;
                    String segment = sub.substring(0, slash);
                    try {
                        long ep = segment.contains("-")
                                ? Long.parseLong(segment.substring(0, segment.indexOf('-')))
                                : Long.parseLong(segment);
                        return ep == epoch;
                    } catch (NumberFormatException ignored) {
                        return false;
                    }
                })
                .map(sub -> sub.substring(0, sub.indexOf('/') + 1))
                .distinct()
                .sorted(Comparator.reverseOrder()) // latest run first
                .toList();

        if (candidates.isEmpty()) {
            String legacyPrefix = searchPrefix + epoch + "/";
            if (objectStoreClient.getObject(bucket, legacyPrefix + "manifest.json").isPresent()) {
                return legacyPrefix;
            }
            throw new IntegrityVerificationException(namespaceId, "manifest.json", "ABSENT_MANIFEST",
                    "No valid manifest found for namespace=" + namespaceId + " epoch=" + epoch);
        }
        return searchPrefix + candidates.get(0);
    }

    private static String resolveExpectedSha(SnapshotManifest manifest, String relPath) {
        if (manifest.files() != null) {
            for (SnapshotManifest.FileEntry fe : manifest.files()) {
                if (fe.path().equals(relPath) || fe.path().equals(relPath.substring(relPath.lastIndexOf('/') + 1))) {
                    return fe.sha256();
                }
            }
        }
        String fileName = relPath.contains("/") ? relPath.substring(relPath.lastIndexOf('/') + 1) : relPath;
        if (manifest.runtime() != null && (relPath.equals(manifest.runtime().file()) || fileName.equals(manifest.runtime().file()))) {
            return manifest.runtime().sha256();
        }
        if (manifest.activePartition() != null && (relPath.equals(manifest.activePartition().id()) || fileName.equals(manifest.activePartition().id()))) {
            return manifest.activePartition().sha256();
        }
        for (SnapshotManifest.SealedPartitionEntry sealed : manifest.sealed()) {
            if (relPath.equals(sealed.id()) || fileName.equals(sealed.id())) {
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

    private static void deleteDirectoryRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }
}
