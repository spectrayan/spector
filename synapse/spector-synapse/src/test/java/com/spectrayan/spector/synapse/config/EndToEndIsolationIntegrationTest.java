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
package com.spectrayan.spector.synapse.config;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.spectrayan.spector.memory.DataLayoutVersion;
import com.spectrayan.spector.memory.LayoutMigrator;
import com.spectrayan.spector.memory.SalienceProfileProvider;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.StorageLayout;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.spring.autoconfigure.SpectorConfigProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.DefaultAdminProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.JwtProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.OidcProperties;
import com.spectrayan.spector.synapse.memory.UserMemoryRegistry;
import com.spectrayan.spector.synapse.security.UserAccountStore;
import com.sun.net.httpserver.HttpServer;

/**
 * End-to-end isolation integration test tying together the three isolation guarantees that make
 * multi-user Spector safe to enable: per-user memory routing, external-OIDC token acceptance/
 * rejection, and startup-gated, idempotent layout migration.
 *
 * <h3>Coverage and the layer at which each guarantee is asserted</h3>
 * <p>Per the design's testing strategy, this "heaviest" test is deliberately split across the
 * cheapest reliable seams rather than forced into one live {@code @SpringBootTest}. Each section
 * documents the layer at which it asserts and why.</p>
 *
 * <ul>
 *   <li><b>Per-user memory isolation (Reqs 8.2, 9.2)</b> — asserted at the
 *       <em>routing + path</em> layer. Standing up two live {@link SpectorMemory} engines (off-heap
 *       storage, native access, an embedding provider) and performing a real write/recall is
 *       heavyweight and flaky in a unit/integration harness, and {@code UserMemoryRegistry} is a
 *       {@code final} class whose per-user instances are built by a private heavyweight builder.
 *       Instead we assert the two properties that <em>guarantee</em> "write as A → recall as B
 *       returns nothing": (1) {@link StorageLayout#namespaceDirSharded(Path, String)} resolves two
 *       distinct, non-overlapping directories for two distinct users so their on-disk state can
 *       never coincide, and (2) {@link UserMemoryRegistry} returns a distinct cached instance per
 *       authenticated principal and never crosses — so a write routed through A's instance is
 *       physically unreachable through B's instance. Mocks are injected into the registry's private
 *       cache via reflection exactly as in {@code UserMemoryRegistryTest}.</li>
 *   <li><b>External OIDC (Reqs 4.1, 4.4)</b> — asserted end-to-end against the production
 *       {@code JwtDecoderConfig.oidcJwtDecoder} using an in-process {@link HttpServer} serving a
 *       real in-memory RSA JWKS (the same harness pattern as {@code JwtDecoderConfigTest}). A token
 *       signed by the served key with the configured issuer is accepted; a wrong-issuer token and
 *       an expired token are rejected.</li>
 *   <li><b>Migration (Reqs 17.1, 17.4)</b> — asserted both at the {@link LayoutMigrator} layer
 *       (a seeded flat layout is relocated into the default user's namespace, double-run is a no-op,
 *       and the stored version is monotonic) and at the {@link AuthStartupInitializer} layer (the
 *       migration runs only when {@code spector.auth.enabled=true} and leaves the flat layout
 *       untouched — no namespace directory, no version advance, no admin seeding — when disabled).
 *       </li>
 * </ul>
 */
@DisplayName("End-to-end multi-user isolation — memory routing, OIDC, and migration")
class EndToEndIsolationIntegrationTest {

    /** 13-char TSID-like principals; valid namespace ids (no illegal characters). */
    private static final String USER_A = "USER0000000AA";
    private static final String USER_B = "USER0000000BB";

    @TempDir
    Path dataRoot;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ══════════════════════════════════════════════════════════════════════════════════
    // Per-user memory isolation — Reqs 8.2 (filesystem), 9.2 (routing)
    // Asserted at the routing + path layer (see class Javadoc for rationale).
    // ══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Per-user memory isolation (routing + path layer)")
    class MemoryIsolation {

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

        @Test
        @DisplayName("Req 8.2: distinct users resolve distinct, non-overlapping sharded directories under the base")
        void distinctUsersResolveDistinctShardedDirectories() {
            Path dirA = StorageLayout.namespaceDirSharded(dataRoot, USER_A);
            Path dirB = StorageLayout.namespaceDirSharded(dataRoot, USER_B);

            assertThat(dirA).isNotEqualTo(dirB);
            // Neither path is an ancestor of the other — no user's tree contains another's.
            assertThat(dirA.startsWith(dirB)).isFalse();
            assertThat(dirB.startsWith(dirA)).isFalse();
            // Both are strict descendants of the configured base (no traversal escape).
            assertThat(dirA.startsWith(dataRoot)).isTrue();
            assertThat(dirB.startsWith(dataRoot)).isTrue();
            assertThat(dirA).isNotEqualTo(dataRoot);
            assertThat(dirB).isNotEqualTo(dataRoot);
        }

        @Test
        @DisplayName("Req 9.2: writing as A then recalling as B cannot cross — each principal routes to its own instance")
        void writeAsARecallAsBReturnsNothing() throws Exception {
            UserMemoryRegistry registry = buildRegistry(true, 512);
            // Two physically distinct per-user instances rooted at the two distinct sharded dirs.
            SpectorMemory memA = mock(SpectorMemory.class);
            SpectorMemory memB = mock(SpectorMemory.class);
            injectHandle(registry, USER_A, memA, 1_000L);
            injectHandle(registry, USER_B, memB, 2_000L);

            // "login as A → write as A": the request thread bound to A resolves ONLY A's instance.
            bind(authenticated(USER_A, "SCOPE_memory:write"));
            SpectorMemory resolvedForA = registry.resolveForCurrentRequest();
            assertThat(resolvedForA).isSameAs(memA);

            // "login as B → recall as B": the request thread bound to B resolves ONLY B's instance.
            SecurityContextHolder.clearContext();
            bind(authenticated(USER_B, "SCOPE_memory:read"));
            SpectorMemory resolvedForB = registry.resolveForCurrentRequest();
            assertThat(resolvedForB).isSameAs(memB);

            // The two principals never share an instance, so B can never observe A's write — the
            // isolation boundary is the distinct instance rooted at a distinct sharded directory.
            assertThat(resolvedForB).isNotSameAs(resolvedForA);
            // Neither user is ever routed to the shared/anonymous instance while authenticated.
            assertThat(resolvedForA).isNotSameAs(shared);
            assertThat(resolvedForB).isNotSameAs(shared);
            assertThat(registry.cachedInstanceCount()).isEqualTo(2);
        }

        private UserMemoryRegistry buildRegistry(boolean authEnabled, int maxInstances) {
            when(embedderProvider.getIfAvailable()).thenReturn(null);
            SpectorConfigProperties cfg = new SpectorConfigProperties();
            cfg.getMemory().setPersistencePath(dataRoot.toString());
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
            var auth = new AuthProperties(authEnabled, null, null, null, null, null, null, null);
            return new SynapseProperties(0, null, dataRoot.toString(), null, null, null, auth);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════════
    // External OIDC — Reqs 4.1 (JWKS-verified acceptance), 4.4 (wrong-issuer/expired rejection)
    // Asserted end-to-end against the production decoder + a live in-process JWKS.
    // ══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("External OIDC RS256 via a live in-process JWKS")
    class Oidc {

        private static final String OIDC_ISSUER = "https://idp.example.com/";
        private static final String SERVER_SECRET = "test-secret-that-is-long-enough-0123456789";
        private static final String SUB = "USER0000000AB";

        private final JwtDecoderConfig config = new JwtDecoderConfig();

        private RSAKey rsaKey;
        private HttpServer jwksServer;
        private String jwksUrl;

        @BeforeEach
        void startJwks() throws Exception {
            rsaKey = new RSAKeyGenerator(2048).keyID("e2e-key-1").generate();
            String jwksJson = new JWKSet(rsaKey.toPublicJWK()).toString();

            jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            jwksServer.createContext("/jwks", exchange -> {
                byte[] body = jwksJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            });
            jwksServer.start();
            jwksUrl = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks";
        }

        @AfterEach
        void stopJwks() {
            if (jwksServer != null) {
                jwksServer.stop(0);
            }
        }

        @Test
        @DisplayName("Req 4.1: a token whose signing key is served by the stub JWKS is accepted")
        void acceptsValidStubJwksToken() throws Exception {
            String token = mintRs256(rsaKey, OIDC_ISSUER, SUB, future());

            Jwt decoded = oidcDecoder(jwksUrl).decode(token);

            assertThat(decoded.getSubject()).isEqualTo(SUB);
            assertThat(decoded.getIssuer()).hasToString(OIDC_ISSUER);
        }

        @Test
        @DisplayName("Req 4.4: a token whose issuer is not oidc.issuer is rejected")
        void rejectsWrongIssuerToken() throws Exception {
            String token = mintRs256(rsaKey, "https://evil-idp.example.com/", SUB, future());

            assertThatThrownBy(() -> oidcDecoder(jwksUrl).decode(token))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("Req 4.4: a token expired beyond the 60s clock-skew is rejected")
        void rejectsExpiredToken() throws Exception {
            String token = mintRs256(rsaKey, OIDC_ISSUER, SUB,
                    Instant.now().minus(120, ChronoUnit.SECONDS));

            assertThatThrownBy(() -> oidcDecoder(jwksUrl).decode(token))
                    .isInstanceOf(JwtException.class);
        }

        private JwtDecoder oidcDecoder(String jwksUrlValue) {
            AuthProperties auth = new AuthProperties(
                    true,
                    new JwtProperties(SERVER_SECRET, Duration.ofHours(1)),
                    null,
                    new OidcProperties(jwksUrlValue, OIDC_ISSUER),
                    null, null, null, null);
            return config.oidcJwtDecoder(auth);
        }

        private static String mintRs256(RSAKey signingKey, String issuer, String subject,
                                        Instant expiry) throws Exception {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(subject)
                    .issueTime(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
                    .expirationTime(Date.from(expiry))
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                    claims);
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        }

        private static Instant future() {
            return Instant.now().plus(1, ChronoUnit.HOURS);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════════
    // Migration — Reqs 17.1 (runs only when enabled), 17.4 (idempotent double-run)
    // Asserted at the LayoutMigrator layer AND the AuthStartupInitializer enabled-gating layer.
    // ══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Layout migration (enabled-gated and idempotent)")
    class Migration {

        /** The anonymous / default principal id the flat layout is relocated into. */
        private static final String DEFAULT_USER_ID = "default";

        /** Known flat-layout contents: relative path (below the data root) -> byte content. */
        private static Map<String, byte[]> flatFixture() {
            Map<String, byte[]> files = new LinkedHashMap<>();
            files.put("runtime/index.midx", new byte[] {0, 1, 2, 3, 4, 5, 6, 7, (byte) 0xFF});
            files.put("runtime/hebbian.graph", "hebbian-graph".getBytes(StandardCharsets.UTF_8));
            files.put("partitions/semantic.mem", new byte[] {10, 20, 30, 40, 50, 60});
            files.put("partitions/text.dat", new byte[] {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF});
            return files;
        }

        private void seedFlatLayout(Map<String, byte[]> files) throws IOException {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                Path target = dataRoot.resolve(e.getKey());
                Files.createDirectories(target.getParent());
                Files.write(target, e.getValue());
            }
        }

        private Path defaultNamespaceRoot() {
            return StorageLayout.namespaceDirSharded(dataRoot, DEFAULT_USER_ID);
        }

        @Test
        @DisplayName("Req 17.1: a flat layout is migrated into the default namespace and Req 17.4: double-run is a no-op")
        void migrationRelocatesFlatLayoutAndIsIdempotent() throws IOException {
            Map<String, byte[]> files = flatFixture();
            seedFlatLayout(files);
            assertThat(DataLayoutVersion.read(dataRoot)).isEqualTo(DataLayoutVersion.LEGACY_FLAT);

            // First run relocates the flat runtime/+partitions/ into namespaces/AA/BB/default/.
            LayoutMigrator.migrateIfNeeded(dataRoot, DEFAULT_USER_ID);

            Path ns = defaultNamespaceRoot();
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                Path dest = ns.resolve(e.getKey());
                assertThat(dest).as("migrated file %s", e.getKey()).isRegularFile();
                assertThat(Files.readAllBytes(dest)).isEqualTo(e.getValue());
            }
            assertThat(DataLayoutVersion.read(dataRoot)).isEqualTo(DataLayoutVersion.CURRENT);

            // Capture the migrated tree to prove the second run changes nothing.
            Map<String, byte[]> afterFirst = snapshotNamespace(ns);

            // Second run is a no-op: version already CURRENT, tree byte-for-byte identical.
            LayoutMigrator.migrateIfNeeded(dataRoot, DEFAULT_USER_ID);

            assertThat(DataLayoutVersion.read(dataRoot))
                    .as("version must be monotonic and never decrease")
                    .isEqualTo(DataLayoutVersion.CURRENT);
            assertThat(snapshotNamespace(ns))
                    .as("double-run must equal single-run (idempotent)")
                    .containsExactlyInAnyOrderEntriesOf(afterFirst);
        }

        @Test
        @DisplayName("Req 17.1: AuthStartupInitializer does NOT migrate when auth is disabled")
        void startupInitializerLeavesFlatLayoutUntouchedWhenDisabled() throws IOException {
            seedFlatLayout(flatFixture());
            UserAccountStore accountStore = mock(UserAccountStore.class);
            AuthStartupInitializer initializer = initializer(false, accountStore);

            initializer.initializeAuth();

            // No per-user namespace directory is created; the flat layout and version are untouched.
            assertThat(defaultNamespaceRoot()).doesNotExist();
            assertThat(StorageLayout.namespacesDir(dataRoot)).doesNotExist();
            assertThat(DataLayoutVersion.read(dataRoot)).isEqualTo(DataLayoutVersion.LEGACY_FLAT);
            // Disabled ⇒ no default-admin seeding either.
            verify(accountStore, never()).seedDefaultAdmin(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("Req 17.1 + 17.4: AuthStartupInitializer migrates once when enabled and re-runs are no-ops")
        void startupInitializerMigratesWhenEnabledAndIsIdempotent() throws IOException {
            seedFlatLayout(flatFixture());
            UserAccountStore accountStore = mock(UserAccountStore.class);
            AuthStartupInitializer initializer = initializer(true, accountStore);

            initializer.initializeAuth();

            assertThat(defaultNamespaceRoot()).isDirectory();
            assertThat(DataLayoutVersion.read(dataRoot)).isEqualTo(DataLayoutVersion.CURRENT);
            verify(accountStore, times(1)).seedDefaultAdmin("admin-secret");

            Map<String, byte[]> afterFirst = snapshotNamespace(defaultNamespaceRoot());

            // Re-running startup initialization is idempotent: no version change, no tree change.
            initializer.initializeAuth();

            assertThat(DataLayoutVersion.read(dataRoot)).isEqualTo(DataLayoutVersion.CURRENT);
            assertThat(snapshotNamespace(defaultNamespaceRoot()))
                    .containsExactlyInAnyOrderEntriesOf(afterFirst);
            // Seeding is idempotent at the store level; the initializer invokes it each ready event.
            verify(accountStore, times(2)).seedDefaultAdmin("admin-secret");
        }

        private AuthStartupInitializer initializer(boolean authEnabled, UserAccountStore accountStore) {
            var auth = new AuthProperties(
                    authEnabled, null, null, null,
                    new DefaultAdminProperties("admin-secret"),
                    null, null, null);
            var synapseProps = new SynapseProperties(0, null, dataRoot.toString(), null, null, null, auth);
            SpectorConfigProperties cfg = new SpectorConfigProperties();
            cfg.getMemory().setPersistencePath(dataRoot.toString());
            return new AuthStartupInitializer(synapseProps, cfg, accountStore);
        }

        /** Reads every regular file under {@code root} into a relative-path -> bytes map. */
        private static Map<String, byte[]> snapshotNamespace(Path root) throws IOException {
            Map<String, byte[]> out = new LinkedHashMap<>();
            if (!Files.exists(root)) {
                return out;
            }
            try (var stream = Files.walk(root)) {
                for (Path p : (Iterable<Path>) stream::iterator) {
                    if (Files.isRegularFile(p)) {
                        out.put(root.relativize(p).toString(), Files.readAllBytes(p));
                    }
                }
            }
            return out;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════════
    // Shared helpers
    // ══════════════════════════════════════════════════════════════════════════════════

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

    // --- reflection into the registry's private cache / handle (mirrors UserMemoryRegistryTest) --

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
