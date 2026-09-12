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
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.config.dr.DisasterRecoveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Background disaster recovery exporter for active, mutable cognitive namespaces
 * (ADR-0034 §11.2, §14, §16, Phase 6, Req R2.1–R2.12).
 *
 * <p>Key Invariants:
 * <ul>
 *   <li>V1: RPO is the measured mutable-snapshot export interval (R2.1, G29).</li>
 *   <li>V5: Export never blocks the write path; throttled via BandwidthThrottler (R2.5, R2.7).</li>
 *   <li>V6: Residency verified before upload — strictly refuses on mismatch (R9.3).</li>
 *   <li>R2.4, R2.6: Payload objects uploaded first; {@code manifest.json} uploaded LAST for atomic visibility.</li>
 *   <li>G27: Traverses directory tree recursively and preserves relative sub-paths in S3 keys.</li>
 *   <li>G28: Enumerates all uploaded payload files into the manifest; refuses unverified fallback synthesis.</li>
 *   <li>G35: Employs immutable export run prefixes {@code {epoch}-{runId}/} to eliminate in-place mutation.</li>
 *   <li>R2.9: Alerts when export lag exceeds multiple of {@code drExportInterval}.</li>
 *   <li>R2.10: Unhandled/failed namespaces explicitly recorded and auditable.</li>
 * </ul>
 * </p>
 */
public class DisasterRecoveryExporter {

    private static final Logger log = LoggerFactory.getLogger(DisasterRecoveryExporter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectStoreClient objectStoreClient;
    private final DisasterRecoveryProperties properties;
    private final String bucket;
    private final AccountCatalog accountCatalog;

    private final Map<String, Long> lastExportTimestampMs = new ConcurrentHashMap<>();
    private final Map<String, Long> lastExportHwm = new ConcurrentHashMap<>();
    private final Map<String, String> unhandledNamespaces = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<Long> measuredIntervalsSec = new ConcurrentLinkedDeque<>();
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

    public record ActiveNamespaceTarget(
            String tenantId,
            String namespaceId,
            Path directory,
            String tenantJurisdiction,
            long epoch,
            long currentHwm
    ) {}

    public DisasterRecoveryExporter(
            ObjectStoreClient objectStoreClient,
            DisasterRecoveryProperties properties,
            String bucket
    ) {
        this(objectStoreClient, properties, bucket, null);
    }

    public DisasterRecoveryExporter(
            ObjectStoreClient objectStoreClient,
            DisasterRecoveryProperties properties,
            String bucket,
            AccountCatalog accountCatalog
    ) {
        this.objectStoreClient = Objects.requireNonNull(objectStoreClient, "objectStoreClient must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.bucket = bucket != null && !bucket.isBlank() ? bucket : properties.getObjectStoreBucket();
        this.accountCatalog = accountCatalog;
    }

    /**
     * Executes a scheduled export cycle across active targets (Req R2.1, G29).
     * Automatically skips idle namespaces whose HWM has not advanced since the last export.
     */
    public List<ExportResult> exportCycle(List<ActiveNamespaceTarget> targets) {
        long now = System.currentTimeMillis();
        List<ExportResult> results = new ArrayList<>();
        if (targets == null || targets.isEmpty()) {
            return results;
        }

        for (ActiveNamespaceTarget target : targets) {
            Long lastHwm = lastExportHwm.get(target.namespaceId());
            // G29: Skip idle namespaces where HWM has not advanced
            if (lastHwm != null && target.currentHwm() <= lastHwm) {
                log.debug("[DRExporter] Namespace {} is idle (hwm={}, lastExportHwm={}), skipping export",
                        target.namespaceId(), target.currentHwm(), lastHwm);
                continue;
            }

            Long lastTime = lastExportTimestampMs.get(target.namespaceId());
            if (lastTime != null) {
                long intervalSec = Math.max(0, (now - lastTime) / 1000L);
                recordExportInterval(target.namespaceId(), intervalSec);
            }

            ExportResult result = exportNamespace(
                    target.directory(),
                    target.tenantId(),
                    target.namespaceId(),
                    target.tenantJurisdiction(),
                    target.epoch(),
                    target.currentHwm()
            );
            results.add(result);
        }
        return results;
    }

    /**
     * Records a measured export interval into the operational RPO tracking reservoir (G29).
     */
    public void recordExportInterval(String namespaceId, long intervalSec) {
        measuredIntervalsSec.add(intervalSec);
        while (measuredIntervalsSec.size() > 1000) {
            measuredIntervalsSec.poll();
        }
        log.info("[DRExporter] Measured export interval for namespace {}: {}s (metric: spector.dr.export.interval)",
                namespaceId, intervalSec);
    }

    /**
     * Computes the measured 99th percentile RPO in seconds (G29).
     * Returns empty when no export intervals have been measured yet.
     */
    public OptionalDouble getMeasuredP99RpoSeconds() {
        List<Long> intervals = new ArrayList<>(measuredIntervalsSec);
        if (intervals.isEmpty()) {
            return OptionalDouble.empty();
        }
        Collections.sort(intervals);
        int p99Index = (int) Math.ceil(0.99 * intervals.size()) - 1;
        p99Index = Math.max(0, Math.min(p99Index, intervals.size() - 1));
        return OptionalDouble.of(intervals.get(p99Index));
    }

    public OptionalLong getMeasuredP99Rpo() {
        OptionalDouble p99 = getMeasuredP99RpoSeconds();
        return p99.isPresent() ? OptionalLong.of(Math.round(p99.getAsDouble())) : OptionalLong.empty();
    }

    public int getMeasuredIntervalCount() {
        return measuredIntervalsSec.size();
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

        // G31: Resolve effective jurisdiction from configuration if not explicitly provided
        String effectiveJurisdiction = tenantJurisdiction;
        if ((effectiveJurisdiction == null || effectiveJurisdiction.isBlank()) && properties != null && tenantId != null) {
            effectiveJurisdiction = properties.getTenantJurisdictions().get(tenantId);
        }
        boolean requireExplicitJurisdiction = properties != null && properties.isRequireTenantJurisdiction();

        // 1. Data Residency Validation (Req R9.3, V6, G31)
        try {
            DataResidencyEnforcer.validateExportResidency(effectiveJurisdiction, objectStoreClient.getRegion(), requireExplicitJurisdiction);
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
        // G35: Immutable export directory per run: snapshots/{tenant}/{namespace}/{epoch}-{runId}/
        String runId = Long.toHexString(System.currentTimeMillis()) + "-" + UUID.randomUUID().toString().substring(0, 8);
        String s3Prefix = "snapshots/" + safeTenant + "/" + namespaceId + "/" + epoch + "-" + runId + "/";

        try {
            // 3. Collect payload files recursively, preserving relative paths (G27, Req R2.2, R2.6)
            List<Path> payloadFiles;
            try (Stream<Path> stream = Files.walk(namespaceDir)) {
                payloadFiles = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> !p.getFileName().toString().equals(StoragePaths.FILE_LOCK))
                        .filter(p -> !p.getFileName().toString().equals("manifest.json"))
                        .filter(p -> !p.getFileName().toString().equals("restore.failed"))
                        .filter(p -> !p.getFileName().toString().endsWith(".tmp"))
                        .toList();
            }

            if (payloadFiles.isEmpty()) {
                String err = "Namespace directory is empty: " + namespaceDir;
                recordFailure(namespaceId, err);
                return new ExportResult(namespaceId, tenantId, epoch, hwm, s3Prefix, 0, false, err);
            }

            SnapshotManifest.RuntimeEntry runtimeEntry = null;
            SnapshotManifest.ActivePartitionEntry activePartitionEntry = null;
            List<SnapshotManifest.SealedPartitionEntry> sealedEntries = new ArrayList<>();
            List<SnapshotManifest.FileEntry> fileEntries = new ArrayList<>();

            // 4. Upload payload files FIRST (Req R2.4)
            int uploadedPayloadCount = 0;
            for (Path file : payloadFiles) {
                String relPath = namespaceDir.relativize(file).toString().replace('\\', '/');
                String fileName = file.getFileName().toString();
                byte[] data = Files.readAllBytes(file);
                String sha256 = AwsSigV4Signer.sha256Hex(data);

                // Identify role and verify format
                if (fileName.endsWith(".bundle") || fileName.equals("runtime.dat") || relPath.startsWith(StoragePaths.DIR_RUNTIME + "/")) {
                    runtimeEntry = new SnapshotManifest.RuntimeEntry(relPath, sha256, 1L);
                } else if (fileName.startsWith("active-") || fileName.startsWith("part-active")) {
                    activePartitionEntry = new SnapshotManifest.ActivePartitionEntry(relPath, sha256);
                } else if (fileName.endsWith(".spct") || fileName.startsWith("sealed-")) {
                    sealedEntries.add(new SnapshotManifest.SealedPartitionEntry(relPath, sha256, null));
                }

                // G28: Add every uploaded object to generic file entries
                fileEntries.add(new SnapshotManifest.FileEntry(relPath, sha256));

                String s3Key = s3Prefix + relPath;
                objectStoreClient.putObject(bucket, s3Key, data, "application/octet-stream", Map.of(
                        "namespace-id", namespaceId,
                        "sha256", sha256
                ));
                uploadedPayloadCount++;
            }

            // G32: Extract authoritative catalog metadata if available
            String namespaceMetadataJson = null;
            if (accountCatalog != null) {
                try {
                    Optional<NamespaceRecord> recordOpt = accountCatalog.resolve(safeTenant, namespaceId);
                    if (recordOpt.isPresent()) {
                        namespaceMetadataJson = MAPPER.writeValueAsString(recordOpt.get());
                    }
                } catch (Exception e) {
                    log.warn("[DRExporter] Could not resolve catalog record for {}: {}", namespaceId, e.getMessage());
                }
            }

            // 5. Build Phase 3 SnapshotManifest (Req R2.2, R2.6, G28, G32)
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
                    null,
                    fileEntries,
                    namespaceMetadataJson
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

            log.info("[DRExporter] Exported namespace={} tenant={} epoch={} hwm={} payloadCount={} prefix={}",
                    namespaceId, tenantId, epoch, hwm, uploadedPayloadCount, s3Prefix);

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
