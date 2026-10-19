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
package com.spectrayan.spector.kernel.bundle;

import com.spectrayan.spector.kernel.bundle.BundleFileLayoutCalculator;

import com.spectrayan.spector.kernel.bundle.BundleFileLayout;

import com.spectrayan.spector.kernel.id.MemoryId;

import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;

import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.region.RegionEntry;
import com.spectrayan.spector.kernel.region.RegionSizeSpec;
import com.spectrayan.spector.kernel.layout.RegionLayout;

import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.layout.StrengthLayout;
import com.spectrayan.spector.kernel.layout.EpisodicLayout;

/**
 * A V4 partition bundle — packs 4 cognitive tier regions (Semantic, Episodic,
 * Procedural, Text) into a single mmap'd file with one shared {@link Arena}.
 *
 * <h3>On-Disk Format</h3>
 * <pre>
 * ┌─────────────────────────────────────┐  offset 0
 * │ 64B RegionPreamble (SMKM)          │  shape=BUNDLE, layoutId=BUND
 * ├─────────────────────────────────────┤  offset 64
 * │ 64B BundleSubHeader (SPTB)         │  magic=SPTB, totalFileSize, etc.
 * ├─────────────────────────────────────┤  offset 128
 * │ RegionEntry[0..3] (64B × 4)        │  SEMANTIC, EPISODIC, PROCEDURAL, TEXT
 * ├─────────────────────────────────────┤  page-aligned (4096B)
 * │ Region 0: SEMANTIC data            │
 * │ Region 1: EPISODIC data            │
 * │ Region 2: PROCEDURAL data          │
 * │ Region 3: TEXT data                │
 * └─────────────────────────────────────┘
 * </pre>
 *
 * <p>Uses the {@code EntityDirectory.Init} initialization API:
 * {@code Init.mmap()} to create, {@code Init.open()} to load,
 * {@code Init.heap()} for tests.</p>
 *
 * <p>Partition regions are <b>fixed-size</b> — they never grow in place.
 * When a partition overflows, the system rolls to a new partition directory
 * with a fresh bundle file.</p>
 *
 * @since 1.2.0
 * @see BundleDirectory
 * @see BundleFileLayoutCalculator
 */
public final class PartitionBundle implements AbstractBundle {

    private static final Logger log = LoggerFactory.getLogger(PartitionBundle.class);

    private final Arena arena;
    private final MemorySegment masterSegment;
    private final BundleDirectory directory;
    private final Path bundlePath;
    private final boolean isNew;
    private final boolean canForward;
    private final com.spectrayan.spector.kernel.storage.PayloadEncryptor encryptor;
    private volatile PartitionBundle rolledTo;
    private final java.util.concurrent.ConcurrentHashMap<RegionId, java.util.concurrent.atomic.AtomicInteger> generations = new java.util.concurrent.ConcurrentHashMap<>();

    private PartitionBundle(Arena arena, MemorySegment masterSegment,
                             BundleDirectory directory, Path bundlePath, boolean isNew) {
        this(arena, masterSegment, directory, bundlePath, isNew, true, null);
    }

    private PartitionBundle(Arena arena, MemorySegment masterSegment,
                             BundleDirectory directory, Path bundlePath, boolean isNew,
                             boolean canForward) {
        this(arena, masterSegment, directory, bundlePath, isNew, canForward, null);
    }

    private PartitionBundle(Arena arena, MemorySegment masterSegment,
                             BundleDirectory directory, Path bundlePath, boolean isNew,
                             boolean canForward,
                             com.spectrayan.spector.kernel.storage.PayloadEncryptor encryptor) {
        this.arena = arena;
        this.masterSegment = masterSegment;
        this.directory = directory;
        this.bundlePath = bundlePath;
        this.isNew = isNew;
        this.canForward = canForward;
        this.encryptor = encryptor;
    }

    /**
     * Returns true if this partition bundle is backed by an active {@link com.spectrayan.spector.kernel.storage.PayloadEncryptor}.
     */
    public boolean isEncrypted() {
        return encryptor != null && encryptor.isEnabled();
    }

    // ── Init factory (follows EntityDirectory.Init pattern) ──

    /**
     * Factory methods for creating and opening partition bundles.
     */
    public static final class Init {

        private Init() {} // static utility

        /**
         * Creates a new partition bundle file with 4 regions.
         *
         * <p>Computes the total file size from the region specs, creates the file,
         * maps it, writes the directory header, and returns an open bundle.</p>
         *
         * @param path               path to the new bundle file
         * @param semanticCapacity   max records for semantic region
         * @param episodicBytes      allocated bytes for the episodic log region (variable-length)
         * @param proceduralCapacity max records for procedural region
         * @param textBytes          allocated bytes for the text append region
         * @param quantizedVecBytes  bytes per quantized vector (for stride calculation)
         * @param cognitiveLayoutId  the layoutId from EngramLayout
         * @param cognitiveSchemaVer the schemaVersion from EngramLayout
         * @param textLayoutId       the layoutId from TextBlobLayout
         * @param textSchemaVer      the schemaVersion from TextBlobLayout
         * @return an open PartitionBundle ready for use
         */
        public static PartitionBundle mmap(Path path,
                                            int semanticCapacity, long episodicBytes,
                                            int proceduralCapacity, long textBytes,
                                            int quantizedVecBytes,
                                            int cognitiveLayoutId, int cognitiveSchemaVer,
                                            int textLayoutId, int textSchemaVer) {
            return mmap(path, semanticCapacity, episodicBytes, proceduralCapacity, textBytes,
                    quantizedVecBytes, cognitiveLayoutId, cognitiveSchemaVer, textLayoutId, textSchemaVer, null);
        }

        /**
         * Creates a new partition bundle file with optional at-rest page encryption.
         */
        public static PartitionBundle mmap(Path path,
                                            int semanticCapacity, long episodicBytes,
                                            int proceduralCapacity, long textBytes,
                                            int quantizedVecBytes,
                                            int cognitiveLayoutId, int cognitiveSchemaVer,
                                            int textLayoutId, int textSchemaVer,
                                            com.spectrayan.spector.kernel.storage.PayloadEncryptor encryptor) {
            int cogStride = computeCognitiveStride(quantizedVecBytes);
            int auditStride = StrengthLayout.INSTANCE.recordStride();
            int episodicCapEstimate = (int) (episodicBytes / Math.max(1, cogStride));
            if (episodicCapEstimate <= 0) episodicCapEstimate = 1_000;
            int totalAuditCapacity = semanticCapacity + episodicCapEstimate + proceduralCapacity;

            List<RegionSizeSpec> specs = List.of(
                    new RegionSizeSpec(
                            RegionId.SEMANTIC,
                            RegionPreamble.PREAMBLE_BYTES + (long) semanticCapacity * cogStride,
                            semanticCapacity, cogStride, cognitiveLayoutId, cognitiveSchemaVer, false),
                    new RegionSizeSpec(
                            RegionId.EPISODIC,
                            RegionPreamble.PREAMBLE_BYTES + episodicBytes,
                            0, 0, EpisodicLayout.INSTANCE.layoutId(),
                            EpisodicLayout.INSTANCE.schemaVersion(), false),
                    new RegionSizeSpec(
                            RegionId.PROCEDURAL,
                            RegionPreamble.PREAMBLE_BYTES + (long) proceduralCapacity * cogStride,
                            proceduralCapacity, cogStride, cognitiveLayoutId, cognitiveSchemaVer, false),
                    new RegionSizeSpec(
                            RegionId.TEXT,
                            RegionPreamble.PREAMBLE_BYTES + textBytes,
                            0, 0, textLayoutId, textSchemaVer, false),
                    new RegionSizeSpec(
                            RegionId.STRENGTH,
                            RegionPreamble.PREAMBLE_BYTES + (long) totalAuditCapacity * auditStride,
                            totalAuditCapacity, auditStride, StrengthLayout.INSTANCE.layoutId(),
                            StrengthLayout.INSTANCE.schemaVersion(), false)
            );

            BundleFileLayoutCalculator.BundleComputedLayout computed =
                    BundleFileLayoutCalculator.compute(BundleSubHeader.MAGIC_PARTITION, specs);

            long totalFileSize = computed.totalFileSize();
            BundleDirectory dir = computed.directory();

            Arena arena = Arena.ofShared();
            try {
                if (encryptor != null && encryptor.isEnabled()) {
                    MemorySegment offHeap = arena.allocate(totalFileSize, 4096);
                    dir.write(offHeap);
                    com.spectrayan.spector.kernel.storage.EncryptedSegmentPageCodec.writeEncryptedSegment(
                            path, offHeap,
                            com.spectrayan.spector.kernel.storage.EncryptedSegmentHeader.SEGMENT_TYPE_PARTITION_BUNDLE,
                            encryptor);
                    log.info("Created encrypted partition bundle: {} ({} regions, {}KB)",
                            path, specs.size(), totalFileSize / 1024);
                    return new PartitionBundle(arena, offHeap, dir, path, true, true, encryptor);
                }

                // Create and size the file
                try (FileChannel fc = FileChannel.open(path,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    // Pre-allocate via write at end
                    fc.write(java.nio.ByteBuffer.allocate(1), totalFileSize - 1);
                    MemorySegment mapped = fc.map(FileChannel.MapMode.READ_WRITE, 0, totalFileSize, arena);
                    // fc is closed after map per Option C (P0 hotfix)
                    dir.write(mapped);
                    log.info("Created partition bundle: {} ({} regions, {}KB)",
                            path, specs.size(), totalFileSize / 1024);
                    return new PartitionBundle(arena, mapped, dir, path, true, true, null);
                }
            } catch (RuntimeException e) {
                arena.close();
                throw e;
            } catch (IOException e) {
                arena.close();
                throw new UncheckedIOException("Failed to create partition bundle: " + path, e);
            }
        }

        /**
         * Opens an existing partition bundle file, validates headers.
         *
         * @param path path to the existing bundle file
         * @return an open PartitionBundle
         */
        public static PartitionBundle open(Path path) {
            return open(path, null);
        }

        /**
         * Opens an existing partition bundle file with optional at-rest page encryption.
         */
        public static PartitionBundle open(Path path,
                                            com.spectrayan.spector.kernel.storage.PayloadEncryptor encryptor) {
            Arena arena = Arena.ofShared();
            try {
                boolean encryptedFile = com.spectrayan.spector.kernel.storage.EncryptedSegmentHeader.isEncryptedFile(path);
                if (encryptedFile) {
                    MemorySegment decrypted = com.spectrayan.spector.kernel.storage.EncryptedSegmentPageCodec.readEncryptedSegment(
                            path,
                            com.spectrayan.spector.kernel.storage.EncryptedSegmentHeader.SEGMENT_TYPE_PARTITION_BUNDLE,
                            encryptor,
                            arena);
                    BundleDirectory dir = BundleDirectory.read(decrypted, BundleSubHeader.MAGIC_PARTITION);
                    log.info("Opened encrypted partition bundle: {} ({} live regions, {}KB)",
                            path, dir.liveRegionCount(), decrypted.byteSize() / 1024);
                    return new PartitionBundle(arena, decrypted, dir, path, false, true, encryptor);
                }

                long fileSize = Files.size(path);
                if (encryptor != null && encryptor.isEnabled()) {
                    MemorySegment offHeap = arena.allocate(Math.max(4096L, fileSize), 4096);
                    byte[] rawBytes = Files.readAllBytes(path);
                    MemorySegment.copy(rawBytes, 0, offHeap, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, rawBytes.length);
                    java.util.Arrays.fill(rawBytes, (byte) 0);
                    BundleDirectory dir = BundleDirectory.read(offHeap, BundleSubHeader.MAGIC_PARTITION);
                    com.spectrayan.spector.kernel.storage.EncryptedSegmentPageCodec.writeEncryptedSegment(
                            path, offHeap,
                            com.spectrayan.spector.kernel.storage.EncryptedSegmentHeader.SEGMENT_TYPE_PARTITION_BUNDLE,
                            encryptor);
                    log.info("Upgraded and opened partition bundle with at-rest encryption: {} ({} live regions, {}KB)",
                            path, dir.liveRegionCount(), fileSize / 1024);
                    return new PartitionBundle(arena, offHeap, dir, path, false, true, encryptor);
                }

                try (FileChannel fc = FileChannel.open(path,
                        StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    MemorySegment mapped = fc.map(FileChannel.MapMode.READ_WRITE, 0, fileSize, arena);
                    // fc closed after map per Option C

                    // Magic enforced here, as RuntimeBundle.Init.open already did. Without it a
                    // runtime.bundle opened as a partition bundle was accepted and simply found no partition
                    // regions — a confusing absence rather than a clear refusal.
                    BundleDirectory dir = BundleDirectory.read(mapped, BundleSubHeader.MAGIC_PARTITION);
                    log.info("Opened partition bundle: {} ({} live regions, {}KB)",
                            path, dir.liveRegionCount(), fileSize / 1024);
                    return new PartitionBundle(arena, mapped, dir, path, false, true, null);
                }
            } catch (RuntimeException e) {
                arena.close();
                throw e;
            } catch (IOException e) {
                arena.close();
                throw new UncheckedIOException("Failed to open partition bundle: " + path, e);
            }
        }

        /**
         * Creates an in-memory (heap) partition bundle for testing.
         *
         * @param semanticCapacity   max records for semantic region
         * @param episodicBytes      allocated bytes for episodic region
         * @param proceduralCapacity max records for procedural region
         * @param textBytes          allocated bytes for the text append region
         * @param quantizedVecBytes  bytes per quantized vector
         * @param cognitiveLayoutId  the layoutId from EngramLayout
         * @param cognitiveSchemaVer the schemaVersion from EngramLayout
         * @param textLayoutId       the layoutId from TextBlobLayout
         * @param textSchemaVer      the schemaVersion from TextBlobLayout
         * @return an in-memory PartitionBundle
         */
        public static PartitionBundle heap(int semanticCapacity, long episodicBytes,
                                            int proceduralCapacity, long textBytes,
                                            int quantizedVecBytes,
                                            int cognitiveLayoutId, int cognitiveSchemaVer,
                                            int textLayoutId, int textSchemaVer) {
            int cogStride = computeCognitiveStride(quantizedVecBytes);
            int auditStride = StrengthLayout.INSTANCE.recordStride();
            int episodicCapEstimate = (int) (episodicBytes / Math.max(1, cogStride));
            if (episodicCapEstimate <= 0) episodicCapEstimate = 1_000;
            int totalAuditCapacity = semanticCapacity + episodicCapEstimate + proceduralCapacity;

            List<RegionSizeSpec> specs = List.of(
                    new RegionSizeSpec(
                            RegionId.SEMANTIC,
                            RegionPreamble.PREAMBLE_BYTES + (long) semanticCapacity * cogStride,
                            semanticCapacity, cogStride, cognitiveLayoutId, cognitiveSchemaVer, false),
                    new RegionSizeSpec(
                            RegionId.EPISODIC,
                            RegionPreamble.PREAMBLE_BYTES + episodicBytes,
                            0, 0, EpisodicLayout.INSTANCE.layoutId(),
                            EpisodicLayout.INSTANCE.schemaVersion(), false),
                    new RegionSizeSpec(
                            RegionId.PROCEDURAL,
                            RegionPreamble.PREAMBLE_BYTES + (long) proceduralCapacity * cogStride,
                            proceduralCapacity, cogStride, cognitiveLayoutId, cognitiveSchemaVer, false),
                    new RegionSizeSpec(
                            RegionId.TEXT,
                            RegionPreamble.PREAMBLE_BYTES + textBytes,
                            0, 0, textLayoutId, textSchemaVer, false),
                    new RegionSizeSpec(
                            RegionId.STRENGTH,
                            RegionPreamble.PREAMBLE_BYTES + (long) totalAuditCapacity * auditStride,
                            totalAuditCapacity, auditStride, StrengthLayout.INSTANCE.layoutId(),
                            StrengthLayout.INSTANCE.schemaVersion(), false)
            );

            BundleFileLayoutCalculator.BundleComputedLayout computed =
                    BundleFileLayoutCalculator.compute(BundleSubHeader.MAGIC_PARTITION, specs);

            long totalSize = computed.totalFileSize();
            BundleDirectory dir = computed.directory();

            Arena arena = Arena.ofShared();
            MemorySegment segment = arena.allocate(totalSize, 4096);
            dir.write(segment);

            log.info("Created in-memory partition bundle ({} regions, {}KB)", specs.size(), totalSize / 1024);
            return new PartitionBundle(arena, segment, dir, null, true);
        }

        /**
         * Computes the cognitive record stride from the quantized vector bytes.
         * This mirrors EngramLayout.stride() = EncodingHeaderFields.HEADER_BYTES + quantizedVecBytes
         */
        private static int computeCognitiveStride(int quantizedVecBytes) {
            // EncodingHeaderFields.HEADER_BYTES = 64
            return 64 + quantizedVecBytes;
        }
    }

    // ── Public API ──

    public void rollTo(PartitionBundle next) {
        this.rolledTo = next;
    }

    public PartitionBundle asFrozen() {
        return new PartitionBundle(arena, masterSegment, directory, bundlePath, false, false, encryptor);
    }

    @Override
    public MemorySegment currentSlice(RegionId id) {
        if (canForward && rolledTo != null) {
            return rolledTo.currentSlice(id);
        }
        return regionSegment(id);
    }

    @Override
    public int generation(RegionId id) {
        if (canForward && rolledTo != null) {
            return rolledTo.generation(id);
        }
        return generations.computeIfAbsent(id, _ -> new java.util.concurrent.atomic.AtomicInteger(0)).get();
    }

    @Override
    public RegionLease lease(RegionId id) {
        if (canForward && rolledTo != null) {
            return rolledTo.lease(id);
        }
        return new RegionLease(currentSlice(id), () -> {});
    }

    // ── Specialized Typed Region Openers ──

    public com.spectrayan.spector.kernel.store.SemanticMemory openSemantic(int semanticCapacity, int quantizedVecBytes) {
        return com.spectrayan.spector.kernel.store.SemanticMemory.fromRegionRef(regionRef(RegionId.SEMANTIC), semanticCapacity, quantizedVecBytes, bundlePath, isNew);
    }

    public com.spectrayan.spector.kernel.store.EpisodicMemory openEpisodic(int episodicPartitionCapacity) {
        return com.spectrayan.spector.kernel.store.EpisodicMemory.fromRegionRef(regionRef(RegionId.EPISODIC), episodicPartitionCapacity, bundlePath, isNew);
    }

    public com.spectrayan.spector.kernel.store.ProceduralMemory openProcedural(int proceduralCapacity, int quantizedVecBytes) {
        return com.spectrayan.spector.kernel.store.ProceduralMemory.fromRegionRef(regionRef(RegionId.PROCEDURAL), proceduralCapacity, quantizedVecBytes, bundlePath, isNew);
    }

    public com.spectrayan.spector.kernel.store.TextBlobMemory openText(Object encryptor) {
        return com.spectrayan.spector.kernel.store.TextBlobMemory.fromRegionRef(regionRef(RegionId.TEXT), bundlePath, isNew, encryptor);
    }

    public com.spectrayan.spector.kernel.store.StrengthMemory openStrength(int semanticCapacity, int episodicCapacity, int proceduralCapacity, String auditName) {
        return hasRegion(RegionId.STRENGTH)
                ? com.spectrayan.spector.kernel.store.StrengthMemory.fromRegionRef(regionRef(RegionId.STRENGTH), semanticCapacity, episodicCapacity, proceduralCapacity, bundlePath, auditName)
                : null;
    }

    // ── Generic RegionOpener Implementation ──

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> com.spectrayan.spector.kernel.shape.RecordMemory<L> openRecord(RegionId id, L layout) {
        RegionRef ref = regionRef(id);
        int count = hasRegion(id) ? (int) RegionPreamble.readCount(currentSlice(id), 0L) : 0;
        return new com.spectrayan.spector.kernel.shape.DefaultRecordMemory<>(com.spectrayan.spector.kernel.id.MemoryId.of("bundle", id.name()), layout, 0, ref, count, bundlePath != null, bundlePath);
    }

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> com.spectrayan.spector.kernel.shape.AppendMemory<L> openAppend(RegionId id, L layout) {
        RegionRef ref = regionRef(id);
        int count = hasRegion(id) ? (int) RegionPreamble.readCount(currentSlice(id), 0L) : 0;
        return new com.spectrayan.spector.kernel.shape.DefaultAppendMemory<>(com.spectrayan.spector.kernel.id.MemoryId.of("bundle", id.name()), layout, 0, ref, count, bundlePath != null, bundlePath);
    }

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> com.spectrayan.spector.kernel.shape.GraphMemory<L> openGraph(RegionId id, L layout) {
        RegionRef ref = regionRef(id);
        int count = hasRegion(id) ? (int) RegionPreamble.readCount(currentSlice(id), 0L) : 0;
        return new com.spectrayan.spector.kernel.shape.DefaultGraphMemory<>(com.spectrayan.spector.kernel.id.MemoryId.of("bundle", id.name()), layout, 1000, 2000, ref, count, bundlePath != null, bundlePath);
    }

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> com.spectrayan.spector.kernel.shape.ChainMemory<L> openChain(RegionId id, L layout) {
        RegionRef ref = regionRef(id);
        int count = hasRegion(id) ? (int) RegionPreamble.readCount(currentSlice(id), 0L) : 0;
        return new com.spectrayan.spector.kernel.shape.DefaultChainMemory<>(com.spectrayan.spector.kernel.id.MemoryId.of("bundle", id.name()), layout, 0, ref, count, bundlePath != null, bundlePath);
    }

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> com.spectrayan.spector.kernel.shape.HashTableMemory<L> openHashTable(RegionId id, L layout) {
        RegionRef ref = regionRef(id);
        int count = hasRegion(id) ? (int) RegionPreamble.readCount(currentSlice(id), 0L) : 0;
        return new com.spectrayan.spector.kernel.shape.DefaultHashTableMemory<>(com.spectrayan.spector.kernel.id.MemoryId.of("bundle", id.name()), layout, 0, ref, count, bundlePath != null, bundlePath);
    }

    @Override
    public com.spectrayan.spector.kernel.shape.RegistryMemory openRegistry(RegionId id) {
        RegionRef ref = regionRef(id);
        int count = hasRegion(id) ? (int) RegionPreamble.readCount(currentSlice(id), 0L) : 0;
        return new com.spectrayan.spector.kernel.shape.DefaultRegistryMemory(com.spectrayan.spector.kernel.id.MemoryId.of("bundle", id.name()), new com.spectrayan.spector.kernel.layout.RegistryLayout(), 0, ref, count, bundlePath != null, bundlePath);
    }

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> java.util.Optional<com.spectrayan.spector.kernel.shape.RecordMemory<L>> tryOpenRecord(RegionId id, L layout) {
        if (!hasRegion(id)) return java.util.Optional.empty();
        return java.util.Optional.of(openRecord(id, layout));
    }

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> java.util.Optional<com.spectrayan.spector.kernel.shape.AppendMemory<L>> tryOpenAppend(RegionId id, L layout) {
        if (!hasRegion(id)) return java.util.Optional.empty();
        return java.util.Optional.of(openAppend(id, layout));
    }

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> java.util.Optional<com.spectrayan.spector.kernel.shape.GraphMemory<L>> tryOpenGraph(RegionId id, L layout) {
        if (!hasRegion(id)) return java.util.Optional.empty();
        return java.util.Optional.of(openGraph(id, layout));
    }

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> java.util.Optional<com.spectrayan.spector.kernel.shape.ChainMemory<L>> tryOpenChain(RegionId id, L layout) {
        if (!hasRegion(id)) return java.util.Optional.empty();
        return java.util.Optional.of(openChain(id, layout));
    }

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> java.util.Optional<com.spectrayan.spector.kernel.shape.HashTableMemory<L>> tryOpenHashTable(RegionId id, L layout) {
        if (!hasRegion(id)) return java.util.Optional.empty();
        return java.util.Optional.of(openHashTable(id, layout));
    }

    @Override
    public java.util.Optional<com.spectrayan.spector.kernel.shape.RegistryMemory> tryOpenRegistry(RegionId id) {
        if (!hasRegion(id)) return java.util.Optional.empty();
        return java.util.Optional.of(openRegistry(id));
    }

    MemorySegment regionSegment(RegionId id) {
        RegionEntry entry = directory.findRegion(id);
        if (entry == null) {
            throw new IllegalArgumentException("Region not found in partition bundle: " + id);
        }
        if (!entry.isLive()) {
            throw new IllegalStateException("Region is not live: " + id);
        }
        return masterSegment.asSlice(entry.offset(), entry.allocatedSize());
    }

    @Override
    public boolean hasRegion(RegionId id) {
        RegionEntry entry = directory.findRegion(id);
        return entry != null && entry.isLive();
    }

    Arena arena() {
        return arena;
    }

    /**
     * Returns the bundle directory with all region entries.
     */
    public BundleDirectory directory() {
        return directory;
    }

    /**
     * Returns the path to the bundle file (null for heap bundles).
     */
    public Path bundlePath() {
        return bundlePath;
    }

    /**
     * Whether this bundle was just created (regions need header initialization).
     */
    public boolean isNew() {
        return isNew;
    }

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    /**
     * Returns true if this partition bundle is closed or its arena has been closed.
     */
    public boolean isClosed() {
        return isClosed.get() || (arena != null && !arena.scope().isAlive());
    }

    /**
     * Persists the partition summary into the bundle header page at offset 512 (64 bytes)
     * with CRC32C checksum and flushes the header slice to disk if file-backed.
     *
     * @param summary the partition summary header to persist
     */
    public void writeSummary(PartitionSummaryHeader summary) {
        if (summary == null) {
            return;
        }
        if (masterSegment != null && masterSegment.byteSize() >= PartitionSummaryHeader.OFFSET + PartitionSummaryHeader.SIZE) {
            summary.write(masterSegment);
            if (bundlePath != null && arena != null && arena.scope().isAlive()) {
                if (isEncrypted()) {
                    flush();
                } else if (masterSegment.isMapped()) {
                    masterSegment.asSlice(PartitionSummaryHeader.OFFSET, PartitionSummaryHeader.SIZE).force();
                }
            }
        }
    }

    /**
     * Convenience method to write summary fields directly.
     */
    public void writeSummary(int seq, long minTimestampMs, long maxTimestampMs,
                             long synapticTagMaskLo, long synapticTagMaskHi,
                             int semanticCount, int episodicCount, int proceduralCount) {
        writeSummary(new PartitionSummaryHeader(
                seq, minTimestampMs, maxTimestampMs,
                synapticTagMaskLo, synapticTagMaskHi,
                semanticCount, episodicCount, proceduralCount));
    }

    /**
     * Reads and CRC-validates the persisted partition summary header at offset 512.
     *
     * @return the validated PartitionSummaryHeader, or {@code null} if absent or CRC mismatch
     */
    public PartitionSummaryHeader readSummary() {
        if (masterSegment == null || masterSegment.byteSize() < PartitionSummaryHeader.OFFSET + PartitionSummaryHeader.SIZE) {
            return null;
        }
        return PartitionSummaryHeader.read(masterSegment);
    }

    /**
     * Returns true if a valid CRC32C-verified summary header is present in this bundle.
     */
    public boolean hasValidSummary() {
        if (masterSegment == null || masterSegment.byteSize() < PartitionSummaryHeader.OFFSET + PartitionSummaryHeader.SIZE) {
            return false;
        }
        return PartitionSummaryHeader.isValid(masterSegment);
    }

    /**
     * Flushes the directory and forces the master segment to disk.
     */
    public void flush() {
        if (bundlePath != null && arena != null && arena.scope().isAlive()) {
            directory.write(masterSegment);
            if (isEncrypted()) {
                try {
                    com.spectrayan.spector.kernel.storage.EncryptedSegmentPageCodec.writeEncryptedSegment(
                            bundlePath, masterSegment,
                            com.spectrayan.spector.kernel.storage.EncryptedSegmentHeader.SEGMENT_TYPE_PARTITION_BUNDLE,
                            encryptor);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to flush encrypted partition bundle: " + bundlePath, e);
                }
            } else if (masterSegment.isMapped()) {
                masterSegment.force();
            }
        }
    }

    /**
     * Flushes the directory and region data to disk, then closes the arena.
     *
     * <p>After close, all region segments become invalid. Stores backed by
     * this bundle must have already been closed (which flushes their slices).</p>
     */
    @Override
    public void close() {
        if (!isClosed.compareAndSet(false, true)) {
            return;
        }
        try {
            if (bundlePath != null && arena != null && arena.scope().isAlive()) {
                flush();
            }
        } catch (Exception e) {
            log.debug("Error flushing partition bundle: {}", e.getMessage());
        }
        try {
            if (arena != null && arena.scope().isAlive()) {
                if (isEncrypted() && masterSegment != null) {
                    masterSegment.fill((byte) 0);
                    java.lang.invoke.VarHandle.fullFence();
                }
                arena.close();
            }
        } catch (IllegalStateException e) {
            log.debug("Partition bundle arena already closed: {}", e.getMessage());
        }
        log.info("Closed partition bundle: {}", bundlePath);
    }
}
