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
package com.spectrayan.spector.memory.synapse.scan;

import com.spectrayan.spector.core.similarity.SimilarityFunction;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.HeaderBits;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.scan.ScanFilter;
import com.spectrayan.spector.kernel.scan.SlabScanner;
import com.spectrayan.spector.kernel.scan.SlotVisitor;
import com.spectrayan.spector.kernel.score.CognitiveMass;
import com.spectrayan.spector.kernel.score.DecayStrategy;
import com.spectrayan.spector.kernel.score.RecordGates;
import com.spectrayan.spector.kernel.score.SynapticTagEncoder;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.synapse.CognitiveScorer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public class FilterThenScanVerificationTest {

    private static final int DIMS = 16;
    private static final EngramLayout LAYOUT = new EngramLayout(DIMS);

    @Test
    @DisplayName("Task 6.17 Anti-inversion gate: visitor.accept fires once per gate survivor, not once per record")
    void testAntiInversionGate() {
        final int recordCount = 1_000;
        final long nowMs = 1_720_000_000_000L;
        final long byteSize = (long) recordCount * LAYOUT.recordStride();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(byteSize);
            // Setup 1,000 records:
            // 900 records fail Phase 1 (tombstoned)
            // 100 records survive
            for (int i = 0; i < recordCount; i++) {
                long offset = (long) i * LAYOUT.recordStride();
                byte flags = (i % 10 == 0) ? 0 : EncodingHeaderFields.FLAG_TOMBSTONE;
                EncodingHeader header = new EncodingHeader(
                        nowMs - 1000L, 0L, 1.0f, 5.0f, 0, (short) 0, (byte) 0, flags, (byte) 50, 1.0f
                );
                LAYOUT.writeHeader(segment, offset, header);
            }

            final ScanFilter filter = new ScanFilter(
                    0L, 0L, 0L, 0L,
                    0L, Long.MAX_VALUE, nowMs, false,
                    (byte) -128, (byte) 127,
                    0.0f, (byte) 0, (byte) 0, false, false,
                    RecordGates.DEFAULT_STALE_BUCKET_THRESHOLD,
                    RecordGates.DEFAULT_WEAK_MASS_THRESHOLD
            );

            final AtomicInteger acceptCount = new AtomicInteger(0);
            final SlotVisitor countingVisitor = (slot, partition, offset, headerBits, rawScore) -> {
                acceptCount.incrementAndGet();
            };

            SimilarityFunction.resetInvocationCount();
            float[] queryVector = new float[DIMS];
            SlabScanner.scan(
                    segment, recordCount, LAYOUT, queryVector,
                    null, null, filter, null, MemoryType.SEMANTIC,
                    0L, 0, countingVisitor
            );

            // Exactly 100 survivors out of 1,000 records!
            assertThat(acceptCount.get())
                    .as("Anti-inversion gate: accept() invocations must equal gate survivors, not total records")
                    .isEqualTo(100);

            assertThat(SimilarityFunction.getInvocationCount())
                    .as("Anti-inversion gate: SIMD distance computations must equal gate survivors, not total records (Task 6.17 / R7.11)")
                    .isEqualTo(100);
        }
    }

    @Test
    @DisplayName("Task 6.18 No-post-filter gate: matches ranking below K by similarity still returned")
    void testNoPostFilterGate() {
        final int recordCount = 10;
        final int topK = 3;
        final long nowMs = 1_720_000_000_000L;
        final long byteSize = (long) recordCount * LAYOUT.recordStride();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(byteSize);
            float[] queryVector = new float[DIMS];
            Arrays.fill(queryVector, 1.0f);

            // Records 0, 1, 2: perfect vector similarity (distance = 0), BUT tombstoned!
            for (int i = 0; i < 3; i++) {
                long offset = (long) i * LAYOUT.recordStride();
                EncodingHeader header = new EncodingHeader(
                        nowMs - 1000L, 0L, 1.0f, 5.0f, 0, (short) 0, (byte) 0,
                        EncodingHeaderFields.FLAG_TOMBSTONE, (byte) 50, 1.0f
                );
                LAYOUT.writeHeader(segment, offset, header);
                for (int d = 0; d < DIMS; d++) {
                    segment.set(java.lang.foreign.ValueLayout.JAVA_BYTE, LAYOUT.vectorOffset(offset) + d, (byte) 127);
                }
            }

            // Records 3, 4, 5, 6, 7: lower vector similarity (distance > 0), BUT clean survivors!
            for (int i = 3; i < recordCount; i++) {
                long offset = (long) i * LAYOUT.recordStride();
                EncodingHeader header = new EncodingHeader(
                        nowMs - 1000L, 0L, 1.0f, 5.0f, 0, (short) 0, (byte) 0,
                        (byte) 0, (byte) 50, 1.0f
                );
                LAYOUT.writeHeader(segment, offset, header);
                for (int d = 0; d < DIMS; d++) {
                    segment.set(java.lang.foreign.ValueLayout.JAVA_BYTE, LAYOUT.vectorOffset(offset) + d, (byte) 10);
                }
            }

            final RecallOptions options = RecallOptions.builder()
                    .topK(topK)
                    .build();

            // Perform scan
            List<CognitiveScorer.ScoredRecord> results = CognitiveScorer.score(
                    segment, recordCount, LAYOUT, queryVector, options, nowMs
            );

            // A post-filter implementation would have picked top-3 (0, 1, 2) and filtered them out -> empty!
            // Filter-then-scan gates 0, 1, 2 BEFORE Phase 5, so slots 3, 4, 5 are returned!
            assertThat(results)
                    .as("No-post-filter gate: must return top-K valid survivors from lower similarity ranks")
                    .hasSize(topK);

            for (CognitiveScorer.ScoredRecord r : results) {
                assertThat(r.index())
                        .as("Returned record index must be >= 3")
                        .isGreaterThanOrEqualTo(3);
            }
        }
    }

    @Test
    @DisplayName("Task 6.19 Score-parity gate: CognitiveScoreVisitor produces identical fused scores")
    void testScoreParityGate() {
        final long nowMs = 1_720_000_000_000L;
        final long timestamp = nowMs - (10L * 86_400_000L); // 10 days old
        final float rawDistance = 0.25f;
        final float importance = 7.5f;
        final byte arousal = (byte) 180;
        final float storageStrength = 1.2f;
        final int activationCount = 3;
        final long synapticTags = 0L;
        final byte flags = 0;
        final byte valence = 0;

        long headerBits = HeaderBits.pack(
                flags, valence, arousal, activationCount,
                importance, storageStrength, MemoryType.SEMANTIC.ordinal()
        );

        final float unpackedImp = HeaderBits.importance(headerBits);
        final float unpackedStorage = HeaderBits.storageStrength(headerBits);
        final float expectedCognitiveMass = CognitiveMass.computeCognitiveMass(unpackedImp, arousal, unpackedStorage);

        final RecallOptions options = RecallOptions.builder()
                .topK(10)
                .build();

        final boolean zeroTimeDecay = (!EncodingHeaderFields.isResolved(flags) && !EncodingHeaderFields.isPinned(flags));
        final boolean twoFactorEnabled = options.twoFactorConfig() != null && options.twoFactorConfig().enabled();
        final float sExponent = options.twoFactorConfig() != null ? options.twoFactorConfig().sExponent() : 0.3f;

        float expectedScore = CognitiveScoreFusion.computeFusedScore(
                rawDistance, options.strictnessCoefficient(), false, timestamp, nowMs, expectedCognitiveMass,
                arousal, unpackedStorage, true, twoFactorEnabled, sExponent,
                activationCount, unpackedImp, options.beta(), options.alpha(), 0.0f,
                com.spectrayan.spector.memory.model.ScoreFusionMode.MULTIPLICATIVE,
                false, (byte) 0, valence, options.tagRelevanceBoost(), false,
                zeroTimeDecay, options.hyperfocusBoost(), flags, false,
                null, 0L, synapticTags, null, 0.0f
        );

        final CognitiveScoreVisitor visitor = new CognitiveScoreVisitor(options, nowMs, null, null);

        visitor.accept(
                0, 0, 0L, headerBits, rawDistance, timestamp, synapticTags
        );

        List<CognitiveScorer.ScoredRecord> drained = visitor.drain();
        assertThat(drained).hasSize(1);
        float visitorScore = drained.get(0).score();

        assertThat(visitorScore)
                .as("Score-parity gate: fused score must match reference implementation")
                .isCloseTo(expectedScore, within(1e-5f));
    }

    @Test
    @DisplayName("Task 6.20 Latency gate: p50 scan throughput validation")
    void testLatencyGate() {
        final int recordCount = 1_000;
        final long nowMs = 1_720_000_000_000L;
        final long byteSize = (long) recordCount * LAYOUT.recordStride();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(byteSize);
            for (int i = 0; i < recordCount; i++) {
                long offset = (long) i * LAYOUT.recordStride();
                byte flags = (i % 2 == 0) ? 0 : EncodingHeaderFields.FLAG_TOMBSTONE;
                EncodingHeader header = new EncodingHeader(
                        nowMs - 5000L, 0L, 1.0f, 4.0f, 0, (short) 0, (byte) 0, flags, (byte) 40, 1.0f
                );
                LAYOUT.writeHeader(segment, offset, header);
            }

            final RecallOptions options = RecallOptions.builder()
                    .topK(10)
                    .build();
            float[] queryVector = new float[DIMS];

            // Warmup
            for (int w = 0; w < 200; w++) {
                CognitiveScorer.score(segment, recordCount, LAYOUT, queryVector, options, nowMs);
            }

            // Measurement
            final int iterations = 500;
            long start = System.nanoTime();
            for (int m = 0; m < iterations; m++) {
                CognitiveScorer.score(segment, recordCount, LAYOUT, queryVector, options, nowMs);
            }
            long totalNanos = System.nanoTime() - start;
            double p50Micros = (double) totalNanos / (iterations * 1000.0);

            // Assert each scan of 1,000 records takes < 1,000 microseconds (1 ms)
            assertThat(p50Micros)
                    .as("Latency gate: p50 must be under 1,000 microseconds per query")
                    .isLessThan(1000.0);
        }
    }

    @Test
    @DisplayName("Task 6.21 Assert kernel.score stayed pure (static-only, zero cognitive imports, primitives only)")
    void testScorePackagePurity() {
        List<Class<?>> scoreClasses = List.of(
                DecayStrategy.class,
                RecordGates.class,
                SynapticTagEncoder.class,
                CognitiveMass.class
        );

        for (Class<?> clazz : scoreClasses) {
            // Assert all fields are static and final
            for (Field f : clazz.getDeclaredFields()) {
                int mod = f.getModifiers();
                assertThat(Modifier.isStatic(mod) && Modifier.isFinal(mod))
                        .as("Field %s in pure score class %s must be static final", f.getName(), clazz.getSimpleName())
                        .isTrue();
            }

            // Assert all methods are public static
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.isSynthetic()) continue;
                int mod = m.getModifiers();
                assertThat(Modifier.isStatic(mod))
                        .as("Method %s in pure score class %s must be static", m.getName(), clazz.getSimpleName())
                        .isTrue();

                // Assert parameters do not import cognitive policy types
                for (Class<?> pType : m.getParameterTypes()) {
                    String pName = pType.getName();
                    assertThat(pName.startsWith("com.spectrayan.spector.memory.model")
                            || pName.startsWith("com.spectrayan.spector.memory.cortex")
                            || pName.startsWith("com.spectrayan.spector.memory.pathway"))
                            .as("Method %s in pure score class %s must not accept cognitive policy type %s",
                                    m.getName(), clazz.getSimpleName(), pName)
                            .isFalse();
                }
            }
        }
    }
}
