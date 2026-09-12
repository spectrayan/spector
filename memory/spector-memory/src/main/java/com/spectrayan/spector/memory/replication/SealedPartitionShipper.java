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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manages sealed partition shipping, enforcing the Sealed-Once Invariant (N1), cold-tier sourcing (B4),
 * sealed-set divergence detection (Req R2.5), and byte-identity verification (Req R2.3).
 */
public final class SealedPartitionShipper {

    private static final Logger log = LoggerFactory.getLogger(SealedPartitionShipper.class);

    private final ColdTierObjectSource coldTierSource;

    public SealedPartitionShipper() {
        this(null);
    }

    public SealedPartitionShipper(ColdTierObjectSource coldTierSource) {
        this.coldTierSource = coldTierSource;
    }

    /**
     * Plan describing how sealed partitions should be shipped to a replica.
     */
    public record SealedShippingPlan(
            List<SnapshotManifest.SealedPartitionEntry> toShipFromOwner,
            List<SnapshotManifest.SealedPartitionEntry> toFetchFromColdTier,
            List<SnapshotManifest.SealedPartitionEntry> alreadyPresent,
            List<String> divergentPartitions
    ) {
        public boolean hasDivergence() {
            return !divergentPartitions.isEmpty();
        }
    }

    /**
     * Evaluates owner sealed partitions against replica known state to produce a shipping plan
     * (Req R2.1, R2.4, R2.5, Invariant N1, Blocker B4).
     *
     * @param ownerSealed          list of sealed partitions currently recorded by owner
     * @param replicaKnownChecksums map of partitionId -> sha256 currently held by replica
     * @return planned shipping actions
     * @throws SealedSetDivergenceException if any existing replica partition has a checksum mismatch
     */
    public SealedShippingPlan planShipping(
            List<SnapshotManifest.SealedPartitionEntry> ownerSealed,
            Map<String, String> replicaKnownChecksums
    ) {
        Objects.requireNonNull(ownerSealed, "ownerSealed must not be null");
        Objects.requireNonNull(replicaKnownChecksums, "replicaKnownChecksums must not be null");

        List<SnapshotManifest.SealedPartitionEntry> toShipFromOwner = new ArrayList<>();
        List<SnapshotManifest.SealedPartitionEntry> toFetchFromColdTier = new ArrayList<>();
        List<SnapshotManifest.SealedPartitionEntry> alreadyPresent = new ArrayList<>();
        List<String> divergentPartitions = new ArrayList<>();

        for (SnapshotManifest.SealedPartitionEntry entry : ownerSealed) {
            String replicaSha = replicaKnownChecksums.get(entry.id());
            if (replicaSha != null) {
                if (entry.sha256().equalsIgnoreCase(replicaSha)) {
                    // Invariant N1 / Req R2.1: Shipped once per replica, skip while checksum matches
                    alreadyPresent.add(entry);
                } else {
                    // Req R2.5: Sealed-set divergence detected
                    log.error("[SealedPartitionShipper] Sealed-set divergence detected for '{}': owner {} != replica {}",
                            entry.id(), entry.sha256(), replicaSha);
                    divergentPartitions.add(entry.id());
                }
            } else {
                // Replica is missing this sealed partition
                if (coldTierSource != null && entry.objectRef() != null && coldTierSource.hasObject(entry.objectRef())) {
                    // Blocker B4 / Req R2.4: Prefer cold tier when object exists
                    toFetchFromColdTier.add(entry);
                } else {
                    // Fall back to owner
                    toShipFromOwner.add(entry);
                }
            }
        }

        if (!divergentPartitions.isEmpty()) {
            String firstDiv = divergentPartitions.get(0);
            SnapshotManifest.SealedPartitionEntry ownerEntry = ownerSealed.stream()
                    .filter(e -> e.id().equals(firstDiv))
                    .findFirst()
                    .orElse(null);
            String ownerSha = ownerEntry != null ? ownerEntry.sha256() : "unknown";
            throw new SealedSetDivergenceException(firstDiv, ownerSha, replicaKnownChecksums.get(firstDiv));
        }

        return new SealedShippingPlan(
                List.copyOf(toShipFromOwner),
                List.copyOf(toFetchFromColdTier),
                List.copyOf(alreadyPresent),
                List.copyOf(divergentPartitions)
        );
    }

    /**
     * Ships a sealed partition bundle from the owner to the destination path, tracking bytes transferred
     * (Req R2.1, R2.6, R11.2).
     */
    public long shipFromOwner(Path sourceBundle, Path destination, ReplicationByteTracker byteTracker) {
        Objects.requireNonNull(sourceBundle, "sourceBundle must not be null");
        Objects.requireNonNull(destination, "destination must not be null");

        try {
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            Files.copy(sourceBundle, destination, StandardCopyOption.REPLACE_EXISTING);
            long bytes = Files.size(destination);
            if (byteTracker != null) {
                byteTracker.recordSealedBytes(bytes);
            }
            return bytes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Fetches a sealed partition bundle from cold tier, tracking bytes transferred (Req R2.4, R2.6, R11.2).
     */
    public long fetchFromColdTier(String objectRef, Path destination, ReplicationByteTracker byteTracker) {
        Objects.requireNonNull(objectRef, "objectRef must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        if (coldTierSource == null) {
            throw new IllegalStateException("ColdTierObjectSource is not configured");
        }

        try {
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            coldTierSource.fetchObject(objectRef, destination);
            long bytes = Files.size(destination);
            if (byteTracker != null) {
                byteTracker.recordSealedBytes(bytes);
            }
            return bytes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Asserts that a sealed bundle file is byte-identical to its corresponding cold-tier object (Req R2.3).
     *
     * @param sealedBundle   local sealed bundle file
     * @param objectRef      cold tier object reference
     * @param coldTierSource cold tier source
     * @throws SpectorValidationException if checksums do not match
     */
    public static void assertColdTierByteIdentity(
            Path sealedBundle,
            String objectRef,
            ColdTierObjectSource coldTierSource
    ) {
        Objects.requireNonNull(sealedBundle, "sealedBundle must not be null");
        Objects.requireNonNull(objectRef, "objectRef must not be null");
        Objects.requireNonNull(coldTierSource, "coldTierSource must not be null");

        String localSha = SnapshotVerifier.calculateSha256(sealedBundle);
        String remoteSha;
        try {
            remoteSha = coldTierSource.calculateSha256(objectRef);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compute cold-tier SHA-256 for " + objectRef, e);
        }

        if (!localSha.equalsIgnoreCase(remoteSha)) {
            throw new SpectorValidationException(
                    ErrorCode.FILE_FORMAT_INVALID,
                    "Sealed bundle " + sealedBundle + " is not byte-identical to cold-tier object " + objectRef
                            + ": local " + localSha + " != remote " + remoteSha + " (Req R2.3)."
            );
        }
    }
}
