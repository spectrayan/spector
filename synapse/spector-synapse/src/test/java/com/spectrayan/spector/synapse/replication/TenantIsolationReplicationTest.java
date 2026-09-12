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

import com.spectrayan.spector.memory.replication.SnapshotKind;
import com.spectrayan.spector.memory.replication.SnapshotManifest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates tenant isolation, allow-list rejection, frames.rejected counting, and zero leakage
 * of tenant identifiers in peer errors (ADR-0034 §10.2, §15.8, Req R6.3, R6.5, R11.3, R12.5).
 */
@DisplayName("Task 5.3, 5.5, 5.9: Tenant Isolation & Allow-List Replication Tests")
class TenantIsolationReplicationTest {

    private static final String TENANT_A = "tenant-alpha";
    private static final String TENANT_B = "tenant-beta";
    private static final String NAMESPACE_ID = "018f9b8c000070008000000000000099";

    @TempDir
    Path tempDir;

    private ReplicationServer server;
    private ReplicationClient client;
    private ReplicationMetrics metrics;
    private TenantAllowListFilter allowListFilter;

    @BeforeEach
    void setUp() throws Exception {
        metrics = new ReplicationMetrics();
        // Replica configured to only allow TENANT_B (Req R12.5)
        allowListFilter = new TenantAllowListFilter(Set.of(TENANT_B));

        server = new ReplicationServer(
                "127.0.0.1",
                0, // dynamic port
                null, // plaintext for isolation logic unit testing
                allowListFilter,
                null,
                metrics,
                tempDir.resolve("staging")
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
    @DisplayName("R12.5 & R6.3: Tenant-A snapshot is rejected by a Tenant-B replica; frames.rejected incremented")
    void testTenantAIsRejectedByTenantBReplica() throws Exception {
        SnapshotManifest tenantAManifest = new SnapshotManifest(
                SnapshotManifest.PLANE_NAMESPACE,
                SnapshotManifest.CURRENT_VERSION,
                TENANT_A,
                NAMESPACE_ID,
                "StoragePaths.tenantRootedNamespaceDir",
                1L,
                100L,
                SnapshotKind.FULL,
                new SnapshotManifest.RuntimeEntry("runtime.bundle", "hash1", 1L),
                new SnapshotManifest.ActivePartitionEntry("001_active", "hash2"),
                List.of(),
                0L,
                100L,
                null
        );

        long rejectedBefore = metrics.getFramesRejected();

        // Ship snapshot for Tenant A to Tenant B's replica
        ReplicationClient.ReplicationResponse response = client.sendSnapshot(
                "127.0.0.1",
                server.getBoundPort(),
                tenantAManifest,
                Map.of("runtime.bundle", new byte[10], "partition.bundle", new byte[20])
        );

        // Verify rejected
        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo("ERROR");

        // R11.3: frames.rejected counted
        assertThat(metrics.getFramesRejected()).isEqualTo(rejectedBefore + 1);
        assertThat(allowListFilter.getFramesRejected()).isEqualTo(1L);

        // R6.5: Refuse unauthorized peer without leaking tenant identifiers into the returned error!
        String returnedMessage = response.message();
        assertThat(returnedMessage).contains(ReplicationAuthorizationException.SANITIZED_PEER_MESSAGE);
        assertThat(returnedMessage)
                .describedAs("Error message must NOT leak tenant identifier (Req R6.5)")
                .doesNotContain(TENANT_A)
                .doesNotContain(TENANT_B);
    }

    @Test
    @DisplayName("R6.3: Tenant-B snapshot is permitted by Tenant-B replica")
    void testTenantBIsPermittedByTenantBReplica() throws Exception {
        SnapshotManifest tenantBManifest = new SnapshotManifest(
                SnapshotManifest.PLANE_NAMESPACE,
                SnapshotManifest.CURRENT_VERSION,
                TENANT_B,
                NAMESPACE_ID,
                "StoragePaths.tenantRootedNamespaceDir",
                1L,
                100L,
                SnapshotKind.FULL,
                new SnapshotManifest.RuntimeEntry("runtime.bundle", "hash1", 1L),
                new SnapshotManifest.ActivePartitionEntry("001_active", "hash2"),
                List.of(),
                0L,
                100L,
                null
        );

        long rejectedBefore = metrics.getFramesRejected();

        ReplicationClient.ReplicationResponse response = client.sendSnapshot(
                "127.0.0.1",
                server.getBoundPort(),
                tenantBManifest,
                Map.of("runtime.bundle", new byte[10], "partition.bundle", new byte[20])
        );

        assertThat(response.success()).isTrue();
        assertThat(response.appliedHwm()).isEqualTo(100L);
        assertThat(metrics.getFramesRejected()).isEqualTo(rejectedBefore);
    }

    @Test
    @DisplayName("R6.5: TenantAllowListFilter direct check throws sanitized exception without tenant leakage")
    void testFilterDirectThrowsSanitized() {
        assertThat(allowListFilter.isAllowed(TENANT_A)).isFalse();

        assertThatThrownBy(() -> allowListFilter.checkAuthorization(TENANT_A))
                .isInstanceOf(ReplicationAuthorizationException.class)
                .hasMessage(ReplicationAuthorizationException.SANITIZED_PEER_MESSAGE)
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).doesNotContain(TENANT_A);
                });
    }
}
