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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SnapshotManifest} (ADR-0034 §9.7, Req R1.1, R1.3, R1.5, N4, Task 1.1).
 */
class SnapshotManifestTest {

    private static final String TENANT = "tenant-alpha";
    private static final String NS = "ns-alpha";
    private static final String PATH_HELPER = "StoragePaths.namespaceDirSharded";

    @Test
    @DisplayName("Task 1.1: SnapshotManifest round-trips cleanly via JSON")
    void manifestJsonRoundTrip() {
        SnapshotManifest.RuntimeEntry runtime = new SnapshotManifest.RuntimeEntry("runtime.bundle", "abc123sha", 1L);
        SnapshotManifest.ActivePartitionEntry active = new SnapshotManifest.ActivePartitionEntry("partition.bundle", "def456sha");
        List<SnapshotManifest.SealedPartitionEntry> sealed = List.of(
                new SnapshotManifest.SealedPartitionEntry("p-001.bundle", "111sha", "s3://bucket/p-001"),
                new SnapshotManifest.SealedPartitionEntry("p-002.bundle", "222sha", null)
        );

        SnapshotManifest manifest = new SnapshotManifest(
                SnapshotManifest.PLANE_NAMESPACE,
                1,
                TENANT,
                NS,
                PATH_HELPER,
                42L,
                1000L,
                SnapshotKind.FULL,
                runtime,
                active,
                sealed,
                500L,
                1000L,
                "key-ref-v1"
        );

        String json = manifest.toJson();
        assertThat(json).contains("\"plane\":\"namespace\"");
        assertThat(json).contains("\"manifestVersion\":1");
        assertThat(json).contains("\"kind\":\"FULL\"");

        SnapshotManifest deserialized = SnapshotManifest.fromJson(json);
        assertThat(deserialized).isEqualTo(manifest);
        assertThat(deserialized.plane()).isEqualTo("namespace");
        assertThat(deserialized.manifestVersion()).isEqualTo(1);
        assertThat(deserialized.epoch()).isEqualTo(42L);
        assertThat(deserialized.hwm()).isEqualTo(1000L);
        assertThat(deserialized.runtime().file()).isEqualTo("runtime.bundle");
        assertThat(deserialized.activePartition().id()).isEqualTo("partition.bundle");
        assertThat(deserialized.sealed()).hasSize(2);
        assertThat(deserialized.sealed().get(0).objectRef()).isEqualTo("s3://bucket/p-001");
        assertThat(deserialized.sealed().get(1).objectRef()).isNull();
    }

    @Test
    @DisplayName("Task 1.3: Reject plane != 'namespace' (Invariant N4, Req R1.3)")
    void rejectsNonNamespacePlane() {
        assertThatThrownBy(() -> new SnapshotManifest(
                "identity",
                1,
                TENANT,
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
        ))
                .isInstanceOf(SpectorValidationException.class)
                .satisfies(e -> assertThat(((SpectorValidationException) e).errorCode()).isEqualTo(ErrorCode.ARGUMENT_INVALID))
                .hasMessageContaining("Invalid snapshot plane 'identity'");
    }

    @Test
    @DisplayName("Task 1.5: Reject manifestVersion < 1 (Req R1.5)")
    void rejectsInvalidManifestVersion() {
        assertThatThrownBy(() -> new SnapshotManifest(
                "namespace",
                0,
                TENANT,
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
        ))
                .isInstanceOf(SpectorValidationException.class)
                .satisfies(e -> assertThat(((SpectorValidationException) e).errorCode()).isEqualTo(ErrorCode.ARGUMENT_INVALID))
                .hasMessageContaining("manifestVersion must be >= 1");
    }

    @Test
    @DisplayName("Task 0.2 & 1.3: Reject identity-plane paths in runtime or partition entries (Invariant N4)")
    void rejectsIdentityPathsInManifest() {
        assertThatThrownBy(() -> new SnapshotManifest(
                "namespace",
                1,
                TENANT,
                NS,
                PATH_HELPER,
                1L,
                100L,
                SnapshotKind.FULL,
                new SnapshotManifest.RuntimeEntry("accounts/alice/identity.bundle", "hash", 1L),
                null,
                List.of(),
                0L,
                100L,
                null
        ))
                .isInstanceOf(SpectorValidationException.class)
                .satisfies(e -> assertThat(((SpectorValidationException) e).errorCode()).isEqualTo(ErrorCode.ARGUMENT_INVALID));

        assertThatThrownBy(() -> new SnapshotManifest(
                "namespace",
                1,
                TENANT,
                NS,
                PATH_HELPER,
                1L,
                100L,
                SnapshotKind.FULL,
                null,
                new SnapshotManifest.ActivePartitionEntry("tenants/acme/identity.bundle", "hash"),
                List.of(),
                0L,
                100L,
                null
        ))
                .isInstanceOf(SpectorValidationException.class)
                .satisfies(e -> assertThat(((SpectorValidationException) e).errorCode()).isEqualTo(ErrorCode.ARGUMENT_INVALID));

        assertThatThrownBy(() -> new SnapshotManifest(
                "namespace",
                1,
                TENANT,
                NS,
                PATH_HELPER,
                1L,
                100L,
                SnapshotKind.FULL,
                null,
                null,
                List.of(new SnapshotManifest.SealedPartitionEntry("accounts/identity.bundle", "hash", null)),
                0L,
                100L,
                null
        ))
                .isInstanceOf(SpectorValidationException.class)
                .satisfies(e -> assertThat(((SpectorValidationException) e).errorCode()).isEqualTo(ErrorCode.ARGUMENT_INVALID));
    }
}
