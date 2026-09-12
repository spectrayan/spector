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
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.memory.replication.fixture.ReplicationBundleFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline corruption verification tests asserting rejection of all four corruption fixtures
 * individually for the right reasons (ADR-0034 §9.7, §10.2, Req R1.2, R1.6, R5.1, R5.7, R11.5, R12.8, R12.9, Task 1.6).
 */
class SnapshotCorruptionVerificationTest {

    @TempDir
    Path tempDir;

    private static final String TENANT = "tenant-test";
    private static final String NS = "ns-test";
    private static final String EXPECTED_RESOLVER = "StoragePaths.namespaceDirSharded";

    @BeforeEach
    void setUp() {
        SnapshotVerifier.resetFailureCount();
    }

    @Test
    @DisplayName("Task 1.5: Valid bundle passes preamble and SHA-256 verification cleanly")
    void validBundlePassesVerification() {
        Path bundle = ReplicationBundleFixtures.createValidPartitionBundle(tempDir.resolve("valid-partition.bundle"));
        String sha256 = ReplicationBundleFixtures.calculateSha256(bundle);

        // Verification should succeed without exception
        SnapshotVerifier.verifyBundleFile(bundle, sha256);
        assertThat(SnapshotVerifier.failureCount()).isZero();
    }

    @Test
    @DisplayName("Task 1.6 / Req R12.8: Corrupted magic fixture is rejected with FILE_FORMAT_INVALID")
    void rejectsCorruptedMagicFixture() {
        Path bundle = ReplicationBundleFixtures.createCorruptedMagicBundle(tempDir.resolve("bad-magic.bundle"));
        String sha256 = ReplicationBundleFixtures.calculateSha256(bundle);

        assertThatThrownBy(() -> SnapshotVerifier.verifyBundleFile(bundle, sha256))
                .isInstanceOf(SpectorValidationException.class)
                .satisfies(e -> {
                    SpectorValidationException sve = (SpectorValidationException) e;
                    assertThat(sve.errorCode()).isEqualTo(ErrorCode.FILE_FORMAT_INVALID);
                    assertThat(sve.getMessage()).contains("Invalid preamble magic");
                    assertThat(sve.getMessage()).contains("SMKM");
                });

        assertThat(SnapshotVerifier.failureCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Task 1.6 / Req R12.8: Corrupted layout ID fixture is rejected with FILE_FORMAT_INVALID")
    void rejectsCorruptedLayoutIdFixture() {
        Path bundle = ReplicationBundleFixtures.createCorruptedLayoutIdBundle(tempDir.resolve("bad-layout.bundle"));
        String sha256 = ReplicationBundleFixtures.calculateSha256(bundle);

        assertThatThrownBy(() -> SnapshotVerifier.verifyBundleFile(bundle, sha256))
                .isInstanceOf(SpectorValidationException.class)
                .satisfies(e -> {
                    SpectorValidationException sve = (SpectorValidationException) e;
                    assertThat(sve.errorCode()).isEqualTo(ErrorCode.FILE_FORMAT_INVALID);
                    assertThat(sve.getMessage()).contains("Invalid bundle layout ID");
                    assertThat(sve.getMessage()).contains("BUND");
                });

        assertThat(SnapshotVerifier.failureCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Task 1.6 / Req R12.8: Mismatched checksum fixture is rejected with FILE_FORMAT_INVALID")
    void rejectsMismatchedChecksumFixture() {
        Path bundle = ReplicationBundleFixtures.createValidPartitionBundle(tempDir.resolve("mismatched-sha.bundle"));
        String validSha256 = ReplicationBundleFixtures.calculateSha256(bundle);
        String badSha256 = ReplicationBundleFixtures.createMismatchedChecksum(validSha256);

        assertThatThrownBy(() -> SnapshotVerifier.verifyBundleFile(bundle, badSha256))
                .isInstanceOf(SpectorValidationException.class)
                .satisfies(e -> {
                    SpectorValidationException sve = (SpectorValidationException) e;
                    assertThat(sve.errorCode()).isEqualTo(ErrorCode.FILE_FORMAT_INVALID);
                    assertThat(sve.getMessage()).contains("SHA-256 checksum mismatch");
                });

        assertThat(SnapshotVerifier.failureCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Task 1.6 / Req R5.7 & R12.8: Mismatched pathHelper in manifest is rejected with ARGUMENT_INVALID")
    void rejectsMismatchedPathHelper() {
        SnapshotManifest manifest = new SnapshotManifest(
                SnapshotManifest.PLANE_NAMESPACE,
                1,
                TENANT,
                NS,
                ReplicationBundleFixtures.MISMATCHED_PATH_HELPER,
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

        assertThatThrownBy(() -> SnapshotVerifier.verifyManifest(manifest, EXPECTED_RESOLVER))
                .isInstanceOf(SpectorValidationException.class)
                .satisfies(e -> {
                    SpectorValidationException sve = (SpectorValidationException) e;
                    assertThat(sve.errorCode()).isEqualTo(ErrorCode.ARGUMENT_INVALID);
                    assertThat(sve.getMessage()).contains("pathHelper mismatch");
                    assertThat(sve.getMessage()).contains(EXPECTED_RESOLVER);
                    assertThat(sve.getMessage()).contains(ReplicationBundleFixtures.MISMATCHED_PATH_HELPER);
                });

        assertThat(SnapshotVerifier.failureCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Task 1.2 / Req R1.2: resolvePathHelper fails loud when namespace.json is absent")
    void resolvePathHelperFailsWhenMarkerAbsent() {
        Path emptyNsDir = tempDir.resolve("empty-ns");

        assertThatThrownBy(() -> SnapshotVerifier.resolvePathHelper(emptyNsDir))
                .isInstanceOf(SpectorValidationException.class)
                .satisfies(e -> {
                    SpectorValidationException sve = (SpectorValidationException) e;
                    assertThat(sve.errorCode()).isEqualTo(ErrorCode.ARGUMENT_INVALID);
                    assertThat(sve.getMessage()).contains("absent");
                });

        assertThat(SnapshotVerifier.failureCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Task 1.2 / Req R1.2: resolvePathHelper fails loud when namespace.json is unparseable")
    void resolvePathHelperFailsWhenMarkerCorrupted() throws IOException {
        Path nsDir = tempDir.resolve("corrupt-ns");
        Files.createDirectories(nsDir);
        Files.writeString(nsDir.resolve(StoragePaths.FILE_NAMESPACE), "{ not valid json @@@");

        assertThatThrownBy(() -> SnapshotVerifier.resolvePathHelper(nsDir))
                .isInstanceOf(SpectorValidationException.class)
                .satisfies(e -> {
                    SpectorValidationException sve = (SpectorValidationException) e;
                    assertThat(sve.errorCode()).isEqualTo(ErrorCode.ARGUMENT_INVALID);
                    assertThat(sve.getMessage()).contains("Failed to read namespace marker");
                });

        assertThat(SnapshotVerifier.failureCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Task 1.2 / Req R1.2: resolvePathHelper fails loud when marker lacks pathHelper and layout")
    void resolvePathHelperFailsWhenMarkerLacksFields() throws IOException {
        Path nsDir = tempDir.resolve("lacking-fields-ns");
        Files.createDirectories(nsDir);
        Files.writeString(nsDir.resolve(StoragePaths.FILE_NAMESPACE), "{\"tenantId\":\"acme\",\"namespaceId\":\"doc-1\"}");

        assertThatThrownBy(() -> SnapshotVerifier.resolvePathHelper(nsDir))
                .isInstanceOf(SpectorValidationException.class)
                .satisfies(e -> {
                    SpectorValidationException sve = (SpectorValidationException) e;
                    assertThat(sve.errorCode()).isEqualTo(ErrorCode.ARGUMENT_INVALID);
                    assertThat(sve.getMessage()).contains("lacks 'pathHelper' or 'layout'");
                });

        assertThat(SnapshotVerifier.failureCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Task 1.2 / Req R1.2: resolvePathHelper resolves pathHelper and fallback layout correctly")
    void resolvePathHelperResolvesCorrectly() throws IOException {
        Path nsDir1 = tempDir.resolve("valid-ns1");
        Files.createDirectories(nsDir1);
        Files.writeString(nsDir1.resolve(StoragePaths.FILE_NAMESPACE),
                "{\"pathHelper\":\"StoragePaths.tenantRootedNamespaceDir\",\"layout\":\"StoragePaths.tenantRootedNamespaceDir\"}");

        assertThat(SnapshotVerifier.resolvePathHelper(nsDir1))
                .isEqualTo("StoragePaths.tenantRootedNamespaceDir");

        Path nsDir2 = tempDir.resolve("valid-ns2");
        Files.createDirectories(nsDir2);
        Files.writeString(nsDir2.resolve(StoragePaths.FILE_NAMESPACE),
                "{\"layout\":\"StoragePaths.namespaceDirSharded\"}");

        assertThat(SnapshotVerifier.resolvePathHelper(nsDir2))
                .isEqualTo("StoragePaths.namespaceDirSharded");

        assertThat(SnapshotVerifier.failureCount()).isZero();
    }
}
