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
package com.spectrayan.spector.synapse.mcp;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
import static org.mockito.Mockito.mock;
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
import com.spectrayan.spector.synapse.memory.UserMemoryRegistry;

/**
 * Integration test for <strong>MCP tool isolation</strong> (Requirement 11).
 *
 * <p>Exercises the exact request-thread binding seam that {@code McpServerConfig}'s tool lambda and
 * {@code McpController#callTool} use to route an MCP tool invocation to the caller's per-user
 * memory: {@link McpRequestMemory#bindForCurrentRequest(UserMemoryRegistry, boolean)} followed by
 * {@link McpRequestMemory#current()} and {@link McpRequestMemory#clear()}. It proves that:</p>
 * <ul>
 *   <li>an authenticated {@code /mcp} call binds the memory instance rooted at the caller's own
 *       sharded namespace, and two authenticated callers never cross (Req 11.1, 11.6);</li>
 *   <li>an unauthenticated call while auth is enabled is denied with an auth-required tool error and
 *       binds nothing — no fallback to the shared or another user's memory (Req 11.5);</li>
 *   <li>a call whose per-user memory resolution fails is denied with a resolution-failed tool error
 *       and binds nothing — again no shared/other-user fallback (Req 11.4);</li>
 *   <li>a stdio invocation (no security context, auth disabled) routes to the single shared memory
 *       (Req 11.7).</li>
 * </ul>
 *
 * <h3>Approach and limitations</h3>
 * <p>A full MCP-over-HTTP transport test (spinning up {@code HttpServletStatelessServerTransport},
 * the servlet container, and two live {@code DefaultSpectorMemory} instances with off-heap storage
 * and native access) is heavyweight, flaky, and adds no signal for the property under test — which
 * is purely about <em>which</em> memory instance the invocation is bound to <em>on the request
 * thread</em>, and how deny paths fail closed. This test therefore drives the genuine
 * {@link McpRequestMemory} binding logic against a real {@link UserMemoryRegistry} whose per-user
 * cache is pre-seeded with distinct Mockito {@link SpectorMemory} mocks (one per user) injected via
 * reflection — mirroring {@code UserMemoryRegistryTest} and
 * {@code ConcurrentRequestThreadCaptureIntegrationTest}. The registry's own real resolution logic
 * (reading {@code SecurityContextHolder} on the calling thread and keying strictly off the
 * authenticated {@code userId}) runs unmocked, and the deny paths drive the genuine fail-closed
 * branches.</p>
 *
 * <p>{@code SecurityContextHolder} uses the default MODE_THREADLOCAL strategy, so each simulated
 * request thread carries its own principal — exactly matching the per-request servlet threads the
 * stateless transport dispatches on.</p>
 */
@DisplayName("MCP Tool Isolation (Integration)")
class McpToolIsolationIntegrationTest {

    private static final String USER_A = "USER0000000AA";
    private static final String USER_B = "USER0000000BB";

    @TempDir
    Path tempDir;

    @AfterEach
    void clearState() {
        SecurityContextHolder.clearContext();
        McpRequestMemory.clear();
    }

    // ══════════════════════════════════════════════════════════════
    // Req 11.1 — authenticated HTTP call binds the caller's own namespace instance
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("authenticated /mcp call binds the caller's own per-user memory instance")
    void authenticatedCall_bindsCallersOwnInstance() throws Exception {
        UserMemoryRegistry registry = buildRegistry(true);
        SpectorMemory memA = mock(SpectorMemory.class);
        injectHandle(registry, USER_A, memA);

        bind(authenticated(USER_A, "SCOPE_memory:read"));

        Optional<McpRequestMemory.DenyReason> deny =
                McpRequestMemory.bindForCurrentRequest(registry, true);

        assertThat(deny).as("authenticated call must not be denied").isEmpty();
        assertThat(McpRequestMemory.current())
                .as("tool routes exclusively to the caller's namespace instance")
                .isSameAs(memA);

        McpRequestMemory.clear();
        assertThat(McpRequestMemory.current())
                .as("clear() removes the request-thread binding")
                .isNull();
    }

    // ══════════════════════════════════════════════════════════════
    // Req 11.1 / 11.6 — two concurrent authenticated callers never cross
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("two authenticated users invoking concurrently each bind their own instance, never crossing")
    void concurrentAuthenticatedCalls_neverCross() throws Exception {
        UserMemoryRegistry registry = buildRegistry(true);
        SpectorMemory memA = mock(SpectorMemory.class);
        SpectorMemory memB = mock(SpectorMemory.class);
        injectHandle(registry, USER_A, memA);
        injectHandle(registry, USER_B, memB);

        CyclicBarrier startLine = new CyclicBarrier(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<SpectorMemory> boundForA = new AtomicReference<>();
        AtomicReference<SpectorMemory> boundForB = new AtomicReference<>();

        ExecutorService requestThreads = Executors.newFixedThreadPool(2);
        try {
            requestThreads.submit(invokeAsUser(USER_A, registry, startLine, failure, boundForA));
            requestThreads.submit(invokeAsUser(USER_B, registry, startLine, failure, boundForB));
            requestThreads.shutdown();
            assertThat(requestThreads.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            requestThreads.shutdownNow();
        }

        assertThat(failure.get()).as("request thread failure").isNull();
        // Each invocation was routed to its own user's instance (Req 11.6) ...
        assertThat(boundForA.get()).isSameAs(memA);
        assertThat(boundForB.get()).isSameAs(memB);
        // ... and never to the other user's instance (Req 11.6, no crossing).
        assertThat(boundForA.get()).isNotSameAs(memB);
        assertThat(boundForB.get()).isNotSameAs(memA);
    }

    // ══════════════════════════════════════════════════════════════
    // Req 11.5 — unauthenticated call (auth enabled) → auth-required tool error, no binding
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("unauthenticated /mcp call while auth enabled → auth-required deny, nothing bound, no fallback")
    void unauthenticatedCall_authEnabled_deniedAuthRequired_noFallback() throws Exception {
        UserMemoryRegistry registry = buildRegistry(true);
        SpectorMemory shared = sharedInstanceOf(registry);
        // No Authentication bound → SecurityUtils resolves anonymous/"default".

        Optional<McpRequestMemory.DenyReason> deny =
                McpRequestMemory.bindForCurrentRequest(registry, true);

        assertThat(deny).contains(McpRequestMemory.DenyReason.AUTH_REQUIRED);
        // Nothing is bound: the call is denied fail-closed — never the shared or a user's instance.
        assertThat(McpRequestMemory.current()).isNull();
        assertThat(McpRequestMemory.current()).isNotSameAs(shared);
        // The tool-error content indicates authentication is required (Req 11.5).
        assertThat(McpRequestMemory.message(McpRequestMemory.DenyReason.AUTH_REQUIRED))
                .containsIgnoringCase("authentication is required");
    }

    @Test
    @DisplayName("anonymous token while auth enabled → auth-required deny, nothing bound")
    void anonymousToken_authEnabled_deniedAuthRequired() throws Exception {
        UserMemoryRegistry registry = buildRegistry(true);
        bind(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        Optional<McpRequestMemory.DenyReason> deny =
                McpRequestMemory.bindForCurrentRequest(registry, true);

        assertThat(deny).contains(McpRequestMemory.DenyReason.AUTH_REQUIRED);
        assertThat(McpRequestMemory.current()).isNull();
    }

    // ══════════════════════════════════════════════════════════════
    // Req 11.4 — resolution failure for an authenticated call → resolution-failed deny, no fallback
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("authenticated call whose memory resolution fails → resolution-failed deny, nothing bound, no fallback")
    void authenticatedCall_resolutionFails_deniedResolutionFailed_noFallback() throws Exception {
        // Auth enabled, no EmbeddingProvider → buildInstance throws for a not-yet-cached user, so
        // resolveForCurrentRequest fails and bindForCurrentRequest denies with RESOLUTION_FAILED.
        UserMemoryRegistry registry = buildRegistry(true);
        SpectorMemory shared = sharedInstanceOf(registry);
        bind(authenticated(USER_A, "SCOPE_memory:read"));

        Optional<McpRequestMemory.DenyReason> deny =
                McpRequestMemory.bindForCurrentRequest(registry, true);

        assertThat(deny).contains(McpRequestMemory.DenyReason.RESOLUTION_FAILED);
        // Fail-closed: nothing bound, no fallback to shared or another user's memory (Req 11.4).
        assertThat(McpRequestMemory.current()).isNull();
        assertThat(McpRequestMemory.current()).isNotSameAs(shared);
        assertThat(registry.cachedInstanceCount())
                .as("failed build leaves nothing cached")
                .isZero();
        assertThat(McpRequestMemory.message(McpRequestMemory.DenyReason.RESOLUTION_FAILED))
                .containsIgnoringCase("memory resolution failed");
    }

    // ══════════════════════════════════════════════════════════════
    // Req 11.7 — stdio (no security context, auth disabled) → shared memory
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("stdio invocation (no security context, auth disabled) → routes to the single shared memory")
    void stdioInvocation_authDisabled_routesToSharedMemory() throws Exception {
        UserMemoryRegistry registry = buildRegistry(false);
        SpectorMemory shared = sharedInstanceOf(registry);
        // No security context bound — mirrors the stdio transport path (auth disabled).

        Optional<McpRequestMemory.DenyReason> deny =
                McpRequestMemory.bindForCurrentRequest(registry, false);

        assertThat(deny).as("stdio path is never denied").isEmpty();
        assertThat(McpRequestMemory.current())
                .as("stdio routes to the single shared memory (legacy single-user behavior)")
                .isSameAs(shared);
    }

    // ══════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════

    private static Runnable invokeAsUser(String userId, UserMemoryRegistry registry,
                                         CyclicBarrier startLine, AtomicReference<Throwable> failure,
                                         AtomicReference<SpectorMemory> boundInstance) {
        return () -> {
            try {
                bind(authenticated(userId, "SCOPE_memory:read"));
                startLine.await(5, TimeUnit.SECONDS); // release both threads together
                Optional<McpRequestMemory.DenyReason> deny =
                        McpRequestMemory.bindForCurrentRequest(registry, true);
                if (deny.isPresent()) {
                    throw new IllegalStateException("unexpected deny for " + userId + ": " + deny.get());
                }
                boundInstance.set(McpRequestMemory.current());
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                McpRequestMemory.clear();
                SecurityContextHolder.clearContext();
            }
        };
    }

    private UserMemoryRegistry buildRegistry(boolean authEnabled) {
        ObjectProvider<SpectorMemory> sharedProvider = mockProvider();
        when(sharedProvider.getIfAvailable()).thenReturn(mock(SpectorMemory.class));
        ObjectProvider<EmbeddingProvider> embedderProvider = mockProvider();
        // No EmbeddingProvider available → per-user buildInstance fails closed (drives Req 11.4).
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

    // --- reflection into the registry's private cache / handle / shared provider ------------------

    /** Reads the shared instance the registry hands out for anonymous/disabled resolution. */
    private static SpectorMemory sharedInstanceOf(UserMemoryRegistry registry) throws Exception {
        Field field = UserMemoryRegistry.class.getDeclaredField("sharedProvider");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<SpectorMemory> provider = (ObjectProvider<SpectorMemory>) field.get(registry);
        return provider.getIfAvailable();
    }

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
