/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.memory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import com.spectrayan.spector.memory.SalienceProfileProvider;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.id.TsidGenerator;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.spring.autoconfigure.SpectorConfigProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.memory.MemoryDto.RememberRequest;
import com.spectrayan.spector.synapse.platform.events.EventPublisher;

/**
 * Integration test for <strong>request-thread capture of asynchronous writes</strong> (Requirement 10).
 *
 * <p>Exercises the full {@link MemoryService#remember(RememberRequest)} write path wired to a real
 * {@link UserMemoryRegistry} (auth enabled). It proves that when two authenticated users issue
 * concurrent writes, each async write is directed to the {@link SpectorMemory} instance resolved for
 * that write's originating {@code userId} — never another user's instance (Req 10.4) — and that the
 * target instance is captured on the request thread <em>before</em> the async task runs, so the async
 * task body never reads the security context (Req 10.1, 10.2, 10.3).</p>
 *
 * <h3>Approach and limitations</h3>
 * <p>A full end-to-end test with two live {@code DefaultSpectorMemory} instances (off-heap storage,
 * native access, embedder/LLM providers) is heavyweight and flaky, and does not add signal for the
 * property under test — which is purely about <em>which</em> instance each write is routed to and
 * <em>on which thread</em> that instance is resolved. This test therefore uses a genuine
 * {@code UserMemoryRegistry} whose per-user cache is pre-seeded with two distinct Mockito
 * {@link SpectorMemory} mocks (one per user) injected via reflection — mirroring the approach in
 * {@code UserMemoryRegistryTest} — and a mocked {@link MemoryAccessObject} so the resolved instance
 * passed into the async {@code mao.remember(...)} call can be captured and verified deterministically.
 * The registry's own real resolution/caching logic (reading {@code SecurityContextHolder} on the
 * calling thread and keying strictly off the authenticated {@code userId}) runs unmocked.</p>
 *
 * <p>{@code SecurityContextHolder} uses the default MODE_THREADLOCAL strategy (a plain, non-inheritable
 * {@link ThreadLocal}). The virtual threads that run the async write tasks therefore do <em>not</em>
 * inherit the request thread's principal. If {@code MemoryService} (incorrectly) resolved the target
 * memory inside the async task instead of on the request thread, resolution would fall back to the
 * shared/{@code "default"} instance and these assertions would fail — which is exactly the regression
 * this test guards against.</p>
 */
@DisplayName("MemoryService — Concurrent Request-Thread Capture (Integration)")
class ConcurrentRequestThreadCaptureIntegrationTest {

    private static final String USER_A = "USER0000000AA";
    private static final String USER_B = "USER0000000BB";

    @TempDir
    Path tempDir;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ══════════════════════════════════════════════════════════════
    // Req 10.4 — concurrent authenticated writes never cross instances
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("two authenticated users writing concurrently each land in their own per-user instance, never crossing")
    void concurrentAuthenticatedWrites_eachLandsInOwnInstance_neverCross() throws Exception {
        MemoryAccessObject mao = mock(MemoryAccessObject.class);
        when(mao.isAvailable(any())).thenReturn(true);
        // Async write completes immediately; capture happens on the request thread before dispatch.
        when(mao.remember(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(1));

        UserMemoryRegistry registry = buildRegistry(true);
        SpectorMemory memA = mock(SpectorMemory.class);
        SpectorMemory memB = mock(SpectorMemory.class);
        injectHandle(registry, USER_A, memA);
        injectHandle(registry, USER_B, memB);

        MemoryService service = newService(mao, registry);

        String idA = "MEM0000000AAA";
        String idB = "MEM0000000BBB";
        RememberRequest reqA = rememberRequest(idA, "user A content");
        RememberRequest reqB = rememberRequest(idB, "user B content");

        // Two request threads, each binding its OWN principal, released simultaneously.
        CyclicBarrier startLine = new CyclicBarrier(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService requestThreads = Executors.newFixedThreadPool(2);
        try {
            requestThreads.submit(runAsUser(USER_A, reqA, service, startLine, failure));
            requestThreads.submit(runAsUser(USER_B, reqB, service, startLine, failure));
            requestThreads.shutdown();
            assertThat(requestThreads.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            requestThreads.shutdownNow();
        }
        assertThat(failure.get()).as("request thread failure").isNull();

        // Each user's write is routed to that user's own instance (Req 10.4) — waiting for the async task.
        verify(mao, timeout(5_000)).remember(same(memA), eq(idA), eq("user A content"),
                any(), any(), any(), any());
        verify(mao, timeout(5_000)).remember(same(memB), eq(idB), eq("user B content"),
                any(), any(), any(), any());

        // No write ever crosses into another user's instance (Req 10.4).
        verify(mao, never()).remember(same(memB), eq(idA), any(), any(), any(), any(), any());
        verify(mao, never()).remember(same(memA), eq(idB), any(), any(), any(), any(), any());
    }

    // ══════════════════════════════════════════════════════════════
    // Req 10.1/10.2/10.3 — instance captured on the request thread, not inside the async task
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("async write uses the instance captured on the request thread even after the security context is cleared")
    void asyncWrite_usesInstanceCapturedOnRequestThread_afterContextCleared() throws Exception {
        // The async mao.remember(...) blocks until we release it, guaranteeing the async body runs
        // strictly AFTER the request thread has cleared its SecurityContext. If MemoryService read the
        // security context inside the async task (violating Req 10.3), it would resolve "default"
        // (shared) instead of memA — so verifying same(memA) proves request-thread capture.
        CountDownLatch proceed = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        MemoryAccessObject mao = mock(MemoryAccessObject.class);
        when(mao.isAvailable(any())).thenReturn(true);
        when(mao.remember(any(), any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            entered.countDown();
            proceed.await(5, TimeUnit.SECONDS);
            return inv.getArgument(1);
        });

        UserMemoryRegistry registry = buildRegistry(true);
        SpectorMemory memA = mock(SpectorMemory.class);
        injectHandle(registry, USER_A, memA);
        MemoryService service = newService(mao, registry);

        // Request thread: bind principal, dispatch the async write, then immediately clear the context.
        bind(authenticated(USER_A, "SCOPE_memory:write"));
        service.remember(rememberRequest("MEM0000000AAA", "captured on request thread"));
        SecurityContextHolder.clearContext();

        // Let the async task run only now that the request-thread context is gone.
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        proceed.countDown();

        verify(mao, timeout(5_000)).remember(same(memA), eq("MEM0000000AAA"),
                eq("captured on request thread"), any(), any(), any(), any());
    }

    // ══════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════

    private static Runnable runAsUser(String userId, RememberRequest request, MemoryService service,
                                      CyclicBarrier startLine, AtomicReference<Throwable> failure) {
        return () -> {
            try {
                bind(authenticated(userId, "SCOPE_memory:write"));
                startLine.await(5, TimeUnit.SECONDS); // release both threads together
                service.remember(request);
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private MemoryService newService(MemoryAccessObject mao, UserMemoryRegistry registry) {
        EventPublisher eventPublisher = mock(EventPublisher.class);
        return new MemoryService(mao, eventPublisher, new TsidGenerator(), null, null, registry);
    }

    private static RememberRequest rememberRequest(String id, String text) {
        return new RememberRequest(id, text, null, null, null, null, null, null, null, null);
    }

    private UserMemoryRegistry buildRegistry(boolean authEnabled) {
        ObjectProvider<SpectorMemory> sharedProvider = mockProvider();
        when(sharedProvider.getIfAvailable()).thenReturn(mock(SpectorMemory.class));
        ObjectProvider<EmbeddingProvider> embedderProvider = mockProvider();
        ObjectProvider<LlmProvider> textGenProvider = mockProvider();
        ObjectProvider<SalienceProfileProvider> salienceProvider = mockProvider();

        SpectorConfigProperties cfg = new SpectorConfigProperties();
        cfg.getMemory().setPersistencePath(tempDir.toString());
        return new UserMemoryRegistry(
                sharedProvider,
                synapseProps(authEnabled),
                cfg,
                embedderProvider,
                textGenProvider,
                salienceProvider,
                512);
    }

    private SynapseProperties synapseProps(boolean authEnabled) {
        var auth = new SynapseProperties.AuthProperties(
                authEnabled, null, null, null, null, null, null, null);
        return new SynapseProperties(0, null, tempDir.toString(), null, null, null, auth);
    }

    private static Authentication authenticated(String principal, String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                principal, "credentials", AuthorityUtils.createAuthorityList(authorities));
    }

    private static void bind(Authentication auth) {
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> mockProvider() {
        return mock(ObjectProvider.class);
    }

    // --- reflection into the registry's private cache / handle (mirrors UserMemoryRegistryTest) ---

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, Object> cacheOf(UserMemoryRegistry registry) throws Exception {
        Field field = UserMemoryRegistry.class.getDeclaredField("cache");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, Object>) field.get(registry);
    }

    private static void injectHandle(UserMemoryRegistry registry, String userId,
                                     SpectorMemory memory) throws Exception {
        Class<?> handleClass = Class.forName(
                "com.spectrayan.spector.synapse.memory.UserMemoryRegistry$MemoryHandle");
        Constructor<?> ctor = handleClass.getDeclaredConstructor(SpectorMemory.class);
        ctor.setAccessible(true);
        Object handle = ctor.newInstance(memory);
        cacheOf(registry).put(userId, handle);
    }
}
