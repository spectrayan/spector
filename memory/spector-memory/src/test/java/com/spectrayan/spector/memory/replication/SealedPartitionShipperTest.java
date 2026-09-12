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

import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.memory.replication.fixture.ReplicationBundleFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for Group 2 sealed partition shipping, immutability, cold tier sourcing,
 * divergence detection, and byte split tracking (ADR-0034 §9.7, Req R2.1–R2.6, R11.2, N1, B4).
 */
class SealedPartitionShipperTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Task 2.1 / Invariant N1: Ship sealed partition at most once; skip when checksum matches")
    void shipsSealedPartitionAtMostOnce() {
        SealedPartitionShipper shipper = new SealedPartitionShipper();

        List<SnapshotManifest.SealedPartitionEntry> ownerSealed = List.of(
                new SnapshotManifest.SealedPartitionEntry("p-001.bundle", "sha_111", null),
                new SnapshotManifest.SealedPartitionEntry("p-002.bundle", "sha_222", null)
        );

        // Replica already has p-001 with exact matching checksum
        Map<String, String> replicaChecksums = Map.of("p-001.bundle", "sha_111");

        SealedPartitionShipper.SealedShippingPlan plan = shipper.planShipping(ownerSealed, replicaChecksums);

        assertThat(plan.hasDivergence()).isFalse();
        assertThat(plan.alreadyPresent())
                .extracting(SnapshotManifest.SealedPartitionEntry::id)
                .containsExactly("p-001.bundle");
        assertThat(plan.toShipFromOwner())
                .extracting(SnapshotManifest.SealedPartitionEntry::id)
                .containsExactly("p-002.bundle");
    }

    @Test
    @DisplayName("Task 2.2 / Req R2.2: Immediate snapshot triggered on partition roll")
    void immediateSnapshotTriggeredOnPartitionRoll() {
        Path bundle = ReplicationBundleFixtures.createValidPartitionBundle(tempDir.resolve("partition-1.bundle"));
        String expectedSha = SnapshotVerifier.calculateSha256(bundle);

        AtomicReference<SnapshotManifest.SealedPartitionEntry> captured = new AtomicReference<>();
        PartitionRollReplicationTrigger trigger = new PartitionRollReplicationTrigger(captured::set);

        trigger.onPartitionRolled(1, tempDir, bundle);

        assertThat(trigger.rollTriggerCount()).isEqualTo(1L);
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().id()).isEqualTo("partition-1.bundle");
        assertThat(captured.get().sha256()).isEqualTo(expectedSha);
    }

    @Test
    @DisplayName("Task 2.3 / Req R2.3: Assert sealed object is byte-identical to cold-tier object")
    void assertColdTierByteIdentityEnforced() throws IOException {
        Path bundle = ReplicationBundleFixtures.createValidPartitionBundle(tempDir.resolve("local-sealed.bundle"));
        String validSha = SnapshotVerifier.calculateSha256(bundle);

        ColdTierObjectSource matchingSource = new ColdTierObjectSource() {
            @Override
            public boolean hasObject(String objectRef) { return true; }

            @Override
            public void fetchObject(String objectRef, Path destination) throws IOException {
                Files.copy(bundle, destination);
            }

            @Override
            public String calculateSha256(String objectRef) { return validSha; }
        };

        // Matching hash should succeed cleanly
        SealedPartitionShipper.assertColdTierByteIdentity(bundle, "s3://bucket/part-1", matchingSource);

        ColdTierObjectSource mismatchedSource = new ColdTierObjectSource() {
            @Override
            public boolean hasObject(String objectRef) { return true; }

            @Override
            public void fetchObject(String objectRef, Path destination) {}

            @Override
            public String calculateSha256(String objectRef) { return "deadbeef1234"; }
        };

        assertThatThrownBy(() -> SealedPartitionShipper.assertColdTierByteIdentity(
                bundle, "s3://bucket/part-1", mismatchedSource))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("not byte-identical to cold-tier object");
    }

    @Test
    @DisplayName("Task 2.4 / Blocker B4: Prefer cold tier when objectRef exists, fallback to owner")
    void prefersColdTierWhenAvailable() {
        ColdTierObjectSource coldTier = new ColdTierObjectSource() {
            @Override
            public boolean hasObject(String objectRef) {
                return "s3://bucket/cold-part".equals(objectRef);
            }

            @Override
            public void fetchObject(String objectRef, Path destination) {}

            @Override
            public String calculateSha256(String objectRef) { return "hash"; }
        };

        SealedPartitionShipper shipper = new SealedPartitionShipper(coldTier);

        List<SnapshotManifest.SealedPartitionEntry> ownerSealed = List.of(
                new SnapshotManifest.SealedPartitionEntry("p-cold.bundle", "sha_cold", "s3://bucket/cold-part"),
                new SnapshotManifest.SealedPartitionEntry("p-local.bundle", "sha_local", "s3://bucket/not-in-cold"),
                new SnapshotManifest.SealedPartitionEntry("p-no-ref.bundle", "sha_no_ref", null)
        );

        SealedPartitionShipper.SealedShippingPlan plan = shipper.planShipping(ownerSealed, Map.of());

        assertThat(plan.toFetchFromColdTier())
                .extracting(SnapshotManifest.SealedPartitionEntry::id)
                .containsExactly("p-cold.bundle");

        assertThat(plan.toShipFromOwner())
                .extracting(SnapshotManifest.SealedPartitionEntry::id)
                .containsExactly("p-local.bundle", "p-no-ref.bundle");
    }

    @Test
    @DisplayName("Task 2.5 / Req R2.5: Detect sealed-set divergence and throw SealedSetDivergenceException")
    void detectsSealedSetDivergence() {
        SealedPartitionShipper shipper = new SealedPartitionShipper();

        List<SnapshotManifest.SealedPartitionEntry> ownerSealed = List.of(
                new SnapshotManifest.SealedPartitionEntry("p-001.bundle", "sha_owner_111", null)
        );

        // Replica has p-001 but with a different checksum!
        Map<String, String> replicaChecksums = Map.of("p-001.bundle", "sha_replica_CORRUPT");

        assertThatThrownBy(() -> shipper.planShipping(ownerSealed, replicaChecksums))
                .isInstanceOf(SealedSetDivergenceException.class)
                .satisfies(e -> {
                    SealedSetDivergenceException de = (SealedSetDivergenceException) e;
                    assertThat(de.partitionId()).isEqualTo("p-001.bundle");
                    assertThat(de.ownerSha256()).isEqualTo("sha_owner_111");
                    assertThat(de.replicaSha256()).isEqualTo("sha_replica_CORRUPT");
                })
                .hasMessageContaining("Full resync required");
    }

    @Test
    @DisplayName("Task 2.6 / Req R11.2: Count snapshot bytes split sealed vs mutable")
    void countsSnapshotBytesSplitSealedVsMutable() throws IOException {
        Path bundle1 = ReplicationBundleFixtures.createValidPartitionBundle(tempDir.resolve("p-1.bundle"));
        long bundle1Size = Files.size(bundle1);

        ReplicationByteTracker tracker = new ReplicationByteTracker();
        SealedPartitionShipper shipper = new SealedPartitionShipper();

        // Ship p-1 for the first time
        Path replicaTarget = tempDir.resolve("replica/p-1.bundle");
        shipper.shipFromOwner(bundle1, replicaTarget, tracker);

        assertThat(tracker.sealedBytes()).isEqualTo(bundle1Size);
        assertThat(tracker.mutableBytes()).isZero();

        // Record some mutable bytes
        tracker.recordMutableBytes(1024L);
        assertThat(tracker.mutableBytes()).isEqualTo(1024L);

        // Second snapshot: p-1 checksum matches, planning skips it, 0 additional sealed bytes shipped!
        Map<String, String> replicaChecksums = Map.of("p-1.bundle", SnapshotVerifier.calculateSha256(bundle1));
        List<SnapshotManifest.SealedPartitionEntry> ownerSealed = List.of(
                new SnapshotManifest.SealedPartitionEntry("p-1.bundle", replicaChecksums.get("p-1.bundle"), null)
        );
        SealedPartitionShipper.SealedShippingPlan plan = shipper.planShipping(ownerSealed, replicaChecksums);
        assertThat(plan.alreadyPresent()).hasSize(1);
        assertThat(plan.toShipFromOwner()).isEmpty();

        // Sealed bytes count remains strictly bundle1Size (no duplicate byte movement)
        assertThat(tracker.sealedBytes()).isEqualTo(bundle1Size);
    }
}
