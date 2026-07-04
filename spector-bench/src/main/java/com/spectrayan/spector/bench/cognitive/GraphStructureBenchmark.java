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
package com.spectrayan.spector.bench.cognitive;

import com.spectrayan.spector.memory.graph.EdgeImportance;
import com.spectrayan.spector.memory.graph.EntityGraph;
import com.spectrayan.spector.memory.graph.HyperEntityGraph;
import com.spectrayan.spector.memory.hebbian.HebbianGraph;
import com.spectrayan.spector.memory.hebbian.HebbianGraphCsr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Head-to-head benchmark: HebbianGraph (legacy V2) vs HebbianGraphCsr (V3) vs HyperEntityGraph.
 *
 * <p>Measures memory footprint, edge insertion throughput, neighbor lookup latency,
 * decay throughput, and persistence I/O for each graph implementation at various scales.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   java ... GraphStructureBenchmark [capacity] [avgDegree]
 *   # defaults: capacity=100000, avgDegree=4
 * }</pre>
 */
public final class GraphStructureBenchmark {

    // ── Configuration ──
    private final int capacity;
    private final int avgDegree;
    private final int totalEdges;
    private final Random rng = new Random(42);

    public GraphStructureBenchmark(int capacity, int avgDegree) {
        this.capacity = capacity;
        this.avgDegree = avgDegree;
        this.totalEdges = capacity * avgDegree / 2; // undirected
    }

    // ═══════════════════════════════════════════════════════════
    //  MAIN
    // ═══════════════════════════════════════════════════════════

    public static void main(String[] args) {
        int capacity = args.length >= 1 ? Integer.parseInt(args[0]) : 100_000;
        int avgDegree = args.length >= 2 ? Integer.parseInt(args[1]) : 4;

        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  Spector Graph Structure Benchmark");
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.printf("  Capacity:   %,d nodes%n", capacity);
        System.out.printf("  Avg degree: %d%n", avgDegree);
        System.out.printf("  Edges:      %,d (undirected)%n", capacity * avgDegree / 2);
        System.out.println();

        var bench = new GraphStructureBenchmark(capacity, avgDegree);

        System.out.println("── 1. HebbianGraph (Legacy V2 — Fixed-Width) ──");
        bench.benchHebbianLegacy();
        System.out.println();

        System.out.println("── 2. HebbianGraphCsr (V3 — CSR Sparse) ──");
        bench.benchHebbianCsr();
        System.out.println();

        System.out.println("── 3. HyperEntityGraph (Hyperedge) ──");
        bench.benchHyperEntityGraph();
        System.out.println();

        System.out.println("── 4. Side-by-side Comparison ──");
        bench.runComparison();
    }

    // ═══════════════════════════════════════════════════════════
    //  HEBBIAN LEGACY (V2)
    // ═══════════════════════════════════════════════════════════

    private void benchHebbianLegacy() {
        rng.setSeed(42);

        long t0 = System.nanoTime();
        HebbianGraph g = new HebbianGraph(capacity);
        long allocNs = System.nanoTime() - t0;

        // Insert edges
        long insertStart = System.nanoTime();
        int inserted = 0;
        for (int i = 0; i < totalEdges; i++) {
            int a = rng.nextInt(capacity);
            int b = rng.nextInt(capacity);
            if (a != b) {
                g.strengthen(a, b, 1.0f + rng.nextFloat());
                inserted++;
            }
        }
        long insertNs = System.nanoTime() - insertStart;

        // Neighbor lookups
        long lookupStart = System.nanoTime();
        int lookups = 10_000;
        int totalNeighbors = 0;
        for (int i = 0; i < lookups; i++) {
            totalNeighbors += g.neighbors(rng.nextInt(capacity)).size();
        }
        long lookupNs = System.nanoTime() - lookupStart;

        // Memory — HebbianGraph layout: (4 + maxDegree * 12) * capacity
        long memBytes = (long) (4 + HebbianGraph.DEFAULT_MAX_DEGREE * 12) * capacity;

        // Decay
        long decayStart = System.nanoTime();
        int decayed = g.decayEdges(0.95f);
        long decayNs = System.nanoTime() - decayStart;

        // Persistence
        long saveNs = 0, loadNs = 0;
        try {
            Path tmpFile = Files.createTempFile("hebbian-legacy-", ".dat");
            long saveStart = System.nanoTime();
            g.save(tmpFile);
            saveNs = System.nanoTime() - saveStart;
            long fileSize = Files.size(tmpFile);

            long loadStart = System.nanoTime();
            HebbianGraph loaded = HebbianGraph.load(tmpFile, capacity);
            loadNs = System.nanoTime() - loadStart;
            loaded.close();
            Files.deleteIfExists(tmpFile);

            printResult("  File size", formatBytes(fileSize));
        } catch (IOException e) {
            System.out.println("  Persistence: SKIPPED (" + e.getMessage() + ")");
        }

        printResult("  Allocation", formatNs(allocNs));
        printResult("  Insert " + inserted + " edges", formatNs(insertNs));
        printResult("  Insert throughput", String.format("%,.0f edges/sec", inserted / (insertNs / 1e9)));
        printResult("  " + lookups + " neighbor lookups", formatNs(lookupNs));
        printResult("  Avg lookup", formatNs(lookupNs / lookups));
        printResult("  Avg neighbors/node", String.format("%.1f", (double) totalNeighbors / lookups));
        printResult("  Memory footprint", formatBytes(memBytes));
        printResult("  Decay (factor=0.95)", formatNs(decayNs) + " (" + decayed + " removed)");
        printResult("  Save", formatNs(saveNs));
        printResult("  Load", formatNs(loadNs));

        g.close();
    }

    // ═══════════════════════════════════════════════════════════
    //  HEBBIAN CSR (V3)
    // ═══════════════════════════════════════════════════════════

    private void benchHebbianCsr() {
        rng.setSeed(42);
        int edgeCapacity = totalEdges * 3; // headroom for bidirectional

        long t0 = System.nanoTime();
        HebbianGraphCsr g = new HebbianGraphCsr(capacity, edgeCapacity,
                HebbianGraph.DEFAULT_MAX_DEGREE, EdgeImportance.DEFAULT);
        long allocNs = System.nanoTime() - t0;

        // Insert edges
        long insertStart = System.nanoTime();
        int inserted = 0;
        for (int i = 0; i < totalEdges; i++) {
            int a = rng.nextInt(capacity);
            int b = rng.nextInt(capacity);
            if (a != b) {
                g.strengthen(a, b, 1.0f + rng.nextFloat());
                inserted++;
            }
        }
        long insertNs = System.nanoTime() - insertStart;

        // Neighbor lookups
        long lookupStart = System.nanoTime();
        int lookups = 10_000;
        int totalNeighbors = 0;
        for (int i = 0; i < lookups; i++) {
            totalNeighbors += g.neighbors(rng.nextInt(capacity)).size();
        }
        long lookupNs = System.nanoTime() - lookupStart;

        // Memory
        long memBytes = g.memoryUsageBytes();

        // Decay
        long decayStart = System.nanoTime();
        int decayed = g.decayEdges(0.95f);
        long decayNs = System.nanoTime() - decayStart;

        // Persistence
        long saveNs = 0, loadNs = 0;
        try {
            Path tmpFile = Files.createTempFile("hebbian-csr-", ".dat");
            long saveStart = System.nanoTime();
            g.save(tmpFile);
            saveNs = System.nanoTime() - saveStart;
            long fileSize = Files.size(tmpFile);

            long loadStart = System.nanoTime();
            HebbianGraphCsr loaded = HebbianGraphCsr.load(tmpFile, capacity);
            loadNs = System.nanoTime() - loadStart;
            loaded.close();
            Files.deleteIfExists(tmpFile);

            printResult("  File size", formatBytes(fileSize));
        } catch (IOException e) {
            System.out.println("  Persistence: SKIPPED (" + e.getMessage() + ")");
        }

        printResult("  Allocation", formatNs(allocNs));
        printResult("  Insert " + inserted + " edges", formatNs(insertNs));
        printResult("  Insert throughput", String.format("%,.0f edges/sec", inserted / (insertNs / 1e9)));
        printResult("  " + lookups + " neighbor lookups", formatNs(lookupNs));
        printResult("  Avg lookup", formatNs(lookupNs / lookups));
        printResult("  Avg neighbors/node", String.format("%.1f", (double) totalNeighbors / lookups));
        printResult("  Memory footprint", formatBytes(memBytes));
        printResult("  Decay (factor=0.95)", formatNs(decayNs) + " (" + decayed + " removed)");
        printResult("  Save", formatNs(saveNs));
        printResult("  Load", formatNs(loadNs));

        g.close();
    }

    // ═══════════════════════════════════════════════════════════
    //  HYPER ENTITY GRAPH
    // ═══════════════════════════════════════════════════════════

    private void benchHyperEntityGraph() {
        rng.setSeed(42);
        int entityCap = capacity;
        int hyperedgeCap = totalEdges; // one hyperedge per relationship

        long t0 = System.nanoTime();
        HyperEntityGraph g = new HyperEntityGraph(entityCap, hyperedgeCap);
        long allocNs = System.nanoTime() - t0;

        // Insert hyperedges (mix of 2-vertex and 3-vertex)
        long insertStart = System.nanoTime();
        int inserted = 0;
        for (int i = 0; i < totalEdges; i++) {
            int a = rng.nextInt(entityCap);
            int b = rng.nextInt(entityCap);
            if (a == b) continue;

            if (rng.nextFloat() < 0.4f) {
                // 3-vertex hyperedge (40% of the time)
                int c = rng.nextInt(entityCap);
                if (c != a && c != b) {
                    g.addHyperedge(new int[]{a, b, c}, new int[]{1, 2, 3},
                            rng.nextInt(10), 1.0f + rng.nextFloat(), i, System.currentTimeMillis());
                    inserted++;
                }
            } else {
                // 2-vertex binary hyperedge
                g.addHyperedge(new int[]{a, b}, new int[]{1, 2},
                        rng.nextInt(10), 1.0f + rng.nextFloat(), i, System.currentTimeMillis());
                inserted++;
            }
        }
        long insertNs = System.nanoTime() - insertStart;

        // Traversal: find co-occurring entities
        long lookupStart = System.nanoTime();
        int lookups = 10_000;
        int totalCoOccurring = 0;
        for (int i = 0; i < lookups; i++) {
            totalCoOccurring += g.findCoOccurringEntities(rng.nextInt(entityCap)).size();
        }
        long lookupNs = System.nanoTime() - lookupStart;

        // Memory
        long memBytes = g.memoryUsageBytes();

        // Decay
        long decayStart = System.nanoTime();
        int decayed = g.decayHyperedges(0.95f, 0.1f);
        long decayNs = System.nanoTime() - decayStart;

        // Persistence
        long saveNs = 0, loadNs = 0;
        try {
            Path tmpFile = Files.createTempFile("hyper-entity-", ".dat");
            long saveStart = System.nanoTime();
            g.save(tmpFile);
            saveNs = System.nanoTime() - saveStart;
            long fileSize = Files.size(tmpFile);

            long loadStart = System.nanoTime();
            HyperEntityGraph loaded = HyperEntityGraph.load(tmpFile, entityCap, hyperedgeCap);
            loadNs = System.nanoTime() - loadStart;
            loaded.close();
            Files.deleteIfExists(tmpFile);

            printResult("  File size", formatBytes(fileSize));
        } catch (Exception e) {
            System.out.println("  Persistence: SKIPPED (" + e.getMessage() + ")");
        }

        printResult("  Allocation", formatNs(allocNs));
        printResult("  Insert " + inserted + " hyperedges", formatNs(insertNs));
        printResult("  Insert throughput", String.format("%,.0f hyperedges/sec", inserted / (insertNs / 1e9)));
        printResult("  " + lookups + " co-occurrence lookups", formatNs(lookupNs));
        printResult("  Avg lookup", formatNs(lookupNs / lookups));
        printResult("  Avg co-occurring/node", String.format("%.1f", (double) totalCoOccurring / lookups));
        printResult("  Total hyperedges", String.valueOf(g.totalHyperedges()));
        printResult("  Memory footprint", formatBytes(memBytes));
        printResult("  Decay (factor=0.95)", formatNs(decayNs) + " (" + decayed + " evicted)");
        printResult("  Save", formatNs(saveNs));
        printResult("  Load", formatNs(loadNs));

        g.close();
    }

    // ═══════════════════════════════════════════════════════════
    //  SIDE-BY-SIDE COMPARISON
    // ═══════════════════════════════════════════════════════════

    private void runComparison() {
        rng.setSeed(42);

        // Create all three with same seed
        HebbianGraph legacy = new HebbianGraph(capacity);
        HebbianGraphCsr csr = new HebbianGraphCsr(capacity, totalEdges * 3,
                HebbianGraph.DEFAULT_MAX_DEGREE, EdgeImportance.DEFAULT);

        // Same edges in both
        rng.setSeed(42);
        for (int i = 0; i < totalEdges; i++) {
            int a = rng.nextInt(capacity);
            int b = rng.nextInt(capacity);
            if (a != b) {
                float w = 1.0f + rng.nextFloat();
                legacy.strengthen(a, b, w);
                csr.strengthen(a, b, w);
            }
        }

        long legacyMem = (long) (4 + HebbianGraph.DEFAULT_MAX_DEGREE * 12) * capacity;
        long csrMem = csr.memoryUsageBytes();
        float reductionPct = (1.0f - (float) csrMem / legacyMem) * 100f;

        System.out.println("  ┌─────────────────────────────┬──────────────┬──────────────┬──────────┐");
        System.out.println("  │ Metric                      │ Legacy (V2)  │ CSR (V3)     │ Δ        │");
        System.out.println("  ├─────────────────────────────┼──────────────┼──────────────┼──────────┤");
        System.out.printf("  │ Memory footprint             │ %12s │ %12s │ -%5.1f%%  │%n",
                formatBytes(legacyMem), formatBytes(csrMem), reductionPct);
        System.out.printf("  │ Total edges                  │ %,12d │ %,12d │          │%n",
                legacy.totalEdges(), csr.totalEdges());
        System.out.println("  └─────────────────────────────┴──────────────┴──────────────┴──────────┘");
        System.out.println();

        // Comparison of relationship representation
        int relationships = 10_000;
        int binaryEdges = relationships * 3; // 3-entity → 3 binary edges
        int hyperEdges = relationships;      // 3-entity → 1 hyperedge
        float graphReduction = (1.0f - (float) hyperEdges / binaryEdges) * 100f;

        System.out.println("  ┌─────────────────────────────┬──────────────┬──────────────┬──────────┐");
        System.out.println("  │ Entity Relationships         │ Binary Edges │ Hyperedges   │ Δ        │");
        System.out.println("  ├─────────────────────────────┼──────────────┼──────────────┼──────────┤");
        System.out.printf("  │ %,d 3-entity relationships   │ %,12d │ %,12d │ -%5.1f%%  │%n",
                relationships, binaryEdges, hyperEdges, graphReduction);
        System.out.println("  └─────────────────────────────┴──────────────┴──────────────┴──────────┘");

        legacy.close();
        csr.close();
    }

    // ═══════════════════════════════════════════════════════════
    //  FORMATTING
    // ═══════════════════════════════════════════════════════════

    private static void printResult(String label, String value) {
        System.out.printf("  %-35s %s%n", label, value);
    }

    private static String formatNs(long ns) {
        if (ns < 1_000) return ns + " ns";
        if (ns < 1_000_000) return String.format("%.1f µs", ns / 1e3);
        if (ns < 1_000_000_000) return String.format("%.2f ms", ns / 1e6);
        return String.format("%.2f s", ns / 1e9);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
