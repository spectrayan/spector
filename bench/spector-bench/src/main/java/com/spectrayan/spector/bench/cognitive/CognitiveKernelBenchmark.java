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

import com.spectrayan.spector.core.cognitive.ActRActivationKernel;
import com.spectrayan.spector.core.cognitive.CognitiveMassKernel;
import com.spectrayan.spector.core.cognitive.CognitiveScoreFusionKernel;
import com.spectrayan.spector.core.cognitive.CognitiveScoreFusionKernel.FusionParams;
import com.spectrayan.spector.core.cognitive.EdgeImportanceKernel;
import com.spectrayan.spector.core.cognitive.MassDilatedDecayKernel;
import com.spectrayan.spector.core.cognitive.PowerLawDecayKernel;
import com.spectrayan.spector.core.math.SoftmaxKernel;
import com.spectrayan.spector.core.similarity.BM25Kernel;

/**
 * JMH throughput benchmarks for the ADR-0033 migrated cognitive kernels.
 *
 * <p>Every kernel that ships a struct-of-arrays batch seam (ADR-0033 Principle 3) is measured
 * <b>both ways</b> over identical synthetic data: a scalar loop over the per-record form versus a
 * single batch call. This is the measurement that decides whether a batch seam actually earns its
 * place — the review found two sites where batching was strictly slower than the inline scalar
 * path, which no correctness test can detect.</p>
 *
 * <p>Also measures the allocating vs scratch-reusing variants of
 * {@link SoftmaxKernel#applySoftmaxTemperature}, which is the ADR-0033 §4 exit-criterion check for
 * the softmax hot path.</p>
 *
 * <h3>Running</h3>
 * <pre>
 *   mvn -pl bench/spector-bench compile exec:java \
 *     -Dexec.mainClass=org.openjdk.jmh.Main \
 *     -Dexec.args="CognitiveKernelBenchmark -f 1 -wi 3 -i 5 -rf json -rff target/adr-0033-throughput.json"
 * </pre>
 *
 * <p>Add {@code -prof gc} to attach the allocation profiler; see
 * {@code CognitiveKernelAllocationBenchmark} for the dedicated memory-pressure suite.</p>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsAppend = {"--add-modules", "jdk.incubator.vector", "--enable-preview"})
public class CognitiveKernelBenchmark {

    /** Candidate-set sizes spanning a typical topK recall through a full partition scan. */
    @Param({"64", "1024", "16384"})
    public int candidates;

    private static final long NOW_MS = 1_760_000_000_000L;
    private static final int ACT_R_SLOTS = 8;
    /** Fixed seed so benchmark runs are comparable across builds. */
    private static final long SEED = 33L;

    // ── Fusion inputs (struct-of-arrays) ──
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

    // ── Decay / mass inputs ──
    private float[] decayBuckets;

    // ── ACT-R inputs ──
    private int[][] actRRingBuffers;
    private long[] creationMs;

    // ── BM25 inputs ──
    private int[] termFrequencies;
    private int[] docLengths;
    private float idf;

    // ── Edge importance inputs ──
    private int[] lastCycles;
    private int[] bridgeScores;
    private int[] sharedNeighbors;
    private float[] importancesB;
    private byte[] arousalsB;
    private byte[] valencesB;
    private float[] storagesB;
    private boolean[] protectedA;
    private boolean[] protectedB;

    // ── Softmax inputs ──
    private float[] softmaxSource;
    private float[] softmaxWorking;
    private float[] softmaxScratch;

    // ── Reused output buffer: keeps the measurement on compute, not allocation ──
    private float[] out;

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

        termFrequencies = new int[n];
        docLengths = new int[n];

        creationMs = new long[n];
        actRRingBuffers = new int[n][ACT_R_SLOTS];

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

            termFrequencies[i] = 1 + r.nextInt(30);
            docLengths[i] = 10 + r.nextInt(4000);

            creationMs[i] = NOW_MS - (long) (r.nextDouble() * 365L * 86_400_000L);
            for (int s = 0; s < ACT_R_SLOTS; s++) {
                actRRingBuffers[i][s] = r.nextInt(4) == 0 ? 0 : r.nextInt(3_000_000);
            }

            softmaxSource[i] = r.nextFloat() * 10.0f;
        }

        decayBuckets = PowerLawDecayKernel.computeBuckets(0.15f, 0.10f);
        idf = BM25Kernel.idf(37, 10_000);
        fusionParams = FusionParams.DEFAULT;

        out = new float[n];
        softmaxWorking = new float[n];
        softmaxScratch = new float[n];
    }

    @Setup(Level.Invocation)
    public void resetSoftmaxWorkingSet() {
        // applySoftmaxTemperature mutates in place; restore the input each invocation so every
        // measurement starts from identical data.
        System.arraycopy(softmaxSource, 0, softmaxWorking, 0, softmaxSource.length);
    }

    // ─────────────────────── 6-phase cognitive score fusion ───────────────────────

    @Benchmark
    public void fusionScalarLoop(Blackhole bh) {
        for (int i = 0; i < candidates; i++) {
            out[i] = CognitiveScoreFusionKernel.computeFusedScore(
                    l2dists[i], timestamps[i], NOW_MS, masses[i], arousals[i],
                    storages[i], hasStorage[i], recallCounts[i], importances[i],
                    tagOverlaps[i], valences[i], (byte) 0,
                    focusMatches[i], zeroTimeDecays[i], priors[i], fusionParams);
        }
        bh.consume(out);
    }

    @Benchmark
    public void fusionBatch(Blackhole bh) {
        CognitiveScoreFusionKernel.computeFusedScores(
                l2dists, timestamps, masses, arousals, storages, hasStorage,
                recallCounts, importances, tagOverlaps, valences, focusMatches,
                zeroTimeDecays, priors, NOW_MS, (byte) 0, fusionParams, out, candidates);
        bh.consume(out);
    }

    /** Slow path: exercises the per-element null/bounds fallback branch. */
    @Benchmark
    public void fusionBatchSlowPath(Blackhole bh) {
        CognitiveScoreFusionKernel.computeFusedScores(
                l2dists, timestamps, masses, arousals, storages, null,
                recallCounts, importances, tagOverlaps, valences, null,
                null, null, NOW_MS, (byte) 0, fusionParams, out, candidates);
        bh.consume(out);
    }

    // ─────────────────────── Linear-blend fusion (semantic re-rank) ───────────────────────

    @Benchmark
    public void linearBlendScalarLoop(Blackhole bh) {
        for (int i = 0; i < candidates; i++) {
            out[i] = CognitiveScoreFusionKernel.computeLinearBlendScore(
                    l2dists[i], importances[i], masses[i], tagOverlaps[i], 0.7f, 0.4f, 0.2f);
        }
        bh.consume(out);
    }

    @Benchmark
    public void linearBlendBatch(Blackhole bh) {
        CognitiveScoreFusionKernel.computeLinearBlendScores(
                l2dists, importances, masses, tagOverlaps, 0.7f, 0.4f, 0.2f, out, candidates);
        bh.consume(out);
    }

    // ─────────────────────── Cognitive mass ───────────────────────

    @Benchmark
    public void cognitiveMassScalarLoop(Blackhole bh) {
        for (int i = 0; i < candidates; i++) {
            out[i] = CognitiveMassKernel.computeMass(importances[i], arousals[i], storages[i]);
        }
        bh.consume(out);
    }

    @Benchmark
    public void cognitiveMassBatch(Blackhole bh) {
        CognitiveMassKernel.computeMassBatch(importances, arousals, storages, out, candidates);
        bh.consume(out);
    }

    // ─────────────────────── Power-law decay ───────────────────────

    @Benchmark
    public void powerLawDecayScalarLoop(Blackhole bh) {
        for (int i = 0; i < candidates; i++) {
            out[i] = PowerLawDecayKernel.computeDecayWithArousal(
                    timestamps[i], NOW_MS, recallCounts[i], arousals[i], decayBuckets);
        }
        bh.consume(out);
    }

    @Benchmark
    public void powerLawDecayBatch(Blackhole bh) {
        PowerLawDecayKernel.computeDecayBatch(
                timestamps, recallCounts, arousals, NOW_MS, decayBuckets, out, candidates);
        bh.consume(out);
    }

    // ─────────────────────── Mass-dilated decay ───────────────────────

    @Benchmark
    public void massDilatedDecayScalarLoop(Blackhole bh) {
        for (int i = 0; i < candidates; i++) {
            out[i] = MassDilatedDecayKernel.compute(
                    timestamps[i], NOW_MS, masses[i], arousals[i],
                    recallCounts[i], zeroTimeDecays[i], 1.0f);
        }
        bh.consume(out);
    }

    @Benchmark
    public void massDilatedDecayBatch(Blackhole bh) {
        MassDilatedDecayKernel.computeBatch(
                timestamps, masses, arousals, recallCounts, zeroTimeDecays, NOW_MS, 1.0f, out, candidates);
        bh.consume(out);
    }

    /** Null-substitution path used by SemanticRecallStrategy for the raw decay curve. */
    @Benchmark
    public void massDilatedDecayBatchNullOptionals(Blackhole bh) {
        MassDilatedDecayKernel.computeBatch(
                timestamps, masses, null, null, null, NOW_MS, 1.0f, out, candidates);
        bh.consume(out);
    }

    // ─────────────────────── ACT-R activation ───────────────────────

    @Benchmark
    public void actRScalarLoop(Blackhole bh) {
        for (int i = 0; i < candidates; i++) {
            out[i] = ActRActivationKernel.computeBucketActivation(
                    actRRingBuffers[i], creationMs[i], NOW_MS, decayBuckets);
        }
        bh.consume(out);
    }

    @Benchmark
    public void actRBatch(Blackhole bh) {
        ActRActivationKernel.computeBucketActivations(
                actRRingBuffers, creationMs, NOW_MS, decayBuckets, out, candidates);
        bh.consume(out);
    }

    // ─────────────────────── BM25 term scoring ───────────────────────

    @Benchmark
    public void bm25ScalarLoop(Blackhole bh) {
        for (int i = 0; i < candidates; i++) {
            out[i] = BM25Kernel.scoreTerm(termFrequencies[i], docLengths[i], 512.0f, 1.2f, 0.75f, idf);
        }
        bh.consume(out);
    }

    @Benchmark
    public void bm25Batch(Blackhole bh) {
        BM25Kernel.scoreTerms(termFrequencies, docLengths, 512.0f, 1.2f, 0.75f, idf, out, candidates);
        bh.consume(out);
    }

    // ─────────────────────── Edge importance (9-signal) ───────────────────────

    @Benchmark
    public void edgeImportanceScalarLoop(Blackhole bh) {
        for (int i = 0; i < candidates; i++) {
            out[i] = EdgeImportanceKernel.score(
                    l2dists[i], 50_000, lastCycles[i], bridgeScores[i], sharedNeighbors[i],
                    importances[i], importancesB[i], arousals[i], arousalsB[i],
                    valences[i], valencesB[i], storages[i], storagesB[i],
                    protectedA[i], protectedB[i], null);
        }
        bh.consume(out);
    }

    @Benchmark
    public void edgeImportanceBatch(Blackhole bh) {
        EdgeImportanceKernel.scoreBatch(
                l2dists, 50_000, lastCycles, bridgeScores, sharedNeighbors,
                importances, importancesB, arousals, arousalsB, valences, valencesB,
                storages, storagesB, protectedA, protectedB, null, out, candidates);
        bh.consume(out);
    }

    // ─────────────────────── Softmax temperature modulation ───────────────────────

    /** Allocates one float[n] scratch buffer internally. */
    @Benchmark
    public void softmaxAllocatingScratch(Blackhole bh) {
        SoftmaxKernel.applySoftmaxTemperature(softmaxWorking, 0.4f);
        bh.consume(softmaxWorking);
    }

    /** Reuses a caller-owned scratch buffer — the allocation-free hot-path variant. */
    @Benchmark
    public void softmaxReusedScratch(Blackhole bh) {
        SoftmaxKernel.applySoftmaxTemperature(softmaxWorking, 0.4f, softmaxScratch);
        bh.consume(softmaxWorking);
    }

    @Benchmark
    public void softmaxComputeProbabilities(Blackhole bh) {
        SoftmaxKernel.computeProbabilities(softmaxSource, 0.4f, out);
        bh.consume(out);
    }
}
