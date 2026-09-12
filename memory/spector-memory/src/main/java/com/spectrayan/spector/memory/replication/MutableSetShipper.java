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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;

/**
 * Ships mutable bundle sets (runtime.bundle + active partition.bundle) under a bounded quiesce window,
 * producing atomic INCREMENTAL snapshot manifests (ADR-0034 §9.7, Req R3.1, R3.4, R3.5, Task 3.2, 3.4).
 */
public final class MutableSetShipper {

    private static final Logger log = LoggerFactory.getLogger(MutableSetShipper.class);

    private MutableSetShipper() {}

    /**
     * Result of copying the active mutable bundle set.
     */
    public record MutableCopyResult(
            Path runtimeSnapshotPath,
            String runtimeSha256,
            Path activePartitionSnapshotPath,
            String activePartitionSha256,
            long hwmAtSnapshot,
            long pauseNanos,
            long totalBytes
    ) {}

    /**
     * Copies the active runtime bundle and active partition bundle under a brief quiesce lock,
     * recording pause duration and updating the byte tracker (Req R3.1, R3.5, D3).
     *
     * @param runtimeSourcePath   active runtime bundle file
     * @param partitionSourcePath active partition bundle file
     * @param targetDir           directory where snapshot files should be written
     * @param quiesceLock         write lock used to bound concurrent mutations during file copy
     * @param currentHwmSupplier  supplier providing the current WAL HWM
     * @param byteTracker         byte tracker for observability
     * @return copy result containing paths, checksums, and measured pause duration
     */
    public static MutableCopyResult copyMutableSet(
            Path runtimeSourcePath,
            Path partitionSourcePath,
            Path targetDir,
            Lock quiesceLock,
            java.util.function.LongSupplier currentHwmSupplier,
            ReplicationByteTracker byteTracker
    ) {
        Objects.requireNonNull(runtimeSourcePath, "runtimeSourcePath must not be null");
        Objects.requireNonNull(partitionSourcePath, "partitionSourcePath must not be null");
        Objects.requireNonNull(targetDir, "targetDir must not be null");
        Objects.requireNonNull(quiesceLock, "quiesceLock must not be null");

        Path runtimeTarget = targetDir.resolve(runtimeSourcePath.getFileName().toString());
        Path partitionTarget = targetDir.resolve(partitionSourcePath.getFileName().toString());

        long startNanos = System.nanoTime();
        long hwmAtSnapshot;
        quiesceLock.lock();
        try {
            hwmAtSnapshot = currentHwmSupplier.getAsLong();
            Files.createDirectories(targetDir);
            Files.copy(runtimeSourcePath, runtimeTarget, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(partitionSourcePath, partitionTarget, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy mutable bundle set", e);
        } finally {
            quiesceLock.unlock();
        }
        long pauseNanos = System.nanoTime() - startNanos;

        String runtimeSha = SnapshotVerifier.calculateSha256(runtimeTarget);
        String partitionSha = SnapshotVerifier.calculateSha256(partitionTarget);

        long totalBytes;
        try {
            totalBytes = Files.size(runtimeTarget) + Files.size(partitionTarget);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (byteTracker != null) {
            byteTracker.recordMutableBytes(totalBytes);
        }

        log.debug("[MutableSetShipper] Mutable set copied: pause={}ms, bytes={}",
                pauseNanos / 1_000_000.0, totalBytes);

        return new MutableCopyResult(
                runtimeTarget,
                runtimeSha,
                partitionTarget,
                partitionSha,
                hwmAtSnapshot,
                pauseNanos,
                totalBytes
        );
    }

    /**
     * Builds an INCREMENTAL SnapshotManifest describing the copied mutable set (Req R3.4).
     */
    public static SnapshotManifest buildIncrementalManifest(
            String tenantId,
            String namespaceId,
            String pathHelper,
            long epoch,
            MutableCopyResult copyResult,
            long walFrom,
            List<SnapshotManifest.SealedPartitionEntry> knownSealed
    ) {
        SnapshotManifest.RuntimeEntry runtime = new SnapshotManifest.RuntimeEntry(
                copyResult.runtimeSnapshotPath().getFileName().toString(),
                copyResult.runtimeSha256(),
                1L
        );
        SnapshotManifest.ActivePartitionEntry active = new SnapshotManifest.ActivePartitionEntry(
                copyResult.activePartitionSnapshotPath().getFileName().toString(),
                copyResult.activePartitionSha256()
        );

        return new SnapshotManifest(
                SnapshotManifest.PLANE_NAMESPACE,
                1,
                tenantId,
                namespaceId,
                pathHelper,
                epoch,
                copyResult.hwmAtSnapshot(),
                SnapshotKind.INCREMENTAL,
                runtime,
                active,
                knownSealed != null ? knownSealed : List.of(),
                walFrom,
                copyResult.hwmAtSnapshot(),
                null
        );
    }
}
