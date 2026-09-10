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
package com.spectrayan.spector.kernel.store;

import com.spectrayan.spector.kernel.api.EngramMemory;
import com.spectrayan.spector.kernel.api.KernelSpec;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.api.NamespaceKernel;
import com.spectrayan.spector.kernel.bundle.PartitionBundle;
import com.spectrayan.spector.kernel.bundle.RuntimeBundle;
import com.spectrayan.spector.kernel.layout.CoActivationLayout;
import com.spectrayan.spector.kernel.layout.ContinuityLayout;
import com.spectrayan.spector.kernel.layout.EpisodicLayout;
import com.spectrayan.spector.kernel.layout.HebbianLayout;
import com.spectrayan.spector.kernel.layout.HyperEntityLayout;
import com.spectrayan.spector.kernel.layout.IndexEntryLayout;
import com.spectrayan.spector.kernel.layout.InsularLayout;
import com.spectrayan.spector.kernel.layout.ProceduralLayout;
import com.spectrayan.spector.kernel.layout.ProvenanceLayout;
import com.spectrayan.spector.kernel.layout.SemanticLayout;
import com.spectrayan.spector.kernel.layout.StrengthLayout;
import com.spectrayan.spector.kernel.layout.TemporalFactLayout;
import com.spectrayan.spector.kernel.layout.TemporalLayout;
import com.spectrayan.spector.kernel.layout.TextBlobLayout;
import com.spectrayan.spector.kernel.layout.WalLayout;
import com.spectrayan.spector.kernel.layout.WorkingLayout;
import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.region.RegionSizeSpec;
import com.spectrayan.spector.kernel.shape.AppendMemory;
import com.spectrayan.spector.kernel.shape.ChainMemory;
import com.spectrayan.spector.kernel.shape.GraphMemory;
import com.spectrayan.spector.kernel.shape.HashTableMemory;
import com.spectrayan.spector.kernel.shape.RecordMemory;
import com.spectrayan.spector.kernel.shape.RegistryMemory;
import com.spectrayan.spector.kernel.storage.StoragePaths;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default package-private implementation of {@link NamespaceKernel}.
 */
public class DefaultNamespaceKernel implements NamespaceKernel {

    private final Path directory;
    private final KernelSpec spec;
    private final String namespaceId;

    private final RuntimeBundle runtimeBundle;
    private final PartitionBundle partitionBundle;

    private final EngramMemory engramMemory;
    private final EntityDirectoryMemory entityDirectoryMemory;
    private final com.spectrayan.spector.kernel.scan.ScanService scanService;

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    public DefaultNamespaceKernel(Path directory, KernelSpec spec) {
        this.directory = Objects.requireNonNull(directory, "directory cannot be null");
        this.spec = Objects.requireNonNull(spec, "spec cannot be null");
        this.namespaceId = directory.getFileName() != null ? directory.getFileName().toString() : "default";

        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create namespace directory: " + directory, e);
        }

        Path runtimeBundlePath = directory.resolve("runtime.bundle");
        if (Files.exists(runtimeBundlePath)) {
            this.runtimeBundle = RuntimeBundle.Init.open(runtimeBundlePath);
        } else {
            List<RegionSizeSpec> runtimeSpecs = buildRuntimeSpecs(spec);
            this.runtimeBundle = RuntimeBundle.Init.mmap(runtimeBundlePath, runtimeSpecs);
        }

        Path partitionsDir = StoragePaths.partitionsDir(directory);
        try {
            Files.createDirectories(partitionsDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create partitions directory: " + partitionsDir, e);
        }

        Path partition0Dir = partitionsDir.resolve("00000");
        try {
            Files.createDirectories(partition0Dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create partition 0 directory: " + partition0Dir, e);
        }

        Path partBundlePath = partition0Dir.resolve("partition.bundle");
        int vecBytes = spec.dimensions() * 4;
        if (Files.exists(partBundlePath)) {
            this.partitionBundle = PartitionBundle.Init.open(partBundlePath);
        } else {
            this.partitionBundle = PartitionBundle.Init.mmap(
                    partBundlePath,
                    spec.partitionCapacity(),
                    10 * 1024 * 1024L,
                    spec.partitionCapacity(),
                    10 * 1024 * 1024L,
                    vecBytes,
                    1, 1, 1, 1
            );
        }

        WorkingMemory working = WorkingMemory.fromRegionRef(
                runtimeBundle.regionRef(RegionId.WORKING),
                spec.workingCapacity(),
                vecBytes,
                runtimeBundlePath,
                false
        );

        SemanticMemory semantic = SemanticMemory.fromRegionRef(
                partitionBundle.regionRef(RegionId.SEMANTIC),
                spec.partitionCapacity(),
                vecBytes,
                partBundlePath,
                false
        );

        ProceduralMemory procedural = ProceduralMemory.fromRegionRef(
                partitionBundle.regionRef(RegionId.PROCEDURAL),
                spec.partitionCapacity(),
                vecBytes,
                partBundlePath,
                false
        );

        EpisodicMemory episodic = EpisodicMemory.fromRegionRef(
                partitionBundle.regionRef(RegionId.EPISODIC),
                spec.partitionCapacity(),
                partBundlePath,
                false
        );

        StrengthMemory strength = partitionBundle.hasRegion(RegionId.STRENGTH)
                ? partitionBundle.openStrength(spec.partitionCapacity(), spec.partitionCapacity(), spec.partitionCapacity(), "kernel")
                : null;

        this.engramMemory = new DefaultEngramMemory(working, semantic, procedural, episodic, strength);
        this.entityDirectoryMemory = runtimeBundle.openEntityDirectory();

        java.util.Map<MemoryType, EngramRegion> scanMap = new java.util.EnumMap<>(MemoryType.class);
        if (working != null) scanMap.put(MemoryType.WORKING, working);
        if (semantic != null) scanMap.put(MemoryType.SEMANTIC, semantic);
        if (procedural != null) scanMap.put(MemoryType.PROCEDURAL, procedural);
        this.scanService = new com.spectrayan.spector.kernel.scan.DefaultScanService(scanMap, strength, 0);
    }

    private static List<RegionSizeSpec> buildRuntimeSpecs(KernelSpec spec) {
        List<RegionSizeSpec> specs = new ArrayList<>();
        int vecBytes = spec.dimensions() * 4;
        specs.add(new RegionSizeSpec(RegionId.WORKING, 64L + (long) spec.workingCapacity() * (64 + vecBytes), spec.workingCapacity(), 64 + vecBytes, 1, 1, false));
        specs.add(new RegionSizeSpec(RegionId.COACTIVATION, 2 * 1024 * 1024L, 10_000, 32, 1, 1, false));
        specs.add(new RegionSizeSpec(RegionId.INDEX_MIDX, 5 * 1024 * 1024L, 50_000, 48, 1, 1, false));
        specs.add(new RegionSizeSpec(RegionId.INDEX_IDPL, 5 * 1024 * 1024L, 50_000, 64, 1, 1, false));
        specs.add(new RegionSizeSpec(RegionId.HEBBIAN, 4 * 1024 * 1024L, 10_000, 32, 1, 1, false));
        specs.add(new RegionSizeSpec(RegionId.TEMPORAL_CHAIN, 2 * 1024 * 1024L, 10_000, 32, 1, 1, false));
        specs.add(new RegionSizeSpec(RegionId.TEMPORAL_FACTS, 2 * 1024 * 1024L, 10_000, 32, 1, 1, false));
        specs.add(new RegionSizeSpec(RegionId.ENTITY_DIRECTORY, 2 * 1024 * 1024L, 10_000, 32, 1, 1, false));
        specs.add(new RegionSizeSpec(RegionId.ENTITY_NAMES, 2 * 1024 * 1024L, 10_000, 32, 1, 1, false));
        specs.add(new RegionSizeSpec(RegionId.HYPERGRAPH, 2 * 1024 * 1024L, 10_000, 32, 1, 1, false));
        specs.add(new RegionSizeSpec(RegionId.ENTITY_TYPES, 512 * 1024L, 1_000, 64, 1, 1, false));
        specs.add(new RegionSizeSpec(RegionId.RELATION_TYPES, 512 * 1024L, 1_000, 64, 1, 1, false));
        specs.add(new RegionSizeSpec(RegionId.INSULA, 512 * 1024L, 1_000, 64, 1, 1, false));
        specs.add(new RegionSizeSpec(RegionId.CONTINUITY, 512 * 1024L, 1_000, 64, 1, 1, false));
        specs.add(new RegionSizeSpec(RegionId.PROVENANCE, 512 * 1024L, 1_000, 64, 1, 1, false));
        return specs;
    }

    @Override
    public String namespaceId() {
        return namespaceId;
    }

    @Override
    public Path directory() {
        return directory;
    }

    @Override
    public EngramMemory engramMemory() {
        return engramMemory;
    }

    @Override
    public AppendMemory<TextBlobLayout> textMemory() {
        return partitionBundle.openAppend(RegionId.TEXT, new TextBlobLayout());
    }

    @Override
    public RecordMemory<IndexEntryLayout> indexMemory() {
        return runtimeBundle.openRecord(RegionId.INDEX_MIDX, new IndexEntryLayout());
    }

    @Override
    public AppendMemory<WalLayout> walMemory() {
        return runtimeBundle.hasRegion(RegionId.CHECKPOINT)
                ? runtimeBundle.openAppend(RegionId.CHECKPOINT, new WalLayout())
                : runtimeBundle.openAppend(RegionId.WORKING, new WalLayout());
    }

    @Override
    public RecordMemory<StrengthLayout> strengthMemory() {
        return partitionBundle.openRecord(RegionId.STRENGTH, StrengthLayout.INSTANCE);
    }

    @Override
    public GraphMemory<HebbianLayout> hebbianMemory() {
        return runtimeBundle.openGraph(RegionId.HEBBIAN, new HebbianLayout());
    }

    @Override
    public GraphMemory<HyperEntityLayout> hyperGraphMemory() {
        return runtimeBundle.openGraph(RegionId.HYPERGRAPH, new HyperEntityLayout());
    }

    @Override
    public ChainMemory<TemporalLayout> temporalChainMemory() {
        return runtimeBundle.openChain(RegionId.TEMPORAL_CHAIN, new TemporalLayout());
    }

    @Override
    public AppendMemory<TemporalFactLayout> temporalFactsMemory() {
        return runtimeBundle.openAppend(RegionId.TEMPORAL_FACTS, new TemporalFactLayout());
    }

    @Override
    public HashTableMemory<CoActivationLayout> coActivationMemory() {
        return runtimeBundle.openHashTable(RegionId.COACTIVATION, new CoActivationLayout());
    }

    @Override
    public RegistryMemory entityTypeMemory() {
        return runtimeBundle.openRegistry(RegionId.ENTITY_TYPES);
    }

    @Override
    public RegistryMemory relationTypeMemory() {
        return runtimeBundle.openRegistry(RegionId.RELATION_TYPES);
    }

    @Override
    public EntityDirectoryMemory entityDirectoryMemory() {
        return entityDirectoryMemory;
    }

    @Override
    public RecordMemory<InsularLayout> insulaMemory() {
        return runtimeBundle.openRecord(RegionId.INSULA, InsularLayout.SINGLETON);
    }

    @Override
    public RecordMemory<ContinuityLayout> continuityMemory() {
        return runtimeBundle.openRecord(RegionId.CONTINUITY, ContinuityLayout.SINGLETON);
    }

    @Override
    public RecordMemory<ProvenanceLayout> provenanceMemory() {
        return runtimeBundle.openRecord(RegionId.PROVENANCE, ProvenanceLayout.INSTANCE);
    }

    @Override
    public com.spectrayan.spector.kernel.scan.ScanService scan() {
        return scanService;
    }

    @Override
    public boolean hasActiveLeases() {
        return runtimeBundle.hasActiveLeases();
    }

    @Override
    public void flush() {
        engramMemory.force();
        entityDirectoryMemory.flush();
        runtimeBundle.flush();
        partitionBundle.flush();
    }

    @Override
    public void close() {
        if (!isClosed.compareAndSet(false, true)) {
            return;
        }
        flush();
        try {
            engramMemory.close();
        } catch (Exception ignored) {}
        try {
            entityDirectoryMemory.close();
        } catch (Exception ignored) {}
        try {
            partitionBundle.close();
        } catch (Exception ignored) {}
        try {
            runtimeBundle.close();
        } catch (Exception ignored) {}
    }
}
