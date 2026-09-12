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
import com.spectrayan.spector.kernel.bundle.BundleFileLayout;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/**
 * Enforces offline snapshot verification and source marker resolution
 * (ADR-0034 §9.7, §10.2, Req R1.2, R1.6, R5.1, R5.7, R11.5, Task 1.2, Task 1.5).
 *
 * <p>Verification is exact with no partial-verification mode. Preamble magic ('SMKM'),
 * bundle layout ID ('BUND'), and SHA-256 checksums are verified before publishing or applying.
 * Path helpers are strictly resolved from source {@code namespace.json} and verified against
 * replica configuration to prevent ADR KI-3 silent directory corruption.</p>
 */
public final class SnapshotVerifier {

    private static final Logger log = LoggerFactory.getLogger(SnapshotVerifier.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final LongAdder FAILURE_COUNT = new LongAdder();

    private SnapshotVerifier() {}

    /**
     * Resolves the {@code pathHelper} layout identifier by reading the source {@code namespace.json}
     * marker file (Req R1.2, N6).
     *
     * <p>Refuses snapshot creation if the marker file is absent or unreadable, matching Phase 0.1 R8.2's
     * fail-loud contract.</p>
     *
     * @param namespaceDir directory of the source namespace
     * @return the recorded pathHelper layout identifier string
     * @throws SpectorValidationException if the marker is absent, unreadable, or missing layout attributes
     */
    public static String resolvePathHelper(Path namespaceDir) {
        Objects.requireNonNull(namespaceDir, "namespaceDir must not be null");
        Path markerFile = namespaceDir.resolve(StoragePaths.FILE_NAMESPACE);
        if (!Files.exists(markerFile)) {
            recordFailure("absent_marker", "Namespace marker " + StoragePaths.FILE_NAMESPACE + " absent in " + namespaceDir);
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Namespace marker '" + StoragePaths.FILE_NAMESPACE + "' is absent in " + namespaceDir
                            + ". Refusing snapshot (Req R1.2, N6)."
            );
        }

        JsonNode node;
        try {
            node = MAPPER.readTree(markerFile.toFile());
        } catch (Exception e) {
            recordFailure("unreadable_marker", "Failed to parse " + markerFile + ": " + e.getMessage());
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Failed to read namespace marker at " + markerFile + ": " + e.getMessage()
            );
        }

        String recorded = null;
        if (node.hasNonNull("pathHelper")) {
            recorded = node.get("pathHelper").asText();
        } else if (node.hasNonNull("layout")) {
            recorded = node.get("layout").asText();
        }

        if (recorded == null || recorded.isBlank()) {
            recordFailure("invalid_marker", "Marker lacks pathHelper and layout in " + markerFile);
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Namespace marker at " + markerFile + " lacks 'pathHelper' or 'layout'. Refusing snapshot (Req R1.2, N6)."
            );
        }

        return recorded;
    }

    /**
     * Verifies manifest fields and validates that {@code manifest.pathHelper()} matches
     * the replica's expected path resolver (Req R1.3, R5.7, N4, N6).
     *
     * @param manifest           the manifest to verify
     * @param expectedPathHelper the expected path helper identifier (e.g. from replica config)
     * @throws SpectorValidationException if manifest verification fails
     */
    public static void verifyManifest(SnapshotManifest manifest, String expectedPathHelper) {
        Objects.requireNonNull(manifest, "manifest must not be null");

        if (!SnapshotManifest.PLANE_NAMESPACE.equalsIgnoreCase(manifest.plane())) {
            recordFailure(manifest.namespaceId(), "invalid_plane", "Plane '" + manifest.plane() + "' is not 'namespace'");
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Invalid snapshot plane '" + manifest.plane() + "'. Must be '" + SnapshotManifest.PLANE_NAMESPACE + "' (Invariant N4)."
            );
        }

        if (manifest.manifestVersion() < 1) {
            recordFailure(manifest.namespaceId(), "invalid_version", "manifestVersion must be >= 1, got: " + manifest.manifestVersion());
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "manifestVersion must be >= 1, got: " + manifest.manifestVersion()
            );
        }

        if (expectedPathHelper != null && !expectedPathHelper.isBlank()) {
            if (!expectedPathHelper.equals(manifest.pathHelper())) {
                recordFailure(manifest.namespaceId(), "path_helper_mismatch",
                        "expected '" + expectedPathHelper + "', got '" + manifest.pathHelper() + "'");
                throw new SpectorValidationException(
                        ErrorCode.ARGUMENT_INVALID,
                        "pathHelper mismatch for namespace '" + manifest.namespaceId()
                                + "': expected '" + expectedPathHelper + "', got '" + manifest.pathHelper() + "' (Req R5.7, N6)."
                );
            }
        }

        // Structural identity-plane assertions (Invariant N4)
        if (manifest.runtime() != null) {
            ReplicationPathFilter.assertNotIdentityPlane(manifest.runtime().file());
        }
        if (manifest.activePartition() != null) {
            ReplicationPathFilter.assertNotIdentityPlane(manifest.activePartition().id());
        }
        for (SnapshotManifest.SealedPartitionEntry s : manifest.sealed()) {
            ReplicationPathFilter.assertNotIdentityPlane(s.id());
            if (s.objectRef() != null) {
                ReplicationPathFilter.assertNotIdentityPlane(s.objectRef());
            }
        }
        if (manifest.files() != null) {
            for (SnapshotManifest.FileEntry f : manifest.files()) {
                ReplicationPathFilter.assertNotIdentityPlane(f.path());
            }
        }
    }

    /**
     * Verifies bundle preamble magic ('SMKM'), bundle layout ID ('BUND'), and SHA-256
     * checksum against the manifest (Req R1.6, R5.1, N3).
     *
     * @param bundleFile     the bundle file to inspect
     * @param expectedSha256 the expected SHA-256 hex string from manifest
     * @throws SpectorValidationException if verification fails
     */
    public static void verifyBundleFile(Path bundleFile, String expectedSha256) {
        Objects.requireNonNull(bundleFile, "bundleFile must not be null");
        Objects.requireNonNull(expectedSha256, "expectedSha256 must not be null");

        Path normalizedFile = bundleFile.toAbsolutePath().normalize();
        if (bundleFile.toString().contains("..") || normalizedFile.toString().contains("..")) {
            throw new IllegalArgumentException("Path traversal sequence in bundleFile: " + bundleFile);
        }

        if (!Files.isRegularFile(normalizedFile) || !Files.isReadable(normalizedFile)) {
            recordFailure("file_unreadable", "Bundle file missing or unreadable: " + normalizedFile);
            throw new SpectorValidationException(
                    ErrorCode.FILE_FORMAT_INVALID,
                    "Bundle file missing or unreadable: " + normalizedFile
            );
        }

        try {
            long size = Files.size(normalizedFile);
            if (size < RegionPreamble.PREAMBLE_BYTES) {
                recordFailure("file_truncated", "Bundle file smaller than preamble: " + size + " bytes");
                throw new SpectorValidationException(
                        ErrorCode.FILE_FORMAT_INVALID,
                        "Bundle file " + normalizedFile + " is smaller than RegionPreamble: " + size + " bytes"
                );
            }

            try (FileChannel channel = FileChannel.open(normalizedFile, StandardOpenOption.READ)) {
                ByteBuffer preambleBuf = ByteBuffer.allocate(RegionPreamble.PREAMBLE_BYTES)
                        .order(java.nio.ByteOrder.nativeOrder());
                channel.read(preambleBuf);
                preambleBuf.flip();

                int magic = preambleBuf.getInt(0); // Offset 0
                if (magic != RegionPreamble.MAGIC) {
                    recordFailure("invalid_magic",
                            "expected 0x" + Integer.toHexString(RegionPreamble.MAGIC).toUpperCase()
                                    + ", got 0x" + Integer.toHexString(magic).toUpperCase());
                    throw new SpectorValidationException(
                            ErrorCode.FILE_FORMAT_INVALID,
                            "Invalid preamble magic: expected 0x" + Integer.toHexString(RegionPreamble.MAGIC).toUpperCase()
                                    + " ('SMKM'), got 0x" + Integer.toHexString(magic).toUpperCase()
                    );
                }

                int layoutId = preambleBuf.getInt(36); // Offset 36
                if (layoutId != BundleFileLayout.LAYOUT_ID) {
                    recordFailure("invalid_layout_id",
                            "expected 0x" + Integer.toHexString(BundleFileLayout.LAYOUT_ID).toUpperCase()
                                    + ", got 0x" + Integer.toHexString(layoutId).toUpperCase());
                    throw new SpectorValidationException(
                            ErrorCode.FILE_FORMAT_INVALID,
                            "Invalid bundle layout ID: expected 0x" + Integer.toHexString(BundleFileLayout.LAYOUT_ID).toUpperCase()
                                    + " ('BUND'), got 0x" + Integer.toHexString(layoutId).toUpperCase()
                    );
                }
            }

            String actualSha256 = calculateSha256(normalizedFile);
            if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
                recordFailure("sha256_mismatch",
                        "expected " + expectedSha256 + ", computed " + actualSha256);
                throw new SpectorValidationException(
                        ErrorCode.FILE_FORMAT_INVALID,
                        "SHA-256 checksum mismatch for " + normalizedFile
                                + ": expected " + expectedSha256 + ", computed " + actualSha256
                );
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Computes the SHA-256 hash of a file using a streaming 64KB buffer.
     */
    public static String calculateSha256(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (path.toString().contains("..") || normalizedPath.toString().contains("..")) {
            throw new IllegalArgumentException("Path traversal sequence in path: " + path);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            try (InputStream in = Files.newInputStream(normalizedPath)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns total number of verification failures recorded (Req R11.5).
     */
    public static long failureCount() {
        return FAILURE_COUNT.sum();
    }

    /**
     * Resets verification failure counter.
     */
    public static void resetFailureCount() {
        FAILURE_COUNT.reset();
    }

    private static void recordFailure(String check, String details) {
        recordFailure("unspecified", check, details);
    }

    private static void recordFailure(String namespaceId, String check, String details) {
        FAILURE_COUNT.increment();
        log.error("[SnapshotVerifier] Verification failure for namespace '{}': check='{}', details='{}'",
                namespaceId, check, details);
    }
}
