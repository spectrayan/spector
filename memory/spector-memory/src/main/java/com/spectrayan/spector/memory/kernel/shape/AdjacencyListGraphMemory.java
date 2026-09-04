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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorInternalException;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.RegionLayout;
import com.spectrayan.spector.memory.kernel.layout.AdjacencyListLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

import static com.spectrayan.spector.memory.kernel.layout.AdjacencyListLayout.EDGE_HEADER_BYTES;
import static com.spectrayan.spector.memory.kernel.layout.AdjacencyListLayout.EDGE_OFF_NEXT;
import static com.spectrayan.spector.memory.kernel.layout.AdjacencyListLayout.EDGE_OFF_TARGET;
import static com.spectrayan.spector.memory.kernel.layout.AdjacencyListLayout.VERTEX_OFF_DEGREE;
import static com.spectrayan.spector.memory.kernel.layout.AdjacencyListLayout.VERTEX_OFF_EDGE_HEAD;
import static com.spectrayan.spector.memory.kernel.layout.AdjacencyListLayout.VERTEX_OFF_FLAGS;
import static com.spectrayan.spector.memory.kernel.layout.AdjacencyListLayout.VERTEX_STRIDE;

/**
 * Bundled <b>reference</b> graph memory over the kernel {@link AbstractGraphMemory} substrate.
 * It backs a graph with a single off-heap {@link MemorySegment} and threads a per-vertex
 * <b>singly linked adjacency list</b> through a shared edge slab.
 *
 * <h2>Storage model — vertex offset slab + edge slab</h2>
 * The backing segment (after the {@link #dataOffset() data offset}) is divided into two
 * contiguous slabs:
 * <pre>
 *   [ vertex slab : vertexCapacity  x {@value AdjacencyListLayout#VERTEX_STRIDE}B ][ edge slab : edgeCapacity x edgeStride ]
 * </pre>
 * Each <b>vertex record</b> stores the slot index of the head of its adjacency list plus a
 * cached degree. Each <b>edge record</b> reserves an {@value AdjacencyListLayout#EDGE_HEADER_BYTES}-byte base
 * prefix ({@code target}, {@code next}) followed by a layout-defined payload
 * ({@code edgeStride - }{@value AdjacencyListLayout#EDGE_HEADER_BYTES} bytes). Adjacency is therefore a per-vertex
 * singly linked list threaded through the shared edge slab, which gives <b>stable edge ids</b>
 * (slots are never relocated), O(1) edge insertion, and O(degree) removal. A <b>vertex
 * free-list</b> and an <b>edge free-list</b> reclaim slots released by {@link #removeNode(int)} /
 * {@link #removeEdge(int)} so the slabs are reused rather than grown unbounded.
 *
 * <h2>Not a contiguous layout</h2>
 * Because adjacency is a linked list, neighbours are <b>not</b> stored contiguously. This impl is
 * a conformance/reference substrate; the production graphs (Hebbian / Entity / HyperEntity) keep
 * neighbours contiguous and drive their own layouts directly on {@link AbstractGraphMemory}.
 *
 * <h2>Concurrency</h2>
 * SWMR is enforced with the substrate {@link #lock}: mutations take the write lock; reads take a
 * (short) read lock. {@link #neighbours(int)} returns a lazy iterator that walks the linked list
 * without allocating a snapshot array.
 *
 * @param <L> the graph memory layout type; {@link RegionLayout#recordStride()} is the per-edge
 *            stride and must be {@code >= }{@value AdjacencyListLayout#EDGE_HEADER_BYTES}
 */
public abstract class AdjacencyListGraphMemory<L extends RegionLayout>
        extends AbstractGraphMemory<L> {

    private static final Logger log = LoggerFactory.getLogger(AdjacencyListGraphMemory.class);

    // ── Adjacency-list wiring: single source of truth is AdjacencyListLayout (#435, TD-14). ──
    // VERTEX_STRIDE / VERTEX_OFF_* / EDGE_OFF_TARGET / EDGE_OFF_NEXT / EDGE_HEADER_BYTES are
    // static-imported from AdjacencyListLayout; this class only references them.

    private static final int NIL = -1;
    private static final int FLAG_ALLOCATED = 0x1;

    // ── Adjacency-list state (heap-side bookkeeping) ──
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

    // ══════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ══════════════════════════════════════════════════════════════

    /**
     * Volatile constructor: allocates an off-heap segment sized for {@code vertexCapacity}
     * vertices and {@code edgeCapacity} edges and initializes an empty graph.
     */
    protected AdjacencyListGraphMemory(MemoryId id, L layout, int vertexCapacity, int edgeCapacity) {
        super(id, layout, vertexCapacity,
                (long) vertexCapacity * VERTEX_STRIDE + (long) edgeCapacity * layout.recordStride());
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
     * File-backed constructor: memory-maps a persistent file with the standard 64-byte header
     * and initializes an empty graph (fresh files only; existing files are left to subclass
     * reload logic).
     */
    protected AdjacencyListGraphMemory(MemoryId id, L layout, int vertexCapacity, int edgeCapacity,
                                       Path filePath) {
        super(id, layout, vertexCapacity,
                (long) vertexCapacity * VERTEX_STRIDE + (long) edgeCapacity * layout.recordStride(),
                filePath);
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
    // CAPACITY
    // ══════════════════════════════════════════════════════════════

    /** Maximum number of edge slots in the edge slab. */
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
    // EDGE MANAGEMENT (GraphMemory contract)
    // ══════════════════════════════════════════════════════════════

    @Override
    public int addEdge(int fromNode, int toNode, MemorySegment edgeBytes) {
        long stamp = lock.writeLock();
        try {
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

    /**
     * Returns a lazy iterator over the neighbours of {@code nodeId}.
     *
     * <p>Unlike a snapshot iterator, this walks the singly linked adjacency list on demand and
     * allocates no per-call {@code int[]} buffer. Each advance reads the current edge slot under
     * a short validated read lock so it observes a consistent {@code (target, next)} pair; the
     * shared edge slab is never relocated, so the walk stays anchored to a stable slot space.</p>
     */
    @Override
    public PrimitiveIterator.OfInt neighbours(int nodeId) {
        if (nodeId < 0 || nodeId >= capacity) {
            return EMPTY_ITERATOR;
        }
        long stamp = lock.readLock();
        int head;
        try {
            head = readVertex(nodeId, VERTEX_OFF_EDGE_HEAD);
        } finally {
            lock.unlockRead(stamp);
        }
        return new AdjacencyIterator(head);
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
    // PROTECTED SLAB ACCESSORS (for subclasses)
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

    private void requireEdgeStride() {
        if (edgeStride < EDGE_HEADER_BYTES) {
            throw new SpectorInternalException(ErrorCode.INVARIANT_VIOLATED,
                    "edge stride (" + edgeStride + ") must be >= " + EDGE_HEADER_BYTES
                            + " for adjacency-list graph " + getClass().getName());
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

    /**
     * Lazy iterator that walks a vertex's singly linked adjacency list without allocating a
     * snapshot array. Each advance reads the edge slot under a short validated read lock.
     */
    private final class AdjacencyIterator implements PrimitiveIterator.OfInt {
        private int nextEdge;
        private int pending;
        private boolean pendingValid;

        AdjacencyIterator(int head) {
            this.nextEdge = head;
            advance();
        }

        private void advance() {
            pendingValid = false;
            while (nextEdge != NIL) {
                int edge = nextEdge;
                long stamp = lock.readLock();
                int target;
                int next;
                try {
                    target = readEdge(edge, EDGE_OFF_TARGET);
                    next = readEdge(edge, EDGE_OFF_NEXT);
                } finally {
                    lock.unlockRead(stamp);
                }
                nextEdge = next;
                if (target != NIL) {
                    pending = target;
                    pendingValid = true;
                    return;
                }
            }
        }

        @Override
        public boolean hasNext() {
            return pendingValid;
        }

        @Override
        public int nextInt() {
            if (!pendingValid) throw new NoSuchElementException();
            int result = pending;
            advance();
            return result;
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
