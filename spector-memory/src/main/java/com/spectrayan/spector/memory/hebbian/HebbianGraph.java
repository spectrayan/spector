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
package com.spectrayan.spector.memory.hebbian;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import com.spectrayan.spector.memory.error.SpectorGraphPersistenceException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Off-heap adjacency list for full Hebbian graph associations (V2).
 *
 * <h3>Biological Analog: Cortical Network Wiring</h3>
 * <p>In the cortex, neurons form complex networks where activating one node
 * (memory) spreads activation to connected nodes. This graph stores explicit
 * memory-to-memory edges with association weights.</p>
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Off-heap adjacency list backed by {@link MemorySegment}</li>
 *   <li>Bounded degree: max 20 neighbors per memory (prevents graph explosion)</li>
 *   <li>Edge weight = co-recall count (strengthened each time both are recalled together)</li>
 *   <li>Enables spreading activation: "if you recalled A, also consider B and C"</li>
 *   <li>Persistence: save/load via raw segment serialization to file</li>
 * </ul>
 */
public final class HebbianGraph implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HebbianGraph.class);

    /** File magic: "HGPH" in ASCII. */
    private static final int FILE_MAGIC = 0x48475048;

    /** File format version. */
    private static final int FILE_VERSION = 1;

    /** File header: 4B magic + 4B version + 4B capacity + 4B reserved = 16 bytes. */
    private static final int FILE_HEADER_BYTES = 16;

    /** Maximum number of Hebbian neighbors per memory. */
    public static final int MAX_DEGREE = 20;

    /** Bytes per edge: 4B (neighbor index) + 4B (weight as float). */
    private static final int EDGE_BYTES = 8;

    /** Bytes per node: 4B (degree) + MAX_DEGREE * EDGE_BYTES. */
    static final int NODE_BYTES = 4 + MAX_DEGREE * EDGE_BYTES;

    private final Arena arena;
    private final MemorySegment segment;
    private final int capacity;
    /** The file channel for mmap'd graphs (null for in-memory). */
    private final FileChannel mappedChannel;
    /** True if this graph is backed by an mmap'd file (vs. heap allocation). */
    private final boolean fileBacked;

    /**
     * Creates a heap-allocated Hebbian graph (in-memory mode, no file backing).
     *
     * <p>Use this for testing, IN_MEMORY persistence mode, or transient graphs.
     * Use {@link #HebbianGraph(Path, int)} for production disk-backed graphs.</p>
     *
     * @param capacity maximum number of nodes (memories)
     */
    public HebbianGraph(int capacity) {
        this.capacity = capacity;
        this.arena = Arena.ofShared();
        this.segment = arena.allocate((long) NODE_BYTES * capacity);
        this.mappedChannel = null;
        this.fileBacked = false;
        // Zero-initialize (all degrees start at 0)
        segment.fill((byte) 0);

        log.info("HebbianGraph initialized (heap): capacity={}, memory={}KB",
                capacity, (long) NODE_BYTES * capacity / 1024);
    }

    /**
     * Creates or opens a file-backed (mmap) Hebbian graph.
     *
     * <p>If the file exists, it is mapped directly — no data copy needed.
     * If the file doesn't exist, a new file is created, pre-allocated, and
     * zero-filled. All writes go directly to the mmap'd segment and are
     * lazily flushed to disk by the OS. Call {@link #save(Path)} to force
     * a sync.</p>
     *
     * @param filePath path to the graph file
     * @param capacity maximum number of nodes (used only for new files)
     */
    public HebbianGraph(Path filePath, int capacity) {
        Path parent = filePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("HebbianGraph", parent, e);
            }
        }

        long dataBytes = (long) NODE_BYTES * capacity;
        boolean isNew = !Files.exists(filePath);

        try {
            FileChannel ch = FileChannel.open(filePath,
                    StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            this.mappedChannel = ch;

            if (isNew || ch.size() < FILE_HEADER_BYTES) {
                // Brand new file — write header with requested capacity
                ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
                header.putInt(FILE_MAGIC);
                header.putInt(FILE_VERSION);
                header.putInt(capacity);
                header.putInt(0);
                header.flip();
                ch.position(0);
                ch.write(header);

                long totalBytes = FILE_HEADER_BYTES + dataBytes;
                if (ch.size() < totalBytes) {
                    ch.position(totalBytes - 1);
                    ch.write(ByteBuffer.wrap(new byte[]{0}));
                }
                ch.force(true);
                this.capacity = capacity;
            } else {
                // Existing file — read capacity from header
                ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
                ch.position(0);
                ch.read(header);
                header.flip();
                int magic = header.getInt();
                int version = header.getInt();
                int fileCapacity = header.getInt();
                header.getInt(); // reserved

                if (magic != FILE_MAGIC || version != FILE_VERSION) {
                    log.warn("Invalid HebbianGraph file, reinitializing: {}", filePath);
                    ch.truncate(0);
                    ByteBuffer newHeader = ByteBuffer.allocate(FILE_HEADER_BYTES);
                    newHeader.putInt(FILE_MAGIC);
                    newHeader.putInt(FILE_VERSION);
                    newHeader.putInt(capacity);
                    newHeader.putInt(0);
                    newHeader.flip();
                    ch.position(0);
                    ch.write(newHeader);
                    long totalBytes = FILE_HEADER_BYTES + dataBytes;
                    ch.position(totalBytes - 1);
                    ch.write(ByteBuffer.wrap(new byte[]{0}));
                    ch.force(true);
                    this.capacity = capacity;
                } else {
                    this.capacity = fileCapacity;
                    dataBytes = (long) NODE_BYTES * fileCapacity;
                }
            }

            // mmap the data portion (after header)
            this.arena = Arena.ofShared();
            this.segment = ch.map(FileChannel.MapMode.READ_WRITE, FILE_HEADER_BYTES,
                    dataBytes, arena);
            this.fileBacked = true;

            log.info("HebbianGraph initialized (mmap): capacity={}, file={}, memory={}KB",
                    this.capacity, filePath.getFileName(), dataBytes / 1024);

        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("HebbianGraph", filePath, e);
        }
    }

    private HebbianGraph(int capacity, Arena arena, MemorySegment segment,
                          FileChannel mappedChannel, boolean fileBacked) {
        this.capacity = capacity;
        this.arena = arena;
        this.segment = segment;
        this.mappedChannel = mappedChannel;
        this.fileBacked = fileBacked;
    }

    /**
     * Returns the capacity (maximum number of nodes).
     */
    public int capacity() {
        return capacity;
    }

    private final ReentrantLock graphLock = new ReentrantLock();

    /**
     * Adds or strengthens a bidirectional Hebbian edge between two memories.
     *
     * @param nodeA index of first memory
     * @param nodeB index of second memory
     * @param weightDelta weight to add to the edge (default: 1.0)
     */
    public void strengthen(int nodeA, int nodeB, float weightDelta) {
        graphLock.lock();
        try {
            if (nodeA < 0 || nodeA >= capacity || nodeB < 0 || nodeB >= capacity) return;
            if (nodeA == nodeB) return;
            addOrUpdateEdge(nodeA, nodeB, weightDelta);
            addOrUpdateEdge(nodeB, nodeA, weightDelta);
        } finally {
            graphLock.unlock();
        }
    }

    /**
     * Returns the Hebbian neighbors of a memory, sorted by descending weight.
     *
     * @param node memory index
     * @return list of (neighborIndex, weight) pairs
     */
    public List<HebbianEdge> neighbors(int node) {
        if (node < 0 || node >= capacity) return List.of();
        long nodeOffset = (long) node * NODE_BYTES;
        int degree = segment.get(ValueLayout.JAVA_INT, nodeOffset);

        List<HebbianEdge> edges = new ArrayList<>(degree);
        for (int i = 0; i < degree; i++) {
            long edgeOffset = nodeOffset + 4 + (long) i * EDGE_BYTES;
            int neighbor = segment.get(ValueLayout.JAVA_INT, edgeOffset);
            float weight = segment.get(ValueLayout.JAVA_FLOAT, edgeOffset + 4);
            edges.add(new HebbianEdge(neighbor, weight));
        }

        edges.sort((a, b) -> Float.compare(b.weight(), a.weight()));
        return edges;
    }

    /**
     * Returns the degree (number of Hebbian edges) for a node.
     */
    public int degree(int node) {
        if (node < 0 || node >= capacity) return 0;
        return segment.get(ValueLayout.JAVA_INT, (long) node * NODE_BYTES);
    }

    /**
     * Returns the total number of edges across all nodes.
     */
    public int totalEdges() {
        int total = 0;
        for (int i = 0; i < capacity; i++) {
            total += degree(i);
        }
        return total;
    }

    private void addOrUpdateEdge(int from, int to, float weightDelta) {
        long nodeOffset = (long) from * NODE_BYTES;
        int degree = segment.get(ValueLayout.JAVA_INT, nodeOffset);

        // Check if edge already exists
        for (int i = 0; i < degree; i++) {
            long edgeOffset = nodeOffset + 4 + (long) i * EDGE_BYTES;
            int neighbor = segment.get(ValueLayout.JAVA_INT, edgeOffset);
            if (neighbor == to) {
                // Strengthen existing edge
                float weight = segment.get(ValueLayout.JAVA_FLOAT, edgeOffset + 4);
                segment.set(ValueLayout.JAVA_FLOAT, edgeOffset + 4, weight + weightDelta);
                return;
            }
        }

        // Add new edge (if room)
        if (degree < MAX_DEGREE) {
            long edgeOffset = nodeOffset + 4 + (long) degree * EDGE_BYTES;
            segment.set(ValueLayout.JAVA_INT, edgeOffset, to);
            segment.set(ValueLayout.JAVA_FLOAT, edgeOffset + 4, weightDelta);
            segment.set(ValueLayout.JAVA_INT, nodeOffset, degree + 1);
        } else {
            // Replace weakest edge if new weight exceeds it
            replaceWeakest(nodeOffset, degree, to, weightDelta);
        }
    }

    private void replaceWeakest(long nodeOffset, int degree, int newNeighbor, float newWeight) {
        float minWeight = Float.MAX_VALUE;
        int minIndex = -1;

        for (int i = 0; i < degree; i++) {
            long edgeOffset = nodeOffset + 4 + (long) i * EDGE_BYTES;
            float weight = segment.get(ValueLayout.JAVA_FLOAT, edgeOffset + 4);
            if (weight < minWeight) {
                minWeight = weight;
                minIndex = i;
            }
        }

        if (newWeight > minWeight && minIndex >= 0) {
            long edgeOffset = nodeOffset + 4 + (long) minIndex * EDGE_BYTES;
            segment.set(ValueLayout.JAVA_INT, edgeOffset, newNeighbor);
            segment.set(ValueLayout.JAVA_FLOAT, edgeOffset + 4, newWeight);
        }
    }

    /**
     * Immutable Hebbian edge record.
     *
     * <p><b>TODO (JDK 28+ / Project Valhalla):</b> Convert to {@code value record}.
     * As a value class, HebbianEdge would be scalarized in the caller's stack frame
     * instead of heap-allocated. With specialized generics, {@code List<HebbianEdge>}
     * would store flattened values instead of boxed pointers.</p>
     *
     * @param neighborIndex index of the connected memory
     * @param weight        association strength
     */
    public record HebbianEdge(int neighborIndex, float weight) {}

    // ── V3: Edge Decay + Session Boundaries + Spreading Activation ──

    private long lastActivityMs = System.currentTimeMillis();
    private long sessionBoundaryMs = 5 * 60 * 1000L; // 5 minutes default

    /**
     * Configures the session boundary inactivity threshold.
     *
     * @param durationMs milliseconds of inactivity that defines a session break
     */
    public void setSessionBoundary(long durationMs) {
        this.sessionBoundaryMs = durationMs;
    }

    /**
     * Checks if a new session has started (inactivity exceeded boundary).
     *
     * @return true if a new session has started since the last activity
     */
    public boolean isNewSession() {
        long now = System.currentTimeMillis();
        boolean isNew = (now - lastActivityMs) > sessionBoundaryMs;
        lastActivityMs = now;
        return isNew;
    }

    /**
     * Decays all edge weights by a factor (V3: called during ReflectDaemon cycles).
     *
     * <p>Unused associations weaken over time — edges that are never re-strengthened
     * eventually drop to zero and get replaced by new associations.</p>
     *
     * @param decayFactor multiplier (e.g., 0.9 = 10% decay per cycle)
     * @return number of edges that dropped below threshold and were removed
     */
    public int decayEdges(float decayFactor) {
        graphLock.lock();
        try {
            int removed = 0;
            float removalThreshold = 0.01f; // edges below this are effectively dead

            for (int node = 0; node < capacity; node++) {
                long nodeOffset = (long) node * NODE_BYTES;
                int degree = segment.get(ValueLayout.JAVA_INT, nodeOffset);
                int newDegree = 0;

                for (int i = 0; i < degree; i++) {
                    long edgeOffset = nodeOffset + 4 + (long) i * EDGE_BYTES;
                    float weight = segment.get(ValueLayout.JAVA_FLOAT, edgeOffset + 4);
                    float decayed = weight * decayFactor;

                    if (decayed >= removalThreshold) {
                        // Keep edge — compact if needed
                        if (newDegree != i) {
                            long newOffset = nodeOffset + 4 + (long) newDegree * EDGE_BYTES;
                            int neighbor = segment.get(ValueLayout.JAVA_INT, edgeOffset);
                            segment.set(ValueLayout.JAVA_INT, newOffset, neighbor);
                            segment.set(ValueLayout.JAVA_FLOAT, newOffset + 4, decayed);
                        } else {
                            segment.set(ValueLayout.JAVA_FLOAT, edgeOffset + 4, decayed);
                        }
                        newDegree++;
                    } else {
                        removed++;
                    }
                }

                segment.set(ValueLayout.JAVA_INT, nodeOffset, newDegree);
            }

            if (removed > 0) {
                log.debug("Hebbian edge decay: {} edges removed (factor={})", removed, decayFactor);
            }
            return removed;
        } finally {
            graphLock.unlock();
        }
    }

    /**
     * Returns the Hebbian neighbors of a memory at a given depth (spreading activation).
     *
     * <p>Depth 1 = direct neighbors. Depth 2 = neighbors of neighbors.
     * Activation strength decreases with each hop.</p>
     *
     * @param node  starting memory index
     * @param depth activation depth (1-3 recommended)
     * @return list of activated edges with compound weights
     */
    public List<HebbianEdge> activateNeighbors(int node, int depth) {
        if (node < 0 || node >= capacity) return List.of();
        List<HebbianEdge> activated = new ArrayList<>();
        // Use boolean[] instead of HashSet<Integer> — eliminates autoboxing overhead
        boolean[] visited = new boolean[capacity];
        activateRecursive(node, depth, 1.0f, activated, visited);
        activated.sort((a, b) -> Float.compare(b.weight(), a.weight()));
        return activated;
    }

    private void activateRecursive(int node, int depth, float attenuation,
                                    List<HebbianEdge> activated, boolean[] visited) {
        if (depth <= 0 || visited[node]) return;
        visited[node] = true;

        for (HebbianEdge edge : neighbors(node)) {
            float compoundWeight = edge.weight() * attenuation;
            if (compoundWeight > 0.01f && !visited[edge.neighborIndex()]) {
                activated.add(new HebbianEdge(edge.neighborIndex(), compoundWeight));
                activateRecursive(edge.neighborIndex(), depth - 1, compoundWeight * 0.5f,
                        activated, visited);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // PERSISTENCE: save / load
    // ══════════════════════════════════════════════════════════════

    /**
     * Saves the graph to a binary file.
     *
     * <h3>File Format</h3>
     * <pre>
     *   [4B magic: "HGPH"]  [4B version: 1]  [4B capacity]  [4B reserved]
     *   [raw segment bytes: capacity × NODE_BYTES]
     * </pre>
     *
     * @param filePath path to write the graph file
     */
    public void save(Path filePath) {
        // mmap-backed: force flush dirty pages to disk
        if (fileBacked) {
            try {
                segment.force();
                if (mappedChannel != null) mappedChannel.force(true);
                log.info("HebbianGraph flushed (mmap): capacity={}, edges={}",
                        capacity, totalEdges());
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("HebbianGraph", filePath, e);
            }
            return;
        }

        // Heap-backed: serialize to file
        Path parent = filePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("HebbianGraph", parent, e);
            }
        }

        try (FileChannel ch = FileChannel.open(filePath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            // Write file header
            ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
            header.putInt(FILE_MAGIC);
            header.putInt(FILE_VERSION);
            header.putInt(capacity);
            header.putInt(0); // reserved
            header.flip();
            ch.write(header);

            // Write raw segment bytes in chunks
            long totalBytes = (long) NODE_BYTES * capacity;
            long written = 0;
            int chunkSize = 64 * 1024; // 64KB chunks
            while (written < totalBytes) {
                int toWrite = (int) Math.min(chunkSize, totalBytes - written);
                ByteBuffer buf = segment.asSlice(written, toWrite)
                        .asByteBuffer().asReadOnlyBuffer();
                ch.write(buf);
                written += toWrite;
            }

            ch.force(true);
            log.info("HebbianGraph saved (heap→file): capacity={}, edges={} → {}",
                    capacity, totalEdges(), filePath);

        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("HebbianGraph", filePath, e);
        }
    }

    /**
     * Loads a graph from a binary file, or returns a new empty graph
     * if the file doesn't exist.
     *
     * @param filePath path to the graph file
     * @param defaultCapacity capacity to use if file doesn't exist
     * @return a HebbianGraph (loaded or new)
     */
    public static HebbianGraph load(Path filePath, int defaultCapacity) {
        if (filePath == null || !Files.exists(filePath)) {
            log.info("HebbianGraph file not found, creating fresh: {}", filePath);
            return new HebbianGraph(defaultCapacity);
        }

        // Use mmap-backed load: open the file and map directly
        try {
            return new HebbianGraph(filePath, defaultCapacity);
        } catch (Exception e) {
            log.error("Failed to mmap HebbianGraph from {}, creating fresh: {}",
                    filePath, e.getMessage());
            return new HebbianGraph(defaultCapacity);
        }
    }

    /**
     * Resets all Hebbian edges by zero-filling the off-heap segment.
     *
     * <p>Unlike {@link #close()}, this does NOT release the arena. The graph
     * remains usable for new edges after the reset. Used by privacy wipe.</p>
     *
     * @return total edges that existed before reset
     */
    public int reset() {
        graphLock.lock();
        try {
            int edgesBefore = totalEdges();
            segment.fill((byte) 0);
            lastActivityMs = System.currentTimeMillis();
            log.info("HebbianGraph reset: {} edges cleared, capacity={}", edgesBefore, capacity);
            return edgesBefore;
        } finally {
            graphLock.unlock();
        }
    }

    @Override
    public void close() {
        log.info("HebbianGraph closing (capacity={}, fileBacked={})", capacity, fileBacked);
        if (fileBacked && mappedChannel != null) {
            try {
                segment.force();
                mappedChannel.close();
            } catch (IOException e) {
                log.warn("Failed to close HebbianGraph channel: {}", e.getMessage());
            }
        }
        arena.close();
    }
}
