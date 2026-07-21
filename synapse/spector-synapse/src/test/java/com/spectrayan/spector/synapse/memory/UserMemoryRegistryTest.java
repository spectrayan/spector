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
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import com.spectrayan.spector.memory.SalienceProfileProvider;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.spring.autoconfigure.SpectorConfigProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties;

/**
 * Unit tests for {@link UserMemoryRegistry}.
 *
 * <p>Verifies the registry's <em>routing decisions</em> and <em>lifecycle contract</em> — the
 * observable behavior that isolates each authenticated user's memory:</p>
 * <ul>
 *   <li>auth disabled → the single shared instance for every caller (Req 1.4);</li>
 *   <li>anonymous / {@code "default"} principal → the shared instance, nothing cached (Req 9.4);</li>
 *   <li>authenticated principal → exactly one cached per-user instance, returned identically on
 *       repeat resolution, keyed purely by {@code userId} (Req 9.2, 9.3);</li>
 *   <li>LRU eviction closes the oldest per-user instance and never the shared one (Req 9.6);</li>
 *   <li>{@link UserMemoryRegistry#close()} closes every cached per-user instance exactly once and
 *       never the shared instance (Req 9.5);</li>
 *   <li>fail-closed: construction failure propagates, nothing is cached, and neither the shared nor
 *       another user's instance is returned (Req 9.7).</li>
 * </ul>
 *
 * <h3>Approach and limitation</h3>
 * <p>{@code UserMemoryRegistry} is a {@code final} class whose per-user instances are built inside a
 * {@code private} {@code buildInstance(...)} that calls the heavyweight
 * {@code DefaultSpectorMemory.builder().build()} (off-heap storage, native access). Constructing a
 * real {@link SpectorMemory} in a unit test is impractical and flaky, and the class cannot be
 * sub-classed or spied to substitute a fake builder. Therefore the caching, eviction, and close
 * behaviors are exercised with Mockito {@link SpectorMemory} mocks injected directly into the
 * registry's private cache via reflection — this validates the routing/LRU/close <em>logic</em>
 * deterministically without a live build. The real construction path is still covered end-to-end:
 * the fail-closed tests drive the genuine {@code buildInstance} path (invalid identifier / missing
 * embedder), and successful live construction is validated by the routing-isolation property test
 * (task 14.2) and the integration suites.</p>
 */
@DisplayName("UserMemoryRegistry — Unit Tests")
class UserMemoryRegistryTest {

    private static final String USER_A = "USER0000000AA";
    private static final String USER_B = "USER0000000BB";
    private static final String USER_C = "USER0000000CC";

    @TempDir
    Path tempDir;

    private SpectorMemory shared;
    private ObjectProvider<SpectorMemory> sharedProvider;
    private ObjectProvider<EmbeddingProvider> embedderProvider;
    private ObjectProvider<LlmProvider> textGenProvider;
    private ObjectProvider<SalienceProfileProvider> salienceProvider;

    @BeforeEach
    void setUp() {
        shared = mock(SpectorMemory.class);
        sharedProvider = mockProvider();
        when(sharedProvider.getIfAvailable()).thenReturn(shared);
        embedderProvider = mockProvider();
        textGenProvider = mockProvider();
        when(textGenProvider.getIfAvailable()).thenReturn(null);
        salienceProvider = mockProvider();
        when(salienceProvider.getIfAvailable()).thenReturn(null);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ══════════════════════════════════════════════════════════════
    // Req 1.4 — auth disabled → always the shared instance
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("auth disabled → resolveForCurrentRequest returns the shared instance for every caller")
    void authDisabled_alwaysReturnsSharedInstance() {
        UserMemoryRegistry registry = buildRegistry(false, 512, null);
        // Even with an authenticated principal bound, a disabled feature must ignore it entirely.
        bind(authenticated(USER_A, "SCOPE_memory:read"));

        for (int i = 0; i < 8; i++) {
            assertThat(registry.resolveForCurrentRequest()).isSameAs(shared);
        }
        assertThat(registry.cachedInstanceCount()).isZero();
        // No per-user construction is ever attempted while disabled.
        verify(embedderProvider, never()).getIfAvailable();
    }

    // ══════════════════════════════════════════════════════════════
    // Req 9.4 — anonymous / default principal → the shared instance
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("auth enabled + no authentication → shared instance, nothing cached")
    void anonymous_noAuthentication_returnsShared() {
        UserMemoryRegistry registry = buildRegistry(true, 512, null);
        // No Authentication bound → SecurityUtils resolves "default".

        assertThat(registry.resolveForCurrentRequest()).isSameAs(shared);
        assertThat(registry.cachedInstanceCount()).isZero();
        verify(embedderProvider, never()).getIfAvailable();
    }

    @Test
    @DisplayName("auth enabled + anonymous token → shared instance, nothing cached")
    void anonymous_anonymousToken_returnsShared() {
        UserMemoryRegistry registry = buildRegistry(true, 512, null);
        bind(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(registry.resolveForCurrentRequest()).isSameAs(shared);
        assertThat(registry.cachedInstanceCount()).isZero();
    }

    @Test
    @DisplayName("resolveFor(default/null/blank) → shared instance, nothing cached")
    void resolveFor_defaultNullBlank_returnsShared() {
        UserMemoryRegistry registry = buildRegistry(true, 512, null);

        assertThat(registry.resolveFor("default")).isSameAs(shared);
        assertThat(registry.resolveFor(null)).isSameAs(shared);
        assertThat(registry.resolveFor("   ")).isSameAs(shared);
        assertThat(registry.cachedInstanceCount()).isZero();
    }

    // ══════════════════════════════════════════════════════════════
    // Req 9.2 — authenticated → one cached per-user instance, stable on repeat
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("authenticated user → same cached per-user instance on repeated resolution")
    void authenticatedUser_returnsSameCachedInstance() throws Exception {
        UserMemoryRegistry registry = buildRegistry(true, 512, null);
        SpectorMemory perUser = mock(SpectorMemory.class);
        injectHandle(registry, USER_A, perUser, 1_000L);

        SpectorMemory first = registry.resolveFor(USER_A);
        SpectorMemory second = registry.resolveFor(USER_A);

        assertThat(first).isSameAs(perUser).isSameAs(second);
        assertThat(first).isNotSameAs(shared);
        assertThat(registry.cachedInstanceCount()).isEqualTo(1);
        // Cache hit — no construction, and the shared instance is never consulted.
        verify(embedderProvider, never()).getIfAvailable();
        verify(sharedProvider, never()).getIfAvailable();
    }

    @Test
    @DisplayName("authenticated request thread → routes to the principal's cached instance")
    void authenticatedRequest_routesToPrincipalInstance() throws Exception {
        UserMemoryRegistry registry = buildRegistry(true, 512, null);
        SpectorMemory perUser = mock(SpectorMemory.class);
        injectHandle(registry, USER_A, perUser, 1_000L);
        bind(authenticated(USER_A, "SCOPE_memory:read"));

        assertThat(registry.resolveForCurrentRequest()).isSameAs(perUser);
        assertThat(registry.resolveForCurrentRequest()).isSameAs(perUser);
    }

    // ══════════════════════════════════════════════════════════════
    // Req 9.3 — routing is a pure function of userId (adversarial input ignored)
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("routing is a pure function of userId — distinct users never collide")
    void routing_isPureFunctionOfUserId() throws Exception {
        UserMemoryRegistry registry = buildRegistry(true, 512, null);
        SpectorMemory memA = mock(SpectorMemory.class);
        SpectorMemory memB = mock(SpectorMemory.class);
        injectHandle(registry, USER_A, memA, 1_000L);
        injectHandle(registry, USER_B, memB, 2_000L);

        // The public surface exposes no namespace/workspace_id/agent_id parameter: resolution keys
        // strictly off the authenticated userId, so client-supplied values cannot alter routing.
        assertThat(registry.resolveFor(USER_A)).isSameAs(memA);
        assertThat(registry.resolveFor(USER_B)).isSameAs(memB);
        assertThat(registry.resolveFor(USER_A)).isSameAs(memA);

        bind(authenticated(USER_B, "SCOPE_memory:read"));
        assertThat(registry.resolveForCurrentRequest()).isSameAs(memB);

        assertThat(registry.cachedInstanceCount()).isEqualTo(2);
    }

    // ══════════════════════════════════════════════════════════════
    // Req 9.6 — LRU eviction closes the oldest per-user instance, never the shared one
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("LRU eviction closes the oldest per-user instance and never the shared instance")
    void lruEviction_closesOldest_neverShared() throws Exception {
        UserMemoryRegistry registry = buildRegistry(true, 512, null);
        SpectorMemory oldest = mock(SpectorMemory.class);
        SpectorMemory middle = mock(SpectorMemory.class);
        SpectorMemory newest = mock(SpectorMemory.class);
        injectHandle(registry, USER_A, oldest, 100L);
        injectHandle(registry, USER_B, middle, 200L);
        injectHandle(registry, USER_C, newest, 300L);

        invokeEvictOldest(registry);

        // The oldest last-resolution instance is evicted and closed exactly once.
        verify(oldest, times(1)).close();
        verify(middle, never()).close();
        verify(newest, never()).close();
        // Shared instance is never held in the cache, so it is never evicted or closed.
        verify(shared, never()).close();

        assertThat(registry.cachedInstanceCount()).isEqualTo(2);
        assertThat(cacheOf(registry)).doesNotContainKey(USER_A)
                .containsKeys(USER_B, USER_C);
    }

    @Test
    @DisplayName("cold-path eviction runs before caching when at the instance cap")
    void coldPath_evictsBeforeCaching_whenAtCap() throws Exception {
        // maxInstances=1, one instance already cached, and the new build fails-closed. Eviction
        // happens before the (failing) build, so the pre-existing instance is still evicted+closed.
        UserMemoryRegistry registry = buildRegistry(true, 1, null); // embedder null → build fails
        SpectorMemory existing = mock(SpectorMemory.class);
        injectHandle(registry, USER_A, existing, 100L);

        assertThatThrownBy(() -> registry.resolveFor(USER_B))
                .isInstanceOf(IllegalStateException.class);

        verify(existing, times(1)).close();       // evicted before the failed build
        assertThat(registry.cachedInstanceCount()).isZero(); // USER_B not cached (fail-closed)
        verify(shared, never()).close();
    }

    // ══════════════════════════════════════════════════════════════
    // Req 9.5 — close() closes every cached per-user instance exactly once
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("close() closes every cached per-user instance exactly once; shared untouched")
    void close_closesAllPerUserInstancesOnce_sharedUntouched() throws Exception {
        UserMemoryRegistry registry = buildRegistry(true, 512, null);
        SpectorMemory memA = mock(SpectorMemory.class);
        SpectorMemory memB = mock(SpectorMemory.class);
        SpectorMemory memC = mock(SpectorMemory.class);
        injectHandle(registry, USER_A, memA, 100L);
        injectHandle(registry, USER_B, memB, 200L);
        injectHandle(registry, USER_C, memC, 300L);

        registry.close();

        verify(memA, times(1)).close();
        verify(memB, times(1)).close();
        verify(memC, times(1)).close();
        verify(shared, never()).close();
        assertThat(registry.cachedInstanceCount()).isZero();

        // Idempotent: a second close() must not close any instance again.
        registry.close();
        verify(memA, times(1)).close();
        verify(memB, times(1)).close();
        verify(memC, times(1)).close();
    }

    // ══════════════════════════════════════════════════════════════
    // Req 9.7 — fail-closed on construction failure
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("fail-closed: missing embedder → resolveFor throws, nothing cached, no shared fallback")
    void failClosed_missingEmbedder_throwsAndCachesNothing() {
        UserMemoryRegistry registry = buildRegistry(true, 512, null); // no EmbeddingProvider

        assertThatThrownBy(() -> registry.resolveFor(USER_A))
                .isInstanceOf(IllegalStateException.class);

        assertThat(registry.cachedInstanceCount()).isZero();
        // Failure is propagated — the shared instance is never returned as a fallback.
        verify(sharedProvider, never()).getIfAvailable();
    }

    @Test
    @DisplayName("fail-closed: unsafe user id is rejected before path resolution, nothing cached")
    void failClosed_unsafeUserId_rejectedAndCachesNothing() {
        UserMemoryRegistry registry = buildRegistry(true, 512, mock(EmbeddingProvider.class));

        assertThatThrownBy(() -> registry.resolveFor("../escape"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(registry.cachedInstanceCount()).isZero();
        verify(sharedProvider, never()).getIfAvailable();
    }

    // ══════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════

    private UserMemoryRegistry buildRegistry(boolean authEnabled, int maxInstances, EmbeddingProvider embedder) {
        when(embedderProvider.getIfAvailable()).thenReturn(embedder);
        SpectorConfigProperties cfg = new SpectorConfigProperties();
        cfg.getMemory().setPersistencePath(tempDir.toString());
        return new UserMemoryRegistry(
                sharedProvider,
                synapseProps(authEnabled),
                cfg,
                embedderProvider,
                textGenProvider,
                salienceProvider,
                maxInstances);
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

    // --- reflection into the registry's private cache / handle / eviction ---------------------

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, Object> cacheOf(UserMemoryRegistry registry) throws Exception {
        Field field = UserMemoryRegistry.class.getDeclaredField("cache");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, Object>) field.get(registry);
    }

    private static void injectHandle(UserMemoryRegistry registry, String userId,
                                     SpectorMemory memory, long lastAccessNanos) throws Exception {
        Class<?> handleClass = Class.forName(
                "com.spectrayan.spector.synapse.memory.UserMemoryRegistry$MemoryHandle");
        Constructor<?> ctor = handleClass.getDeclaredConstructor(SpectorMemory.class);
        ctor.setAccessible(true);
        Object handle = ctor.newInstance(memory);
        Field lastAccess = handleClass.getDeclaredField("lastAccessNanos");
        lastAccess.setAccessible(true);
        lastAccess.setLong(handle, lastAccessNanos);
        cacheOf(registry).put(userId, handle);
    }

    private static void invokeEvictOldest(UserMemoryRegistry registry) throws Exception {
        Method method = UserMemoryRegistry.class.getDeclaredMethod("evictOldestLocked");
        method.setAccessible(true);
        method.invoke(registry);
    }
}
