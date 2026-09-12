/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.replication;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.memory.replication.fixture.ReplicationBundleFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ReplicaApplyEngine} (ADR-0034 §5, Req R5.1–R5.7, R6.3, R11.5, Tasks 4.1–4.5, 4.8).
 */
class ReplicaApplyEngineTest {

    @TempDir
    Path tempDir;

    private static final String TENANT = "tenant-alpha";
    private static final String NS = "ns-alpha";
    private static final String PATH_HELPER = "StoragePaths.namespaceDirSharded";

    @Test
    @DisplayName("Task 4.2 / Req R5.7 & KI-3: Reject manifest with wrong pathHelper")
    void rejectsPathHelperMismatch() {
        Path root = tempDir.resolve("root");
        ReplicaApplyEngine engine = new ReplicaApplyEngine(root, PATH_HELPER, Set.of(TENANT), Set.of());

        SnapshotManifest manifest = new SnapshotManifest(
                SnapshotManifest.PLANE_NAMESPACE,
                1,
                TENANT,
                NS,
                "StoragePaths.tenantRootedNamespaceDir", // Mismatched!
                1L,
                100L,
                SnapshotKind.FULL,
                null,
                null,
                List.of(),
                0L,
                100L,
                null
        );

        assertThatThrownBy(() -> engine.applySnapshot(manifest, Map.of(), List.of()))
                .isInstanceOf(SpectorValidationException.class)
                .satisfies(e -> assertThat(((SpectorValidationException) e).errorCode()).isEqualTo(ErrorCode.ARGUMENT_INVALID))
                .hasMessageContaining("pathHelper mismatch");
    }

    @Test
    @DisplayName("Task 4.1 & Req R6.3: Reject manifest for tenant outside allow-list")
    void rejectsTenantOutsideAllowList() {
        Path root = tempDir.resolve("root");
        ReplicaApplyEngine engine = new ReplicaApplyEngine(root, PATH_HELPER, Set.of("permitted-tenant"), Set.of());

        SnapshotManifest manifest = new SnapshotManifest(
                SnapshotManifest.PLANE_NAMESPACE,
                1,
                "unauthorized-tenant",
                NS,
                PATH_HELPER,
                1L,
                100L,
                SnapshotKind.FULL,
                null,
                null,
                List.of(),
                0L,
                100L,
                null
        );

        assertThatThrownBy(() -> engine.applySnapshot(manifest, Map.of(), List.of()))
                .isInstanceOf(SpectorValidationException.class)
                .satisfies(e -> assertThat(((SpectorValidationException) e).errorCode()).isEqualTo(ErrorCode.NAMESPACE_ACCESS_DENIED))
                .hasMessageContaining("Tenant not permitted");
    }

    @Test
    @DisplayName("Task 4.3 / Req R5.4: Remap flag is true only if namespace is in replicaHotSet")
    void respectsReplicaHotSetRemapFlag() {
        Path root = tempDir.resolve("root");
        Path rt = ReplicationBundleFixtures.createValidRuntimeBundle(tempDir.resolve("rt.bundle"));
        Path pt = ReplicationBundleFixtures.createValidPartitionBundle(tempDir.resolve("pt.bundle"));

        SnapshotManifest manifest = new SnapshotManifest(
                SnapshotManifest.PLANE_NAMESPACE,
                1,
                TENANT,
                NS,
                PATH_HELPER,
                1L,
                50L,
                SnapshotKind.FULL,
                new SnapshotManifest.RuntimeEntry("runtime.bundle", SnapshotVerifier.calculateSha256(rt), 1L),
                new SnapshotManifest.ActivePartitionEntry("partition.bundle", SnapshotVerifier.calculateSha256(pt)),
                List.of(),
                0L,
                50L,
                null
        );

        // Case 1: NOT in hot set -> remapped is false
        ReplicaApplyEngine engine1 = new ReplicaApplyEngine(root, PATH_HELPER, Set.of(TENANT), Set.of("other-ns"));
        ReplicaApplyEngine.ApplyResult res1 = engine1.applySnapshot(
                manifest,
                Map.of("runtime.bundle", rt, "partition.bundle", pt),
                List.of()
        );
        assertThat(res1.remapped()).isFalse();

        // Case 2: In hot set -> remapped is true
        ReplicaApplyEngine engine2 = new ReplicaApplyEngine(root.resolve("r2"), PATH_HELPER, Set.of(TENANT), Set.of(NS));
        ReplicaApplyEngine.ApplyResult res2 = engine2.applySnapshot(
                manifest,
                Map.of("runtime.bundle", rt, "partition.bundle", pt),
                List.of()
        );
        assertThat(res2.remapped()).isTrue();
    }

    @Test
    @DisplayName("Task 4.4 / Req R5.5: Re-applying same manifest is a verified no-op")
    void reApplyingSameManifestIsVerifiedNoOp() {
        Path root = tempDir.resolve("root");
        Path rt = ReplicationBundleFixtures.createValidRuntimeBundle(tempDir.resolve("rt.bundle"));
        Path pt = ReplicationBundleFixtures.createValidPartitionBundle(tempDir.resolve("pt.bundle"));

        SnapshotManifest manifest = new SnapshotManifest(
                SnapshotManifest.PLANE_NAMESPACE,
                1,
                TENANT,
                NS,
                PATH_HELPER,
                1L,
                50L,
                SnapshotKind.FULL,
                new SnapshotManifest.RuntimeEntry("runtime.bundle", SnapshotVerifier.calculateSha256(rt), 1L),
                new SnapshotManifest.ActivePartitionEntry("partition.bundle", SnapshotVerifier.calculateSha256(pt)),
                List.of(),
                0L,
                50L,
                null
        );

        ReplicaApplyEngine engine = new ReplicaApplyEngine(root, PATH_HELPER, Set.of(TENANT), Set.of(NS));

        // First apply
        ReplicaApplyEngine.ApplyResult res1 = engine.applySnapshot(
                manifest,
                Map.of("runtime.bundle", rt, "partition.bundle", pt),
                List.of()
        );
        assertThat(res1.updated()).isTrue();

        // Re-apply same manifest
        ReplicaApplyEngine.ApplyResult res2 = engine.applySnapshot(
                manifest,
                Map.of("runtime.bundle", rt, "partition.bundle", pt),
                List.of()
        );
        assertThat(res2.updated()).isFalse();
        assertThat(res2.appliedHwm()).isEqualTo(50L);
    }
}
