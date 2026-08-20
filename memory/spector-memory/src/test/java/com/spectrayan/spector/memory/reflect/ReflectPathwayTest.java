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
package com.spectrayan.spector.memory.reflect;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.ReflectPathway;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.hippocampus.CircadianPolicy;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReflectPathway: Biological Sleep Consolidation Tests")
class ReflectPathwayTest {

    private static final int DIMS = 16;
    private SpectorMemory memory;
    private TestEmbeddingProvider embeddingProvider;
    private MockLlmProvider llmProvider;

    @BeforeEach
    void setUp() {
        embeddingProvider = new TestEmbeddingProvider(DIMS);
        llmProvider = new MockLlmProvider();

        memory = DefaultSpectorMemory.builder()
                .dimensions(DIMS)
                .embeddingProvider(embeddingProvider)
                .LlmProvider(llmProvider)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .usePathwayEngine(true)
                .circadianPolicy(CircadianPolicy.builder().timeTrigger(Duration.ofMinutes(30)).build())
                .build();
    }

    @AfterEach
    void tearDown() {
        if (memory != null) {
            memory.close();
        }
    }

    @Test
    @DisplayName("reflect() executes end-to-end and returns a structured ReflectReport")
    void testReflectExecutesAndReturnsReport() {
        memory.remember("ep-1", "First turn in conversation regarding architecture", MemoryType.EPISODIC, MemorySource.OBSERVED);
        memory.remember("ep-2", "Second turn in conversation regarding architecture", MemoryType.EPISODIC, MemorySource.OBSERVED);

        ReflectReport report = memory.reflect();

        assertThat(report).isNotNull();
        assertThat(report.duration()).isNotNull();
        assertThat(report.consolidatedCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("ReflectPathway builder constructs functional pathway")
    void testReflectPathwayBuilder() {
        ScalarQuantizer quantizer = Mockito.mock(ScalarQuantizer.class);
        ReflectPathway pathway = ReflectPathway.builder()
                .quantizer(quantizer)
                .embeddingProvider(embeddingProvider)
                .textGenerator(llmProvider)
                .soulDriftRefusionEnabled(true)
                .soulDriftRefusionBatchSize(50)
                .build();

        assertThat(pathway).isNotNull();
        pathway.close();
    }

    static class TestEmbeddingProvider implements EmbeddingProvider {
        private final int dims;

        TestEmbeddingProvider(int dims) { this.dims = dims; }

        @Override
        public EmbeddingResult embed(String text) {
            Random rng = new Random(text.hashCode());
            float[] vec = new float[dims];
            for (int i = 0; i < dims; i++) {
                vec[i] = (rng.nextFloat() - 0.5f) * 2.0f;
            }
            float norm = 0f;
            for (float v : vec) norm += v * v;
            norm = (float) Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < dims; i++) vec[i] /= norm;
            }
            return new EmbeddingResult(vec, text.split("\\s+").length, "test");
        }

        @Override public int dimensions() { return dims; }
        @Override public String modelName() { return "test"; }
    }

    static class MockLlmProvider implements LlmProvider {
        @Override
        public LlmResponse generate(LlmRequest request, GenerationOptions options) {
            return new LlmResponse("User is interested in modular cognitive pathways.", 10, 10, "mock-llm");
        }

        @Override public boolean isAvailable() { return true; }
        @Override public String modelName() { return "mock-llm"; }
    }
}
