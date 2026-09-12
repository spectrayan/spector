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

import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.storage.NamespacePathResolver;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.replication.ReplicaApplyEngine;
import com.spectrayan.spector.memory.replication.SnapshotKind;
import com.spectrayan.spector.memory.replication.SnapshotManifest;
import com.spectrayan.spector.memory.replication.SnapshotVerifier;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.synapse.config.replication.ReplicationProperties;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-0034 §15.9 Phase 3 Exit Criterion Integration Test:
 *
 * <p><b>"Snapshot a V4 namespace on owner, apply on a clean node, open with SpectorMemory,
 * recall the expected results." (Req R12.4, ADR §15.9 Phase 3)</b></p>
 *
 * <p>Verifies the end-to-end replication contract across network transport, crash-safe apply,
 * replica opening, bounded-staleness recall (R10.1–R10.4, N7), and unconditional write refusal (R10.6, N2).</p>
 */
@DisplayName("Task 6.14: ADR-0034 §15.9 Phase 3 Exit Criterion Integration Test")
class CellHaPhase3ReplicaOpenRecallIntegrationTest {

    private static final String TENANT_ID = "018f9b8c000070008000000000000001";
    private static final String NAMESPACE_ID = "018f9b8c000070008000000000000088";
    private static final String RESOLVER_ID = NamespacePathResolver.Layout.TENANT_SHA256.id();

    private static final int DIMS = 16;

    @TempDir
    Path tempDir;

    private Path ownerPersistenceRoot;
    private Path replicaPersistenceRoot;
    private ReplicationServer replicationServer;
    private ReplicationClient replicationClient;
    private ReplicationProperties replicationProps;
    private ReplicaRecallGuard recallGuard;

    @BeforeEach
    void setUp() throws Exception {
        ownerPersistenceRoot = tempDir.resolve("owner_data");
        replicaPersistenceRoot = tempDir.resolve("replica_clean_node");
        Files.createDirectories(ownerPersistenceRoot);
        Files.createDirectories(replicaPersistenceRoot);

        replicationProps = new ReplicationProperties();
        replicationProps.setEnabled(true);
        replicationProps.setReplicaReadsEnabled(true);
        replicationProps.setMaxReplicaLagSeconds(30L);

        recallGuard = new ReplicaRecallGuard(replicationProps);

        // Configure replica apply engine on clean node
        ReplicaApplyEngine applyEngine = new ReplicaApplyEngine(
                replicaPersistenceRoot,
                RESOLVER_ID,
                Set.of(TENANT_ID),
                Set.of(NAMESPACE_ID)
        );

        TenantAllowListFilter allowList = new TenantAllowListFilter(Set.of(TENANT_ID));

        replicationServer = new ReplicationServer(
                "127.0.0.1",
                0, // dynamic dedicated replication port
                null,
                allowList,
                applyEngine,
                new ReplicationMetrics(),
                tempDir.resolve("replica_staging")
        );
        replicationServer.start();

        replicationClient = new ReplicationClient(null);
    }

    @AfterEach
    void tearDown() {
        if (replicationServer != null) {
            replicationServer.stop();
        }
    }

    private static MemoryProperties createMemoryProperties() {
        var memProps = new MemoryProperties()
                .setDimensions(DIMS)
                .setWorkingCapacity(10)
                .setEpisodicPartitionCapacity(100)
                .setSemanticCapacity(100)
                .setProceduralCapacity(100);
        memProps.getRemember().setSurpriseWarmup(1);
        return memProps;
    }

    private static SpectorMemory openMemory(Path dir) {
        return DefaultSpectorMemory.builder(createMemoryProperties())
                .embeddingProvider(new TestEmbedder(DIMS))
                .persistenceMode(MemoryPersistenceMode.DISK)
                .persistence(dir)
                .build();
    }

    @Test
    @DisplayName("Exit Criterion: Snapshot V4 namespace on owner -> ship over wire -> apply on clean node -> open with SpectorMemory -> recall expected results")
    void testPhase3ExitCriterionReplicaOpenAndRecall() throws Exception {
        // ── Step 1: Initialize and populate namespace on owner ──
        Path ownerNsDir = StoragePaths.tenantRootedNamespaceDir(ownerPersistenceRoot, TENANT_ID, NAMESPACE_ID);
        Files.createDirectories(ownerNsDir);

        try (SpectorMemory ownerMemory = openMemory(ownerNsDir)) {
            ownerMemory.remember("pref-dark", "User prefers dark mode in all applications.",
                    MemoryType.EPISODIC, MemorySource.USER_STATED, "ui", "theme");
            ownerMemory.remember("pref-java", "User writes system components in modern Java 25.",
                    MemoryType.EPISODIC, MemorySource.USER_STATED, "runtime", "language");
            ownerMemory.remember("alert-db", "Database lock timeout observed on users table.",
                    MemoryType.EPISODIC, MemorySource.OBSERVED, "system", "incident");

            assertThat(ownerMemory.totalMemories()).isEqualTo(3);
        }

        // ── Step 2: Build snapshot manifest on owner ──
        Path runtimeBundle = ownerNsDir.resolve(StoragePaths.DIR_RUNTIME).resolve(StoragePaths.FILE_RUNTIME_BUNDLE);
        assertThat(runtimeBundle).exists();

        // Locate active partition bundle
        Path partitionsDir = ownerNsDir.resolve(StoragePaths.DIR_PARTITIONS);
        Path activePartitionDir = null;
        try (var stream = Files.list(partitionsDir)) {
            activePartitionDir = stream.filter(p -> Files.isDirectory(p) && StoragePaths.isPartitionDir(p.getFileName().toString()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Missing partition directory"));
        }
        Path activeBundle = activePartitionDir.resolve(StoragePaths.FILE_PARTITION_BUNDLE);
        assertThat(activeBundle).exists();

        String activePartId = activePartitionDir.getFileName().toString();
        String runtimeSha = SnapshotVerifier.calculateSha256(runtimeBundle);
        String activeSha = SnapshotVerifier.calculateSha256(activeBundle);

        SnapshotManifest manifest = new SnapshotManifest(
                SnapshotManifest.PLANE_NAMESPACE,
                SnapshotManifest.CURRENT_VERSION,
                TENANT_ID,
                NAMESPACE_ID,
                RESOLVER_ID,
                1L, // epoch
                42019L, // hwm
                SnapshotKind.FULL,
                new SnapshotManifest.RuntimeEntry(StoragePaths.DIR_RUNTIME + "/" + StoragePaths.FILE_RUNTIME_BUNDLE, runtimeSha, 1L),
                new SnapshotManifest.ActivePartitionEntry(activePartId, activeSha),
                List.of(),
                0L,
                42019L,
                null
        );

        Map<String, byte[]> bundleFiles = new HashMap<>();
        bundleFiles.put(StoragePaths.DIR_RUNTIME + "/" + StoragePaths.FILE_RUNTIME_BUNDLE, Files.readAllBytes(runtimeBundle));
        bundleFiles.put(StoragePaths.DIR_PARTITIONS + "/" + activePartId + "/" + StoragePaths.FILE_PARTITION_BUNDLE, Files.readAllBytes(activeBundle));

        // ── Step 3: Ship snapshot to replica clean node over dedicated transport ──
        ReplicationClient.ReplicationResponse response = replicationClient.sendSnapshot(
                "127.0.0.1",
                replicationServer.getBoundPort(),
                manifest,
                bundleFiles
        );

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.appliedHwm()).isEqualTo(42019L);

        // ── Step 4: Verify files on clean replica node ──
        Path replicaNsDir = StoragePaths.tenantRootedNamespaceDir(replicaPersistenceRoot, TENANT_ID, NAMESPACE_ID);
        assertThat(replicaNsDir.resolve(StoragePaths.DIR_RUNTIME).resolve(StoragePaths.FILE_RUNTIME_BUNDLE)).exists();
        assertThat(replicaNsDir.resolve(StoragePaths.DIR_PARTITIONS).resolve(activePartId).resolve(StoragePaths.FILE_PARTITION_BUNDLE)).exists();

        // ── Step 5: Open namespace on clean replica node with SpectorMemory ──
        try (SpectorMemory replicaMemory = openMemory(replicaNsDir)) {
            // Memory must open intact and report identical memory count
            assertThat(replicaMemory.totalMemories()).isEqualTo(3);

            // Recall must return the expected results!
            List<CognitiveResult> results = replicaMemory.recall("dark mode");
            assertThat(results).isNotEmpty();
            assertThat(results.get(0).text()).contains("User prefers dark mode");

            List<CognitiveResult> javaResults = replicaMemory.recall("Java 25");
            assertThat(javaResults).isNotEmpty();
            assertThat(javaResults.get(0).text()).contains("modern Java 25");
        }

        // ── Step 6: Validate bounded-staleness recall guard & write refusal ──
        var routingKey = new com.spectrayan.spector.cluster.routing.RoutingKey("cell-1", TENANT_ID, NAMESPACE_ID);
        long now = System.currentTimeMillis();

        // Fresh replica recall within lag bound permitted
        recallGuard.validateReplicaRecall(routingKey, true, now - 1000L, now, true);

        // Stale replica recall exceeding bound refused (Req R10.4, Invariant N7)
        assertThatThrownBy(() -> recallGuard.validateReplicaRecall(routingKey, true, now - 60_000L, now, true))
                .isInstanceOf(ReplicaStalenessExceededException.class);

        // Replica unconditionally refuses writes (Req R10.6, Invariant N2)
        assertThatThrownBy(() -> recallGuard.validateWritePermitted(NAMESPACE_ID, true))
                .isInstanceOf(ReplicaWriteRefusedException.class);
    }

    private static final class TestEmbedder implements EmbeddingProvider {
        private final int dims;

        TestEmbedder(int dims) {
            this.dims = dims;
        }

        @Override
        public int dimensions() {
            return dims;
        }

        @Override
        public EmbeddingResult embed(String text) {
            float[] vec = new float[dims];
            // Deterministic hash-based non-zero embedding
            int hash = Math.abs(text.hashCode());
            for (int i = 0; i < dims; i++) {
                vec[i] = (float) ((hash >> (i % 16)) & 0x0F) / 16.0f;
            }
            return new EmbeddingResult(vec, 1, "test-embedder");
        }

        @Override
        public String modelName() {
            return "test-embedder";
        }

        @Override
        public void close() {}
    }
}
