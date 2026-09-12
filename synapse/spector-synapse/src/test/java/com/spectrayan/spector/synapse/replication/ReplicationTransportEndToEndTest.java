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
package com.spectrayan.spector.synapse.replication;

import com.spectrayan.spector.kernel.bundle.PartitionBundle;
import com.spectrayan.spector.kernel.bundle.RuntimeBundle;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.layout.TextBlobLayout;
import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.region.RegionSizeSpec;
import com.spectrayan.spector.kernel.storage.NamespacePathResolver;
import com.spectrayan.spector.memory.replication.ReplicaApplyEngine;
import com.spectrayan.spector.memory.replication.SnapshotKind;
import com.spectrayan.spector.memory.replication.SnapshotManifest;
import com.spectrayan.spector.memory.replication.SnapshotVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test verifying full transport shipping over dedicated port,
 * tenant allow-list validation, atomic apply engine execution, and ACK receipt
 * (ADR-0034 §10, Req R6.1, R6.3, R5.1–R5.5).
 */
@DisplayName("Task 5.1–5.9: End-to-End Replication Transport Integration Test")
class ReplicationTransportEndToEndTest {

    private static final String TENANT_ID = "tenant-001";
    private static final String NAMESPACE_ID = "018f9b8c000070008000000000000055";
    private static final String RESOLVER_ID = NamespacePathResolver.Layout.TENANT_SHA256.id();

    @TempDir
    Path tempDir;

    private Path replicaPersistenceRoot;
    private Path ownerSourceDir;
    private ReplicaApplyEngine applyEngine;
    private ReplicationServer server;
    private ReplicationClient client;
    private ReplicationMetrics metrics;

    @BeforeEach
    void setUp() throws Exception {
        replicaPersistenceRoot = tempDir.resolve("replica_root");
        ownerSourceDir = tempDir.resolve("owner_src");
        Files.createDirectories(replicaPersistenceRoot);
        Files.createDirectories(ownerSourceDir);

        metrics = new ReplicationMetrics();
        TenantAllowListFilter allowListFilter = new TenantAllowListFilter(Set.of(TENANT_ID));

        applyEngine = new ReplicaApplyEngine(
                replicaPersistenceRoot,
                RESOLVER_ID,
                Set.of(TENANT_ID),
                Set.of(NAMESPACE_ID)
        );

        server = new ReplicationServer(
                "127.0.0.1",
                0, // dynamic port
                null, // plaintext for clean transport verification
                allowListFilter,
                applyEngine,
                metrics,
                tempDir.resolve("staging"),
                true // G8: explicit insecure mode for testing
        );
        server.start();

        client = new ReplicationClient(null);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("Ships valid snapshot payload over dedicated port; replica applies crash-safely and returns ACK")
    void testEndToEndSnapshotReplicationOverWire() throws Exception {
        // Create valid owner bundles
        Path runtimeBundle = ownerSourceDir.resolve("runtime.bundle");
        Path partitionBundle = ownerSourceDir.resolve("001_active").resolve("partition.bundle");
        Files.createDirectories(partitionBundle.getParent());

        List<RegionSizeSpec> specs = List.of(
                new RegionSizeSpec(RegionId.WORKING, 4096, 10, 64, 0x574F524B, 1, false),
                new RegionSizeSpec(RegionId.INDEX_MIDX, 4096, 10, 32, 0x494E4458, 1, true)
        );
        try (RuntimeBundle rb = RuntimeBundle.Init.mmap(runtimeBundle, specs)) {
            // cleanly created
        }

        EngramLayout cogLayout = new EngramLayout(16);
        TextBlobLayout textLayout = new TextBlobLayout();
        try (PartitionBundle pb = PartitionBundle.Init.mmap(
                partitionBundle,
                10, 4096L, 5, 4096L, 16,
                cogLayout.layoutId(), cogLayout.schemaVersion(),
                textLayout.layoutId(), textLayout.schemaVersion()
        )) {
            // cleanly created
        }

        String runtimeSha = SnapshotVerifier.calculateSha256(runtimeBundle);
        String partitionSha = SnapshotVerifier.calculateSha256(partitionBundle);

        SnapshotManifest manifest = new SnapshotManifest(
                SnapshotManifest.PLANE_NAMESPACE,
                SnapshotManifest.CURRENT_VERSION,
                TENANT_ID,
                NAMESPACE_ID,
                RESOLVER_ID,
                1L,
                42019L,
                SnapshotKind.FULL,
                new SnapshotManifest.RuntimeEntry("runtime.bundle", runtimeSha, 1L),
                new SnapshotManifest.ActivePartitionEntry("001_active", partitionSha),
                List.of(),
                0L,
                0L,
                null
        );

        Map<String, byte[]> fileBytesMap = new HashMap<>();
        fileBytesMap.put("runtime.bundle", Files.readAllBytes(runtimeBundle));
        fileBytesMap.put("001_active/partition.bundle", Files.readAllBytes(partitionBundle));

        // Send over network transport
        ReplicationClient.ReplicationResponse response = client.sendSnapshot(
                "127.0.0.1",
                server.getBoundPort(),
                manifest,
                fileBytesMap
        );

        // Verify ACK
        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.appliedHwm()).isEqualTo(42019L);

        // Verify files landed on replica disk
        Path replicaNsDir = com.spectrayan.spector.kernel.storage.StoragePaths.tenantRootedNamespaceDir(replicaPersistenceRoot, TENANT_ID, NAMESPACE_ID);
        assertThat(replicaNsDir.resolve("runtime.bundle")).exists();
        assertThat(replicaNsDir.resolve("001_active").resolve("partition.bundle")).exists();

        // Idempotent re-apply over wire returns ALREADY_APPLIED (Req R5.5)
        ReplicationClient.ReplicationResponse secondResponse = client.sendSnapshot(
                "127.0.0.1",
                server.getBoundPort(),
                manifest,
                fileBytesMap
        );

        assertThat(secondResponse.success()).isTrue();
        assertThat(secondResponse.status()).isEqualTo("ALREADY_APPLIED");
        assertThat(secondResponse.appliedHwm()).isEqualTo(42019L);
    }
}
