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
package com.spectrayan.spector.memory.namespace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.spectrayan.spector.commons.error.SpectorMemoryException;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.test.FakeEmbeddingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@DisplayName("NamespaceRegistry Tests")
class NamespaceRegistryTest {

    private FakeEmbeddingProvider embedProvider;
    private NamespaceRegistry registry;

    @BeforeEach
    void setUp() {
        embedProvider = new FakeEmbeddingProvider();
    }

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.close();
        }
    }

    private SpectorMemory createTestMemory(String namespaceId) {
        var memProps = new com.spectrayan.spector.config.properties.MemoryProperties()
                .setDimensions(embedProvider.dimensions())
                .setNamespaceId(namespaceId);
        return DefaultSpectorMemory.builder(memProps)
                .embeddingProvider(embedProvider)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .managedByRegistry(true)
                .build();
    }

    @Test
    @DisplayName("Verify LRU Eviction behavior")
    void testLruEviction() {
        registry = new NamespaceRegistry(2);

        SpectorMemory ns1 = registry.getOrOpen("ns1", () -> createTestMemory("ns1"));
        SpectorMemory ns2 = registry.getOrOpen("ns2", () -> createTestMemory("ns2"));

        assertThat(registry.activeCount()).isEqualTo(2);
        assertThat(registry.evictedCount()).isEqualTo(0);

        // Touch ns1 to make it most recently used
        registry.getOrOpen("ns1", () -> createTestMemory("ns1"));

        // Open ns3, which should trigger eviction of ns2 (since ns1 was touched)
        registry.getOrOpen("ns3", () -> createTestMemory("ns3"));

        assertThat(registry.activeCount()).isEqualTo(2);
        assertThat(registry.evictedCount()).isEqualTo(1);

        // Check that ns2 is no longer tracked
        SpectorMemory cachedNs2 = registry.getOrOpen("ns2", () -> {
            // Should call opener to reopen
            return createTestMemory("ns2");
        });

        // Now ns1 (oldest MRU) should be evicted
        assertThat(registry.evictedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Verify active lease prevents eviction")
    void testLeasePreventsEviction() {
        registry = new NamespaceRegistry(2);

        SpectorMemory ns1 = registry.getOrOpen("ns1", () -> createTestMemory("ns1"));
        SpectorMemory ns2 = registry.getOrOpen("ns2", () -> createTestMemory("ns2"));

        // Acquire lease on ns1
        ((DefaultSpectorMemory) ns1).acquireLease();

        // Open ns3. Normally ns1 (eldest) is evicted, but since it has a lease, ns2 should be evicted instead
        registry.getOrOpen("ns3", () -> createTestMemory("ns3"));

        assertThat(registry.activeCount()).isEqualTo(2);
        assertThat(registry.evictedCount()).isEqualTo(1);

        // Verify ns1 is still active
        assertThat(registry.getOrOpen("ns1", () -> {
            throw new AssertionError("Should not reopen ns1 as it must still be active");
        })).isSameAs(ns1);

        // Release lease
        ((DefaultSpectorMemory) ns1).releaseLease();
    }

    @Test
    @DisplayName("Verify warm reopen logic")
    void testWarmReopen() {
        registry = new NamespaceRegistry(1);
        AtomicInteger openCount = new AtomicInteger(0);

        SpectorMemory ns1 = registry.getOrOpen("ns1", () -> {
            openCount.incrementAndGet();
            return createTestMemory("ns1");
        });

        // Trigger eviction of ns1
        registry.getOrOpen("ns2", () -> createTestMemory("ns2"));
        assertThat(registry.evictedCount()).isEqualTo(1);

        // Touch ns1 again, should reload it
        SpectorMemory ns1Reloaded = registry.getOrOpen("ns1", () -> {
            openCount.incrementAndGet();
            return createTestMemory("ns1");
        });

        assertThat(openCount.get()).isEqualTo(2);
        assertThat(ns1Reloaded).isNotSameAs(ns1);
    }

    @Test
    @DisplayName("Verify FD diagnostics executes without failure")
    void testFdDiagnostics() {
        NamespaceRegistry.logFdDiagnostics(10);
        NamespaceRegistry.logFdDiagnostics(1000000); // Trigger warning if Unix MXBean is available
    }

    private SpectorMemory wrapWithControlledClose(SpectorMemory delegate, Runnable onClose) {
        return (SpectorMemory) Proxy.newProxyInstance(
                SpectorMemory.class.getClassLoader(),
                new Class<?>[]{SpectorMemory.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName()) && (args == null || args.length == 0)) {
                        try {
                            onClose.run();
                        } finally {
                            delegate.close();
                        }
                        return null;
                    }
                    return method.invoke(delegate, args);
                });
    }

    @Test
    @DisplayName("Eviction closes removed namespace outside registry lock so concurrent lookups do not block")
    void testEvictionClosesOutsideLockAllowingConcurrentLookups() throws Exception {
        registry = new NamespaceRegistry(2);
        CountDownLatch closeEntered = new CountDownLatch(1);
        CountDownLatch allowCloseFinish = new CountDownLatch(1);

        SpectorMemory rawNs1 = createTestMemory("ns1");
        SpectorMemory slowClosingNs1 = wrapWithControlledClose(rawNs1, () -> {
            closeEntered.countDown();
            try {
                allowCloseFinish.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        registry.getOrOpen("ns1", () -> slowClosingNs1);
        SpectorMemory ns2 = registry.getOrOpen("ns2", () -> createTestMemory("ns2"));

        CompletableFuture<SpectorMemory> openNs3 = CompletableFuture.supplyAsync(
                () -> registry.getOrOpen("ns3", () -> createTestMemory("ns3")));

        try {
            assertThat(closeEntered.await(5, TimeUnit.SECONDS))
                    .as("Eviction of ns1 must begin closing ns1")
                    .isTrue();

            // While ns1.close() is blocked, a lookup on active ns2 must not block on the registry lock
            SpectorMemory hitNs2 = assertTimeoutPreemptively(
                    Duration.ofMillis(500),
                    () -> registry.getOrOpen("ns2", () -> {
                        throw new AssertionError("ns2 must already be active");
                    }),
                    "Concurrent getOrOpen on active namespace must not be blocked by eviction close()");
            assertThat(hitNs2).isSameAs(ns2);
        } finally {
            allowCloseFinish.countDown();
        }

        SpectorMemory ns3 = openNs3.get(5, TimeUnit.SECONDS);
        assertThat(ns3).isNotNull();
        assertThat(registry.activeCount()).isEqualTo(2);
        assertThat(registry.evictedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cold open does not hold global registry lock across opener.get()")
    void testColdOpenDoesNotBlockActiveLookupsOrDistinctColdOpen() throws Exception {
        registry = new NamespaceRegistry(3);
        SpectorMemory ns1 = registry.getOrOpen("ns1", () -> createTestMemory("ns1"));

        CountDownLatch openNs2Entered = new CountDownLatch(1);
        CountDownLatch allowNs2Finish = new CountDownLatch(1);

        CompletableFuture<SpectorMemory> openNs2 = CompletableFuture.supplyAsync(
                () -> registry.getOrOpen("ns2", () -> {
                    openNs2Entered.countDown();
                    try {
                        allowNs2Finish.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return createTestMemory("ns2");
                }));

        try {
            assertThat(openNs2Entered.await(5, TimeUnit.SECONDS)).isTrue();

            SpectorMemory hitNs1 = assertTimeoutPreemptively(
                    Duration.ofMillis(500),
                    () -> registry.getOrOpen("ns1", () -> {
                        throw new AssertionError("ns1 must be cached");
                    }),
                    "Active lookup must not block while another namespace is opening");
            assertThat(hitNs1).isSameAs(ns1);

            SpectorMemory ns3 = assertTimeoutPreemptively(
                    Duration.ofMillis(1000),
                    () -> registry.getOrOpen("ns3", () -> createTestMemory("ns3")),
                    "Distinct cold namespace open must not block while another namespace is opening");
            assertThat(ns3).isNotNull();
        } finally {
            allowNs2Finish.countDown();
        }

        assertThat(openNs2.get(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(registry.activeCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Reopening an evicted namespace waits until its in-flight close completes")
    void testReopenSerializesAfterEvictionClose() throws Exception {
        registry = new NamespaceRegistry(1);
        CountDownLatch closeEntered = new CountDownLatch(1);
        CountDownLatch allowCloseFinish = new CountDownLatch(1);
        AtomicBoolean ns1CloseFinished = new AtomicBoolean(false);

        SpectorMemory rawNs1 = createTestMemory("ns1");
        SpectorMemory slowClosingNs1 = wrapWithControlledClose(rawNs1, () -> {
            closeEntered.countDown();
            try {
                allowCloseFinish.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ns1CloseFinished.set(true);
        });

        registry.getOrOpen("ns1", () -> slowClosingNs1);

        CompletableFuture<SpectorMemory> evictViaNs2 = CompletableFuture.supplyAsync(
                () -> registry.getOrOpen("ns2", () -> createTestMemory("ns2")));

        assertThat(closeEntered.await(5, TimeUnit.SECONDS)).isTrue();

        AtomicBoolean sawClosedBeforeReopen = new AtomicBoolean(false);
        CompletableFuture<SpectorMemory> reopenNs1 = CompletableFuture.supplyAsync(
                () -> registry.getOrOpen("ns1", () -> {
                    sawClosedBeforeReopen.set(ns1CloseFinished.get());
                    return createTestMemory("ns1");
                }));

        Thread.sleep(100);
        assertThat(reopenNs1.isDone())
                .as("Reopen of ns1 must wait while previous ns1 instance is still closing")
                .isFalse();

        allowCloseFinish.countDown();

        evictViaNs2.get(5, TimeUnit.SECONDS);
        reopenNs1.get(5, TimeUnit.SECONDS);
        assertThat(sawClosedBeforeReopen.get())
                .as("Reopen opener for ns1 must run only after evicted ns1 close() finished")
                .isTrue();
    }

    @Test
    @DisplayName("Strictly enforces maxActiveNamespaces when all active entries hold leases")
    void testStrictCapEnforcementWhenAllEntriesLeased() {
        registry = new NamespaceRegistry(2);

        DefaultSpectorMemory ns1 = (DefaultSpectorMemory) registry.getOrOpen("ns1", () -> createTestMemory("ns1"));
        DefaultSpectorMemory ns2 = (DefaultSpectorMemory) registry.getOrOpen("ns2", () -> createTestMemory("ns2"));

        ns1.acquireLease();
        ns2.acquireLease();
        try {
            AtomicBoolean openerCalled = new AtomicBoolean(false);
            assertThatThrownBy(() -> registry.getOrOpen("ns3", () -> {
                openerCalled.set(true);
                return createTestMemory("ns3");
            }))
                    .isInstanceOf(SpectorMemoryException.class)
                    .hasMessageContaining("SPE-310-032");

            assertThat(openerCalled.get())
                    .as("Opener must not be invoked when capacity cannot be freed")
                    .isFalse();
            assertThat(registry.activeCount())
                    .as("Active count must never exceed maxActiveNamespaces")
                    .isEqualTo(2);
            assertThat(registry.evictedCount()).isEqualTo(0);
        } finally {
            ns1.releaseLease();
            ns2.releaseLease();
        }

        // Once leases are released, opening ns3 succeeds and evicts the eldest entry
        SpectorMemory ns3 = registry.getOrOpen("ns3", () -> createTestMemory("ns3"));
        assertThat(ns3).isNotNull();
        assertThat(registry.activeCount()).isEqualTo(2);
        assertThat(registry.evictedCount()).isEqualTo(1);
    }
}
