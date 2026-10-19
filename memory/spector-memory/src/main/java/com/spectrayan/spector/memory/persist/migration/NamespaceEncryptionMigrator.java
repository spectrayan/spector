/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.memory.persist.migration;

import com.spectrayan.spector.commons.concurrent.SpectorTaskQueue;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.core.cognitive.SynapticTag128;
import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.bundle.PartitionBundle;
import com.spectrayan.spector.kernel.bundle.RuntimeBundle;
import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.error.SpectorEncryptionException;
import com.spectrayan.spector.memory.persist.DataEncryptor;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.WalEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;

/**
 * Re-encrypts an existing plaintext namespace (`schemaVersion 1.1.0`) in-place into an
 * ADR-0088 envelope-encrypted namespace (`schemaVersion 1.2.0`) under a per-namespace DEK.
 *
 * <p>Per ADR-0088 §3, §6, and §9, migration performs:</p>
 * <ol>
 *   <li>Opens {@code runtime/runtime.bundle} and all {@code partitions/NNN_epoch/partition.bundle}
 *       with the target {@link DataEncryptor}, converting plaintext segments into
 *       {@code EncryptedSegmentHeader} + 64 KiB {@code AES-256-GCM} authenticated pages.</li>
 *   <li>Re-stamps the 128-bit {@code synapticTagsLo} / {@code synapticTagsHi} Bloom filter
 *       masks in every indexed engram header using keyed {@code SipHash-2-4} ({@code dek_tag})
 *       from the raw tag strings preserved in {@code INDEX_MIDX}.</li>
 *   <li>Checkpoints and rewrites all {@code wal/wal-*.bin} chunks with {@code FLAG_ENCRYPTED}
 *       ({@code dek_wal} {@code AES-256-GCM}).</li>
 *   <li>Updates {@code .encryption-ready} and {@code manifest.json} with
 *       {@code "schemaVersion": "1.2.0"} and {@code "encryptionEnabled": true}.</li>
 * </ol>
 */
public final class NamespaceEncryptionMigrator {

    private static final Logger log = LoggerFactory.getLogger(NamespaceEncryptionMigrator.class);

    public record MigrationSummary(
            Path namespaceDir,
            int partitionBundlesEncrypted,
            int recordsReKeyed,
            int walEventsEncrypted,
            boolean runtimeBundleEncrypted
    ) {}

    private NamespaceEncryptionMigrator() {}

    /**
     * Synchronously migrates {@code namespaceDir} from plaintext to ADR-0088 envelope encryption.
     */
    public static MigrationSummary migrate(Path namespaceDir, DataEncryptor encryptor) throws IOException {
        Objects.requireNonNull(namespaceDir, "namespaceDir must not be null");
        if (encryptor == null || !encryptor.isEnabled()) {
            throw new SpectorEncryptionException(
                    ErrorCode.MEMORY_ENCRYPTION_KEY_MISSING,
                    "Namespace encryption migration requires an enabled DataEncryptor for " + namespaceDir);
        }
        if (!Files.exists(namespaceDir)) {
            throw new IOException("Namespace directory does not exist: " + namespaceDir);
        }

        Path runtimeBundlePath = StoragePaths.runtimeBundleFile(namespaceDir);
        Path partitionsRoot = StoragePaths.partitionsDir(namespaceDir);
        Path walDir = StoragePaths.walDir(namespaceDir);

        RuntimeBundle runtimeBundle = null;
        Map<Integer, PartitionBundle> partitionBundles = new LinkedHashMap<>();
        int recordsReKeyed = 0;
        int walEventsEncrypted = 0;
        boolean runtimeEncrypted = false;

        try {
            if (Files.exists(runtimeBundlePath)) {
                runtimeBundle = RuntimeBundle.Init.open(runtimeBundlePath, encryptor);
                runtimeEncrypted = true;
            }

            PartitionBundle defaultPartitionBundle = null;
            if (Files.exists(partitionsRoot) && Files.isDirectory(partitionsRoot)) {
                List<Path> partDirs;
                try (var stream = Files.list(partitionsRoot)) {
                    partDirs = stream
                            .filter(Files::isDirectory)
                            .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                            .toList();
                }
                for (Path partDir : partDirs) {
                    Path bundleFile = resolvePartitionBundleFile(partDir);
                    if (bundleFile != null && Files.exists(bundleFile)) {
                        int seq = parsePartitionSeq(partDir.getFileName().toString());
                        PartitionBundle pb = PartitionBundle.Init.open(bundleFile, encryptor);
                        partitionBundles.put(seq, pb);
                        defaultPartitionBundle = pb;
                    }
                }
            }

            // Re-stamp 128-bit synaptic tag masks in engram headers using keyed SipHash-2-4
            if (runtimeBundle != null && runtimeBundle.hasRegion(RegionId.INDEX_MIDX)
                    && runtimeBundle.hasRegion(RegionId.INDEX_IDPL)) {
                MemoryIndex index = MemoryIndex.fromRegionRefs(
                        runtimeBundle.regionRef(RegionId.INDEX_MIDX),
                        runtimeBundle.regionRef(RegionId.INDEX_IDPL),
                        runtimeBundle.bundlePath(),
                        false);

                for (String id : index.allIds()) {
                    MemoryLocation loc = index.locate(id);
                    if (loc == null) {
                        continue;
                    }
                    String[] tags = index.tags(id);
                    long lo = 0L;
                    long hi = 0L;
                    if (tags != null) {
                        for (String tag : tags) {
                            if (tag != null && !tag.isEmpty()) {
                                SynapticTag128 t = encryptor.encodeTag128(tag);
                                lo |= t.lo();
                                hi |= t.hi();
                            }
                        }
                    }

                    if (writeTierSynapticTags(loc, lo, hi, runtimeBundle, partitionBundles, defaultPartitionBundle)) {
                        recordsReKeyed++;
                    }
                }
            }

            // Encrypt WAL chunks and record post-migration checkpoint watermark
            if (Files.exists(walDir) && Files.isDirectory(walDir)) {
                try (MemoryWal wal = new MemoryWal(walDir, encryptor)) {
                    WalEvent snapEvent = wal.appendSnapshotMark("migration-v1.2", wal.highWaterMark());
                    wal.flush();
                    wal.rewriteAllChunksEncrypted();
                    walEventsEncrypted = wal.size();
                    if (runtimeBundle != null) {
                        runtimeBundle.writeCheckpointWalWatermark(snapEvent.sequence());
                    }
                }
            }

            // Flush all partition bundles and runtime bundle to persist encrypted pages
            for (PartitionBundle pb : partitionBundles.values()) {
                pb.flush();
            }
            if (runtimeBundle != null) {
                runtimeBundle.flush();
            }
        } catch (com.spectrayan.spector.commons.error.SpectorMemoryException e) {
            throw SpectorEncryptionException.from(e, e.getMessage());
        } finally {
            for (PartitionBundle pb : partitionBundles.values()) {
                try {
                    pb.close();
                } catch (Exception ignored) {
                }
            }
            if (runtimeBundle != null) {
                try {
                    runtimeBundle.close();
                } catch (Exception ignored) {
                }
            }
        }

        writeEncryptionMetadata(namespaceDir);
        log.info("[NamespaceEncryptionMigrator] Migrated namespace '{}' to ADR-0088 envelope encryption "
                        + "(partitions={}, recordsReKeyed={}, walEvents={})",
                namespaceDir.getFileName(), partitionBundles.size(), recordsReKeyed, walEventsEncrypted);

        return new MigrationSummary(
                namespaceDir,
                partitionBundles.size(),
                recordsReKeyed,
                walEventsEncrypted,
                runtimeEncrypted);
    }

    /**
     * Submits an asynchronous online re-encryption job onto the supplied {@link SpectorTaskQueue}
     * adhering to Model B concurrency (ADR-0077).
     */
    public static CompletableFuture<MigrationSummary> migrateAsync(
            Path namespaceDir,
            DataEncryptor encryptor,
            SpectorTaskQueue<Runnable> taskQueue) {
        Objects.requireNonNull(taskQueue, "taskQueue must not be null");
        CompletableFuture<MigrationSummary> future = new CompletableFuture<>();
        boolean accepted = taskQueue.submit("migrate-encrypt-" + namespaceDir.getFileName(), () -> {
            try {
                future.complete(migrate(namespaceDir, encryptor));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        if (!accepted) {
            future.completeExceptionally(new IllegalStateException(
                    "Task queue rejected encryption migration task for " + namespaceDir));
        }
        return future;
    }

    private static Path resolvePartitionBundleFile(Path partDir) {
        Path v4Bundle = StoragePaths.partitionBundleFile(partDir);
        if (Files.exists(v4Bundle)) {
            return v4Bundle;
        }
        Path legacyBundle = partDir.resolve("bundle.mem");
        if (Files.exists(legacyBundle)) {
            return legacyBundle;
        }
        return null;
    }

    private static boolean writeTierSynapticTags(
            MemoryLocation loc,
            long lo,
            long hi,
            RuntimeBundle runtimeBundle,
            Map<Integer, PartitionBundle> partitionBundles,
            PartitionBundle defaultPartitionBundle) {
        MemoryType type = loc.type();
        long recordOff = loc.offset();
        if (type == MemoryType.WORKING) {
            return runtimeBundle != null
                    && runtimeBundle.writeSynapticTags128(RegionId.WORKING, recordOff, lo, hi);
        }
        PartitionBundle pb = partitionBundles.getOrDefault(loc.colocatedPartition(), defaultPartitionBundle);
        if (pb == null) {
            return false;
        }
        return switch (type) {
            case SEMANTIC -> pb.writeSynapticTags128(RegionId.SEMANTIC, recordOff, lo, hi);
            case PROCEDURAL -> pb.writeSynapticTags128(RegionId.PROCEDURAL, recordOff, lo, hi);
            default -> false;
        };
    }

    private static int parsePartitionSeq(String dirName) {
        Matcher m = StoragePaths.PARTITION_DIR_PATTERN.matcher(dirName);
        if (m.matches()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        if (dirName.startsWith("part-")) {
            try {
                return Integer.parseInt(dirName.substring("part-".length()));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private static void writeEncryptionMetadata(Path namespaceDir) throws IOException {
        Instant now = Instant.now();
        Path markerPath = namespaceDir.resolve(".encryption-ready");
        String markerJson = String.format(
                "{\"migratedAt\": \"%s\", \"encryptionEnabled\": true, \"schemaVersion\": \"1.2.0\", \"cipherSuite\": \"AES-256-GCM\"}\n",
                now);
        Files.writeString(markerPath, markerJson);

        Path manifestPath = namespaceDir.resolve(MigrationPipeline.MANIFEST_FILE);
        String manifestJson = String.format("""
                {
                  "schemaVersion": "1.2.0",
                  "walVersion": "1.2.0",
                  "indexVersion": "1.2.0",
                  "encryptionVersion": "1.2.0",
                  "encryptionEnabled": true,
                  "lastMigration": "%s"
                }
                """, now);
        Files.writeString(manifestPath, manifestJson);
    }
}
