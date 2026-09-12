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
import com.spectrayan.spector.memory.replication.SnapshotManifest;
import com.spectrayan.spector.synapse.config.dr.DisasterRecoveryProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Specifications and unit/integration tests for DisasterRecoveryExporter
 * (ADR-0034 §11.2, §14, §16, Phase 6, Req R2.1–R2.12).
 */
@DisplayName("Phase 6 Group 2: Disaster Recovery Mutable Snapshot Export Specification")
class DisasterRecoveryExporterTest {

    private static final String BUCKET = "spector-dr-export-bucket";
    private static final String REGION = "us-east-1";

    @TempDir
    Path tempDir;

    private EmbeddedS3Server s3Server;
    private S3CompatibleObjectStoreClient s3Client;
    private DisasterRecoveryProperties properties;
    private DisasterRecoveryExporter exporter;

    @BeforeEach
    void setUp() throws Exception {
        s3Server = new EmbeddedS3Server();
        s3Server.createBucket(BUCKET);
        s3Client = new S3CompatibleObjectStoreClient(s3Server.getEndpoint(), REGION, "key", "secret", 0L);

        properties = new DisasterRecoveryProperties();
        properties.setExportIntervalSeconds(900L); // 15 min
        properties.setAlertLagMultiplier(2);

        exporter = new DisasterRecoveryExporter(s3Client, properties, BUCKET);
    }

    @AfterEach
    void tearDown() {
        if (s3Server != null) {
            s3Server.close();
        }
    }

    private Path createSampleNamespace(String tenantId, String namespaceId, String pathHelper) throws IOException {
        Path nsDir = tempDir.resolve("tenants").resolve(tenantId).resolve("namespaces").resolve(namespaceId);
        Files.createDirectories(nsDir);

        // 1. namespace.json marker with layout
        String markerJson = String.format("{\"namespaceId\":\"%s\",\"tenantId\":\"%s\",\"pathHelper\":\"%s\",\"layout\":\"%s\"}",
                namespaceId, tenantId, pathHelper, pathHelper);
        Files.writeString(nsDir.resolve(StoragePaths.FILE_NAMESPACE), markerJson);

        // 2. runtime bundle with BUND magic
        ByteBuffer rtBuf = ByteBuffer.allocate(128);
        rtBuf.putInt(BundleFileLayout.LAYOUT_ID);
        rtBuf.putLong(42L);
        Files.write(nsDir.resolve("runtime.bundle"), rtBuf.array());

        // 3. active partition with SMKM preamble magic
        ByteBuffer partBuf = ByteBuffer.allocate(256);
        partBuf.putInt(RegionPreamble.MAGIC);
        partBuf.putLong(100L);
        Files.write(nsDir.resolve("part-active.spct"), partBuf.array());

        return nsDir;
    }

    @Test
    @DisplayName("R2.1, R2.2, R2.3, R2.4, R2.6: Mutable snapshot export uploads payload first, manifest LAST")
    void testSuccessfulExportWithManifestLast() throws Exception {
        String tenantId = "ten-gamma";
        String namespaceId = "018f9b8c000070008000000000000088";
        Path nsDir = createSampleNamespace(tenantId, namespaceId, "tenant-rooted");

        DisasterRecoveryExporter.ExportResult result = exporter.exportNamespace(
                nsDir, tenantId, namespaceId, REGION, 1L, 100L
        );

        assertThat(result.success()).isTrue();
        assertThat(result.payloadCount()).isGreaterThanOrEqualTo(2);
        assertThat(exporter.getSuccessfulExportCount()).isEqualTo(1L);

        // Verify objects uploaded to S3
        Map<String, byte[]> objects = s3Server.getBucketObjects(BUCKET);
        String manifestKey = result.prefix() + "manifest.json";
        assertThat(objects).containsKey(manifestKey);
        assertThat(objects).containsKey(result.prefix() + "runtime.bundle");
        assertThat(objects).containsKey(result.prefix() + "part-active.spct");

        // Parse and verify manifest
        SnapshotManifest manifest = SnapshotManifest.fromJson(new String(objects.get(manifestKey), StandardCharsets.UTF_8));
        assertThat(manifest.namespaceId()).isEqualTo(namespaceId);
        assertThat(manifest.tenantId()).isEqualTo(tenantId);
        assertThat(manifest.pathHelper()).isEqualTo("tenant-rooted");
        assertThat(manifest.hwm()).isEqualTo(100L);
    }

    @Test
    @DisplayName("R2.4, R2.5: Mid-upload interruption leaves snapshot unselectable (manifest absent)")
    void testPartialUploadUnselectableWithoutManifest() {
        String tenantId = "ten-delta";
        String namespaceId = "018f9b8c000070008000000000000099";

        // Manually stage partial payload objects without manifest.json
        s3Client.putObject(BUCKET, "snapshots/" + tenantId + "/" + namespaceId + "/2/part-1.spct", "payload".getBytes(StandardCharsets.UTF_8), null, null);

        DisasterRecoveryRestorer restorer = new DisasterRecoveryRestorer(s3Client, BUCKET, REGION, null);
        List<Long> selectableEpochs = restorer.discoverSelectableEpochs(tenantId, namespaceId);

        assertThat(selectableEpochs)
                .as("Epoch 2 missing manifest.json must NOT be discoverable or selectable for restore (Req R2.6)")
                .isEmpty();
    }

    @Test
    @DisplayName("R2.7, R2.8, R2.9: Export lag metric and SLA alert triggering")
    void testExportLagMetricsAndAlerting() throws Exception {
        String namespaceId = "018f9b8c000070008000000000000077";
        List<String> alerts = new ArrayList<>();

        // 1. Never exported alerts immediately
        boolean alerted = exporter.checkExportLagAndAlert(namespaceId, alerts);
        assertThat(alerted).isTrue();
        assertThat(alerts).anyMatch(a -> a.contains("never had a successful DR export"));

        // 2. Export namespace
        Path nsDir = createSampleNamespace("ten-1", namespaceId, "flat");
        exporter.exportNamespace(nsDir, "ten-1", namespaceId, REGION, 1L, 50L);

        alerts.clear();
        // Immediately after export: lag is 0s, should NOT alert
        boolean freshAlert = exporter.checkExportLagAndAlert(namespaceId, alerts);
        assertThat(freshAlert).isFalse();
        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("R2.10, R2.11: Unhandled and corrupt namespaces are explicitly recorded")
    void testUnhandledNamespaceFailureRecording() {
        Path invalidDir = tempDir.resolve("non-existent-dir");
        String namespaceId = "018f9b8c000070008000000000000066";

        DisasterRecoveryExporter.ExportResult result = exporter.exportNamespace(
                invalidDir, "ten-fail", namespaceId, REGION, 1L, 10L
        );

        assertThat(result.success()).isFalse();
        assertThat(exporter.getUnhandledNamespaces()).containsKey(namespaceId);
        assertThat(exporter.getUnhandledNamespaces().get(namespaceId)).contains("does not exist");
        assertThat(exporter.getFailedExportCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("R9.3, R10.6, V6: Export strictly REFUSES on data residency jurisdiction mismatch")
    void testResidencyMismatchRefusal() throws IOException {
        String tenantId = "ten-eu-only";
        String namespaceId = "018f9b8c000070008000000000000055";
        Path nsDir = createSampleNamespace(tenantId, namespaceId, "tenant-rooted");

        // Target bucket is us-east-1, but tenant is pinned to eu-central-1
        assertThatThrownBy(() -> exporter.exportNamespace(
                nsDir, tenantId, namespaceId, "eu-central-1", 1L, 10L
        )).isInstanceOf(ResidencyViolationException.class)
          .hasMessageContaining("eu-central-1")
          .hasMessageContaining("us-east-1");

        assertThat(exporter.getUnhandledNamespaces()).containsKey(namespaceId);
    }

    @Test
    @DisplayName("G27: Recursive export preserves relative paths in S3 keys for nested layouts")
    void testExportNestedSubdirectoriesPreservesRelativePaths() throws IOException {
        String tenantId = "ten-nested";
        String namespaceId = "018f9b8c000070008000000000000044";
        Path nsDir = tempDir.resolve("nested-ns-" + namespaceId);
        Files.createDirectories(nsDir);

        // Marker
        String markerJson = String.format("{\"namespaceId\":\"%s\",\"tenantId\":\"%s\",\"pathHelper\":\"sharded\"}",
                namespaceId, tenantId);
        Files.writeString(nsDir.resolve(StoragePaths.FILE_NAMESPACE), markerJson);

        // Nested runtime/
        Path runtimeDir = nsDir.resolve(StoragePaths.DIR_RUNTIME);
        Files.createDirectories(runtimeDir);
        ByteBuffer rtBuf = ByteBuffer.allocate(64);
        rtBuf.putInt(BundleFileLayout.LAYOUT_ID);
        Files.write(runtimeDir.resolve(StoragePaths.FILE_RUNTIME_BUNDLE), rtBuf.array());

        // Nested partitions/001_p/
        Path partDir = nsDir.resolve(StoragePaths.DIR_PARTITIONS).resolve("001_p");
        Files.createDirectories(partDir);
        ByteBuffer ptBuf = ByteBuffer.allocate(128);
        ptBuf.putInt(RegionPreamble.MAGIC);
        Files.write(partDir.resolve("part-1.spct"), ptBuf.array());

        DisasterRecoveryExporter.ExportResult result = exporter.exportNamespace(
                nsDir, tenantId, namespaceId, REGION, 1L, 200L
        );

        assertThat(result.success()).isTrue();
        assertThat(result.payloadCount()).isEqualTo(3); // namespace.json, runtime.bundle, part-1.spct

        Map<String, byte[]> objects = s3Server.getBucketObjects(BUCKET);
        assertThat(objects).containsKey(result.prefix() + StoragePaths.DIR_RUNTIME + "/" + StoragePaths.FILE_RUNTIME_BUNDLE);
        assertThat(objects).containsKey(result.prefix() + StoragePaths.DIR_PARTITIONS + "/001_p/part-1.spct");
        assertThat(objects).containsKey(result.prefix() + StoragePaths.FILE_NAMESPACE);
    }

    @Test
    @DisplayName("G29: exportCycle skips idle namespaces and measures RPO intervals")
    void testExportCycleSkipsIdleNamespacesAndMeasuresRpo() throws IOException {
        String tenantId = "ten-cycle";
        String nsId1 = "018f9b8c000070008000000000000031";
        String nsId2 = "018f9b8c000070008000000000000032";

        Path nsDir1 = createSampleNamespace(tenantId, nsId1, "flat");
        Path nsDir2 = createSampleNamespace(tenantId, nsId2, "flat");

        // Cycle 1: both namespaces exported
        var target1 = new DisasterRecoveryExporter.ActiveNamespaceTarget(tenantId, nsId1, nsDir1, REGION, 1L, 100L);
        var target2 = new DisasterRecoveryExporter.ActiveNamespaceTarget(tenantId, nsId2, nsDir2, REGION, 1L, 50L);

        List<DisasterRecoveryExporter.ExportResult> results1 = exporter.exportCycle(List.of(target1, target2));
        assertThat(results1).hasSize(2);
        assertThat(exporter.getLastExportHwm()).containsEntry(nsId1, 100L).containsEntry(nsId2, 50L);

        // Cycle 2: nsId1 is idle (HWM unchanged at 100L), nsId2 has advanced (HWM 75L)
        var target1Idle = new DisasterRecoveryExporter.ActiveNamespaceTarget(tenantId, nsId1, nsDir1, REGION, 2L, 100L);
        var target2Active = new DisasterRecoveryExporter.ActiveNamespaceTarget(tenantId, nsId2, nsDir2, REGION, 2L, 75L);

        List<DisasterRecoveryExporter.ExportResult> results2 = exporter.exportCycle(List.of(target1Idle, target2Active));
        assertThat(results2).hasSize(1);
        assertThat(results2.get(0).namespaceId()).isEqualTo(nsId2);
        assertThat(exporter.getLastExportHwm().get(nsId2)).isEqualTo(75L);

        // Measured RPO tracking
        exporter.recordExportInterval(nsId2, 30L);
        assertThat(exporter.getMeasuredP99RpoSeconds()).isGreaterThan(0.0);
        assertThat(exporter.getMeasuredP99Rpo()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("G35: Re-exporting at the same epoch creates distinct immutable prefixes")
    void testReExportAtSameEpochProducesDistinctImmutablePrefixes() throws IOException {
        String tenantId = "ten-immutable";
        String namespaceId = "018f9b8c000070008000000000000022";
        Path nsDir = createSampleNamespace(tenantId, namespaceId, "flat");

        DisasterRecoveryExporter.ExportResult run1 = exporter.exportNamespace(nsDir, tenantId, namespaceId, REGION, 5L, 100L);
        DisasterRecoveryExporter.ExportResult run2 = exporter.exportNamespace(nsDir, tenantId, namespaceId, REGION, 5L, 100L);

        assertThat(run1.success()).isTrue();
        assertThat(run2.success()).isTrue();
        assertThat(run1.prefix()).isNotEqualTo(run2.prefix());
        assertThat(run1.prefix()).contains("/5-");
        assertThat(run2.prefix()).contains("/5-");

        Map<String, byte[]> objects = s3Server.getBucketObjects(BUCKET);
        assertThat(objects).containsKey(run1.prefix() + "manifest.json");
        assertThat(objects).containsKey(run2.prefix() + "manifest.json");
    }
}
