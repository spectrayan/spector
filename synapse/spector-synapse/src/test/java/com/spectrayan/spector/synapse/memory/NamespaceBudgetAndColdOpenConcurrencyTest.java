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
package com.spectrayan.spector.synapse.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.store.InsulaMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.api.SalienceProfileProvider;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.runtime.SpectorRuntime;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.AccountFlags;
import com.spectrayan.spector.synapse.catalog.AccountProfile;
import com.spectrayan.spector.synapse.catalog.AccountQuotas;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.PrincipalKind;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.SynapseSalienceProvider;

@Timeout(value = 15, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@DisplayName("Issue #1178: Budget-Derived Namespace Cap and Concurrent Cold-Open Tests")
class NamespaceBudgetAndColdOpenConcurrencyTest {

    @TempDir
    Path tempDir;

    private AccountCatalog catalog;
    private SynapseProperties synapseProps;
    private EmbeddingProvider embedder;
    private SpectorRuntime runtime;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        catalog = mock(AccountCatalog.class);
        embedder = mock(EmbeddingProvider.class);
        when(embedder.modelName()).thenReturn("mock-embed");
        when(embedder.dimensions()).thenReturn(4);
        runtime = mock(SpectorRuntime.class);
        objectMapper = new ObjectMapper();

        synapseProps = new SynapseProperties();
        synapseProps.setDataDir(tempDir.resolve("data").toString());
        synapseProps.getMemory().setPersistencePath(tempDir.resolve("memory").toString());
        synapseProps.getMemory().setDimensions(4);
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> providerOf(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bean);
        when(provider.getIfAvailable(any())).thenAnswer(inv ->
                bean != null ? bean : ((java.util.function.Supplier<T>) inv.getArgument(0)).get());
        return provider;
    }

    private SpectorMemory newMockMemory() {
        SpectorMemory mem = mock(SpectorMemory.class);
        SpectorMemoryAdmin admin = mock(SpectorMemoryAdmin.class);
        InsulaMemory insula = mock(InsulaMemory.class);
        when(mem.admin()).thenReturn(admin);
        when(admin.insularCortex()).thenReturn(insula);
        when(insula.get()).thenReturn(Optional.empty());
        return mem;
    }

    private static int maxInstancesOf(NamespaceResolver resolver) throws Exception {
        Field field = NamespaceResolver.class.getDeclaredField("maxInstances");
        field.setAccessible(true);
        return field.getInt(resolver);
    }

    @Test
    @DisplayName("Unset maxInstances derives cap from memory budget and per-namespace footprint; explicit value overrides")
    void maxInstancesScalesWithMemoryBudgetAndFootprint() throws Exception {
        MemoryProperties smallProps = new MemoryProperties();
        smallProps.setDimensions(384);
        smallProps.setSemanticCapacity(5_000);
        smallProps.setEpisodicPartitionCapacity(500);
        smallProps.setProceduralCapacity(500);
        smallProps.setWorkingCapacity(100);
        smallProps.setEntityGraphCapacity(10_000);
        smallProps.setPersistencePath(tempDir.resolve("memory-small").toString());

        MemoryProperties largeProps = new MemoryProperties();
        largeProps.setDimensions(1536);
        largeProps.setSemanticCapacity(50_000);
        largeProps.setEpisodicPartitionCapacity(5_000);
        largeProps.setProceduralCapacity(5_000);
        largeProps.setWorkingCapacity(500);
        largeProps.setEntityGraphCapacity(100_000);
        largeProps.setPersistencePath(tempDir.resolve("memory-large").toString());

        SynapseProperties smallSynapse = new SynapseProperties();
        smallSynapse.setDataDir(tempDir.resolve("data-small").toString());
        smallSynapse.setMemory(smallProps);

        SynapseProperties largeSynapse = new SynapseProperties();
        largeSynapse.setDataDir(tempDir.resolve("data-large").toString());
        largeSynapse.setMemory(largeProps);

        int capSmall;
        try (NamespaceResolver smallResolver = new NamespaceResolver(
                catalog, smallSynapse, providerOf(embedder), providerOf(null), providerOf(null),
                providerOf(objectMapper), providerOf(null), providerOf(null), providerOf(null),
                providerOf(null), providerOf(null), providerOf(null), 0)) {
            capSmall = maxInstancesOf(smallResolver);
        }

        int capLarge;
        try (NamespaceResolver largeResolver = new NamespaceResolver(
                catalog, largeSynapse, providerOf(embedder), providerOf(null), providerOf(null),
                providerOf(objectMapper), providerOf(null), providerOf(null), providerOf(null),
                providerOf(null), providerOf(null), providerOf(null), 0)) {
            capLarge = maxInstancesOf(largeResolver);
        }

        assertThat(capSmall)
                .as("Unset maxInstances (0) must derive a positive cap > 1 from runtime memory budget")
                .isGreaterThan(1);
        assertThat(capSmall)
                .as("Smaller per-namespace footprint must allow more active namespaces than a 10x larger footprint")
                .isGreaterThan(capLarge);

        long smallFootprint = NamespaceResolver.estimateNamespaceFootprintBytes(smallProps);
        long largeFootprint = NamespaceResolver.estimateNamespaceFootprintBytes(largeProps);
        assertThat(largeFootprint).isGreaterThan(smallFootprint * 5);

        // Explicit budget-bytes scales derived cap deterministically
        SynapseProperties budget128M = new SynapseProperties();
        budget128M.setDataDir(tempDir.resolve("data-128m").toString());
        budget128M.setMemory(smallProps);
        budget128M.getAuth().getMemory().setBudgetBytes(128L * 1024 * 1024);

        SynapseProperties budget512M = new SynapseProperties();
        budget512M.setDataDir(tempDir.resolve("data-512m").toString());
        budget512M.setMemory(smallProps);
        budget512M.getAuth().getMemory().setBudgetBytes(512L * 1024 * 1024);

        try (NamespaceResolver r128 = new NamespaceResolver(
                catalog, budget128M, providerOf(embedder), providerOf(null), providerOf(null),
                providerOf(objectMapper), providerOf(null), providerOf(null), providerOf(null),
                providerOf(null), providerOf(null), providerOf(null), 0);
             NamespaceResolver r512 = new NamespaceResolver(
                catalog, budget512M, providerOf(embedder), providerOf(null), providerOf(null),
                providerOf(objectMapper), providerOf(null), providerOf(null), providerOf(null),
                providerOf(null), providerOf(null), providerOf(null), 0)) {
            assertThat(r512.maxInstances()).isGreaterThan(r128.maxInstances());
        }

        // Explicit positive maxInstances overrides derived value
        try (NamespaceResolver explicitResolver = new NamespaceResolver(
                catalog, smallSynapse, providerOf(embedder), providerOf(null), providerOf(null),
                providerOf(objectMapper), providerOf(null), providerOf(null), providerOf(null),
                providerOf(null), providerOf(null), providerOf(null), 77)) {
            assertThat(explicitResolver.maxInstances()).isEqualTo(77);
            assertThat(maxInstancesOf(explicitResolver)).isEqualTo(77);
        }
    }

    @Test
    @DisplayName("Concurrent cold opens for distinct namespaces execute buildInstance in parallel without global lock")
    void concurrentColdOpensForDistinctNamespacesRunInParallel() throws Exception {
        int distinctNamespaces = 4;
        CountDownLatch allEnteredAttach = new CountDownLatch(distinctNamespaces);
        AtomicInteger concurrentInAttach = new AtomicInteger(0);
        AtomicInteger maxConcurrentInAttach = new AtomicInteger(0);

        when(runtime.attach(anyString(), any())).thenAnswer(inv -> {
            int current = concurrentInAttach.incrementAndGet();
            maxConcurrentInAttach.accumulateAndGet(current, Math::max);
            allEnteredAttach.countDown();
            try {
                boolean allArrived = allEnteredAttach.await(2, TimeUnit.SECONDS);
                if (!allArrived) {
                    throw new IllegalStateException(
                            "Timed out waiting for parallel cold opens; coldPathLock serialized buildInstance");
                }
                return newMockMemory();
            } finally {
                concurrentInAttach.decrementAndGet();
            }
        });

        for (int i = 0; i < distinctNamespaces; i++) {
            String accountId = "acct-" + i;
            String nsId = "ns-parallel-" + i;
            Account acct = new Account(
                    accountId, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                    accountId, new AccountQuotas(10, 10, -1, -1),
                    new AccountFlags(true, true, true), nsId, Instant.now());
            when(catalog.getOrCreateAccount(accountId)).thenReturn(acct);
            when(catalog.getAccount(accountId)).thenReturn(acct);
        }

        try (NamespaceResolver resolver = new NamespaceResolver(
                catalog, synapseProps, providerOf(embedder), providerOf(null), providerOf(null),
                providerOf(objectMapper), providerOf(null), providerOf(null), providerOf(null),
                providerOf(null), providerOf(null), providerOf(null), 32)) {
            resolver.setRuntime(runtime);

            List<Future<SpectorMemory>> futures = new ArrayList<>();
            try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < distinctNamespaces; i++) {
                    final String accountId = "acct-" + i;
                    futures.add(exec.submit(() -> resolver.resolve(accountId)));
                }
                for (Future<SpectorMemory> f : futures) {
                    assertThat(f.get(5, TimeUnit.SECONDS)).isNotNull();
                }
            }

            assertThat(maxConcurrentInAttach.get()).isEqualTo(distinctNamespaces);
            assertThat(resolver.cachedInstanceCount()).isEqualTo(distinctNamespaces);
        }
    }

    @Test
    @DisplayName("Concurrent cold opens for the same namespaceId coalesce onto a single buildInstance call")
    void concurrentColdOpensForSameNamespaceCoalesceSingleFlight() throws Exception {
        String accountId = "acct-shared";
        String nsId = "ns-shared-singleflight";
        Account acct = new Account(
                accountId, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                accountId, new AccountQuotas(10, 10, -1, -1),
                new AccountFlags(true, true, true), nsId, Instant.now());
        when(catalog.getOrCreateAccount(accountId)).thenReturn(acct);
        when(catalog.getAccount(accountId)).thenReturn(acct);

        CountDownLatch attachStarted = new CountDownLatch(1);
        CountDownLatch releaseAttach = new CountDownLatch(1);
        AtomicInteger attachCallCount = new AtomicInteger(0);
        SpectorMemory singleInstance = newMockMemory();

        when(runtime.attach(eq(nsId), any())).thenAnswer(inv -> {
            attachCallCount.incrementAndGet();
            attachStarted.countDown();
            releaseAttach.await(5, TimeUnit.SECONDS);
            return singleInstance;
        });

        try (NamespaceResolver resolver = new NamespaceResolver(
                catalog, synapseProps, providerOf(embedder), providerOf(null), providerOf(null),
                providerOf(objectMapper), providerOf(null), providerOf(null), providerOf(null),
                providerOf(null), providerOf(null), providerOf(null), 32)) {
            resolver.setRuntime(runtime);

            int callers = 12;
            CountDownLatch callersReady = new CountDownLatch(callers);
            List<Future<SpectorMemory>> futures = new ArrayList<>();
            try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < callers; i++) {
                    futures.add(exec.submit(() -> {
                        callersReady.countDown();
                        return resolver.resolve(accountId);
                    }));
                }
                assertThat(callersReady.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(attachStarted.await(3, TimeUnit.SECONDS)).isTrue();
                Thread.sleep(50);
                releaseAttach.countDown();

                Set<SpectorMemory> results = ConcurrentHashMap.newKeySet();
                for (Future<SpectorMemory> f : futures) {
                    results.add(f.get(5, TimeUnit.SECONDS));
                }
                assertThat(results).containsExactly(singleInstance);
            }

            assertThat(attachCallCount.get()).isEqualTo(1);
            verify(runtime, times(1)).attach(eq(nsId), any());
            assertThat(resolver.cachedInstanceCount()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Process and account hot caps remain enforced under concurrent cold opens")
    void hotCapsEnforcedUnderConcurrentColdOpens() throws Exception {
        String accountId = "acct-capped";
        int accountMaxHot = 3;
        int processMaxHot = 4;
        int totalNamespaces = 12;

        Account acct = new Account(
                accountId, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                accountId, new AccountQuotas(20, accountMaxHot, -1, -1),
                new AccountFlags(true, true, true), "ns-cap-0", Instant.now());
        when(catalog.getOrCreateAccount(accountId)).thenReturn(acct);
        when(catalog.getAccount(accountId)).thenReturn(acct);

        for (int i = 0; i < totalNamespaces; i++) {
            String nsId = "ns-cap-" + i;
            String slug = "slug-" + i;
            NamespaceRecord rec = new NamespaceRecord(
                    nsId, slug, accountId, NamespaceType.PROJECT,
                    NamespaceStatus.ACTIVE, "Proj " + i, "", null, Instant.now(), null);
            when(catalog.resolve(accountId, slug)).thenReturn(Optional.of(rec));
        }

        when(runtime.attach(anyString(), any())).thenAnswer(inv -> {
            Thread.sleep(10);
            return newMockMemory();
        });

        try (NamespaceResolver resolver = new NamespaceResolver(
                catalog, synapseProps, providerOf(embedder), providerOf(null), providerOf(null),
                providerOf(objectMapper), providerOf(null), providerOf(null), providerOf(null),
                providerOf(null), providerOf(null), providerOf(null), processMaxHot)) {
            resolver.setRuntime(runtime);

            List<Future<SpectorMemory>> futures = new ArrayList<>();
            try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < totalNamespaces; i++) {
                    final String slug = "slug-" + i;
                    futures.add(exec.submit(() -> resolver.resolve(accountId, slug)));
                }
                for (Future<SpectorMemory> f : futures) {
                    assertThat(f.get(10, TimeUnit.SECONDS)).isNotNull();
                }
            }

            assertThat(resolver.cachedInstanceCount()).isLessThanOrEqualTo(accountMaxHot);
            assertThat(resolver.cachedInstanceCount()).isLessThanOrEqualTo(processMaxHot);
        }
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("Cold open with SynapseSalienceProvider does not re-enter NamespaceResolver or self-deadlock")
    void coldOpenWithSynapseSalienceProviderDoesNotSelfDeadlockOnReentrantResolve() {
        String accountId = "acct-salience-reentrant";
        String nsId = "ns-salience-reentrant";
        Account acct = new Account(
                accountId, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                accountId, new AccountQuotas(10, 10, -1, -1),
                new AccountFlags(true, true, true), nsId, Instant.now());
        when(catalog.getOrCreateAccount(accountId)).thenReturn(acct);
        when(catalog.getAccount(accountId)).thenReturn(acct);
        NamespaceRecord rec = new NamespaceRecord(
                nsId, nsId, accountId, NamespaceType.PROJECT,
                NamespaceStatus.ACTIVE, "Personal", "", null, Instant.now(), null);
        when(catalog.resolve(accountId, nsId)).thenReturn(Optional.of(rec));

        synapseProps.getAuth().setEnabled(true);

        AtomicReference<MemoryRegistry> registryRef = new AtomicReference<>();
        @SuppressWarnings("unchecked")
        ObjectProvider<MemoryRegistry> registryProvider = mock(ObjectProvider.class);
        when(registryProvider.getIfAvailable()).thenAnswer(inv -> registryRef.get());

        SynapseSalienceProvider salienceProvider = new SynapseSalienceProvider(embedder, registryProvider);
        AtomicReference<SalienceProfile> assembledProfile = new AtomicReference<>();

        when(runtime.attach(eq(nsId), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<SpectorMemoryBuilder> customizer = inv.getArgument(1);
            SpectorMemoryBuilder builder = SpectorMemory.builder().embeddingProvider(embedder);
            customizer.accept(builder);
            SalienceProfileProvider configuredProvider = builder.salienceProfileProvider();
            if (configuredProvider != null) {
                assembledProfile.set(configuredProvider.effectiveProfile());
            } else {
                assembledProfile.set(builder.salienceProfile());
            }
            return newMockMemory();
        });

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(accountId, "N/A", List.of()));
        try (MemoryRegistry registry = new MemoryRegistry(
                providerOf(null), synapseProps, providerOf(embedder), providerOf(null),
                providerOf(salienceProvider), providerOf(objectMapper),
                providerOf(null), providerOf(null), providerOf(null),
                providerOf(null), providerOf(null), providerOf(null),
                catalog, 32)) {
            registryRef.set(registry);
            registry.namespaceResolver().setRuntime(runtime);

            SpectorMemory resolved = registry.namespaceResolver().resolve(accountId, nsId);
            assertThat(resolved).isNotNull();
            assertThat(assembledProfile.get()).isEqualTo(SalienceProfile.NEUTRAL);
            verify(runtime, times(1)).attach(eq(nsId), any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("Same-thread re-entrant resolve on an in-flight namespace fails fast instead of self-waiting")
    void sameThreadReentrantResolveFailsFastInsteadOfSelfWaiting() {
        String accountId = "acct-direct-reentrant";
        String nsId = "ns-direct-reentrant";
        Account acct = new Account(
                accountId, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                accountId, new AccountQuotas(10, 10, -1, -1),
                new AccountFlags(true, true, true), nsId, Instant.now());
        when(catalog.getOrCreateAccount(accountId)).thenReturn(acct);
        when(catalog.getAccount(accountId)).thenReturn(acct);

        try (NamespaceResolver resolver = new NamespaceResolver(
                catalog, synapseProps, providerOf(embedder), providerOf(null), providerOf(null),
                providerOf(objectMapper), providerOf(null), providerOf(null), providerOf(null),
                providerOf(null), providerOf(null), providerOf(null), 32)) {
            resolver.setRuntime(runtime);

            AtomicReference<Throwable> reentrantError = new AtomicReference<>();
            when(runtime.attach(eq(nsId), any())).thenAnswer(inv -> {
                try {
                    resolver.resolve(accountId);
                } catch (Throwable t) {
                    reentrantError.set(t);
                }
                return newMockMemory();
            });

            SpectorMemory built = resolver.resolve(accountId);
            assertThat(built).isNotNull();
            assertThat(reentrantError.get())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Re-entrant");
        }
    }
}
