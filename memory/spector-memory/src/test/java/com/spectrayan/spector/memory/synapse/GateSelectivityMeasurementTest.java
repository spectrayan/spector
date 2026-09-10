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
package com.spectrayan.spector.memory.synapse;
import com.spectrayan.spector.kernel.score.SynapticTagEncoder;

import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.score.DecayStrategy;
import com.spectrayan.spector.memory.synapse.scan.CognitiveScoreFusion;
import com.spectrayan.spector.kernel.score.RecordGates;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

public class GateSelectivityMeasurementTest {

    @Test
    void measureSelectivityAcrossPhases() {
        final int count = 10_000;
        final int dims = 16;
        final EngramLayout layout = new EngramLayout(dims);
        final long nowMs = 1_720_000_000_000L;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate((long) layout.stride() * count);
            Random rng = new Random(1337);

            for (int i = 0; i < count; i++) {
                long offset = (long) i * layout.stride();
                byte flags = 0;
                if (i % 20 == 0) flags |= EncodingHeaderFields.FLAG_TOMBSTONE;
                if (i % 10 == 0) flags |= EncodingHeaderFields.FLAG_RESOLVED;
                if (i % 20 == 1) flags |= EncodingHeaderFields.FLAG_PINNED;

                byte cFlags = 0;
                if (i % 25 == 0) cFlags |= EncodingHeaderFields.FLAG_CONTRADICTED;
                if (i % 20 == 2) cFlags |= EncodingHeaderFields.FLAG_SIMULATED;

                byte sourceCode = (i % 20 == 2) ? EncodingHeaderFields.SOURCE_SIMULATED : EncodingHeaderFields.SOURCE_EXPERIENCED;
                long ts = (i % 50 == 0) ? (nowMs + 86_400_000L) : (nowMs - (long) (rng.nextDouble() * 365.0 * 86_400_000.0));
                String tag = "topic-" + (i % 10);
                long tags = SynapticTagEncoder.encode(tag);
                byte valence = (byte) (rng.nextInt(201) - 100);
                byte arousal = (byte) rng.nextInt(256);
                float importance = 0.1f + rng.nextFloat() * 8.9f;

                EncodingHeader header = new EncodingHeader(
                        ts, tags, 1.0f, importance, 0, (short) 0, valence, flags, arousal, 1.0f
                );
                layout.writeHeader(seg, offset, header);
                layout.headerLayout().writeConsolidationFlags(seg, offset, cFlags);
                layout.headerLayout().writeSourceCode(seg, offset, sourceCode);
            }

            final long queryTagMask = SynapticTagEncoder.encode("topic-1", "topic-3");
            final byte minValence = -50;
            final byte maxValence = 80;
            final float minImportance = 0.3f;
            final Long minTimestamp = nowMs - (180L * 86_400_000L);
            final Long maxTimestamp = nowMs;
            final boolean allowFuture = false;
            final boolean includeContradictions = false;
            final boolean allowSimulated = false;

            int phase1TombstoneEliminated = 0;
            int phase1cContradictionSimEliminated = 0;
            int phase1bTemporalEliminated = 0;
            int phase2TagEliminated = 0;
            int phase3ValenceEliminated = 0;
            int phase4StaleWeakEliminated = 0;
            int survivors = 0;

            for (int i = 0; i < count; i++) {
                long offset = (long) i * layout.stride();

                byte flags = layout.readFlags(seg, offset);
                if (EncodingHeaderFields.isTombstoned(flags)) {
                    phase1TombstoneEliminated++;
                    continue;
                }

                byte cFlags = layout.readConsolidationFlags(seg, offset);
                byte sourceCode = layout.readSourceCode(seg, offset);
                if ((!includeContradictions && EncodingHeaderFields.isContradicted(cFlags))
                        || (!allowSimulated && (sourceCode == EncodingHeaderFields.SOURCE_SIMULATED || EncodingHeaderFields.isSimulated(cFlags)))) {
                    phase1cContradictionSimEliminated++;
                    continue;
                }

                long timestamp = layout.readTimestamp(seg, offset);
                if (RecordGates.isTemporalGated(timestamp, minTimestamp, maxTimestamp, nowMs, allowFuture)) {
                    phase1bTemporalEliminated++;
                    continue;
                }

                long recordTags = layout.readSynapticTags(seg, offset);
                if (RecordGates.isTagGated(recordTags, queryTagMask, 0L)) {
                    phase2TagEliminated++;
                    continue;
                }

                byte valence = layout.readValence(seg, offset);
                if (RecordGates.isValenceGated(valence, minValence, maxValence)) {
                    phase3ValenceEliminated++;
                    continue;
                }

                float rawImportance = layout.readImportance(seg, offset);
                if (rawImportance < minImportance) {
                    phase4StaleWeakEliminated++;
                    continue;
                }
                int rawBucket = DecayStrategy.ageToBucket(timestamp, nowMs);
                byte arousal = layout.readArousal(seg, offset);
                float storageStrength = 1.0f;
                float cognitiveMass = CognitiveScoreFusion.computeCognitiveMass(rawImportance, arousal, storageStrength);
                if (RecordGates.isStaleAndWeak(rawBucket, rawImportance, flags, cognitiveMass)) {
                    phase4StaleWeakEliminated++;
                    continue;
                }

                survivors++;
            }

            System.out.println("=== Task 6.1 Gate Selectivity Results (10,000 records) ===");
            System.out.printf("Total records:                         %d%n", count);
            System.out.printf("Phase 1 (Tombstone) eliminated:        %d (%.2f%%)%n", phase1TombstoneEliminated, (phase1TombstoneEliminated * 100.0 / count));
            System.out.printf("Phase 1c (Contradict/Sim) eliminated:  %d (%.2f%%)%n", phase1cContradictionSimEliminated, (phase1cContradictionSimEliminated * 100.0 / count));
            System.out.printf("Phase 1b (Temporal) eliminated:        %d (%.2f%%)%n", phase1bTemporalEliminated, (phase1bTemporalEliminated * 100.0 / count));
            System.out.printf("Phase 2 (Synaptic Tag) eliminated:     %d (%.2f%%)%n", phase2TagEliminated, (phase2TagEliminated * 100.0 / count));
            System.out.printf("Phase 3 (Valence) eliminated:          %d (%.2f%%)%n", phase3ValenceEliminated, (phase3ValenceEliminated * 100.0 / count));
            System.out.printf("Phase 4 (Importance/Decay) eliminated: %d (%.2f%%)%n", phase4StaleWeakEliminated, (phase4StaleWeakEliminated * 100.0 / count));
            System.out.printf("Surviving to Phase 5 (SIMD L2):        %d (%.2f%%)%n", survivors, (survivors * 100.0 / count));
            System.out.printf("Total pre-SIMD elimination rate:       %.2f%%%n", ((count - survivors) * 100.0 / count));

            assertThat(survivors).isLessThan(count / 5);
        }
    }
}
