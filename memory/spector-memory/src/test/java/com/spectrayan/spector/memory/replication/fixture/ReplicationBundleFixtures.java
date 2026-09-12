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
package com.spectrayan.spector.memory.replication.fixture;

import com.spectrayan.spector.kernel.bundle.PartitionBundle;
import com.spectrayan.spector.kernel.bundle.RuntimeBundle;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.layout.TextBlobLayout;
import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.region.RegionSizeSpec;
import com.spectrayan.spector.kernel.shape.MemoryShape;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Fixture factory for producing valid and deliberately corrupted V4 bundle files
 * for offline snapshot verification testing (ADR-0034 §9.7, §10.2, Req R12.8, Task 0.1, Task 1.6).
 */
public final class ReplicationBundleFixtures {

    public static final int TEST_DIMS = 16;
    public static final int TEST_SEM_CAP = 10;
    public static final int TEST_EPI_CAP = 5;
    public static final EngramLayout TEST_COG_LAYOUT = new EngramLayout(TEST_DIMS);
    public static final long TEST_EPI_BYTES = 4096L;
    public static final int TEST_PROC_CAP = 5;
    public static final long TEST_TEXT_BYTES = 4096L;
    public static final TextBlobLayout TEST_TEXT_LAYOUT = new TextBlobLayout();

    public static final int BAD_MAGIC = 0x44454144; // 'DEAD' != 'SMKM' (0x534D4B4D)
    public static final int BAD_LAYOUT_ID = 0x58585858; // 'XXXX' != 'BUND' (0x42554E44)
    public static final String MISMATCHED_PATH_HELPER = "StoragePaths.invalidResolverMarker";

    private ReplicationBundleFixtures() {}

    /**
     * Creates a valid V4 partition bundle on disk.
     */
    public static Path createValidPartitionBundle(Path destination) {
        try {
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            try (PartitionBundle bundle = PartitionBundle.Init.mmap(
                    destination,
                    TEST_SEM_CAP,
                    TEST_EPI_BYTES,
                    TEST_PROC_CAP,
                    TEST_TEXT_BYTES,
                    TEST_DIMS,
                    TEST_COG_LAYOUT.layoutId(),
                    TEST_COG_LAYOUT.schemaVersion(),
                    TEST_TEXT_LAYOUT.layoutId(),
                    TEST_TEXT_LAYOUT.schemaVersion()
            )) {
                // Instantiation writes preamble, subheader, and page-aligned region headers
            }
            return destination;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Creates a valid V4 runtime bundle on disk.
     */
    public static Path createValidRuntimeBundle(Path destination) {
        try {
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            List<RegionSizeSpec> specs = List.of(
                    new RegionSizeSpec(RegionId.WORKING, 4096, 10, 64, 0x574F524B, 1, false),
                    new RegionSizeSpec(RegionId.INDEX_MIDX, 4096, 10, 32, 0x494E4458, 1, true)
            );
            try (RuntimeBundle bundle = RuntimeBundle.Init.mmap(destination, specs)) {
                // Initialized and closed cleanly
            }
            return destination;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Creates a partition bundle corrupted with an invalid preamble magic (offset 0 != 'SMKM').
     */
    public static Path createCorruptedMagicBundle(Path destination) {
        createValidPartitionBundle(destination);
        try (FileChannel ch = FileChannel.open(destination, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer buf = ByteBuffer.allocate(4);
            buf.putInt(BAD_MAGIC);
            buf.flip();
            ch.write(buf, 0); // Offset 0 is RegionPreamble.MAGIC
            ch.force(true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return destination;
    }

    /**
     * Creates a partition bundle corrupted with an invalid preamble layout ID (offset 36 != 'BUND').
     */
    public static Path createCorruptedLayoutIdBundle(Path destination) {
        createValidPartitionBundle(destination);
        try (FileChannel ch = FileChannel.open(destination, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer buf = ByteBuffer.allocate(4);
            buf.putInt(BAD_LAYOUT_ID);
            buf.flip();
            ch.write(buf, 36); // Offset 36 is layoutId
            ch.force(true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return destination;
    }

    /**
     * Creates a partition bundle and returns a SHA-256 checksum that intentionally does NOT match.
     */
    public static String calculateSha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Creates a deliberately invalid SHA-256 checksum.
     */
    public static String createMismatchedChecksum(String validSha256) {
        if (validSha256.startsWith("0")) {
            return "f" + validSha256.substring(1);
        } else {
            return "0" + validSha256.substring(1);
        }
    }
}
