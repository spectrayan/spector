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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import com.spectrayan.spector.memory.SalienceProfileProvider;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.StorageLayout;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.spring.autoconfigure.SpectorConfigProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeContainer;

/**
 * Property-based tests (jqwik) for <b>Property 3: Routing isolation under adversarial client
 * input</b> of the multi-user auth design.
 *
 * <p>The routing guarantee under test: for ANY authenticated {@code userId} and ANY client-supplied
 * {@code namespace}/{@code workspace_id}/{@code agent_id} values,
 * {@link UserMemoryRegistry#resolveForCurrentRequest()} (and {@link UserMemoryRegistry#resolveFor}
 * ) routes to the instance rooted at
 * {@link StorageLayout#namespaceDirSharded(Path, String) namespaceDirSharded(base, userId)} — i.e.
 * resolution is a <em>pure function of the authenticated {@code userId}</em>. Adversarial client
 * params can never change which user's memory is returned, and the sharded directory that keys the
 * routing for user A never equals user B's and is always a descendant of the base.</p>
 *
 * <h3>Approach and limitation</h3>
 * <p>{@code UserMemoryRegistry} is a {@code final} class whose per-user instances are built inside a
 * {@code private buildInstance(...)} that constructs a heavyweight {@code DefaultSpectorMemory}
 * (off-heap storage, native access). Building real {@link SpectorMemory} instances for every jqwik
 * try would be prohibitively slow and flaky, and a full data-write/read isolation property is
 * therefore impractical at unit-test speed. This test instead focuses on the two facets of the
 * routing guarantee that are observable without a live build:</p>
 * <ol>
 *   <li><b>Registry routing purity</b> — mock {@link SpectorMemory} instances are injected directly
 *       into the registry's private cache via reflection (the same helper pattern used by
 *       {@code UserMemoryRegistryTest}). For two distinct authenticated users A != B, and for any
 *       stream of adversarial {@code namespace}/{@code workspace_id}/{@code agent_id} strings
 *       (smuggled onto the {@link Authentication} as authorities/details), resolution for A always
 *       returns the <em>same</em> instance across repeated calls and is always <em>distinct</em>
 *       from B's and from the shared instance. The registry's public API is also asserted to expose
 *       <em>no</em> namespace/workspace_id/agent_id parameter — its only routing key is the
 *       authenticated {@code userId}.</li>
 *   <li><b>Path-derivation isolation</b> — the registry's routing key,
 *       {@code StorageLayout.namespaceDirSharded(base, userId)}, is asserted directly: for A != B
 *       the sharded dirs differ and neither is an ancestor of the other, and A's dir is always a
 *       strict descendant of the base (no traversal escape).</li>
 * </ol>
 *
 * <p><b>Validates: Requirements 9.3, 11.2, 11.3</b></p>
 */
class UserMemoryRegistryRoutingPropertyTest {

    /** Literal user id that resolves to the single shared instance. */
    private static final String DEFAULT_USER_ID = "default";

    /**
     * A base persistence root shared across tries. Path resolution is a pure function that never
     * touches the filesystem, so a single stable root is sufficient. The registry construction in
     * these tests never triggers a live build (mocks are pre-injected), so nothing is written here.
     */
    private static Path base;

    @BeforeContainer
    static void createBase() throws IOException {
        base = Files.createTempDirectory("spector-routing-prop").toAbsolutePath().normalize();
    }

    @AfterContainer
    static void deleteBase() throws IOException {
        if (base != null && Files.exists(base)) {
            try (Stream<Path> walk = Files.walk(base)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                });
            }
        }
    }

    /** The SecurityContextHolder is a thread-local; clear it after every try to avoid bleed. */
    @AfterTry
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Generators ──

    /**
     * Valid {@code User_Id} values: non-blank identifiers from a safe alphabet (alphanumeric plus
     * {@code -} and {@code _}) constrained to 1..256 characters. These are safe to route and to
     * shard via {@link StorageLayout#namespaceDirSharded(Path, String)}.
     */
    @Provide
    Arbitrary<String> validUserIds() {
        return Arbitraries.strings()
                .withChars("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_")
                .ofMinLength(1)
                .ofMaxLength(256)
                .filter(s -> !s.isBlank() && !DEFAULT_USER_ID.equals(s));
    }

    /** Pairs of <em>distinct</em> valid user ids (A != B). */
    @Provide
    Arbitrary<String[]> distinctUserPairs() {
        return Combinators.combine(validUserIds(), validUserIds())
                .as((a, b) -> new String[] {a, b})
                .filter(pair -> !pair[0].equals(pair[1]));
    }

    /**
     * Adversarial client-supplied {@code namespace}/{@code workspace_id}/{@code agent_id} values a
     * malicious caller might use to try to cross into another user's memory: path-traversal
     * sequences, absolute paths, separators, control characters, and free-form strings (which may
     * coincidentally equal another user's id).
     */
    @Provide
    Arbitrary<String> adversarialParams() {
        Arbitrary<String> dangerousSamples = Arbitraries.of(
                "..", ".", "../", "..\\", "../../etc/passwd", "/etc/shadow",
                "\\\\server\\share", "namespaces/AA/BB/victim", "victim",
                "../victim", "..%2f..%2fvictim", "\u0000", "a/b", "a\\b",
                "default", "", "   ", "workspace-1", "agent://evil");
        Arbitrary<String> freeform = Arbitraries.strings().ofMaxLength(64);
        return Arbitraries.oneOf(dangerousSamples, freeform);
    }

    // ── Properties ──

    /**
     * Requirements 9.3, 11.2, 11.3 — resolution on the request thread is a pure function of the
     * authenticated principal. For an authenticated user A (with B also cached), binding any
     * adversarial {@code namespace}/{@code workspace_id}/{@code agent_id} value onto the request's
     * {@link Authentication} (as an authority and as request details) never changes routing:
     * {@code resolveForCurrentRequest()} returns A's instance every time, never B's, never shared.
     */
    @Property(tries = 300)
    void adversarialParamsNeverReRouteAwayFromAuthenticatedUser(
            @ForAll("distinctUserPairs") String[] users,
            @ForAll("adversarialParams") String adversary) throws Exception {
        String userA = users[0];
        String userB = users[1];

        Fixture fx = newFixture();
        SpectorMemory memA = mock(SpectorMemory.class);
        SpectorMemory memB = mock(SpectorMemory.class);
        injectHandle(fx.registry, userA, memA, 1_000L);
        injectHandle(fx.registry, userB, memB, 2_000L);

        // Simulate a client attempting to smuggle a cross-user hint via the authentication:
        // the principal name is the *authenticated* userId A; the adversarial value rides along as
        // an extra authority and as the request details. The registry must ignore all of it.
        bind(authenticatedWithAdversary(userA, adversary));

        for (int i = 0; i < 4; i++) {
            SpectorMemory resolved = fx.registry.resolveForCurrentRequest();
            assertThat(resolved)
                    .as("routing must key strictly off the authenticated userId, ignoring '%s'", adversary)
                    .isSameAs(memA);
            assertThat(resolved).as("must never cross into another user's memory").isNotSameAs(memB);
            assertThat(resolved).as("must never fall back to the shared instance").isNotSameAs(fx.shared);
        }
    }

    /**
     * Requirements 9.3, 11.2, 11.3 — {@link UserMemoryRegistry#resolveFor(String)} is a pure
     * function of {@code userId}: repeated resolution of the same authenticated user returns the
     * same cached instance, and interleaving resolution of a different user never perturbs it. The
     * adversarial value is threaded through the try only to demonstrate it has no channel into the
     * routing decision (the API accepts no such parameter).
     */
    @Property(tries = 300)
    void resolveForIsPureFunctionOfUserId(
            @ForAll("distinctUserPairs") String[] users,
            @ForAll("adversarialParams") String adversary) throws Exception {
        String userA = users[0];
        String userB = users[1];

        Fixture fx = newFixture();
        SpectorMemory memA = mock(SpectorMemory.class);
        SpectorMemory memB = mock(SpectorMemory.class);
        injectHandle(fx.registry, userA, memA, 1_000L);
        injectHandle(fx.registry, userB, memB, 2_000L);

        // Adversary is deliberately unused by the API — its presence in the try is the point:
        // there is no way to hand it to resolveFor(...). Reference it so the shrinker keeps it.
        Assume.that(adversary != null);

        assertThat(fx.registry.resolveFor(userA)).isSameAs(memA);
        assertThat(fx.registry.resolveFor(userB)).isSameAs(memB);
        // Interleaving B does not perturb A's routing, and neither ever returns the shared instance.
        assertThat(fx.registry.resolveFor(userA)).isSameAs(memA).isNotSameAs(fx.shared);
        assertThat(fx.registry.resolveFor(userB)).isSameAs(memB).isNotSameAs(fx.shared);
    }

    /**
     * Requirements 9.3, 11.2, 11.3 — the registry's routing key,
     * {@link StorageLayout#namespaceDirSharded(Path, String)}, isolates users at the path level:
     * for A != B the sharded dirs are distinct and neither is an ancestor of the other, each is a
     * strict descendant of the base (no traversal escape), and each terminates in its own userId.
     * This is the path-derivation guarantee the registry relies on for {@code buildInstance}.
     */
    @Property
    void shardedRoutingKeyIsolatesUsersAtPathLevel(@ForAll("distinctUserPairs") String[] users) {
        Path dirA = StorageLayout.namespaceDirSharded(base, users[0]).toAbsolutePath().normalize();
        Path dirB = StorageLayout.namespaceDirSharded(base, users[1]).toAbsolutePath().normalize();

        assertThat(dirA).isNotEqualTo(dirB);
        assertThat(dirA.startsWith(dirB)).as("A's dir must not be a descendant of B's").isFalse();
        assertThat(dirB.startsWith(dirA)).as("B's dir must not be a descendant of A's").isFalse();

        assertThat(dirA.startsWith(base)).as("A's dir must stay within the base").isTrue();
        assertThat(dirB.startsWith(base)).as("B's dir must stay within the base").isTrue();
        assertThat(dirA).isNotEqualTo(base);
        assertThat(dirB).isNotEqualTo(base);
        assertThat(dirA.getFileName().toString()).isEqualTo(users[0]);
        assertThat(dirB.getFileName().toString()).isEqualTo(users[1]);
    }

    /**
     * Requirements 9.3, 11.2, 11.3 — determinism of the routing key: repeated resolution of the
     * same userId yields byte-for-byte equal sharded paths, so the routing decision cannot drift
     * between requests regardless of any adversarial input in between.
     */
    @Property
    void shardedRoutingKeyIsDeterministic(@ForAll("validUserIds") String userId) {
        Path first = StorageLayout.namespaceDirSharded(base, userId);
        Path second = StorageLayout.namespaceDirSharded(base, userId);
        assertThat(first).isEqualTo(second);
    }

    /**
     * Requirements 9.3, 11.2, 11.3 — structural guarantee that the routing surface exposes no
     * client-controlled {@code namespace}/{@code workspace_id}/{@code agent_id} channel: the only
     * public method that accepts a parameter is {@code resolveFor(String userId)} with exactly one
     * {@code String} parameter (the authenticated userId), and {@code resolveForCurrentRequest()}
     * takes none. There is therefore no API path by which a client param could alter routing.
     */
    @Example
    void publicApiExposesNoNamespaceOrWorkspaceOrAgentParameter() {
        List<Method> publicMethodsWithParams = Stream.of(UserMemoryRegistry.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .filter(m -> m.getParameterCount() > 0)
                .toList();

        assertThat(publicMethodsWithParams)
                .as("only resolveFor(String) may take a parameter — no client-param routing channel")
                .hasSize(1);

        Method resolveFor = publicMethodsWithParams.get(0);
        assertThat(resolveFor.getName()).isEqualTo("resolveFor");
        assertThat(resolveFor.getParameterCount()).isEqualTo(1);
        assertThat(resolveFor.getParameterTypes()[0]).isEqualTo(String.class);
    }

    // ══════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════

    /** Registry-under-test paired with the shared instance it must never route to. */
    private record Fixture(UserMemoryRegistry registry, SpectorMemory shared) {}

    /** Builds an auth-enabled registry backed entirely by mocks (no live build path is taken). */
    private Fixture newFixture() {
        SpectorMemory shared = mock(SpectorMemory.class);
        ObjectProvider<SpectorMemory> sharedProvider = mockProvider();
        when(sharedProvider.getIfAvailable()).thenReturn(shared);
        ObjectProvider<EmbeddingProvider> embedderProvider = mockProvider();
        when(embedderProvider.getIfAvailable()).thenReturn(null);
        ObjectProvider<LlmProvider> textGenProvider = mockProvider();
        when(textGenProvider.getIfAvailable()).thenReturn(null);
        ObjectProvider<SalienceProfileProvider> salienceProvider = mockProvider();
        when(salienceProvider.getIfAvailable()).thenReturn(null);

        SpectorConfigProperties cfg = new SpectorConfigProperties();
        cfg.getMemory().setPersistencePath(base.toString());

        var auth = new SynapseProperties.AuthProperties(
                true, null, null, null, null, null, null, null);
        var synapse = new SynapseProperties(0, null, base.toString(), null, null, null, auth);

        UserMemoryRegistry registry = new UserMemoryRegistry(
                sharedProvider, synapse, cfg, embedderProvider, textGenProvider, salienceProvider, 512);
        return new Fixture(registry, shared);
    }

    /**
     * Builds an {@link Authentication} whose principal name is the authenticated {@code userId}
     * while an adversarial namespace/workspace/agent value rides along as an extra authority and as
     * the request details — modeling a client's attempt to smuggle a cross-user hint.
     */
    private static Authentication authenticatedWithAdversary(String userId, String adversary) {
        String safeAuthority = "NS_" + Integer.toHexString(adversary.hashCode());
        var token = new UsernamePasswordAuthenticationToken(
                userId, "credentials",
                AuthorityUtils.createAuthorityList("SCOPE_memory:read", safeAuthority));
        token.setDetails(adversary);
        return token;
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
}
