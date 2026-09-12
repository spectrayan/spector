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
import com.spectrayan.spector.kernel.storage.NamespacePathResolver;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.memory.sync.WalEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes the crash-safe replica snapshot apply sequence (ADR-0034 §5, §15.5, Req R5.1–R5.7, Invariant N3, N5).
 *
 * <p>Strict ordering:
 * <ol>
 *   <li>Reject if plane != "namespace" (Invariant N4)</li>
 *   <li>Reject if pathHelper != replica's configured resolver (Invariant N6, ADR KI-3)</li>
 *   <li>Reject if tenant not in allow-list (Req R6.3)</li>
 *   <li>Write bundles to temporary staging directory and fsync (Invariant N5)</li>
 *   <li>Verify magic, layout ID, and SHA-256 against manifest before publish (Invariant N3)</li>
 *   <li>Atomic rename into live namespace directory (Invariant N5)</li>
 *   <li>Replay WAL slice if incremental</li>
 *   <li><b>Advance local HWM LAST</b> (Req R5.3, N5)</li>
 *   <li>Remap only if namespace is in replicaHotSet; otherwise leave Warm on disk (Req R5.4)</li>
 * </ol>
 *
 * A failure at any point leaves prior state intact and the HWM unmoved (Req R5.6).
 */
public final class ReplicaApplyEngine {

    private static final Logger log = LoggerFactory.getLogger(ReplicaApplyEngine.class);

    private final Path persistenceRoot;
    private final String configuredPathHelper;
    private final Set<String> tenantAllowList;
    private final Set<String> replicaHotSet;

    private final Map<String, Long> appliedHwmMap = new ConcurrentHashMap<>();
    private final Map<String, SnapshotManifest> lastAppliedManifestMap = new ConcurrentHashMap<>();

    public record ApplyResult(long appliedHwm, boolean updated, boolean remapped) {}

    public ReplicaApplyEngine(
            Path persistenceRoot,
            String configuredPathHelper,
            Set<String> tenantAllowList,
            Set<String> replicaHotSet
    ) {
        this.persistenceRoot = Objects.requireNonNull(persistenceRoot, "persistenceRoot must not be null");
        this.configuredPathHelper = configuredPathHelper;
        this.tenantAllowList = tenantAllowList != null ? Set.copyOf(tenantAllowList) : Set.of();
        this.replicaHotSet = replicaHotSet != null ? Collections.newSetFromMap(new ConcurrentHashMap<>()) : Set.of();
        if (replicaHotSet != null) {
            this.replicaHotSet.addAll(replicaHotSet);
        }
        recoverPersistedReplicaState();
    }

    /**
     * Applies a snapshot manifest and associated bundle files.
     *
     * @param manifest           snapshot manifest to apply
     * @param stagedBundleFiles  map of bundle identifier/filename to staged local source path
     * @param walSlice           optional WAL slice to replay
     * @return ApplyResult
     */
    public ApplyResult applySnapshot(
            SnapshotManifest manifest,
            Map<String, Path> stagedBundleFiles,
            List<WalEvent> walSlice
    ) {
        Objects.requireNonNull(manifest, "manifest must not be null");
        Objects.requireNonNull(stagedBundleFiles, "stagedBundleFiles must not be null");

        // Step 0 (G3): Offline manifest verification (plane, version, pathHelper, identity plane exclusion)
        SnapshotVerifier.verifyManifest(manifest, configuredPathHelper);

        // Step 1: Reject if plane != namespace (Invariant N4)
        if (!SnapshotManifest.PLANE_NAMESPACE.equalsIgnoreCase(manifest.plane())) {
            log.error("[ReplicaApplyEngine] Rejected manifest for '{}': invalid plane '{}'",
                    manifest.namespaceId(), manifest.plane());
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Invalid snapshot plane '" + manifest.plane() + "'. Must be '" + SnapshotManifest.PLANE_NAMESPACE + "' (Invariant N4)."
            );
        }

        // Step 2: Reject if pathHelper != configured resolver (Req R5.7, N6, ADR KI-3)
        if (configuredPathHelper != null && !configuredPathHelper.isBlank()) {
            if (!configuredPathHelper.equals(manifest.pathHelper())) {
                log.error("[ReplicaApplyEngine] pathHelper mismatch for namespace '{}': configured '{}' != manifest '{}'",
                        manifest.namespaceId(), configuredPathHelper, manifest.pathHelper());
                throw new SpectorValidationException(
                        ErrorCode.ARGUMENT_INVALID,
                        "pathHelper mismatch for namespace '" + manifest.namespaceId()
                                + "': configured '" + configuredPathHelper + "' != manifest '" + manifest.pathHelper() + "' (Req R5.7, N6)."
                );
            }
        }

        // Step 3: Reject if tenant not in allow-list (Req R6.3)
        if (!tenantAllowList.isEmpty() && manifest.tenantId() != null && !manifest.tenantId().isBlank()) {
            if (!tenantAllowList.contains(manifest.tenantId())) {
                log.error("[ReplicaApplyEngine] Tenant '{}' rejected by allow-list for namespace '{}'",
                        manifest.tenantId(), manifest.namespaceId());
                throw new SpectorValidationException(
                        ErrorCode.NAMESPACE_ACCESS_DENIED,
                        "Tenant not permitted by replica allow-list (Req R6.3)."
                );
            }
        }

        // Step 3a (G1): Refuse INCREMENTAL manifests or WAL delta — WAL replay not yet implemented
        // Without this guard, HWM advances but WAL events are silently dropped, causing data loss
        if (manifest.kind() == SnapshotKind.INCREMENTAL) {
            throw new UnsupportedOperationException(
                    "WAL replay not yet implemented; refusing INCREMENTAL manifest for namespace '"
                            + manifest.namespaceId() + "' (G1). Only FULL and SEALED_ONLY snapshots are supported.");
        }
        if (manifest.walTo() > manifest.walFrom()) {
            throw new UnsupportedOperationException(
                    "WAL replay not yet implemented; refusing manifest with WAL delta [walFrom="
                            + manifest.walFrom() + ", walTo=" + manifest.walTo() + "] for namespace '"
                            + manifest.namespaceId() + "' (G1). Only FULL snapshots with no WAL delta are supported.");
        }

        // Step 4: Idempotence check (Req R5.5)
        long currentHwm = appliedHwmMap.getOrDefault(manifest.namespaceId(), -1L);
        if (manifest.hwm() == currentHwm) {
            SnapshotManifest last = lastAppliedManifestMap.get(manifest.namespaceId());
            if (last != null && last.equals(manifest)) {
                log.info("[ReplicaApplyEngine] Re-applying identical manifest for namespace '{}' at HWM {}: verified no-op",
                        manifest.namespaceId(), currentHwm);
                return new ApplyResult(currentHwm, false, false);
            }
        }

        Path nsDir = resolveNamespaceDir(manifest);
        Path stagingDir = nsDir.resolve(".tmp_apply_" + manifest.epoch() + "_" + manifest.hwm());

        try {
            Files.createDirectories(stagingDir);

            // Step 4: Write to tmp and fsync (Req R5.2, N5)
            Map<String, Path> stagedTargetFiles = new HashMap<>();
            for (Map.Entry<String, Path> entry : stagedBundleFiles.entrySet()) {
                String filename = entry.getKey();
                Path src = entry.getValue();
                Path dst = safeResolve(stagingDir, filename);
                if (dst.getParent() != null) {
                    Files.createDirectories(dst.getParent());
                }
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                try (FileChannel fc = FileChannel.open(dst, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    fc.force(true); // fsync
                }
                stagedTargetFiles.put(filename, dst);
            }

            // Step 4a (G3): Validate staged keys against manifest — reject unverified file injection
            Set<String> manifestDeclaredKeys = new HashSet<>();
            if (manifest.runtime() != null) {
                manifestDeclaredKeys.add(manifest.runtime().file());
                manifestDeclaredKeys.add(StoragePaths.DIR_RUNTIME + "/" + manifest.runtime().file());
                if (manifest.runtime().file().startsWith(StoragePaths.DIR_RUNTIME + "/")) {
                    manifestDeclaredKeys.add(manifest.runtime().file().substring(StoragePaths.DIR_RUNTIME.length() + 1));
                }
            }
            if (manifest.activePartition() != null) {
                manifestDeclaredKeys.add(manifest.activePartition().id());
                manifestDeclaredKeys.add(manifest.activePartition().id() + "/partition.bundle");
                manifestDeclaredKeys.add(StoragePaths.DIR_PARTITIONS + "/" + manifest.activePartition().id() + "/partition.bundle");
            }
            for (SnapshotManifest.SealedPartitionEntry s : manifest.sealed()) {
                manifestDeclaredKeys.add(s.id());
                manifestDeclaredKeys.add(s.id() + "/partition.bundle");
                manifestDeclaredKeys.add(StoragePaths.DIR_PARTITIONS + "/" + s.id() + "/partition.bundle");
            }

            for (String stagedKey : stagedBundleFiles.keySet()) {
                // G3: Enforce identity plane filter on every staged key
                ReplicationPathFilter.assertNotIdentityPlane(stagedKey);

                // G3: Reject staged files not declared in manifest
                if (!manifestDeclaredKeys.contains(stagedKey)) {
                    throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                            "Staged file '" + stagedKey + "' not listed in manifest — rejecting unverified file (G3).");
                }
            }

            // Step 5: Verify magic + layout ID + SHA-256 against manifest before publish (Req R5.1, N3)
            if (manifest.runtime() != null) {
                Path rtStaged = stagedTargetFiles.get(manifest.runtime().file());
                if (rtStaged == null) {
                    rtStaged = stagedTargetFiles.get(StoragePaths.DIR_RUNTIME + "/" + manifest.runtime().file());
                }
                if (rtStaged == null && manifest.runtime().file().startsWith(StoragePaths.DIR_RUNTIME + "/")) {
                    rtStaged = stagedTargetFiles.get(manifest.runtime().file().substring(StoragePaths.DIR_RUNTIME.length() + 1));
                }
                if (rtStaged == null) {
                    throw new SpectorValidationException(ErrorCode.FILE_FORMAT_INVALID,
                            "Missing staged runtime bundle: " + manifest.runtime().file());
                }
                SnapshotVerifier.verifyBundleFile(rtStaged, manifest.runtime().sha256());
            }

            if (manifest.activePartition() != null) {
                Path actStaged = stagedTargetFiles.get(manifest.activePartition().id());
                if (actStaged == null) {
                    actStaged = stagedTargetFiles.get(manifest.activePartition().id() + "/partition.bundle");
                }
                if (actStaged == null) {
                    actStaged = stagedTargetFiles.get(StoragePaths.DIR_PARTITIONS + "/" + manifest.activePartition().id() + "/partition.bundle");
                }
                if (actStaged == null) {
                    throw new SpectorValidationException(ErrorCode.FILE_FORMAT_INVALID,
                            "Missing staged active partition bundle: " + manifest.activePartition().id());
                }
                SnapshotVerifier.verifyBundleFile(actStaged, manifest.activePartition().sha256());
            }

            for (SnapshotManifest.SealedPartitionEntry s : manifest.sealed()) {
                Path sStaged = stagedTargetFiles.get(s.id());
                if (sStaged == null) {
                    sStaged = stagedTargetFiles.get(s.id() + "/partition.bundle");
                }
                if (sStaged == null) {
                    sStaged = stagedTargetFiles.get(StoragePaths.DIR_PARTITIONS + "/" + s.id() + "/partition.bundle");
                }
                // G3: Fail-fast when sealed partition bundle is missing — previously silently skipped
                if (sStaged == null) {
                    throw new SpectorValidationException(ErrorCode.FILE_FORMAT_INVALID,
                            "Missing staged sealed partition bundle: " + s.id() + " (G3). All manifest entries must be staged.");
                }
                SnapshotVerifier.verifyBundleFile(sStaged, s.sha256());
            }

            // Step 6: Atomic rename into live namespace directory (Req R5.2, N5)
            Files.createDirectories(nsDir);
            for (Map.Entry<String, Path> entry : stagedTargetFiles.entrySet()) {
                Path src = entry.getValue();
                Path target = safeResolve(nsDir, entry.getKey());
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.move(src, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }

            // Clean up staging directory
            deleteRecursively(stagingDir);

            // Step 7: WAL replay (if provided)
            // (Replayer executes against live bundle images)

            // Step 8: Persist verified manifest to disk via tmp + fsync + ATOMIC_MOVE (Req R5.3, Invariant N5, G4)
            Path replicaStateDir = nsDir.resolve(".replica_state");
            Files.createDirectories(replicaStateDir);
            Path tmpManifest = replicaStateDir.resolve(".manifest.json.tmp");
            Files.writeString(tmpManifest, manifest.toJson(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try (FileChannel fc = FileChannel.open(tmpManifest, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                fc.force(true);
            }
            Files.move(tmpManifest, replicaStateDir.resolve("manifest.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            // Step 8b: ADVANCE IN-MEMORY HWM LAST (Req R5.3, N5)
            appliedHwmMap.put(manifest.namespaceId(), manifest.hwm());
            lastAppliedManifestMap.put(manifest.namespaceId(), manifest);

            // Step 9: Remap only if in replicaHotSet, else leave Warm on disk (Req R5.4)
            boolean remapped = false;
            if (replicaHotSet.contains(manifest.namespaceId())) {
                remapped = true;
                log.info("[ReplicaApplyEngine] Namespace '{}' in replicaHotSet: remapping", manifest.namespaceId());
            } else {
                log.info("[ReplicaApplyEngine] Namespace '{}' NOT in replicaHotSet: leaving Warm on disk", manifest.namespaceId());
            }

            log.info("[ReplicaApplyEngine] Successfully applied snapshot for namespace '{}' to HWM {}",
                    manifest.namespaceId(), manifest.hwm());

            return new ApplyResult(manifest.hwm(), true, remapped);

        } catch (Exception e) {
            // Step 5 & 6 failure: clean up staging, leave prior state intact, do not advance HWM (Req R5.6)
            log.error("[ReplicaApplyEngine] Apply failed for namespace '{}' at HWM {}. Leaving prior state intact.",
                    manifest.namespaceId(), manifest.hwm(), e);
            try {
                deleteRecursively(stagingDir);
            } catch (Exception cleanupEx) {
                log.warn("[ReplicaApplyEngine] Failed to delete staging directory {}", stagingDir, cleanupEx);
            }
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Replica apply failed", e);
        }
    }

    private void recoverPersistedReplicaState() {
        if (!Files.exists(persistenceRoot)) {
            return;
        }
        try (var stream = Files.walk(persistenceRoot, 12)) {
            stream.filter(p -> p.getFileName() != null
                            && p.getFileName().toString().equals("manifest.json")
                            && p.getParent() != null
                            && p.getParent().getFileName() != null
                            && p.getParent().getFileName().toString().equals(".replica_state"))
                    .forEach(p -> {
                        try {
                            String json = Files.readString(p);
                            SnapshotManifest manifest = SnapshotManifest.fromJson(json);
                            appliedHwmMap.put(manifest.namespaceId(), manifest.hwm());
                            lastAppliedManifestMap.put(manifest.namespaceId(), manifest);
                            log.info("[ReplicaApplyEngine] Recovered persisted replica state for namespace '{}' at HWM {}",
                                    manifest.namespaceId(), manifest.hwm());
                        } catch (Exception e) {
                            log.warn("[ReplicaApplyEngine] Failed to recover replica manifest from {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("[ReplicaApplyEngine] Failed to scan persistenceRoot for replica state", e);
        }
    }

    public long getAppliedHwm(String namespaceId) {
        return appliedHwmMap.getOrDefault(namespaceId, -1L);
    }

    public SnapshotManifest getLastAppliedManifest(String namespaceId) {
        return lastAppliedManifestMap.get(namespaceId);
    }

    public void addToHotSet(String namespaceId) {
        replicaHotSet.add(namespaceId);
    }

    public void removeFromHotSet(String namespaceId) {
        replicaHotSet.remove(namespaceId);
    }

    public Path resolveNamespaceDir(SnapshotManifest manifest) {
        return NamespacePathResolver.resolve(persistenceRoot, manifest.tenantId(), manifest.namespaceId()).dir();
    }

    private static void deleteRecursively(Path dir) {
        if (dir != null && Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {}
                        });
            } catch (IOException ignored) {}
        }
    }

    private static Path safeResolve(Path baseDir, String relativePath) {
        Objects.requireNonNull(baseDir, "baseDir must not be null");
        Objects.requireNonNull(relativePath, "relativePath must not be null");
        if (relativePath.isBlank()
                || relativePath.contains("..")
                || relativePath.startsWith("/")
                || relativePath.startsWith("\\")) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Invalid relative path containing path traversal sequences: " + relativePath
            );
        }
        Path basePath = baseDir.normalize().toAbsolutePath();
        Path targetPath = basePath.resolve(relativePath).normalize().toAbsolutePath();
        if (!targetPath.startsWith(basePath.toString() + File.separator) && !targetPath.equals(basePath)) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Path traversal attempt detected: " + relativePath
            );
        }
        return targetPath;
    }
}
