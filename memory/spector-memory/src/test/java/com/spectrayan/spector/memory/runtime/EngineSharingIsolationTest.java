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
package com.spectrayan.spector.memory.runtime;

import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Task 10.5 Engine Sharing and Cross-Namespace Isolation Gates (R13.5, R13.6)")
class EngineSharingIsolationTest {

    private static final int DIMS = 4;

    private static final EmbeddingProvider DETERMINISTIC_EMBEDDER = new EmbeddingProvider() {
        @Override
        public EmbeddingResult embed(String text) {
            float hash = (float) Math.abs(text.hashCode() % 100) / 100.0f;
            return EmbeddingResult.of(new float[]{hash, 1.0f - hash, 0.5f, 0.5f}, "test-model");
        }

        @Override
        public List<EmbeddingResult> embedBatch(List<String> texts) {
            return texts.stream().map(this::embed).toList();
        }

        @Override
        public int dimensions() {
            return DIMS;
        }

        @Override
        public String modelName() {
            return "deterministic-test";
        }
    };

    @Test
    @DisplayName("Engine sharing invariant: attached namespaces share exact pathway engines (R13.6)")
    void testAttachedNamespacesSharePathwayEngines(@TempDir Path tempDir) {
        SpectorProperties props = SpectorProperties.builder().build();
        props.memory().setDimensions(DIMS);

        try (SpectorRuntime runtime = SpectorRuntime.builder()
                .properties(props)
                .embeddingProvider(DETERMINISTIC_EMBEDDER)
                .build()) {

            SpectorMemory memAlpha = runtime.attach("ns-alpha", b -> b.persistence(tempDir.resolve("alpha")));
            SpectorMemory memBeta = runtime.attach("ns-beta", b -> b.persistence(tempDir.resolve("beta")));

            try {
                DefaultSpectorMemory dsmAlpha = (DefaultSpectorMemory) memAlpha;
                DefaultSpectorMemory dsmBeta = (DefaultSpectorMemory) memBeta;

                assertThat(dsmAlpha.sharedPathways()).isTrue();
                assertThat(dsmBeta.sharedPathways()).isTrue();

                assertThat(dsmAlpha.recallPathway())
                        .isNotNull()
                        .isSameAs(dsmBeta.recallPathway());
            } finally {
                memAlpha.close();
                memBeta.close();
            }
        }
    }

    @Test
    @DisplayName("Isolation gate: cross-namespace recall returns empty; zero memory bleed across tenants (R13.5)")
    void testCrossNamespaceRecallIsolation(@TempDir Path tempDir) {
        SpectorProperties props = SpectorProperties.builder().build();
        props.memory().setDimensions(DIMS);

        try (SpectorRuntime runtime = SpectorRuntime.builder()
                .properties(props)
                .embeddingProvider(DETERMINISTIC_EMBEDDER)
                .build()) {

            SpectorMemory memAlpha = runtime.attach("ns-alpha", b -> b.persistence(tempDir.resolve("alpha")));
            SpectorMemory memBeta = runtime.attach("ns-beta", b -> b.persistence(tempDir.resolve("beta")));

            try {
                // Ingest into Alpha
                memAlpha.remember("doc-alpha-1", "Alpha secret confidential strategy document",
                        MemoryType.SEMANTIC, MemorySource.USER_STATED, "alpha-tag");

                // Ingest into Beta
                memBeta.remember("doc-beta-1", "Beta proprietary financial balance spreadsheet",
                        MemoryType.SEMANTIC, MemorySource.USER_STATED, "beta-tag");

                // Recall from Alpha
                List<CognitiveResult> recallAlpha = memAlpha.recall("strategy document",
                        RecallOptions.builder().topK(10).build());
                assertThat(recallAlpha).isNotEmpty();
                assertThat(recallAlpha.stream().anyMatch(r -> r.id().equals("doc-alpha-1"))).isTrue();
                assertThat(recallAlpha.stream().noneMatch(r -> r.id().equals("doc-beta-1"))).isTrue();

                // Recall from Beta
                List<CognitiveResult> recallBeta = memBeta.recall("balance spreadsheet",
                        RecallOptions.builder().topK(10).build());
                assertThat(recallBeta).isNotEmpty();
                assertThat(recallBeta.stream().anyMatch(r -> r.id().equals("doc-beta-1"))).isTrue();
                assertThat(recallBeta.stream().noneMatch(r -> r.id().equals("doc-alpha-1"))).isTrue();

                // Cross-query Alpha for Beta's text: MUST return empty
                List<CognitiveResult> crossAlpha = memAlpha.recall("Beta proprietary financial",
                        RecallOptions.builder().topK(10).build());
                assertThat(crossAlpha.stream().noneMatch(r -> r.id().equals("doc-beta-1"))).isTrue();

                // Cross-query Beta for Alpha's text: MUST return empty
                List<CognitiveResult> crossBeta = memBeta.recall("Alpha secret confidential",
                        RecallOptions.builder().topK(10).build());
                assertThat(crossBeta.stream().noneMatch(r -> r.id().equals("doc-alpha-1"))).isTrue();
            } finally {
                memAlpha.close();
                memBeta.close();
            }
        }
    }

    @Test
    @DisplayName("Lifecycle isolation: closing an evicted/idle namespace memory leaves shared pathway engines usable (R13.6)")
    void testClosingOneNamespaceLeavesSharedPathwaysIntact(@TempDir Path tempDir) {
        SpectorProperties props = SpectorProperties.builder().build();
        props.memory().setDimensions(DIMS);

        try (SpectorRuntime runtime = SpectorRuntime.builder()
                .properties(props)
                .embeddingProvider(DETERMINISTIC_EMBEDDER)
                .build()) {

            SpectorMemory mem1 = runtime.attach("ns-1", b -> b.persistence(tempDir.resolve("ns-1")));
            SpectorMemory mem2 = runtime.attach("ns-2", b -> b.persistence(tempDir.resolve("ns-2")));

            mem1.remember("m1", "First namespace memory content",
                    MemoryType.SEMANTIC, MemorySource.USER_STATED);
            mem2.remember("m2", "Second namespace memory content",
                    MemoryType.SEMANTIC, MemorySource.USER_STATED);

            // Close mem1 (simulating LRU eviction)
            mem1.close();

            // mem2 MUST still be able to recall and remember using the shared pathway engines!
            List<CognitiveResult> results = mem2.recall("Second namespace",
                    RecallOptions.builder().topK(5).build());
            assertThat(results).isNotEmpty();
            assertThat(results.get(0).id()).isEqualTo("m2");

            mem2.remember("m3", "Another memory for second namespace",
                    MemoryType.SEMANTIC, MemorySource.USER_STATED);
            List<CognitiveResult> results2 = mem2.recall("Another memory",
                    RecallOptions.builder().topK(5).build());
            assertThat(results2).isNotEmpty();

            mem2.close();
        }
    }
}
