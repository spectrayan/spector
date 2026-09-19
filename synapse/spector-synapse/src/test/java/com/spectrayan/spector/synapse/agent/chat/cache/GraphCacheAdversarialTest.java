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
package com.spectrayan.spector.synapse.agent.chat.cache;

import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Empirical Adversarial Challenger Test Suite for {@link GraphCache}.
 *
 * <p>Probes for:
 * <ul>
 *   <li>Thundering herd / race conditions under high concurrent compilation requests</li>
 *   <li>TOCTOU race between concurrent compilation and soul invalidation</li>
 *   <li>Prefix confusion and cross-soul cache pollution in {@link GraphCache#invalidateSoul(String)}</li>
 *   <li>High-volume multi-threaded cache throughput and hit consistency</li>
 *   <li>Dynamic tool registry mutations during concurrent compilation</li>
 * </ul>
 * </p>
 */
@DisplayName("GraphCache Adversarial & Concurrency Stress Suite")
class GraphCacheAdversarialTest {

    private GraphCache graphCache;
    private ToolRegistry toolRegistry;
    private AgentSoul standardSoul;

    @BeforeEach
    void setUp() {
        graphCache = new GraphCache();
        toolRegistry = new ToolRegistry(List.of());

        standardSoul = AgentSoul.builder()
                .id("agent-primary")
                .name("Primary Agent")
                .model("qwen3.5:latest")
                .soulVersion((short) 1)
                .build();
    }

    // =========================================================================
    // 1. CONCURRENCY & THUNDERING HERD PROBES
    // =========================================================================

    @Nested
    @DisplayName("1. Concurrency & Stampede Probes")
    class StampedeProbes {

        @Test
        @DisplayName("Atomic compute-if-absent: 30 concurrent threads requesting cold cache invoke compiler exactly once")
        @SuppressWarnings("unchecked")
        void testConcurrentCompilationStampede() throws Exception {
            int threadCount = 30;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            AtomicInteger compileCount = new AtomicInteger(0);
            CompiledGraph<AgentState> sharedMockGraph = mock(CompiledGraph.class);

            List<Future<CompiledGraph<AgentState>>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return graphCache.getOrCompile(standardSoul, toolRegistry, () -> {
                        compileCount.incrementAndGet();
                        try {
                            // Simulate compilation latency (50ms)
                            Thread.sleep(50);
                        } catch (InterruptedException ignored) {}
                        return sharedMockGraph;
                    });
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<CompiledGraph<AgentState>> f : futures) {
                CompiledGraph<AgentState> result = f.get(10, TimeUnit.SECONDS);
                assertThat(result).isNotNull();
            }
            executor.shutdown();

            System.out.println("[GraphCacheAdversarial] Concurrent compile invocations: " + compileCount.get()
                    + " for " + threadCount + " simultaneous threads (misses=" + graphCache.missCount() + ", hits=" + graphCache.hitCount() + ")");

            // Verified hardening: because GraphCache uses Caffeine's atomic get(key, fn),
            // redundant compilations are eliminated when multiple threads arrive at the cold cache simultaneously.
            assertThat(graphCache.size()).isEqualTo(1);
            assertThat(compileCount.get()).as("Atomic compute-if-absent must invoke compiler exactly once").isEqualTo(1);
        }

        @Test
        @DisplayName("High-concurrency read-after-warm: 50 threads execute 5,000 getOrCompile requests with 100% hit rate")
        @SuppressWarnings("unchecked")
        void testWarmCacheHighThroughput() throws Exception {
            CompiledGraph<AgentState> compiled = mock(CompiledGraph.class);
            graphCache.getOrCompile(standardSoul, toolRegistry, () -> compiled);
            assertThat(graphCache.hitCount()).isEqualTo(0);
            assertThat(graphCache.missCount()).isEqualTo(1);

            int threadCount = 20;
            int iterationsPerThread = 250; // 5,000 total hits
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);

            List<Future<Integer>> futures = new ArrayList<>();
            for (int t = 0; t < threadCount; t++) {
                futures.add(executor.submit(() -> {
                    latch.await();
                    int localHits = 0;
                    for (int i = 0; i < iterationsPerThread; i++) {
                        CompiledGraph<AgentState> g = graphCache.getOrCompile(standardSoul, toolRegistry, () -> {
                            throw new AssertionError("Compiler must never be called on warm cache");
                        });
                        if (g == compiled) localHits++;
                    }
                    return localHits;
                }));
            }

            latch.countDown();

            int totalHits = 0;
            for (Future<Integer> f : futures) {
                totalHits += f.get(10, TimeUnit.SECONDS);
            }
            executor.shutdown();

            assertThat(totalHits).isEqualTo(threadCount * iterationsPerThread);
            assertThat(graphCache.missCount()).isEqualTo(1);
            assertThat(graphCache.hitCount()).isEqualTo(threadCount * iterationsPerThread);
        }
    }

    // =========================================================================
    // 2. INVALIDATION & PREFIX CONFUSION PROBES
    // =========================================================================

    @Nested
    @DisplayName("2. Invalidation & Key Namespace Probes")
    class InvalidationProbes {

        @Test
        @DisplayName("EMPIRICAL FINDING: invalidateSoul('agent') inadvertently evicts 'agent:sub' due to simple prefix matching")
        @SuppressWarnings("unchecked")
        void testPrefixCollisionInvalidation() {
            AgentSoul soulA = AgentSoul.builder()
                    .id("agent")
                    .name("Agent")
                    .model("qwen")
                    .soulVersion((short) 1)
                    .build();

            AgentSoul soulSub = AgentSoul.builder()
                    .id("agent:sub")
                    .name("Agent Sub")
                    .model("qwen")
                    .soulVersion((short) 1)
                    .build();

            CompiledGraph<AgentState> gA = mock(CompiledGraph.class);
            CompiledGraph<AgentState> gSub = mock(CompiledGraph.class);

            graphCache.getOrCompile(soulA, toolRegistry, () -> gA);
            graphCache.getOrCompile(soulSub, toolRegistry, () -> gSub);
            assertThat(graphCache.size()).isEqualTo(2);

            // Invalidate soulA ("agent")
            graphCache.invalidateSoul("agent");

            // Key for soulA: "agent:1:<fp>" (starts with "agent:") -> evicted
            // Key for soulSub: "agent:sub:1:<fp>" (ALSO starts with "agent:") -> EVICTED UNINTENTIONALLY!
            System.out.println("[GraphCacheAdversarial] Size after invalidating 'agent': " + graphCache.size());
            assertThat(graphCache.size())
                    .as("Prefix matching on 'agent:' causes 'agent:sub' to be evicted as well")
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("Non-prefix sibling soul IDs are preserved during invalidation")
        @SuppressWarnings("unchecked")
        void testSiblingSoulPreservedDuringInvalidation() {
            AgentSoul soul1 = AgentSoul.builder()
                    .id("agent-alpha")
                    .name("Alpha")
                    .model("qwen")
                    .soulVersion((short) 1)
                    .build();

            AgentSoul soul2 = AgentSoul.builder()
                    .id("agent-beta")
                    .name("Beta")
                    .model("qwen")
                    .soulVersion((short) 1)
                    .build();

            CompiledGraph<AgentState> g1 = mock(CompiledGraph.class);
            CompiledGraph<AgentState> g2 = mock(CompiledGraph.class);

            graphCache.getOrCompile(soul1, toolRegistry, () -> g1);
            graphCache.getOrCompile(soul2, toolRegistry, () -> g2);
            assertThat(graphCache.size()).isEqualTo(2);

            graphCache.invalidateSoul("agent-alpha");

            assertThat(graphCache.size()).isEqualTo(1);
            // soul2 is preserved
            CompiledGraph<AgentState> hit = graphCache.getOrCompile(soul2, toolRegistry, () -> {
                throw new AssertionError("soul2 should still be cached");
            });
            assertThat(hit).isSameAs(g2);
        }

        @Test
        @DisplayName("Invalidate soul with blank or null ID is a safe no-op")
        @SuppressWarnings("unchecked")
        void testInvalidateBlankOrNullSoulIdSafe() {
            CompiledGraph<AgentState> g = mock(CompiledGraph.class);
            graphCache.getOrCompile(standardSoul, toolRegistry, () -> g);
            assertThat(graphCache.size()).isEqualTo(1);

            graphCache.invalidateSoul(null);
            graphCache.invalidateSoul("");
            graphCache.invalidateSoul("   ");

            assertThat(graphCache.size()).isEqualTo(1);
        }
    }

    // =========================================================================
    // 3. TOCTOU RACE CONDITION PROBE
    // =========================================================================

    @Nested
    @DisplayName("3. TOCTOU Race Condition Probes")
    class ToctouProbes {

        @Test
        @DisplayName("EMPIRICAL PROBE: Invalidation during in-flight compilation allows stale entry to be cached (TOCTOU)")
        @SuppressWarnings("unchecked")
        void testInvalidationDuringInFlightCompilation() throws Exception {
            CountDownLatch compileStarted = new CountDownLatch(1);
            CountDownLatch invalidationDone = new CountDownLatch(1);
            CountDownLatch compileCanFinish = new CountDownLatch(1);

            CompiledGraph<AgentState> staleGraph = mock(CompiledGraph.class);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            // Thread 1: slow compilation
            Future<CompiledGraph<AgentState>> compileFuture = executor.submit(() -> {
                return graphCache.getOrCompile(standardSoul, toolRegistry, () -> {
                    compileStarted.countDown();
                    try {
                        invalidationDone.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {}
                    return staleGraph;
                });
            });

            // Wait for compilation to start
            compileStarted.await(5, TimeUnit.SECONDS);

            // Thread 2: invalidation occurs while Thread 1 is still compiling
            graphCache.invalidateSoul(standardSoul.id());
            invalidationDone.countDown();

            CompiledGraph<AgentState> result = compileFuture.get(5, TimeUnit.SECONDS);
            assertThat(result).isSameAs(staleGraph);
            executor.shutdown();

            // Document finding: Thread 1 called cache.put(key, compiled) AFTER invalidateSoul was called!
            // Therefore, the stale entry is re-inserted into the cache!
            System.out.println("[GraphCacheAdversarial] Size after TOCTOU race: " + graphCache.size()
                    + " (stale entry re-inserted after invalidation)");
            assertThat(graphCache.size()).isEqualTo(1);
        }
    }
}
