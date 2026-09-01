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
package com.spectrayan.spector.memory.stress;

import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.temporal.TemporalFact;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VirtualThreadMemoryStressTest")
class VirtualThreadMemoryStressTest {

    private static final int DIMENSIONS = 32;
    private DefaultSpectorMemory memory;
    private StressEmbeddingProvider embeddingProvider;
    private StressLlmProvider llmProvider;

    @BeforeEach
    void setUp() {
        embeddingProvider = new StressEmbeddingProvider(DIMENSIONS);
        llmProvider = new StressLlmProvider();

        memory = (DefaultSpectorMemory) DefaultSpectorMemory.builder()
                .dimensions(DIMENSIONS)
                .embeddingProvider(embeddingProvider)
                .LlmProvider(llmProvider)
                .entityExtractionMode(com.spectrayan.spector.memory.graph.EntityExtractionMode.LLM)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .workingCapacity(100)
                .episodicPartitionCapacity(500)
                .semanticCapacity(1000)
                .proceduralCapacity(500)
                .eagerConsolidationQueueCapacity(1000)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (memory != null) {
            memory.close();
        }
    }

    @Test
    @DisplayName("500 virtual threads mixed workload: remembers, recalls, CADP, and reminders")
    void testConcurrentMixedWorkload_500VirtualThreads() throws Exception {
        int threadCount = 500;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger rememberSuccess = new AtomicInteger(0);
        AtomicInteger recallSuccess = new AtomicInteger(0);
        AtomicInteger reminderSuccess = new AtomicInteger(0);
        List<Throwable> errors = new ArrayList<>();

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threadCount; i++) {
                final int id = i;
                virtualExecutor.submit(() -> {
                    try {
                        String sessionId = "sess-" + (id % 20);
                        String namespaceId = "tenant-" + (id % 5);
                        MemoryScope.runWithScope(sessionId, namespaceId, () -> {
                            int op = id % 5;
                            if (op == 0 || op == 1) { // 40% remember
                                String memId = "stress-mem-" + id;
                                memory.remember(memId, "Knowledge document about topic " + id,
                                        MemoryType.SEMANTIC, MemorySource.OBSERVED, "stress", "topic-" + (id % 10));
                                rememberSuccess.incrementAndGet();
                            } else if (op == 2 || op == 3) { // 40% recall
                                List<CognitiveResult> results = memory.recall("query topic " + (id % 10),
                                        RecallOptions.builder().topK(5).build());
                                assertThat(results).isNotNull();
                                recallSuccess.incrementAndGet();
                            } else { // 20% prospective reminder
                                memory.scheduleReminder("Reminder for task " + id,
                                        Instant.now().plusSeconds(600), "remind");
                                reminderSuccess.incrementAndGet();
                            }
                        });
                    } catch (Throwable t) {
                        synchronized (errors) {
                            errors.add(t);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        boolean finished = latch.await(15, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
        assertThat(errors).isEmpty();
        assertThat(rememberSuccess.get()).isEqualTo(200);
        assertThat(recallSuccess.get()).isEqualTo(200);
        assertThat(reminderSuccess.get()).isEqualTo(100);
    }

    @Test
    @DisplayName("CADP contradiction cascade across 100 concurrent virtual threads for the same entity")
    void testConcurrentCadpContradictionStorm() throws Exception {
        int aliceId = memory.entityDirectory().intern("Alice", "PERSON");
        int factId = memory.assertFact("Alice", "lives_in", "London", 1000L, Long.MAX_VALUE, 0.9f);
        assertThat(factId).isGreaterThan(0);

        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        float[] sharedVector = new float[DIMENSIONS];
        sharedVector[0] = 1.0f;

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 1; i <= threadCount; i++) {
                final int step = i;
                final String text = "Alice lives in City-" + step + ".";
                embeddingProvider.register(text, sharedVector);

                virtualExecutor.submit(() -> {
                    try {
                        memory.remember("city-mem-" + step, text,
                                MemoryType.SEMANTIC, MemorySource.OBSERVED, "city");
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        boolean finished = latch.await(15, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        // Trigger consolidation to ensure CADP resolution completes
        memory.consolidate();

        // Verify that only the newest winning facts remain active, and obsolete ones are retracted
        long deadline = System.currentTimeMillis() + 5000;
        boolean retracted = false;
        while (System.currentTimeMillis() < deadline) {
            List<TemporalFact> active = memory.temporalKnowledgeGraph()
                    .factsAbout(aliceId)
                    .excludeRetracted()
                    .resolve();
            if (active.stream().noneMatch(f -> f.factId() == factId)) {
                retracted = true;
                break;
            }
            Thread.sleep(50);
        }
        assertThat(retracted).as("Initial fact should be retracted by CADP").isTrue();
    }

    @Test
    @DisplayName("Partition roll under concurrent 100 writers and 100 readers")
    void testConcurrentPartitionRollUnderReadWriteStress() throws Exception {
        DefaultSpectorMemory rollingMemory = (DefaultSpectorMemory) DefaultSpectorMemory.builder()
                .dimensions(DIMENSIONS)
                .embeddingProvider(embeddingProvider)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .workingCapacity(5) // Tiny working capacity to force frequent working -> episodic rolls
                .episodicPartitionCapacity(500)
                .semanticCapacity(500)
                .proceduralCapacity(500)
                .build();

        try {
            int writers = 100;
            int readers = 100;
            CountDownLatch latch = new CountDownLatch(writers + readers);
            AtomicInteger writesCompleted = new AtomicInteger(0);
            AtomicInteger readsCompleted = new AtomicInteger(0);
            List<Throwable> errors = new ArrayList<>();

            try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
                // Launch writers
                for (int w = 0; w < writers; w++) {
                    final int id = w;
                    virtualExecutor.submit(() -> {
                        try {
                            rollingMemory.remember("roll-mem-" + id, "Working memory overflow item " + id,
                                    MemoryType.WORKING, MemorySource.OBSERVED, "roll");
                            writesCompleted.incrementAndGet();
                        } catch (Throwable t) {
                            synchronized (errors) {
                                errors.add(t);
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                // Launch readers
                for (int r = 0; r < readers; r++) {
                    final int id = r;
                    virtualExecutor.submit(() -> {
                        try {
                            List<CognitiveResult> results = rollingMemory.recall("overflow item " + id,
                                    RecallOptions.builder().topK(5).build());
                            assertThat(results).isNotNull();
                            readsCompleted.incrementAndGet();
                        } catch (Throwable t) {
                            synchronized (errors) {
                                errors.add(t);
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
            }

            boolean done = latch.await(15, TimeUnit.SECONDS);
            assertThat(done).isTrue();
            assertThat(errors).isEmpty();
            assertThat(writesCompleted.get()).isEqualTo(writers);
            assertThat(readsCompleted.get()).isEqualTo(readers);
        } finally {
            rollingMemory.close();
        }
    }

    static class StressEmbeddingProvider implements EmbeddingProvider {
        private final int dims;
        private final Map<String, float[]> presetVectors = new ConcurrentHashMap<>();

        StressEmbeddingProvider(int dims) {
            this.dims = dims;
        }

        void register(String text, float[] vector) {
            presetVectors.put(text, vector);
        }

        @Override
        public EmbeddingResult embed(String text) {
            float[] vec = presetVectors.get(text);
            if (vec == null) {
                Random rng = new Random(text.hashCode());
                vec = new float[dims];
                for (int i = 0; i < dims; i++) {
                    vec[i] = (rng.nextFloat() - 0.5f) * 2.0f;
                }
                float norm = 0f;
                for (float v : vec) norm += v * v;
                norm = (float) Math.sqrt(norm);
                if (norm > 0) {
                    for (int i = 0; i < dims; i++) vec[i] /= norm;
                }
            }
            return new EmbeddingResult(vec, text.split("\\s+").length, "stress");
        }

        @Override public int dimensions() { return dims; }
        @Override public String modelName() { return "stress"; }
    }

    static class StressLlmProvider implements LlmProvider {
        @Override
        public LlmResponse generate(LlmRequest request, GenerationOptions options) {
            String prompt = request.messages().isEmpty() ? "" : request.messages().get(0).text();
            if (prompt.contains("Extract all named entities") || prompt.contains("ENTITY:")) {
                return new LlmResponse("ENTITY: Alice | PERSON", 5, 5, "stress-llm");
            }
            if (prompt.contains("Analyze these two statements") || prompt.contains("Contradicts:")) {
                return new LlmResponse("YES", 5, 5, "stress-llm");
            }
            return new LlmResponse("NO", 5, 5, "stress-llm");
        }

        @Override public boolean isAvailable() { return true; }
        @Override public String modelName() { return "stress-llm"; }
    }
}
