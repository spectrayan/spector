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
package com.spectrayan.spector.kernel.shape;

import com.spectrayan.spector.kernel.region.RegionPreamble;

import com.spectrayan.spector.kernel.shape.AbstractMemory;
import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.layout.RegionLayout;
import com.spectrayan.spector.kernel.shape.MemoryShape;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.concurrent.locks.StampedLock;

/**
 * Kernel <b>substrate</b> for graph-shaped memories. This base is deliberately thin: it
 * unifies the boilerplate every graph memory needs and leaves the neighbour storage model
 * entirely to the concrete implementation.
 *
 * <h2>What the substrate provides</h2>
 * <ul>
 *   <li>The standardized 64-byte kernel {@link com.spectrayan.spector.kernel.region.RegionPreamble}
 *       ({@code MemoryShape.GRAPH}, magic {@code 0x534D4B4D}) on the file-backed path.</li>
 *   <li>Arena / {@link MemorySegment} ownership and lifecycle ({@code flush}/{@code close}).</li>
 *   <li>Kernel identity, shape, layout, capacity, and schema version.</li>
 *   <li>A {@link StampedLock} for SWMR (single-writer / multiple-reader) coordination.
 *       {@code synchronized} is never used (it pins virtual threads).</li>
 *   <li>WAL binding (inherited from {@link AbstractMemory}).</li>
 *   <li>The vertex-id space (via {@link #capacity()}).</li>
 * </ul>
 *
 * <h2>What the substrate does NOT provide</h2>
 * The substrate has no opinion about how edges/neighbours are laid out. Concrete graphs own
 * their segment layout and implement the {@link GraphMemory} contract themselves
 * ({@link #addEdge}, {@link #removeEdge}, {@link #neighbours}, {@link #edgeCount},
 * {@link #nodeCount}). Real graphs keep neighbours <b>contiguous</b> for cache locality
 * (e.g. the Hebbian prefix-sum CSR, the Entity contiguous {@code edgeStart+degree} blocks with
 * a region-doubling entity&rarr;memory adjacency, the HyperEntity bipartite incidence lists).
 *
 * <h2>Two ways to extend</h2>
 * <ol>
 *   <li><b>Substrate mode</b> — call one of the {@code (id, layout, capacity, segmentBytes[, filePath])}
 *       or wrapping constructors to obtain the segment/arena/header/identity plumbing and lay the
 *       segment out yourself. This is the mode used by all three production graphs.</li>
 *   <li><b>Adjacency-list reference mode</b> — extend
 *       {@link AdjacencyListGraphMemory}, a bundled reference implementation over the substrate
 *       that threads a singly linked adjacency list through a shared edge slab. It is a
 *       conformance/reference impl and does not preserve neighbour contiguity.</li>
 * </ol>
 *
 * @param <L> the graph memory layout type
 */
public abstract class AbstractGraphMemory<L extends RegionLayout>
        extends AbstractMemory<L> implements GraphMemory<L> {

    /** SWMR guard shared by the substrate and its subclasses. Never use {@code synchronized}. */
    protected final StampedLock lock = new StampedLock();

    // ══════════════════════════════════════════════════════════════
    // CONSTRUCTORS — substrate mode (subclass owns the segment layout)
    // ══════════════════════════════════════════════════════════════

    /** Volatile substrate constructor. Subclasses implement the {@link GraphMemory} methods. */
    protected AbstractGraphMemory(MemoryId id, L layout, int capacity, long segmentBytes) {
        super(id, layout, capacity, segmentBytes);
    }

    /** File-backed substrate constructor. Subclasses implement the {@link GraphMemory} methods. */
    protected AbstractGraphMemory(MemoryId id, L layout, int capacity, long segmentBytes, Path filePath) {
        super(id, layout, capacity, segmentBytes, filePath);
    }

    /** Wrapping substrate constructor (adopts a pre-made arena/segment). */
    protected AbstractGraphMemory(MemoryId id, L layout, int capacity,
                                  Arena arena, MemorySegment segment, int count,
                                  boolean persistent, Path filePath, FileChannel fileChannel) {
        super(id, layout, capacity, arena, segment, count, persistent, filePath, fileChannel);
    }

    protected AbstractGraphMemory(MemoryId id, L layout, int capacity,
                                  Arena arena, MemorySegment segment, int count,
                                  boolean persistent, Path filePath, FileChannel fileChannel,
                                  boolean bundleManaged) {
        super(id, layout, capacity, arena, segment, count, persistent, filePath, fileChannel, bundleManaged);
    }

    protected AbstractGraphMemory(MemoryId id, L layout, int capacity,
                                  com.spectrayan.spector.kernel.bundle.RegionRef regionRef, int count,
                                  boolean persistent, Path filePath) {
        super(id, layout, capacity, regionRef, count, persistent, filePath);
    }

    // ══════════════════════════════════════════════════════════════
    // SHAPE / IDENTITY
    // ══════════════════════════════════════════════════════════════

    @Override
    public MemoryShape shape() {
        return MemoryShape.GRAPH;
    }
}
