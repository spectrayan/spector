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

import com.spectrayan.spector.memory.persist.PartitionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * Listens to partition roll events and triggers an immediate snapshot for the newly sealed bundle
 * (ADR-0034 §9.7, Req R2.2, Task 2.2).
 *
 * <p>A freshly sealed bundle provides a durable, immutable boundary; convergent after WAL tail replay.</p>
 */
public final class PartitionRollReplicationTrigger implements PartitionManager.PartitionRollListener {

    private static final Logger log = LoggerFactory.getLogger(PartitionRollReplicationTrigger.class);

    private final Consumer<SnapshotManifest.SealedPartitionEntry> snapshotConsumer;
    private final LongAdder rollTriggerCount = new LongAdder();

    public PartitionRollReplicationTrigger(Consumer<SnapshotManifest.SealedPartitionEntry> snapshotConsumer) {
        this.snapshotConsumer = Objects.requireNonNull(snapshotConsumer, "snapshotConsumer must not be null");
    }

    @Override
    public void onPartitionRolled(int newlyFrozenSeq, Path partitionDir, Path bundlePath) {
        rollTriggerCount.increment();
        log.info("[PartitionRollReplicationTrigger] Triggering immediate snapshot on partition roll (seq={}, path={})",
                newlyFrozenSeq, bundlePath);

        if (bundlePath == null || !Files.isRegularFile(bundlePath)) {
            throw new IllegalStateException(
                    "Cannot trigger snapshot for rolled partition seq=" + newlyFrozenSeq
                            + ": bundle path is null or not a regular file: " + bundlePath);
        }

        String sha256 = SnapshotVerifier.calculateSha256(bundlePath);
        String id = bundlePath.getFileName().toString();

        SnapshotManifest.SealedPartitionEntry sealedEntry =
                new SnapshotManifest.SealedPartitionEntry(id, sha256, null);

        try {
            snapshotConsumer.accept(sealedEntry);
        } catch (Exception e) {
            log.error("[PartitionRollReplicationTrigger] Failed to process immediate snapshot for partition seq={}",
                    newlyFrozenSeq, e);
        }
    }

    /**
     * Total number of partition rolls that triggered an immediate snapshot.
     */
    public long rollTriggerCount() {
        return rollTriggerCount.sum();
    }
}
