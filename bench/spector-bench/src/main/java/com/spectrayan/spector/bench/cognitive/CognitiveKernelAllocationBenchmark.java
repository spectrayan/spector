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

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.spectrayan.spector.core.cognitive.CognitiveScoreFusionKernel;
import com.spectrayan.spector.core.cognitive.CognitiveScoreFusionKernel.FusionParams;
import com.spectrayan.spector.core.cognitive.EdgeImportanceKernel;
import com.spectrayan.spector.core.cognitive.MassDilatedDecayKernel;
import com.spectrayan.spector.core.math.SoftmaxKernel;
import com.spectrayan.spector.core.similarity.CosineSimilarity;
import com.spectrayan.spector.core.similarity.VectorOps;

/**
 * Allocation-rate benchmarks for ADR-0033 hot paths. <b>Must be run with {@code -prof gc}</b> —
 * the throughput numbers are secondary; the figure of merit is
 * {@code gc.alloc.rate.norm}, which reports bytes allocated per operation.
 *
 * <h3>Why this exists</h3>
 * <p>The repo rule is zero allocation in hot paths, and ADR-0033 §4 makes JMH deltas an exit
 * criterion. Two regressions found in review were invisible to correctness tests and to throughput
 * alone: a batch seam that added four array allocations per graph-edge insertion, and a softmax
 * that traded one buffer for N extra {@code Math.exp} calls. Both show up immediately in
 * {@code gc.alloc.rate.norm}. Each pair below contrasts an allocating shape against its
 * allocation-free equivalent, so a regression is a visible number rather than a judgement call.</p>
 *
 * <h3>Expected results</h3>
 * <ul>
 *   <li>{@code *ReusedBuffer} / {@code *PreAllocated} variants should report
 *       {@code gc.alloc.rate.norm} at or near <b>0 B/op</b>.</li>
 *   <li>{@code *PerCallAllocation} variants scale linearly with {@code candidates} and exist purely
 *       as the contrast baseline — they are what the code must NOT do.</li>
 *   <li>{@code fusionBatchReusedBuffer} at 16384 candidates must stay at 0 B/op; any regression
 *       there means a kernel started allocating internally.</li>
 * </ul>
 *
 * <h3>Running</h3>
 * <pre>
 *   mvn -pl bench/spector-bench compile exec:java \
 *     -Dexec.mainClass=org.openjdk.jmh.Main \
 *     -Dexec.args="CognitiveKernelAllocationBenchmark -f 1 -wi 3 -i 5 -prof gc \
 *                  -rf json -rff target/adr-0033-allocation.json"
 * </pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsAppend = {"--add-modules", "jdk.incubator.vector", "--enable-preview"})
public class CognitiveKernelAllocationBenchmark {

    @Param({"64", "1024", "16384"})
    public int candidates;

    /** Embedding width for the similarity allocation probes. */
    @Param({"768"})
    public int dimensions;

    private static final long NOW_MS = 1_760_000_000_000L;
    /** Fixed seed so benchmark runs are comparable across builds. */
    private static final long SEED = 33L;

    private float[] l2dists;
    private long[] timestamps;
    private float[] masses;
    private byte[] arousals;
    private float[] storages;
    private boolean[] hasStorage;
    private int[] recallCounts;
    private float[] importances;
    private float[] tagOverlaps;
    private byte[] valences;
    private boolean[] focusMatches;
    private boolean[] zeroTimeDecays;
    private float[] priors;
    private FusionParams fusionParams;

    private int[] lastCycles;
    private int[] bridgeScores;
    private int[] sharedNeighbors;
    private float[] importancesB;
    private byte[] arousalsB;
    private byte[] valencesB;
    private float[] storagesB;
    private boolean[] protectedA;
    private boolean[] protectedB;

    private float[] reusedOut;
    private float[] softmaxSource;
    private float[] softmaxWorking;
    private float[] softmaxScratch;

    private float[] vectorA;
    private float[] vectorB;
    private float[] normalizeTarget;

    @Setup(Level.Trial)
    public void setup() {
        final Random r = new Random(SEED);
        final int n = candidates;

        l2dists = new float[n];
        timestamps = new long[n];
        masses = new float[n];
        arousals = new byte[n];
        storages = new float[n];
        hasStorage = new boolean[n];
        recallCounts = new int[n];
        importances = new float[n];
        tagOverlaps = new float[n];
        valences = new byte[n];
        focusMatches = new boolean[n];
        zeroTimeDecays = new boolean[n];
        priors = new float[n];

        lastCycles = new int[n];
        bridgeScores = new int[n];
        sharedNeighbors = new int[n];
        importancesB = new float[n];
        arousalsB = new byte[n];
        valencesB = new byte[n];
        storagesB = new float[n];
        protectedA = new boolean[n];
        protectedB = new boolean[n];

        softmaxSource = new float[n];

        for (int i = 0; i < n; i++) {
            l2dists[i] = r.nextFloat() * 4.0f;
            timestamps[i] = NOW_MS - (long) (r.nextDouble() * 3L * 365 * 86_400_000L);
            masses[i] = r.nextFloat() * 5.0f;
            arousals[i] = (byte) r.nextInt(256);
            storages[i] = 1.0f + r.nextFloat() * 4.0f;
            hasStorage[i] = r.nextBoolean();
            recallCounts[i] = r.nextInt(15);
            importances[i] = r.nextFloat() * 10.0f;
            tagOverlaps[i] = r.nextFloat();
            valences[i] = (byte) (r.nextInt(255) - 128);
            focusMatches[i] = r.nextInt(20) == 0;
            zeroTimeDecays[i] = r.nextInt(20) == 0;
            priors[i] = r.nextFloat();

            lastCycles[i] = 50_000 - r.nextInt(500);
            bridgeScores[i] = r.nextInt(256);
            sharedNeighbors[i] = r.nextInt(20);
            importancesB[i] = r.nextFloat() * 10.0f;
            arousalsB[i] = (byte) r.nextInt(256);
            valencesB[i] = (byte) (r.nextInt(255) - 128);
            storagesB[i] = 1.0f + r.nextFloat() * 4.0f;
            protectedA[i] = r.nextInt(8) == 0;
            protectedB[i] = r.nextInt(8) == 0;

            softmaxSource[i] = r.nextFloat() * 10.0f;
        }

        fusionParams = FusionParams.DEFAULT;
        reusedOut = new float[n];
        softmaxWorking = new float[n];
        softmaxScratch = new float[n];

        vectorA = new float[dimensions];
        vectorB = new float[dimensions];
        normalizeTarget = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            vectorA[i] = r.nextFloat() * 2.0f - 1.0f;
            vectorB[i] = r.nextFloat() * 2.0f - 1.0f;
        }
    }

    @Setup(Level.Invocation)
    public void resetSoftmaxWorkingSet() {
        System.arraycopy(softmaxSource, 0, softmaxWorking, 0, softmaxSource.length);
    }

    // ─────────────────────── Fusion: reused vs per-call output buffer ───────────────────────

    /** Target: 0 B/op. A non-zero result means a kernel began allocating internally. */
    @Benchmark
    public void fusionBatchReusedBuffer(Blackhole bh) {
        CognitiveScoreFusionKernel.computeFusedScores(
                l2dists, timestamps, masses, arousals, storages, hasStorage,
                recallCounts, importances, tagOverlaps, valences, focusMatches,
                zeroTimeDecays, priors, NOW_MS, (byte) 0, fusionParams, reusedOut, candidates);
        bh.consume(reusedOut);
    }

    /** Contrast baseline: allocates the output array on every call. */
    @Benchmark
    public void fusionBatchPerCallAllocation(Blackhole bh) {
        float[] out = new float[candidates];
        CognitiveScoreFusionKernel.computeFusedScores(
                l2dists, timestamps, masses, arousals, storages, hasStorage,
                recallCounts, importances, tagOverlaps, valences, focusMatches,
                zeroTimeDecays, priors, NOW_MS, (byte) 0, fusionParams, out, candidates);
        bh.consume(out);
    }

    // ─────────────────────── Decay: null substitution vs throwaway zero arrays ───────────────────────

    /**
     * The shape SemanticRecallStrategy uses for its raw decay curve after the ADR-0033 fix:
     * null optionals, no throwaway arrays. Target: 0 B/op.
     */
    @Benchmark
    public void massDilatedDecayNullOptionals(Blackhole bh) {
        MassDilatedDecayKernel.computeBatch(
                timestamps, masses, null, null, null, NOW_MS, 1.0f, reusedOut, candidates);
        bh.consume(reusedOut);
    }

    /**
     * The pre-fix shape: allocates a zero-filled {@code byte[n]} and {@code int[n]} per query
     * purely to pass neutral values. Kept as the contrast baseline.
     */
    @Benchmark
    public void massDilatedDecayThrowawayZeroArrays(Blackhole bh) {
        MassDilatedDecayKernel.computeBatch(
                timestamps, masses, new byte[candidates], new int[candidates], new boolean[candidates],
                NOW_MS, 1.0f, reusedOut, candidates);
        bh.consume(reusedOut);
    }

    // ─────────────────────── Softmax: scratch reuse vs internal allocation ───────────────────────

    /** Target: 0 B/op — the hot-path variant. */
    @Benchmark
    public void softmaxReusedScratch(Blackhole bh) {
        SoftmaxKernel.applySoftmaxTemperature(softmaxWorking, 0.4f, softmaxScratch);
        bh.consume(softmaxWorking);
    }

    /** Allocates a single float[n] internally — should be exactly 4*n bytes plus header. */
    @Benchmark
    public void softmaxInternalAllocation(Blackhole bh) {
        SoftmaxKernel.applySoftmaxTemperature(softmaxWorking, 0.4f);
        bh.consume(softmaxWorking);
    }

    // ─────────────────────── Edge importance: batch marshalling cost ───────────────────────

    /**
     * Batch scoring with pre-marshalled arrays. Target: 0 B/op.
     *
     * <p>Compare against {@link #edgeImportanceScalarInlineNoMarshalling} to see why the hot
     * graph-eviction path scans inline instead of batching: on that path the arrays do not already
     * exist, so batching would have to allocate them first — see
     * {@link #edgeImportanceBatchWithMarshalling}.</p>
     */
    @Benchmark
    public void edgeImportanceBatchPreMarshalled(Blackhole bh) {
        EdgeImportanceKernel.scoreBatch(
                l2dists, 50_000, lastCycles, bridgeScores, sharedNeighbors,
                importances, importancesB, arousals, arousalsB, valences, valencesB,
                storages, storagesB, protectedA, protectedB, null, reusedOut, candidates);
        bh.consume(reusedOut);
    }

    /**
     * Batch scoring including the marshalling the hot path would have to pay. This is the
     * allocation profile the graph-eviction regression had: several arrays per invocation.
     */
    @Benchmark
    public void edgeImportanceBatchWithMarshalling(Blackhole bh) {
        float[] weights = new float[candidates];
        int[] cycles = new int[candidates];
        int[] bridges = new int[candidates];
        float[] out = new float[candidates];
        for (int i = 0; i < candidates; i++) {
            weights[i] = l2dists[i];
            cycles[i] = lastCycles[i];
            bridges[i] = bridgeScores[i];
        }
        EdgeImportanceKernel.scoreStructuralBatch(
                weights, 50_000, null, null, bridges, null, out, candidates);
        bh.consume(out);
        bh.consume(cycles);
    }

    /** The zero-allocation single-pass shape the hot eviction path actually uses. */
    @Benchmark
    public void edgeImportanceScalarInlineNoMarshalling(Blackhole bh) {
        float minScore = Float.MAX_VALUE;
        for (int i = 0; i < candidates; i++) {
            float score = EdgeImportanceKernel.scoreStructural(
                    l2dists[i], 50_000, lastCycles[i], bridgeScores[i], 0, null);
            if (score < minScore) {
                minScore = score;
            }
        }
        bh.consume(minScore);
    }

    // ─────────────────────── Similarity: allocation-free scalar reductions ───────────────────────

    /** Target: 0 B/op. SIMD cosine must not allocate. */
    @Benchmark
    public void cosineSimd(Blackhole bh) {
        bh.consume(CosineSimilarity.compute(vectorA, vectorB));
    }

    /** Target: 0 B/op. The double-accumulator variant must also be allocation-free. */
    @Benchmark
    public void cosineDoubleAccumulator(Blackhole bh) {
        bh.consume(CosineSimilarity.computeDouble(vectorA, vectorB));
    }

    /** Target: 0 B/op. computeSafe adds only guard branches, never a copy. */
    @Benchmark
    public void cosineSafe(Blackhole bh) {
        bh.consume(CosineSimilarity.computeSafe(vectorA, vectorB));
    }

    /** Slice form writes into a caller buffer. Target: 0 B/op. */
    @Benchmark
    public void normalizeIntoReusedBuffer(Blackhole bh) {
        VectorOps.normalize(vectorA, 0, normalizeTarget, 0, dimensions);
        bh.consume(normalizeTarget);
    }

    /** Array-returning form allocates one float[dimensions] — the contrast baseline. */
    @Benchmark
    public void normalizeAllocatingForm(Blackhole bh) {
        bh.consume(VectorOps.normalize(vectorA));
    }
}
