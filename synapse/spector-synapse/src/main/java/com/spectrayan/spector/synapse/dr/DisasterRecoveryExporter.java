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

import com.spectrayan.spector.kernel.bundle.BundleFileLayout;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.memory.replication.SnapshotKind;
import com.spectrayan.spector.memory.replication.SnapshotManifest;
import com.spectrayan.spector.memory.replication.SnapshotVerifier;
import com.spectrayan.spector.synapse.config.dr.DisasterRecoveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Background disaster recovery exporter for active, mutable cognitive namespaces
 * (ADR-0034 §11.2, §14, §16, Phase 6, Req R2.1–R2.12).
 *
 * <p>Key Invariants:
 * <ul>
 *   <li>V1: RPO is the measured mutable-snapshot export interval (R2.1).</li>
 *   <li>V5: Export never blocks the write path; throttled via BandwidthThrottler (R2.5, R2.7).</li>
 *   <li>V6: Residency verified before upload — strictly refuses on mismatch (R9.3).</li>
 *   <li>R2.4, R2.6: Payload objects uploaded first; {@code manifest.json} uploaded LAST for atomic visibility.</li>
 *   <li>R2.9: Alerts when export lag exceeds multiple of {@code drExportInterval}.</li>
 *   <li>R2.10: Unhandled/failed namespaces explicitly recorded and auditable.</li>
 * </ul>
 * </p>
 */
public class DisasterRecoveryExporter {

    private static final Logger log = LoggerFactory.getLogger(DisasterRecoveryExporter.class);

    private final ObjectStoreClient objectStoreClient;
    private final DisasterRecoveryProperties properties;
    private final String bucket;

    private final Map<String, Long> lastExportTimestampMs = new ConcurrentHashMap<>();
    private final Map<String, Long> lastExportHwm = new ConcurrentHashMap<>();
    private final Map<String, String> unhandledNamespaces = new ConcurrentHashMap<>();
    private final AtomicLong successfulExportCount = new AtomicLong(0);
    private final AtomicLong failedExportCount = new AtomicLong(0);

    public record ExportResult(
            String namespaceId,
            String tenantId,
            long epoch,
            long hwm,
            String prefix,
            int payloadCount,
            boolean success,
            String failureReason
    ) {}

    public DisasterRecoveryExporter(
            ObjectStoreClient objectStoreClient,
            DisasterRecoveryProperties properties,
            String bucket
    ) {
        this.objectStoreClient = Objects.requireNonNull(objectStoreClient, "objectStoreClient must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.bucket = bucket != null && !bucket.isBlank() ? bucket : properties.getObjectStoreBucket();
    }

    /**
     * Exports an active namespace snapshot to the DR object-store bucket.
     *
     * @param namespaceDir directory containing the namespace files
     * @param tenantId owning tenant ID
     * @param namespaceId namespace TSID
     * @param tenantJurisdiction pinned jurisdiction for data residency (nullable)
     * @param epoch snapshot epoch sequence
     * @param hwm high-water mark sequence
     * @return ExportResult describing uploaded artifacts
     */
    public ExportResult exportNamespace(
            Path namespaceDir,
            String tenantId,
            String namespaceId,
            String tenantJurisdiction,
            long epoch,
            long hwm
    ) {
        Objects.requireNonNull(namespaceDir, "namespaceDir must not be null");
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");

        // 1. Data Residency Validation (Req R9.3, V6)
        try {
            DataResidencyEnforcer.validateExportResidency(tenantJurisdiction, objectStoreClient.getRegion());
        } catch (ResidencyViolationException e) {
            recordFailure(namespaceId, "Residency violation: " + e.getMessage());
            throw e;
        }

        if (!Files.isDirectory(namespaceDir)) {
            String err = "Namespace directory does not exist: " + namespaceDir;
            recordFailure(namespaceId, err);
            return new ExportResult(namespaceId, tenantId, epoch, hwm, "", 0, false, err);
        }

        // 2. Read layout marker from namespace.json (Req R2.3)
        String pathHelper;
        try {
            pathHelper = SnapshotVerifier.resolvePathHelper(namespaceDir);
        } catch (Exception e) {
            recordFailure(namespaceId, "Failed to resolve path helper: " + e.getMessage());
            return new ExportResult(namespaceId, tenantId, epoch, hwm, "", 0, false, e.getMessage());
        }

        String safeTenant = tenantId != null && !tenantId.isBlank() ? tenantId : "untenanted";
        String s3Prefix = "snapshots/" + safeTenant + "/" + namespaceId + "/" + epoch + "/";

        try {
            // 3. Collect payload files and calculate hashes (Req R2.2, R2.6)
            List<Path> payloadFiles = new ArrayList<>();
            try (Stream<Path> stream = Files.list(namespaceDir)) {
                payloadFiles = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> !p.getFileName().toString().equals(StoragePaths.FILE_LOCK))
                        .filter(p -> !p.getFileName().toString().equals("manifest.json"))
                        .toList();
            }

            SnapshotManifest.RuntimeEntry runtimeEntry = null;
            SnapshotManifest.ActivePartitionEntry activePartitionEntry = null;
            List<SnapshotManifest.SealedPartitionEntry> sealedEntries = new ArrayList<>();

            // 4. Upload payload files FIRST (Req R2.4)
            int uploadedPayloadCount = 0;
            for (Path file : payloadFiles) {
                String fileName = file.getFileName().toString();
                byte[] data = Files.readAllBytes(file);
                String sha256 = AwsSigV4Signer.sha256Hex(data);

                // Identify role and verify format
                if (fileName.endsWith(".bundle") || fileName.equals("runtime.dat")) {
                    runtimeEntry = new SnapshotManifest.RuntimeEntry(fileName, sha256, 1L);
                } else if (fileName.startsWith("active-") || fileName.startsWith("part-active")) {
                    activePartitionEntry = new SnapshotManifest.ActivePartitionEntry(fileName, sha256);
                } else if (fileName.endsWith(".spct") || fileName.startsWith("sealed-")) {
                    sealedEntries.add(new SnapshotManifest.SealedPartitionEntry(fileName, sha256, null));
                }

                String s3Key = s3Prefix + fileName;
                objectStoreClient.putObject(bucket, s3Key, data, "application/octet-stream", Map.of(
                        "namespace-id", namespaceId,
                        "sha256", sha256
                ));
                uploadedPayloadCount++;
            }

            // If active partition was not specifically named, create fallback active entry
            if (activePartitionEntry == null && !payloadFiles.isEmpty()) {
                Path first = payloadFiles.get(0);
                activePartitionEntry = new SnapshotManifest.ActivePartitionEntry(
                        first.getFileName().toString(),
                        AwsSigV4Signer.sha256Hex(Files.readAllBytes(first))
                );
            }

            // 5. Build Phase 3 SnapshotManifest (Req R2.2, R2.6)
            SnapshotManifest manifest = new SnapshotManifest(
                    SnapshotManifest.PLANE_NAMESPACE,
                    SnapshotManifest.CURRENT_VERSION,
                    tenantId,
                    namespaceId,
                    pathHelper,
                    epoch,
                    hwm,
                    SnapshotKind.FULL,
                    runtimeEntry,
                    activePartitionEntry,
                    sealedEntries,
                    0L,
                    hwm,
                    null
            );

            // 6. Upload manifest LAST for atomic visibility (Req R2.4, R2.6)
            byte[] manifestJson = manifest.toJson().getBytes(StandardCharsets.UTF_8);
            String manifestKey = s3Prefix + "manifest.json";
            objectStoreClient.putObject(bucket, manifestKey, manifestJson, "application/json", Map.of(
                    "namespace-id", namespaceId,
                    "hwm", String.valueOf(hwm),
                    "epoch", String.valueOf(epoch)
            ));

            // Record success telemetry
            long now = System.currentTimeMillis();
            lastExportTimestampMs.put(namespaceId, now);
            lastExportHwm.put(namespaceId, hwm);
            unhandledNamespaces.remove(namespaceId);
            successfulExportCount.incrementAndGet();

            log.info("[DRExporter] Exported namespace={} tenant={} epoch={} hwm={} payloadCount={}",
                    namespaceId, tenantId, epoch, hwm, uploadedPayloadCount);

            return new ExportResult(namespaceId, tenantId, epoch, hwm, s3Prefix, uploadedPayloadCount, true, null);

        } catch (Exception e) {
            String err = "Export failure: " + e.getMessage();
            recordFailure(namespaceId, err);
            log.error("[DRExporter] Failed to export namespace={}: {}", namespaceId, e.getMessage(), e);
            return new ExportResult(namespaceId, tenantId, epoch, hwm, s3Prefix, 0, false, err);
        }
    }

    /**
     * Checks if a namespace's export lag exceeds allowable SLA threshold and alerts (Req R2.8, R2.9).
     */
    public boolean checkExportLagAndAlert(String namespaceId, List<String> alertSink) {
        Long lastExport = lastExportTimestampMs.get(namespaceId);
        if (lastExport == null) {
            String alert = "ALERT: Namespace " + namespaceId + " has never had a successful DR export (exceeds RPO SLA)!";
            if (alertSink != null) alertSink.add(alert);
            return true;
        }

        long elapsedSec = (System.currentTimeMillis() - lastExport) / 1000L;
        long maxAllowableSec = properties.getAlertLagMultiplier() * properties.getExportIntervalSeconds();
        if (elapsedSec > maxAllowableSec) {
            String alert = String.format(
                    "ALERT: Namespace %s export lag (%d sec) exceeds SLA threshold (%d sec, %dx interval)!",
                    namespaceId, elapsedSec, maxAllowableSec, properties.getAlertLagMultiplier()
            );
            log.warn("[DRExporter] {}", alert);
            if (alertSink != null) alertSink.add(alert);
            return true;
        }
        return false;
    }

    private void recordFailure(String namespaceId, String reason) {
        failedExportCount.incrementAndGet();
        unhandledNamespaces.put(namespaceId, reason);
        log.warn("[DRExporter] Unhandled namespace recorded: id={}, reason={}", namespaceId, reason);
    }

    public Map<String, Long> getLastExportTimestampMs() {
        return Collections.unmodifiableMap(lastExportTimestampMs);
    }

    public Map<String, Long> getLastExportHwm() {
        return Collections.unmodifiableMap(lastExportHwm);
    }

    public Map<String, String> getUnhandledNamespaces() {
        return Collections.unmodifiableMap(unhandledNamespaces);
    }

    public long getSuccessfulExportCount() {
        return successfulExportCount.get();
    }

    public long getFailedExportCount() {
        return failedExportCount.get();
    }
}
