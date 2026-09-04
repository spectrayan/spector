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
package com.spectrayan.spector.memory.kernel.layout;

import com.spectrayan.spector.memory.cortex.SemanticMemory;
import com.spectrayan.spector.memory.model.EngramSource;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.synapse.CognitiveScorer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates Source Honesty (NF7 / R6.1, R6.2, R6.3):
 * <ul>
 *     <li>R6.1: Dedicated header field at byte offset 46 stores {@link EngramSource}.</li>
 *     <li>R6.2: Default recall hard-gates {@link EngramSource#SIMULATED}.</li>
 *     <li>R6.3: Source provenance is never inferred from ID string prefixes or synaptic tags.</li>
 * </ul>
 */
@DisplayName("Source Honesty & Provenance Isolation Tests (NF7 / R6.1, R6.2, R6.3)")
class SourceHonestyTest {

    private final EncodingHeaderLayout layout = EncodingHeaderLayout.INSTANCE;

    @Test
    @DisplayName("R6.1: All four EngramSource values write and read correctly at offset 46")
    void testSourceFieldByteOffsetAndRoundTrip() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(64L);

            for (EngramSource expectedSource : EngramSource.values()) {
                long now = System.currentTimeMillis();
                EncodingHeader header = new EncodingHeader(
                        now, 0x1234L, 1.0f, 5.0f, 0, (short) 0, (byte) 10,
                        (byte) 0, (byte) 50, 1.0f, (byte) 1, (byte) 2, (byte) 3,
                        (short) 4, 0.5f, (byte) 0, expectedSource
                );

                layout.writeHeader(segment, 0L, header);

                // 1. Direct raw byte inspection at offset 46
                byte rawByte = segment.get(ValueLayout.JAVA_BYTE, EncodingHeaderFields.OFFSET_V2_SOURCE);
                assertThat(rawByte)
                        .as("Offset 46 must match raw code for %s", expectedSource)
                        .isEqualTo(expectedSource.code());

                // 2. Layout fast zero-allocation accessor
                assertThat(layout.readSourceCode(segment, 0L)).isEqualTo(expectedSource.code());

                // 3. Layout enum accessor
                assertThat(layout.readSource(segment, 0L)).isEqualTo(expectedSource);

                // 4. Full header record read
                EncodingHeader readHeader = layout.readHeader(segment, 0L);
                assertThat(readHeader.source()).isEqualTo(expectedSource);
            }
        }
    }

    @Test
    @DisplayName("R6.2: Default recall gates out SIMULATED traces, while allowSimulated(true) admits them")
    void testDefaultRecallHardGatesSimulatedSources() {
        final int dims = 8;
        final long now = System.currentTimeMillis();

        try (SemanticMemory store = new SemanticMemory(dims, 10)) {
            final EngramLayout engramLayout = store.cognitiveLayout();
            final byte[] dummyVec = new byte[engramLayout.quantizedVecBytes()];

            // Record 0: EXPERIENCED
            EncodingHeader h0 = new EncodingHeader(now, 0L, 1.0f, 5.0f, 0, (short) 0, (byte) 0,
                    (byte) 0, (byte) 0, 1.0f, (byte) 0, (byte) 0, (byte) 0, (short) 0, 0.0f, (byte) 0,
                    EngramSource.EXPERIENCED);
            store.append(h0, dummyVec);

            // Record 1: SIMULATED (should be gated out by default recall)
            EncodingHeader h1 = new EncodingHeader(now, 0L, 1.0f, 5.0f, 0, (short) 0, (byte) 0,
                    (byte) 0, (byte) 0, 1.0f, (byte) 0, (byte) 0, (byte) 0, (short) 0, 0.0f, (byte) 0,
                    EngramSource.SIMULATED);
            store.append(h1, dummyVec);

            // Record 2: DISTILLED
            EncodingHeader h2 = new EncodingHeader(now, 0L, 1.0f, 5.0f, 0, (short) 0, (byte) 0,
                    (byte) 0, (byte) 0, 1.0f, (byte) 0, (byte) 0, (byte) 0, (short) 0, 0.0f, (byte) 0,
                    EngramSource.DISTILLED);
            store.append(h2, dummyVec);

            // Record 3: REHEARSED
            EncodingHeader h3 = new EncodingHeader(now, 0L, 1.0f, 5.0f, 0, (short) 0, (byte) 0,
                    (byte) 0, (byte) 0, 1.0f, (byte) 0, (byte) 0, (byte) 0, (short) 0, 0.0f, (byte) 0,
                    EngramSource.REHEARSED);
            store.append(h3, dummyVec);

            final float[] queryVector = new float[dims];

            // Default recall: allowSimulated is false
            RecallOptions defaultOptions = RecallOptions.builder().topK(10).build();
            List<CognitiveScorer.ScoredRecord> defaultResults = CognitiveScorer.score(
                    store.primarySegment(), 4, engramLayout, queryVector, defaultOptions, now, 0L,
                    null, null, null, null, null, null);

            assertThat(defaultResults).hasSize(3);
            for (CognitiveScorer.ScoredRecord r : defaultResults) {
                assertThat(engramLayout.readSource(store.primarySegment(), r.offset()))
                        .as("Default recall must never return SIMULATED traces")
                        .isNotEqualTo(EngramSource.SIMULATED);
            }

            // Permissive recall: allowSimulated is true
            RecallOptions simOptions = RecallOptions.builder().topK(10).allowSimulated(true).build();
            List<CognitiveScorer.ScoredRecord> simResults = CognitiveScorer.score(
                    store.primarySegment(), 4, engramLayout, queryVector, simOptions, now, 0L,
                    null, null, null, null, null, null);

            assertThat(simResults).hasSize(4);
        }
    }

    @Test
    @DisplayName("R6.3: Source honesty is determined strictly by header field, NOT by ID prefixes or tags")
    void testSourceNotDerivedFromIdPrefixOrSynapticTags() {
        final int dims = 8;
        final long now = System.currentTimeMillis();

        try (SemanticMemory store = new SemanticMemory(dims, 10)) {
            final EngramLayout engramLayout = store.cognitiveLayout();
            final byte[] dummyVec = new byte[engramLayout.quantizedVecBytes()];

            // Record A: Synaptic tags set to non-zero (e.g. 0xSIM_TAG) but source is EXPERIENCED
            long simTagBits = 0x8000000000000000L;
            EncodingHeader experiencedWithSimTag = new EncodingHeader(
                    now, simTagBits, 1.0f, 5.0f, 0, (short) 0, (byte) 0,
                    (byte) 0, (byte) 0, 1.0f, (byte) 0, (byte) 0, (byte) 0, (short) 0, 0.0f, (byte) 0,
                    EngramSource.EXPERIENCED);
            store.append(experiencedWithSimTag, dummyVec);

            // Record B: Synaptic tags are 0 (clean), but source is SIMULATED
            EncodingHeader simulatedWithCleanTags = new EncodingHeader(
                    now, 0L, 1.0f, 5.0f, 0, (short) 0, (byte) 0,
                    (byte) 0, (byte) 0, 1.0f, (byte) 0, (byte) 0, (byte) 0, (short) 0, 0.0f, (byte) 0,
                    EngramSource.SIMULATED);
            store.append(simulatedWithCleanTags, dummyVec);

            final float[] queryVector = new float[dims];
            RecallOptions defaultOptions = RecallOptions.builder().topK(10).build();

            List<CognitiveScorer.ScoredRecord> defaultResults = CognitiveScorer.score(
                    store.primarySegment(), 2, engramLayout, queryVector, defaultOptions, now, 0L,
                    null, null, null, null, null, null);

            // Record A (EXPERIENCED) must be returned despite any synaptic tag values
            assertThat(defaultResults).hasSize(1);
            assertThat(engramLayout.readSource(store.primarySegment(), defaultResults.get(0).offset()))
                    .isEqualTo(EngramSource.EXPERIENCED);
            assertThat(engramLayout.readSynapticTags(store.primarySegment(), defaultResults.get(0).offset()))
                    .isEqualTo(simTagBits);
        }
    }

    @Test
    @DisplayName("R6.3 Codebase Scan: No production code path derives provenance source from ID prefixes or tags")
    void testNoCodePathDerivesSourceFromIdPrefixOrTags() throws IOException {
        Path srcMain = Path.of("src/main/java/com/spectrayan/spector/memory");
        if (!Files.exists(srcMain)) {
            srcMain = Path.of("memory/spector-memory/src/main/java/com/spectrayan/spector/memory");
        }
        assertThat(srcMain).as("Source directory must exist").exists();

        try (Stream<Path> stream = Files.walk(srcMain)) {
            List<Path> javaFiles = stream.filter(p -> p.toString().endsWith(".java")).toList();

            for (Path javaFile : javaFiles) {
                List<String> lines = Files.readAllLines(javaFile);
                for (int lineNum = 0; lineNum < lines.size(); lineNum++) {
                    String line = lines.get(lineNum);

                    // Check for anti-pattern: inspecting ID prefix to determine simulation or source
                    if (line.contains("startsWith(\"rem-log-\")") || line.contains("startsWith(\"sim-\")")) {
                        assertThat(line)
                                .as("File %s:%d must not infer source or simulation status from ID prefix: %s",
                                        javaFile.getFileName(), lineNum + 1, line)
                                .doesNotContain("Source", "simulated", "SIMULATED");
                    }
                }
            }
        }
    }
}
