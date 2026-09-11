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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.kernel.storage.NamespacePathResolver;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.kernel.store.InsulaMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.InsulaSelfModel;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.runtime.SpectorRuntime;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.synapse.catalog.*;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.identity.IdentityPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for {@link NamespaceResolver} (Task 3.12, Req R10.6).
 *
 * <p>Validates path resolution under feature flags, cache key invariants (Task 3.4),
 * INSULA Region 24 fallback behavior with identity bundles (Task 3.7), layout marker
 * verification (Task 3.11), and identityRoot probing (Task 3.5 &amp; 3.6).</p>
 */
@DisplayName("NamespaceResolver — Comprehensive Unit & Contract Tests")
class NamespaceResolverTest {

    private static final String ALICE_ID = "018f2a3b4c5d6";
    private static final String BOB_ID = "018f2a3b4c5d7";
    private static final String TENANT_ACME = "acme";
    private static final String SHARED_NS_ID = "018f2a3b4c5d8";
    private static final String SHARED_SLUG = "shared-proj";

    @TempDir
    Path tempDir;

    private Path basePath;
    private Path dataDir;
    private AccountCatalog catalog;
    private SynapseProperties synapseProps;
    private EmbeddingProvider embedder;
    private SpectorRuntime runtime;
    private SpectorMemory mockMemory;
    private SpectorMemoryAdmin mockAdmin;
    private InsulaMemory mockInsula;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        basePath = tempDir.resolve("cognitive");
        dataDir = tempDir.resolve("data");
        objectMapper = new ObjectMapper();

        catalog = mock(AccountCatalog.class);
        embedder = mock(EmbeddingProvider.class);
        runtime = mock(SpectorRuntime.class);
        mockMemory = mock(SpectorMemory.class);
        mockAdmin = mock(SpectorMemoryAdmin.class);
        mockInsula = mock(InsulaMemory.class);

        when(mockMemory.admin()).thenReturn(mockAdmin);
        when(mockAdmin.insularCortex()).thenReturn(mockInsula);
        when(mockInsula.get()).thenReturn(Optional.empty());

        when(runtime.attach(anyString(), any())).thenReturn(mockMemory);

        synapseProps = new SynapseProperties();
        synapseProps.setDataDir(dataDir.toString());
        synapseProps.getMemory().setPersistencePath(basePath.toString());
        synapseProps.getNamespace().getTenantRooted().setEnabled(false);
    }

    @AfterEach
    void tearDown() {
        // clean
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> providerOf(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bean);
        when(provider.getIfAvailable(any())).thenAnswer(inv -> bean != null ? bean : ((java.util.function.Supplier<T>) inv.getArgument(0)).get());
        return provider;
    }

    private NamespaceResolver createResolver() {
        NamespaceResolver resolver = new NamespaceResolver(
                catalog,
                synapseProps,
                providerOf(embedder),
                providerOf(null),
                providerOf(null),
                providerOf(objectMapper),
                providerOf(null),
                providerOf(null),
                providerOf(null),
                providerOf(null),
                providerOf(null),
                100
        );
        resolver.setRuntime(runtime);
        return resolver;
    }

    @Test
    @DisplayName("Task 3.1 & 3.2: With flag OFF, tenanted account resolves to flat sharded path (Layout A)")
    void flagOff_resolvesFlatShardedLayout() {
        synapseProps.getNamespace().getTenantRooted().setEnabled(false);
        Account alice = new Account(ALICE_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                ALICE_ID, AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO),
                AccountFlags.forProfile(AccountProfile.HUMAN_SOLO),
                ALICE_ID, Instant.now(), TENANT_ACME, false);

        when(catalog.getOrCreateAccount(ALICE_ID)).thenReturn(alice);

        try (NamespaceResolver resolver = createResolver()) {
            SpectorMemory memory = resolver.resolve(ALICE_ID);
            assertSame(mockMemory, memory);

            Path expectedDir = StoragePaths.namespaceDirSharded(basePath, ALICE_ID);
            ArgumentCaptor<Consumer<SpectorMemoryBuilder>> captor = ArgumentCaptor.forClass(Consumer.class);
            verify(runtime).attach(eq(ALICE_ID), captor.capture());

            SpectorMemoryBuilder builder = mock(SpectorMemoryBuilder.class);
            captor.getValue().accept(builder);
            verify(builder).persistence(expectedDir);

            // Layout marker in namespace.json should record FLAT_SHA256
            Path marker = expectedDir.resolve(StoragePaths.FILE_NAMESPACE);
            assertThat(marker).exists();
            try {
                var json = objectMapper.readTree(marker.toFile());
                assertThat(json.get("layout").asText()).isEqualTo(NamespacePathResolver.Layout.FLAT_SHA256.id());
                assertThat(json.get("pathHelper").asText()).isEqualTo(NamespacePathResolver.Layout.FLAT_SHA256.id());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    @DisplayName("Task 3.1 & 3.2: With flag ON, tenanted account resolves to tenant-rooted path (Layout B)")
    void flagOn_tenantedAccount_resolvesTenantRootedLayout() {
        synapseProps.getNamespace().getTenantRooted().setEnabled(true);
        Account alice = new Account(ALICE_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                ALICE_ID, AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO),
                AccountFlags.forProfile(AccountProfile.HUMAN_SOLO),
                ALICE_ID, Instant.now(), TENANT_ACME, false);

        when(catalog.getOrCreateAccount(ALICE_ID)).thenReturn(alice);

        try (NamespaceResolver resolver = createResolver()) {
            SpectorMemory memory = resolver.resolve(ALICE_ID);
            assertSame(mockMemory, memory);

            Path expectedDir = StoragePaths.tenantRootedNamespaceDir(basePath, TENANT_ACME, ALICE_ID);
            ArgumentCaptor<Consumer<SpectorMemoryBuilder>> captor = ArgumentCaptor.forClass(Consumer.class);
            verify(runtime).attach(eq(ALICE_ID), captor.capture());

            SpectorMemoryBuilder builder = mock(SpectorMemoryBuilder.class);
            captor.getValue().accept(builder);
            verify(builder).persistence(expectedDir);

            // Layout marker in namespace.json should record TENANT_SHA256 and tenantId
            Path marker = expectedDir.resolve(StoragePaths.FILE_NAMESPACE);
            assertThat(marker).exists();
            try {
                var json = objectMapper.readTree(marker.toFile());
                assertThat(json.get("layout").asText()).isEqualTo(NamespacePathResolver.Layout.TENANT_SHA256.id());
                assertThat(json.get("tenantId").asText()).isEqualTo(TENANT_ACME);
                assertThat(json.get("namespaceId").asText()).isEqualTo(ALICE_ID);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    @DisplayName("Task 3.1 & 3.2: With flag ON, untenanted account (null tenantId) resolves to flat sharded path (Layout A)")
    void flagOn_untenantedAccount_resolvesFlatShardedLayout() {
        synapseProps.getNamespace().getTenantRooted().setEnabled(true);
        Account alice = new Account(ALICE_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                ALICE_ID, AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO),
                AccountFlags.forProfile(AccountProfile.HUMAN_SOLO),
                ALICE_ID, Instant.now(), null, false);

        when(catalog.getOrCreateAccount(ALICE_ID)).thenReturn(alice);

        try (NamespaceResolver resolver = createResolver()) {
            SpectorMemory memory = resolver.resolve(ALICE_ID);
            assertSame(mockMemory, memory);

            Path expectedDir = StoragePaths.namespaceDirSharded(basePath, ALICE_ID);
            ArgumentCaptor<Consumer<SpectorMemoryBuilder>> captor = ArgumentCaptor.forClass(Consumer.class);
            verify(runtime).attach(eq(ALICE_ID), captor.capture());

            SpectorMemoryBuilder builder = mock(SpectorMemoryBuilder.class);
            captor.getValue().accept(builder);
            verify(builder).persistence(expectedDir);
        }
    }

    @Test
    @DisplayName("Task 3.4 / Req R1.4, I4: Cache key is namespaceId only; two principals resolve same SHARED instance")
    void cacheKeyIsNamespaceId_singleSharedInstanceReturnedForMultiplePrincipals() {
        Account alice = new Account(ALICE_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                ALICE_ID, AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO),
                AccountFlags.forProfile(AccountProfile.HUMAN_SOLO),
                ALICE_ID, Instant.now(), TENANT_ACME, false);

        Account bob = new Account(BOB_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                BOB_ID, AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO),
                AccountFlags.forProfile(AccountProfile.HUMAN_SOLO),
                BOB_ID, Instant.now(), TENANT_ACME, false);

        NamespaceRecord sharedNs = new NamespaceRecord(SHARED_NS_ID, SHARED_SLUG, ALICE_ID,
                NamespaceType.PROJECT, NamespaceStatus.ACTIVE, "Shared", "Shared NS", null,
                Instant.now(), Instant.now(), false);

        when(catalog.getOrCreateAccount(ALICE_ID)).thenReturn(alice);
        when(catalog.getOrCreateAccount(BOB_ID)).thenReturn(bob);
        when(catalog.resolve(ALICE_ID, SHARED_SLUG)).thenReturn(Optional.of(sharedNs));
        when(catalog.resolve(BOB_ID, SHARED_SLUG)).thenReturn(Optional.of(sharedNs));

        try (NamespaceResolver resolver = createResolver()) {
            SpectorMemory memAlice = resolver.resolve(ALICE_ID, SHARED_SLUG);
            SpectorMemory memBob = resolver.resolve(BOB_ID, SHARED_SLUG);

            // Single shared instance reference across both principals
            assertSame(memAlice, memBob);
            // Engine attach only called once
            verify(runtime, times(1)).attach(eq(SHARED_NS_ID), any());
            // Hot cache contains exactly 1 instance under SHARED_NS_ID
            assertThat(resolver.isHot(SHARED_NS_ID)).isTrue();
            assertThat(resolver.cachedInstanceCount()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Task 3.5, 3.6, 3.7 / Req R7.4: When identity bundle exists, INSULA Region 24 fallback does NOT run")
    void insulaFallback_whenIdentityBundlePresent_doesNotRunFallback() throws IOException {
        Account alice = new Account(ALICE_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                ALICE_ID, AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO),
                AccountFlags.forProfile(AccountProfile.HUMAN_SOLO),
                ALICE_ID, Instant.now(), null, false);
        when(catalog.getOrCreateAccount(ALICE_ID)).thenReturn(alice);

        // Pre-create account identity bundle under identityRoot
        Path bundlePath = IdentityPaths.accountIdentityBundle(dataDir, ALICE_ID);
        Files.createDirectories(bundlePath.getParent());
        Files.writeString(bundlePath, "dummy-bundle");

        // Mock insular cortex returning data
        InsulaSelfModel model = new InsulaSelfModel("HUMAN", null, SalienceProfile.builder().build(), Map.of());
        when(mockInsula.get()).thenReturn(Optional.of(objectMapper.writeValueAsBytes(model)));

        try (NamespaceResolver resolver = createResolver()) {
            resolver.resolve(ALICE_ID);
            // Because bundle exists, INSULA fallback must not restore salience profile
            verify(mockMemory, never()).setSalienceProfile(any());
        }
    }

    @Test
    @DisplayName("Task 3.5, 3.6, 3.7 / Req R7.4: When identity bundle is absent, INSULA Region 24 fallback runs")
    void insulaFallback_whenIdentityBundleAbsent_runsFallback() throws IOException {
        Account alice = new Account(ALICE_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                ALICE_ID, AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO),
                AccountFlags.forProfile(AccountProfile.HUMAN_SOLO),
                ALICE_ID, Instant.now(), null, false);
        when(catalog.getOrCreateAccount(ALICE_ID)).thenReturn(alice);

        // No bundle file created

        // Mock insular cortex returning data
        InsulaSelfModel model = new InsulaSelfModel("HUMAN", null, SalienceProfile.builder().build(), Map.of());
        when(mockInsula.get()).thenReturn(Optional.of(objectMapper.writeValueAsBytes(model)));

        try (NamespaceResolver resolver = createResolver()) {
            resolver.resolve(ALICE_ID);
            // Because bundle is absent, INSULA fallback must restore salience profile
            verify(mockMemory, times(1)).setSalienceProfile(any(SalienceProfile.class));
        }
    }

    @Test
    @DisplayName("Task 3.6: Tenanted account probes tenantAccountIdentityBundle under identityRoot")
    void insulaFallback_tenantedAccount_probesTenantAccountIdentityBundle() throws IOException {
        synapseProps.getNamespace().getTenantRooted().setEnabled(true);
        Account alice = new Account(ALICE_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                ALICE_ID, AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO),
                AccountFlags.forProfile(AccountProfile.HUMAN_SOLO),
                ALICE_ID, Instant.now(), TENANT_ACME, false);
        when(catalog.getOrCreateAccount(ALICE_ID)).thenReturn(alice);

        // Pre-create tenanted account identity bundle under identityRoot
        Path bundlePath = IdentityPaths.tenantAccountIdentityBundle(dataDir, TENANT_ACME, ALICE_ID);
        Files.createDirectories(bundlePath.getParent());
        Files.writeString(bundlePath, "dummy-tenanted-bundle");

        InsulaSelfModel model = new InsulaSelfModel("HUMAN", null, SalienceProfile.builder().build(), Map.of());
        when(mockInsula.get()).thenReturn(Optional.of(objectMapper.writeValueAsBytes(model)));

        try (NamespaceResolver resolver = createResolver()) {
            resolver.resolve(ALICE_ID);
            // Bundle was present at tenantAccountIdentityBundle, so fallback did NOT run
            verify(mockMemory, never()).setSalienceProfile(any());
        }
    }

    @Test
    @DisplayName("Task 3.11 / Req R8.2: Tampered layout marker throws IllegalStateException naming expected and found")
    void layoutMarkerMismatch_throwsIllegalStateExceptionWithExpectedAndFound() throws IOException {
        synapseProps.getNamespace().getTenantRooted().setEnabled(false);
        Account alice = new Account(ALICE_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                ALICE_ID, AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO),
                AccountFlags.forProfile(AccountProfile.HUMAN_SOLO),
                ALICE_ID, Instant.now(), null, false);
        when(catalog.getOrCreateAccount(ALICE_ID)).thenReturn(alice);

        // Pre-create namespace dir with mismatched layout marker
        Path dir = StoragePaths.namespaceDirSharded(basePath, ALICE_ID);
        Files.createDirectories(dir);
        Path marker = dir.resolve(StoragePaths.FILE_NAMESPACE);
        String mismatchedMarker = """
                {
                  "layout": "StoragePaths.tenantRootedNamespaceDir",
                  "namespaceId": "%s"
                }
                """.formatted(ALICE_ID);
        Files.writeString(marker, mismatchedMarker);

        try (NamespaceResolver resolver = createResolver()) {
            assertThatThrownBy(() -> resolver.resolve(ALICE_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Namespace layout mismatch")
                    .hasMessageContaining("expected")
                    .hasMessageContaining("StoragePaths.namespaceDirSharded")
                    .hasMessageContaining("found")
                    .hasMessageContaining("StoragePaths.tenantRootedNamespaceDir");
        }
    }

    @Test
    @DisplayName("Task 3.11 / Req R8.1: Missing layout in namespace.json infers FLAT_SHA256 and opens on flag off")
    void layoutMarkerMissing_infersFlatLayout() throws IOException {
        synapseProps.getNamespace().getTenantRooted().setEnabled(false);
        Account alice = new Account(ALICE_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                ALICE_ID, AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO),
                AccountFlags.forProfile(AccountProfile.HUMAN_SOLO),
                ALICE_ID, Instant.now(), null, false);
        when(catalog.getOrCreateAccount(ALICE_ID)).thenReturn(alice);

        // Pre-create namespace dir with legacy marker (no layout field)
        Path dir = StoragePaths.namespaceDirSharded(basePath, ALICE_ID);
        Files.createDirectories(dir);
        Path marker = dir.resolve(StoragePaths.FILE_NAMESPACE);
        String legacyMarker = """
                {
                  "id": "%s",
                  "display_name": "Legacy Project"
                }
                """.formatted(ALICE_ID);
        Files.writeString(marker, legacyMarker);

        try (NamespaceResolver resolver = createResolver()) {
            SpectorMemory mem = resolver.resolve(ALICE_ID);
            assertSame(mockMemory, mem);
        }
    }

    @Test
    @DisplayName("Eviction removes namespace from hot cache and closes it")
    void evict_removesFromCacheAndCloses() {
        Account alice = new Account(ALICE_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                ALICE_ID, AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO),
                AccountFlags.forProfile(AccountProfile.HUMAN_SOLO),
                ALICE_ID, Instant.now(), null, false);
        when(catalog.getOrCreateAccount(ALICE_ID)).thenReturn(alice);

        try (NamespaceResolver resolver = createResolver()) {
            resolver.resolve(ALICE_ID);
            assertThat(resolver.isHot(ALICE_ID)).isTrue();

            resolver.evict(ALICE_ID);
            assertThat(resolver.isHot(ALICE_ID)).isFalse();
            verify(mockMemory, times(1)).close();
        }
    }

    @Test
    @DisplayName("basePath and identityRoot derive correct distinct paths")
    void basePathAndIdentityRootDerivedCorrectly() {
        try (NamespaceResolver resolver = createResolver()) {
            assertThat(resolver.basePath()).isEqualTo(basePath);
            assertThat(resolver.identityRoot()).isEqualTo(dataDir);
        }
    }
}
