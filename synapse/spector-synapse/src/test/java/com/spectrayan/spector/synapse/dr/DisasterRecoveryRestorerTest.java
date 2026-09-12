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
import com.spectrayan.spector.memory.replication.SnapshotKind;
import com.spectrayan.spector.memory.replication.SnapshotManifest;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.file.FileAccountCatalog;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Specifications and unit/integration tests for DisasterRecoveryRestorer
 * (ADR-0034 §11.2, §14, §16, Phase 6, Req R3.1–R3.12).
 */
@DisplayName("Phase 6 Group 3: Standby Rehydration & Verified Restore Specification")
class DisasterRecoveryRestorerTest {

    private static final String BUCKET = "spector-dr-restore-bucket";
    private static final String STANDBY_REGION = "us-west-2";

    @TempDir
    Path tempDir;

    private EmbeddedS3Server s3Server;
    private S3CompatibleObjectStoreClient s3Client;
    private AccountCatalog accountCatalog;
    private DisasterRecoveryRestorer restorer;

    @BeforeEach
    void setUp() throws Exception {
        s3Server = new EmbeddedS3Server();
        s3Server.createBucket(BUCKET);
        s3Client = new S3CompatibleObjectStoreClient(s3Server.getEndpoint(), STANDBY_REGION, "key", "secret", 0L);

        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        accountCatalog = new FileAccountCatalog(tempDir.resolve("catalog"), mapper);

        restorer = new DisasterRecoveryRestorer(s3Client, BUCKET, STANDBY_REGION, accountCatalog);
    }

    @AfterEach
    void tearDown() {
        if (s3Server != null) {
            s3Server.close();
        }
    }

    private void stageValidSnapshot(
            String tenantId,
            String namespaceId,
            long epoch,
            long hwm,
            String pathHelper
    ) {
        String s3Prefix = "snapshots/" + tenantId + "/" + namespaceId + "/" + epoch + "/";

        // Runtime bundle with BUND magic
        ByteBuffer rtBuf = ByteBuffer.allocate(64);
        rtBuf.putInt(BundleFileLayout.LAYOUT_ID);
        rtBuf.putLong(42L);
        byte[] rtData = rtBuf.array();
        String rtSha = AwsSigV4Signer.sha256Hex(rtData);
        s3Client.putObject(BUCKET, s3Prefix + "runtime.bundle", rtData, null, null);

        // Partition with SMKM preamble magic
        ByteBuffer ptBuf = ByteBuffer.allocate(128);
        ptBuf.putInt(RegionPreamble.MAGIC);
        ptBuf.putLong(hwm);
        byte[] ptData = ptBuf.array();
        String ptSha = AwsSigV4Signer.sha256Hex(ptData);
        s3Client.putObject(BUCKET, s3Prefix + "part-active.spct", ptData, null, null);

        // Manifest
        SnapshotManifest manifest = new SnapshotManifest(
                SnapshotManifest.PLANE_NAMESPACE,
                1,
                tenantId,
                namespaceId,
                pathHelper,
                epoch,
                hwm,
                SnapshotKind.FULL,
                new SnapshotManifest.RuntimeEntry("runtime.bundle", rtSha, 1L),
                new SnapshotManifest.ActivePartitionEntry("part-active.spct", ptSha),
                List.of(),
                0L,
                hwm,
                null
        );
        s3Client.putObject(BUCKET, s3Prefix + "manifest.json", manifest.toJson().getBytes(StandardCharsets.UTF_8), null, null);
    }

    @Test
    @DisplayName("R3.1, R3.3, R3.4, R3.6, R3.8: Clean restore verifies integrity, reconstructs paths, discloses HWM")
    void testCleanRestoreEndToEnd() {
        String tenantId = "ten-prod-01";
        String namespaceId = "018f9b8c000070008000000000000011";
        long epoch = 10L;
        long hwm = 5000L;

        stageValidSnapshot(tenantId, namespaceId, epoch, hwm, "tenant-rooted");

        Path restoreBaseDir = tempDir.resolve("standby-data");
        DisasterRecoveryRestorer.RestoreResult result = restorer.restoreNamespace(
                tenantId, namespaceId, epoch, restoreBaseDir, STANDBY_REGION
        );

        assertThat(result.success()).isTrue();
        assertThat(result.hwm()).isEqualTo(hwm);
        assertThat(result.epoch()).isEqualTo(epoch);
        assertThat(result.verifiedArtifactCount()).isEqualTo(2);

        // Verify path reconstruction: tenants/ten-prod-01/namespaces/{id}
        Path expectedDir = restoreBaseDir.resolve("tenants").resolve(tenantId).resolve("namespaces").resolve(namespaceId);
        assertThat(result.restoredDir()).isEqualTo(expectedDir);
        assertThat(Files.isDirectory(expectedDir)).isTrue();
        assertThat(Files.exists(expectedDir.resolve("runtime.bundle"))).isTrue();
        assertThat(Files.exists(expectedDir.resolve("part-active.spct"))).isTrue();
        assertThat(Files.exists(expectedDir.resolve(StoragePaths.FILE_NAMESPACE))).isTrue();

        // Verify catalog identity state rebuild cross-cell (Req R3.8, R3.10)
        assertThat(accountCatalog.resolve(tenantId, namespaceId)).isPresent();
    }

    @Test
    @DisplayName("R3.1, R3.2, V4: Corrupted SHA-256 strictly REFUSES TO SERVE")
    void testCorruptedShaRefusesToServe() {
        String tenantId = "ten-prod-02";
        String namespaceId = "018f9b8c000070008000000000000022";
        long epoch = 1L;

        stageValidSnapshot(tenantId, namespaceId, epoch, 100L, "flat");

        // Tamper with the active partition payload in S3
        String key = "snapshots/" + tenantId + "/" + namespaceId + "/" + epoch + "/part-active.spct";
        byte[] tampered = "corrupted-payload-bytes".getBytes(StandardCharsets.UTF_8);
        s3Client.putObject(BUCKET, key, tampered, null, null);

        Path restoreBaseDir = tempDir.resolve("standby-tamper");
        assertThatThrownBy(() -> restorer.restoreNamespace(
                tenantId, namespaceId, epoch, restoreBaseDir, STANDBY_REGION
        )).isInstanceOf(IntegrityVerificationException.class)
          .hasMessageContaining("SHA_MISMATCH")
          .hasMessageContaining("Refusing to serve (V4, R3.4)");
    }

    @Test
    @DisplayName("R3.1, R3.2, V4: Corrupted preamble magic ('SMKM') strictly REFUSES TO SERVE")
    void testBadPreambleMagicRefusal() {
        String tenantId = "ten-prod-03";
        String namespaceId = "018f9b8c000070008000000000000033";
        long epoch = 1L;

        String s3Prefix = "snapshots/" + tenantId + "/" + namespaceId + "/" + epoch + "/";

        // Bad magic header (0xDEADBEEF instead of SMKM)
        ByteBuffer badBuf = ByteBuffer.allocate(64);
        badBuf.putInt(0xDEADBEEF);
        badBuf.putLong(1L);
        byte[] badData = badBuf.array();
        String badSha = AwsSigV4Signer.sha256Hex(badData);
        s3Client.putObject(BUCKET, s3Prefix + "part-active.spct", badData, null, null);

        SnapshotManifest manifest = new SnapshotManifest(
                SnapshotManifest.PLANE_NAMESPACE,
                1,
                tenantId,
                namespaceId,
                "flat",
                epoch,
                1L,
                SnapshotKind.FULL,
                null,
                new SnapshotManifest.ActivePartitionEntry("part-active.spct", badSha),
                List.of(),
                0L,
                1L,
                null
        );
        s3Client.putObject(BUCKET, s3Prefix + "manifest.json", manifest.toJson().getBytes(StandardCharsets.UTF_8), null, null);

        Path restoreBaseDir = tempDir.resolve("standby-bad-magic");
        assertThatThrownBy(() -> restorer.restoreNamespace(
                tenantId, namespaceId, epoch, restoreBaseDir, STANDBY_REGION
        )).isInstanceOf(IntegrityVerificationException.class)
          .hasMessageContaining("BAD_PREAMBLE_MAGIC");
    }

    @Test
    @DisplayName("R3.7, Task 3.9: Prioritized restore ordering recovers highest priority tenants first")
    void testPrioritizedRestoreOrdering() {
        stageValidSnapshot("ten-low", "018f9b8c000070008000000000000041", 1L, 10L, "flat");
        stageValidSnapshot("ten-vip", "018f9b8c000070008000000000000042", 1L, 50L, "flat");

        List<DisasterRecoveryRestorer.RestoreRequest> requests = List.of(
                new DisasterRecoveryRestorer.RestoreRequest("ten-low", "018f9b8c000070008000000000000041", 1L, 1, STANDBY_REGION),
                new DisasterRecoveryRestorer.RestoreRequest("ten-vip", "018f9b8c000070008000000000000042", 1L, 100, STANDBY_REGION)
        );

        Path restoreBaseDir = tempDir.resolve("standby-priority");
        List<DisasterRecoveryRestorer.RestoreResult> results = restorer.restorePrioritized(requests, restoreBaseDir);

        assertThat(results).hasSize(2);
        // VIP priority 100 restored first
        assertThat(results.get(0).tenantId()).isEqualTo("ten-vip");
        assertThat(results.get(1).tenantId()).isEqualTo("ten-low");
    }

    @Test
    @DisplayName("R1.2, R3.12: Export -> Restore full round-trip against S3 / MinIO test harness")
    void testExportRestoreRoundTrip() throws IOException {
        String tenantId = "ten-roundtrip";
        String namespaceId = "018f9b8c000070008000000000000077";

        // 1. Source Cell: Create namespace and export
        Path sourceDir = tempDir.resolve("source-cell").resolve("namespaces").resolve(namespaceId);
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve(StoragePaths.FILE_NAMESPACE), "{\"namespaceId\":\"" + namespaceId + "\",\"pathHelper\":\"flat\"}");

        ByteBuffer ptBuf = ByteBuffer.allocate(64);
        ptBuf.putInt(RegionPreamble.MAGIC);
        ptBuf.putLong(999L);
        Files.write(sourceDir.resolve("part-active.spct"), ptBuf.array());

        DisasterRecoveryProperties props = new DisasterRecoveryProperties();
        DisasterRecoveryExporter exporter = new DisasterRecoveryExporter(s3Client, props, BUCKET);
        DisasterRecoveryExporter.ExportResult expResult = exporter.exportNamespace(
                sourceDir, tenantId, namespaceId, STANDBY_REGION, 5L, 999L
        );
        assertThat(expResult.success()).isTrue();

        // 2. Standby Cell: Restore from S3
        Path standbyBase = tempDir.resolve("standby-cell");
        DisasterRecoveryRestorer.RestoreResult resResult = restorer.restoreNamespace(
                tenantId, namespaceId, 5L, standbyBase, STANDBY_REGION
        );

        assertThat(resResult.success()).isTrue();
        assertThat(resResult.hwm()).isEqualTo(999L);
        assertThat(Files.exists(standbyBase.resolve("namespaces").resolve(namespaceId).resolve("part-active.spct"))).isTrue();
    }

    @Test
    @DisplayName("R9.4, Task 5.16, V6: Standby restore REFUSES on residency jurisdiction mismatch")
    void testStandbyResidencyRefusal() {
        String tenantId = "ten-eu";
        String namespaceId = "018f9b8c000070008000000000000088";
        stageValidSnapshot(tenantId, namespaceId, 1L, 10L, "flat");

        Path standbyBase = tempDir.resolve("standby-wrong-region");
        // Tenant pinned to eu-central-1, but standby cell is in us-west-2
        assertThatThrownBy(() -> restorer.restoreNamespace(
                tenantId, namespaceId, 1L, standbyBase, "eu-central-1"
        )).isInstanceOf(ResidencyViolationException.class)
          .hasMessageContaining("eu-central-1")
          .hasMessageContaining("us-west-2");
    }

    @Test
    @DisplayName("G26: Checksum failure leaves target directory quarantined with restore.failed sentinel")
    void testPartialFailureLeavesNoCorruptedFilesAndWritesSentinel() {
        String tenantId = "ten-fail";
        String namespaceId = "018f9b8c000070008000000000000091";
        String s3Prefix = "snapshots/" + tenantId + "/" + namespaceId + "/1/";

        // Runtime bundle with valid checksum
        ByteBuffer rtBuf = ByteBuffer.allocate(64);
        rtBuf.putInt(BundleFileLayout.LAYOUT_ID);
        byte[] rtData = rtBuf.array();
        String rtSha = AwsSigV4Signer.sha256Hex(rtData);
        s3Client.putObject(BUCKET, s3Prefix + "runtime.bundle", rtData, null, null);

        // Partition with invalid checksum (corrupted in S3)
        ByteBuffer ptBuf = ByteBuffer.allocate(128);
        ptBuf.putInt(RegionPreamble.MAGIC);
        byte[] ptData = ptBuf.array();
        s3Client.putObject(BUCKET, s3Prefix + "part-active.spct", ptData, null, null);

        // Manifest declares expected partition hash as "badbadbad..."
        SnapshotManifest manifest = new SnapshotManifest(
                SnapshotManifest.PLANE_NAMESPACE,
                SnapshotManifest.CURRENT_VERSION,
                tenantId,
                namespaceId,
                "flat",
                1L,
                100L,
                SnapshotKind.FULL,
                new SnapshotManifest.RuntimeEntry("runtime.bundle", rtSha, 1L),
                new SnapshotManifest.ActivePartitionEntry("part-active.spct", "badbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbad0"),
                List.of(),
                0L,
                100L,
                null
        );
        s3Client.putObject(BUCKET, s3Prefix + "manifest.json", manifest.toJson().getBytes(StandardCharsets.UTF_8), null, null);

        Path restoreBase = tempDir.resolve("quarantine-test");
        Path targetDir = restoreBase.resolve("namespaces").resolve(namespaceId);

        assertThatThrownBy(() -> restorer.restoreNamespace(
                tenantId, namespaceId, 1L, restoreBase, STANDBY_REGION
        )).isInstanceOf(IntegrityVerificationException.class)
          .hasMessageContaining("SHA-256 mismatch");

        // G26 Assertions: target directory has sentinel, and NO payload files are present
        assertThat(Files.exists(targetDir.resolve("restore.failed"))).isTrue();
        assertThat(Files.exists(targetDir.resolve("runtime.bundle"))).isFalse();
        assertThat(Files.exists(targetDir.resolve("part-active.spct"))).isFalse();
    }

    @Test
    @DisplayName("G28, G33: S3 contains unmanifested artifact — restorer strictly REFUSES to serve")
    void testUnmanifestedArtifactInS3RefusesToServe() {
        String tenantId = "ten-unman";
        String namespaceId = "018f9b8c000070008000000000000092";
        stageValidSnapshot(tenantId, namespaceId, 1L, 50L, "flat");

        // Plant an unmanifested payload in the snapshot prefix
        String s3Prefix = "snapshots/" + tenantId + "/" + namespaceId + "/1/";
        s3Client.putObject(BUCKET, s3Prefix + "rogue-unmanifested.bin", "rogue-bytes".getBytes(StandardCharsets.UTF_8), null, null);

        Path restoreBase = tempDir.resolve("unman-test");
        assertThatThrownBy(() -> restorer.restoreNamespace(
                tenantId, namespaceId, 1L, restoreBase, STANDBY_REGION
        )).isInstanceOf(IntegrityVerificationException.class)
          .satisfies(e -> {
              IntegrityVerificationException ive = (IntegrityVerificationException) e;
              assertThat(ive.getFailureMode()).isEqualTo("UNMANIFESTED_ARTIFACT");
          });
    }

    @Test
    @DisplayName("G32: Erased namespace with tombstone marker in DR bucket REFUSES restore and discovery")
    void testErasedNamespaceRefusesRestoreAndDiscovery() {
        String tenantId = "ten-erased";
        String namespaceId = "018f9b8c000070008000000000000093";
        stageValidSnapshot(tenantId, namespaceId, 1L, 100L, "flat");

        // Plant tombstone marker in DR bucket
        s3Client.putObject(BUCKET, "snapshots/.tombstones/" + namespaceId,
                "{\"erased\":true}".getBytes(StandardCharsets.UTF_8), null, null);

        // 1. Discovery returns empty
        List<Long> epochs = restorer.discoverSelectableEpochs(tenantId, namespaceId);
        assertThat(epochs).isEmpty();

        // 2. Restore throws IntegrityVerificationException
        Path restoreBase = tempDir.resolve("erased-test");
        assertThatThrownBy(() -> restorer.restoreNamespace(
                tenantId, namespaceId, 1L, restoreBase, STANDBY_REGION
        )).isInstanceOf(IntegrityVerificationException.class)
          .satisfies(e -> {
              IntegrityVerificationException ive = (IntegrityVerificationException) e;
              assertThat(ive.getFailureMode()).isEqualTo("NAMESPACE_ALREADY_ERASED");
          });
    }

    @Test
    @DisplayName("G33: restorePrioritized provides error isolation across requests")
    void testPrioritizedRestoreErrorIsolation() {
        String tenantGood = "ten-good";
        String nsGood = "018f9b8c000070008000000000000094";
        stageValidSnapshot(tenantGood, nsGood, 1L, 100L, "flat");

        String tenantBad = "ten-bad";
        String nsBad = "018f9b8c000070008000000000000095"; // No snapshot staged in S3

        List<DisasterRecoveryRestorer.RestoreRequest> requests = List.of(
                new DisasterRecoveryRestorer.RestoreRequest(tenantBad, nsBad, 1L, 100, STANDBY_REGION),
                new DisasterRecoveryRestorer.RestoreRequest(tenantGood, nsGood, 1L, 50, STANDBY_REGION)
        );

        Path restoreBase = tempDir.resolve("prioritized-isolation");
        List<DisasterRecoveryRestorer.RestoreResult> results = restorer.restorePrioritized(requests, restoreBase);

        assertThat(results).hasSize(2);
        // Bad request failed
        assertThat(results.get(0).namespaceId()).isEqualTo(nsBad);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).refusalReason()).isNotNull();

        // Good request succeeded despite bad request running first
        assertThat(results.get(1).namespaceId()).isEqualTo(nsGood);
        assertThat(results.get(1).success()).isTrue();
    }

    @Test
    @DisplayName("G32: Full NamespaceRecord metadata and legalHold restored verbatim cross-cell")
    void testCatalogMetadataRestoredVerbatim() throws IOException {
        String tenantId = "ten-meta";
        String namespaceId = "018f9b8c000070008000000000000096";

        Path sourceDir = tempDir.resolve("meta-source").resolve("namespaces").resolve(namespaceId);
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve(StoragePaths.FILE_NAMESPACE), "{\"namespaceId\":\"" + namespaceId + "\",\"pathHelper\":\"flat\"}");

        ByteBuffer ptBuf = ByteBuffer.allocate(64);
        ptBuf.putInt(RegionPreamble.MAGIC);
        Files.write(sourceDir.resolve("part-active.spct"), ptBuf.array());

        // Create authoritative catalog entry with legalHold=true and custom description in source catalog
        accountCatalog.getOrCreateAccount(tenantId);
        NamespaceRecord sourceRecord = new NamespaceRecord(
                namespaceId,
                "vault-slug",
                tenantId,
                NamespaceType.PROJECT,
                com.spectrayan.spector.synapse.catalog.NamespaceStatus.ACTIVE,
                "Classified Vault",
                "Mission-critical namespace",
                null,
                Instant.now(),
                Instant.now(),
                true // legalHold = true!
        );
        accountCatalog.importNamespace(sourceRecord);

        // Export with catalog metadata
        DisasterRecoveryProperties props = new DisasterRecoveryProperties();
        DisasterRecoveryExporter exporter = new DisasterRecoveryExporter(s3Client, props, BUCKET, accountCatalog);
        DisasterRecoveryExporter.ExportResult expResult = exporter.exportNamespace(
                sourceDir, tenantId, namespaceId, STANDBY_REGION, 1L, 500L
        );
        assertThat(expResult.success()).isTrue();

        // Standby cell with fresh catalog
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AccountCatalog standbyCatalog = new FileAccountCatalog(tempDir.resolve("standby-catalog"), mapper);
        DisasterRecoveryRestorer standbyRestorer = new DisasterRecoveryRestorer(s3Client, BUCKET, STANDBY_REGION, standbyCatalog);

        Path standbyBase = tempDir.resolve("meta-standby");
        DisasterRecoveryRestorer.RestoreResult resResult = standbyRestorer.restoreNamespace(
                tenantId, namespaceId, 1L, standbyBase, STANDBY_REGION
        );

        assertThat(resResult.success()).isTrue();

        // Verify catalog record restored verbatim
        Optional<NamespaceRecord> restoredOpt = standbyCatalog.resolve(tenantId, "vault-slug");
        assertThat(restoredOpt).isPresent();
        NamespaceRecord restored = restoredOpt.get();
        assertThat(restored.namespaceId()).isEqualTo(namespaceId);
        assertThat(restored.displayName()).isEqualTo("Classified Vault");
        assertThat(restored.description()).isEqualTo("Mission-critical namespace");
        assertThat(restored.legalHold()).as("Legal hold must be preserved cross-cell").isTrue();
    }
}
