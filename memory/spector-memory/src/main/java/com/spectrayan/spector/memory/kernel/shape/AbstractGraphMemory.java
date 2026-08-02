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
package com.spectrayan.spector.memory.kernel.shape;

import com.spectrayan.spector.memory.kernel.AbstractMemory;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryLayout;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.concurrent.locks.StampedLock;

/**
 * Abstract base class for adjacency graph memory backed by a single off-heap
 * {@link MemorySegment} and the standardized 64-byte kernel {@link MemoryShape#GRAPH}
 * header (magic {@code 0x534D4B4D}).
 *
 * <h2>Storage model — vertex offset slab + edge slab</h2>
 * The backing segment (after the {@link #dataOffset() data offset}) is divided into two
 * contiguous slabs:
 * <pre>
 *   [ vertex slab : vertexCapacity  x {@value #VERTEX_STRIDE}B ][ edge slab : edgeCapacity x edgeStride ]
 * </pre>
 * Each <b>vertex record</b> stores the offset (slot index) of the head of its adjacency
 * list plus a cached degree. Each <b>edge record</b> reserves an {@value #EDGE_HEADER_BYTES}-byte
 * base prefix ({@code target}, {@code next}) followed by a layout-defined payload
 * ({@code edgeStride - }{@value #EDGE_HEADER_BYTES} bytes). Adjacency is therefore a
 * per-vertex singly linked list threaded through the shared edge slab, which gives
 * <b>stable edge ids</b> (slots are never relocated), O(1) edge insertion, and O(degree)
 * removal. A <b>vertex free-list</b> and an <b>edge free-list</b> reclaim slots released by
 * {@link #removeNode(int)} / {@link #removeEdge(int)} so the slabs are reused rather than
 * grown unbounded.
 *
 * <h2>Concurrency</h2>
 * SWMR (single-writer / multiple-reader) is enforced with a {@link StampedLock}: mutations
 * take the write lock; reads take a (short) read lock. {@code synchronized} is never used
 * (it pins virtual threads). Read paths avoid allocation beyond the returned iterator.
 *
 * <h2>Two ways to extend</h2>
 * <ol>
 *   <li><b>Generic CSR mode</b> — call the {@code (id, layout, vertexCapacity, edgeCapacity)}
 *       constructors. The base fully implements {@link #addEdge}, {@link #removeEdge},
 *       {@link #neighbours}, {@link #edgeCount} and {@link #nodeCount} over the slabs above.</li>
 *   <li><b>Substrate mode</b> — call the raw {@code (id, layout, capacity, segmentBytes)}
 *       constructors to obtain only the segment/arena/header/identity plumbing and lay the
 *       segment out yourself. Subclasses in this mode <b>must</b> override the
 *       {@link GraphMemory} methods (the generic ones throw if invoked).</li>
 * </ol>
 *
 * @param <L> the graph memory layout type; {@link MemoryLayout#recordStride()} is the
 *            per-edge stride and must be {@code >= }{@value #EDGE_HEADER_BYTES} in generic mode
 */
public abstract class AbstractGraphMemory<L extends MemoryLayout>
        extends AbstractMemory<L> implements GraphMemory<L> {

    private static final Logger log = LoggerFactory.getLogger(AbstractGraphMemory.class);

    // ── Vertex record layout (universal adjacency wiring; 16 bytes) ──
    /** Bytes per vertex record in the vertex offset slab. */
    public static final int VERTEX_STRIDE = 16;
    /** Vertex field: head edge slot index (-1 = no edges). */
    public static final int VERTEX_OFF_EDGE_HEAD = 0;
    /** Vertex field: cached degree (number of outgoing edges). */
    public static final int VERTEX_OFF_DEGREE = 4;
    /** Vertex field: flags (bit0 = allocated). */
    public static final int VERTEX_OFF_FLAGS = 8;

    // ── Edge record base prefix (universal; payload follows) ──
    /** Edge field: target vertex id (-1 = tombstoned). */
    public static final int EDGE_OFF_TARGET = 0;
    /** Edge field: next edge slot in this vertex's list (-1 = end). */
    public static final int EDGE_OFF_NEXT = 4;
    /** Size of the base edge prefix; layout payload begins here. */
    public static final int EDGE_HEADER_BYTES = 8;

    private static final int NIL = -1;
    private static final int FLAG_ALLOCATED = 0x1;

    // ── Generic CSR state (heap-side bookkeeping) ──
    private final boolean genericCsr;
    private final int edgeCapacity;
    private final int edgeStride;
    private final long vertexSlabOffset;
    private final long edgeSlabOffset;

    /** Owner vertex per edge slot, enabling O(degree) unlink on removal. */
    private final int[] edgeOwner;
    private final IntStack edgeFree;
    private final IntStack vertexFree;
    private int edgeHighWater;
    private int vertexHighWater;
    private int activeEdges;
    private int activeNodes;

    /** SWMR guard. Never use {@code synchronized} (virtual-thread pinning). */
    protected final StampedLock lock = new StampedLock();

    // ══════════════════════════════════════════════════════════════
    // CONSTRUCTORS — generic CSR mode
    // ══════════════════════════════════════════════════════════════

    /**
     * Volatile generic-CSR constructor: allocates an off-heap segment sized for
     * {@code vertexCapacity} vertices and {@code edgeCapacity} edges and initializes
     * an empty graph.
     */
    protected AbstractGraphMemory(MemoryId id, L layout, int vertexCapacity, int edgeCapacity) {
        super(id, layout, vertexCapacity,
                (long) vertexCapacity * VERTEX_STRIDE + (long) edgeCapacity * layout.recordStride());
        this.genericCsr = true;
        this.edgeCapacity = edgeCapacity;
        this.edgeStride = layout.recordStride();
        this.vertexSlabOffset = dataOffset();
        this.edgeSlabOffset = vertexSlabOffset + (long) vertexCapacity * VERTEX_STRIDE;
        this.edgeOwner = new int[Math.max(1, edgeCapacity)];
        this.edgeFree = new IntStack();
        this.vertexFree = new IntStack();
        requireEdgeStride();
        initVertexSlab();
    }

    /**
     * File-backed generic-CSR constructor: memory-maps a persistent file with the
     * standard 64-byte header and initializes an empty graph (fresh files only; existing
     * files are left to subclass reload logic).
     */
    protected AbstractGraphMemory(MemoryId id, L layout, int vertexCapacity, int edgeCapacity,
                                  Path filePath) {
        super(id, layout, vertexCapacity,
                (long) vertexCapacity * VERTEX_STRIDE + (long) edgeCapacity * layout.recordStride(),
                filePath);
        this.genericCsr = true;
        this.edgeCapacity = edgeCapacity;
        this.edgeStride = layout.recordStride();
        this.vertexSlabOffset = dataOffset();
        this.edgeSlabOffset = vertexSlabOffset + (long) vertexCapacity * VERTEX_STRIDE;
        this.edgeOwner = new int[Math.max(1, edgeCapacity)];
        this.edgeFree = new IntStack();
        this.vertexFree = new IntStack();
        requireEdgeStride();
        initVertexSlab();
    }

    // ══════════════════════════════════════════════════════════════
    // CONSTRUCTORS — substrate mode (subclass owns the segment layout)
    // ══════════════════════════════════════════════════════════════

    /** Volatile substrate constructor. Subclasses must override the {@link GraphMemory} methods. */
    protected AbstractGraphMemory(MemoryId id, L layout, int capacity, long segmentBytes) {
        super(id, layout, capacity, segmentBytes);
        this.genericCsr = false;
        this.edgeCapacity = 0;
        this.edgeStride = layout.recordStride();
        this.vertexSlabOffset = dataOffset();
        this.edgeSlabOffset = dataOffset();
        this.edgeOwner = null;
        this.edgeFree = null;
        this.vertexFree = null;
    }

    /** File-backed substrate constructor. Subclasses must override the {@link GraphMemory} methods. */
    protected AbstractGraphMemory(MemoryId id, L layout, int capacity, long segmentBytes, Path filePath) {
        super(id, layout, capacity, segmentBytes, filePath);
        this.genericCsr = false;
        this.edgeCapacity = 0;
        this.edgeStride = layout.recordStride();
        this.vertexSlabOffset = dataOffset();
        this.edgeSlabOffset = dataOffset();
        this.edgeOwner = null;
        this.edgeFree = null;
        this.vertexFree = null;
    }

    /** Wrapping substrate constructor (adopts a pre-made arena/segment). */
    protected AbstractGraphMemory(MemoryId id, L layout, int capacity,
                                  Arena arena, MemorySegment segment, int count,
                                  boolean persistent, Path filePath, FileChannel fileChannel) {
        super(id, layout, capacity, arena, segment, count, persistent, filePath, fileChannel);
        this.genericCsr = false;
        this.edgeCapacity = 0;
        this.edgeStride = layout.recordStride();
        this.vertexSlabOffset = dataOffset();
        this.edgeSlabOffset = dataOffset();
        this.edgeOwner = null;
        this.edgeFree = null;
        this.vertexFree = null;
    }

    // ══════════════════════════════════════════════════════════════
    // SHAPE / IDENTITY
    // ══════════════════════════════════════════════════════════════

    @Override
    public MemoryShape shape() {
        return MemoryShape.GRAPH;
    }

    /** Maximum number of edge slots in the edge slab (generic mode only). */
    public final int edgeCapacity() {
        return edgeCapacity;
    }

    // ══════════════════════════════════════════════════════════════
    // NODE MANAGEMENT (vertex free-list)
    // ══════════════════════════════════════════════════════════════

    /**
     * Allocates a vertex id, reusing a freed slot when available.
     *
     * @return the new vertex id, or -1 if the vertex capacity is exhausted
     */
    public int addNode() {
        long stamp = lock.writeLock();
        try {
            ensureGeneric();
            int id;
            if (!vertexFree.isEmpty()) {
                id = vertexFree.pop();
            } else if (vertexHighWater < capacity) {
                id = vertexHighWater++;
            } else {
                return NIL;
            }
            writeVertex(id, VERTEX_OFF_EDGE_HEAD, NIL);
            writeVertex(id, VERTEX_OFF_DEGREE, 0);
            writeVertex(id, VERTEX_OFF_FLAGS, FLAG_ALLOCATED);
            return id;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Removes a vertex and all of its outgoing edges, returning both the vertex slot
     * and its edge slots to the free-lists.
     *
     * @param nodeId the vertex to remove
     */
    public void removeNode(int nodeId) {
        long stamp = lock.writeLock();
        try {
            ensureGeneric();
            if (nodeId < 0 || nodeId >= capacity) return;
            int edge = readVertex(nodeId, VERTEX_OFF_EDGE_HEAD);
            while (edge != NIL) {
                int next = readEdge(edge, EDGE_OFF_NEXT);
                if (readEdge(edge, EDGE_OFF_TARGET) != NIL) activeEdges--;
                edgeOwner[edge] = NIL;
                edgeFree.push(edge);
                edge = next;
            }
            if (readVertex(nodeId, VERTEX_OFF_DEGREE) > 0) activeNodes--;
            writeVertex(nodeId, VERTEX_OFF_EDGE_HEAD, NIL);
            writeVertex(nodeId, VERTEX_OFF_DEGREE, 0);
            writeVertex(nodeId, VERTEX_OFF_FLAGS, 0);
            vertexFree.push(nodeId);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // EDGE MANAGEMENT (GraphMemory contract — generic CSR impl)
    // ══════════════════════════════════════════════════════════════

    @Override
    public int addEdge(int fromNode, int toNode, MemorySegment edgeBytes) {
        long stamp = lock.writeLock();
        try {
            ensureGeneric();
            if (fromNode < 0 || fromNode >= capacity || toNode < 0 || toNode >= capacity) {
                return NIL;
            }
            int slot = allocateEdge();
            if (slot == NIL) return NIL;

            writeEdge(slot, EDGE_OFF_TARGET, toNode);
            int head = readVertex(fromNode, VERTEX_OFF_EDGE_HEAD);
            writeEdge(slot, EDGE_OFF_NEXT, head);
            if (edgeBytes != null) {
                long payload = Math.min(edgeBytes.byteSize(), (long) edgeStride - EDGE_HEADER_BYTES);
                if (payload > 0) {
                    MemorySegment.copy(edgeBytes, 0,
                            segment(), edgeSlabOffset + (long) slot * edgeStride + EDGE_HEADER_BYTES,
                            payload);
                }
            }
            writeVertex(fromNode, VERTEX_OFF_EDGE_HEAD, slot);
            int degree = readVertex(fromNode, VERTEX_OFF_DEGREE);
            if (degree == 0) activeNodes++;
            writeVertex(fromNode, VERTEX_OFF_DEGREE, degree + 1);
            edgeOwner[slot] = fromNode;
            activeEdges++;
            count = activeNodes;
            persistCount();
            publishVisible();
            return slot;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public void removeEdge(int edgeId) {
        long stamp = lock.writeLock();
        try {
            ensureGeneric();
            if (edgeId < 0 || edgeId >= edgeCapacity) return;
            int owner = edgeOwner[edgeId];
            if (owner == NIL || readEdge(edgeId, EDGE_OFF_TARGET) == NIL) return;

            // Unlink from the owner's singly linked adjacency list.
            int prev = NIL;
            int cur = readVertex(owner, VERTEX_OFF_EDGE_HEAD);
            while (cur != NIL && cur != edgeId) {
                prev = cur;
                cur = readEdge(cur, EDGE_OFF_NEXT);
            }
            if (cur != edgeId) return; // inconsistent; nothing to do
            int next = readEdge(edgeId, EDGE_OFF_NEXT);
            if (prev == NIL) {
                writeVertex(owner, VERTEX_OFF_EDGE_HEAD, next);
            } else {
                writeEdge(prev, EDGE_OFF_NEXT, next);
            }

            writeEdge(edgeId, EDGE_OFF_TARGET, NIL);
            edgeOwner[edgeId] = NIL;
            edgeFree.push(edgeId);
            int degree = readVertex(owner, VERTEX_OFF_DEGREE);
            if (degree == 1) activeNodes--;
            writeVertex(owner, VERTEX_OFF_DEGREE, Math.max(0, degree - 1));
            activeEdges--;
            count = activeNodes;
            persistCount();
            publishVisible();
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public PrimitiveIterator.OfInt neighbours(int nodeId) {
        long stamp = lock.readLock();
        try {
            ensureGeneric();
            if (nodeId < 0 || nodeId >= capacity) {
                return EMPTY_ITERATOR;
            }
            // Snapshot the adjacency list under the read lock so iteration is safe even
            // if a concurrent writer mutates the list afterwards (SWMR).
            int degree = readVertex(nodeId, VERTEX_OFF_DEGREE);
            int[] buf = new int[Math.max(0, degree)];
            int n = 0;
            int edge = readVertex(nodeId, VERTEX_OFF_EDGE_HEAD);
            while (edge != NIL) {
                int target = readEdge(edge, EDGE_OFF_TARGET);
                if (target != NIL) {
                    if (n == buf.length) { // tombstone accounting drift; grow defensively
                        int[] grown = new int[Math.max(1, buf.length << 1)];
                        System.arraycopy(buf, 0, grown, 0, n);
                        buf = grown;
                    }
                    buf[n++] = target;
                }
                edge = readEdge(edge, EDGE_OFF_NEXT);
            }
            return new ArrayIntIterator(buf, n);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public int edgeCount() {
        long stamp = lock.tryOptimisticRead();
        int c = activeEdges;
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                c = activeEdges;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return c;
    }

    @Override
    public int nodeCount() {
        long stamp = lock.tryOptimisticRead();
        int c = activeNodes;
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                c = activeNodes;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return c;
    }

    // ══════════════════════════════════════════════════════════════
    // PROTECTED SLAB ACCESSORS (for generic + substrate subclasses)
    // ══════════════════════════════════════════════════════════════

    /** Byte offset of the vertex slab within the backing segment. */
    protected final long vertexSlabOffset() {
        return vertexSlabOffset;
    }

    /** Byte offset of the edge slab within the backing segment. */
    protected final long edgeSlabOffset() {
        return edgeSlabOffset;
    }

    /** Reads an int field from a vertex record. */
    protected final int readVertex(int vertexId, int fieldOffset) {
        return segment().get(ValueLayout.JAVA_INT,
                vertexSlabOffset + (long) vertexId * VERTEX_STRIDE + fieldOffset);
    }

    /** Writes an int field to a vertex record. */
    protected final void writeVertex(int vertexId, int fieldOffset, int value) {
        segment().set(ValueLayout.JAVA_INT,
                vertexSlabOffset + (long) vertexId * VERTEX_STRIDE + fieldOffset, value);
    }

    /** Reads an int field from an edge record. */
    protected final int readEdge(int edgeSlot, int fieldOffset) {
        return segment().get(ValueLayout.JAVA_INT,
                edgeSlabOffset + (long) edgeSlot * edgeStride + fieldOffset);
    }

    /** Writes an int field to an edge record. */
    protected final void writeEdge(int edgeSlot, int fieldOffset, int value) {
        segment().set(ValueLayout.JAVA_INT,
                edgeSlabOffset + (long) edgeSlot * edgeStride + fieldOffset, value);
    }

    // ══════════════════════════════════════════════════════════════
    // INTERNAL
    // ══════════════════════════════════════════════════════════════

    private void ensureGeneric() {
        if (!genericCsr) {
            throw new IllegalStateException(
                    "Generic CSR operation invoked on a substrate-mode graph (" + getClass().getName()
                            + "): subclass must override GraphMemory methods");
        }
    }

    private void requireEdgeStride() {
        if (edgeStride < EDGE_HEADER_BYTES) {
            throw new IllegalArgumentException(
                    "Edge stride (" + edgeStride + ") must be >= " + EDGE_HEADER_BYTES
                            + " for generic CSR graph " + getClass().getName());
        }
    }

    private void initVertexSlab() {
        for (int v = 0; v < capacity; v++) {
            writeVertex(v, VERTEX_OFF_EDGE_HEAD, NIL);
            writeVertex(v, VERTEX_OFF_DEGREE, 0);
            writeVertex(v, VERTEX_OFF_FLAGS, 0);
        }
    }

    private int allocateEdge() {
        if (!edgeFree.isEmpty()) {
            return edgeFree.pop();
        }
        if (edgeHighWater < edgeCapacity) {
            return edgeHighWater++;
        }
        return NIL;
    }

    private static final PrimitiveIterator.OfInt EMPTY_ITERATOR = new PrimitiveIterator.OfInt() {
        @Override public boolean hasNext() { return false; }
        @Override public int nextInt() { throw new NoSuchElementException(); }
    };

    /** Iterator over a snapshotted neighbour id array. */
    private static final class ArrayIntIterator implements PrimitiveIterator.OfInt {
        private final int[] data;
        private final int length;
        private int idx;

        ArrayIntIterator(int[] data, int length) {
            this.data = data;
            this.length = length;
        }

        @Override
        public boolean hasNext() {
            return idx < length;
        }

        @Override
        public int nextInt() {
            if (idx >= length) throw new NoSuchElementException();
            return data[idx++];
        }
    }

    /** Minimal growable primitive-int stack for the free-lists (avoids Integer boxing). */
    private static final class IntStack {
        private int[] data = new int[16];
        private int size;

        boolean isEmpty() {
            return size == 0;
        }

        void push(int v) {
            if (size == data.length) {
                int[] grown = new int[data.length << 1];
                System.arraycopy(data, 0, grown, 0, size);
                data = grown;
            }
            data[size++] = v;
        }

        int pop() {
            return data[--size];
        }
    }
}
