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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.kernel.storage.NamespacePathResolver;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.kernel.store.InsulaMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.runtime.SpectorRuntime;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.synapse.catalog.*;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceNotFoundException;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ADR-0033 KI-5 Exit-Criteria Integration Tests (Task 4.8, Reqs R10.1–R10.4, R9).
 *
 * <p>Verifies the end-to-end multi-tenant cell HA layout guarantees:
 * <ul>
 *   <li>R10.1: Alice in tenant acme creates AGENT slugs jira and docs -> two distinct directories under tenant root.</li>
 *   <li>R10.2: Bob with a READER grant opens the same directory and shares the same SpectorMemory instance (I4).</li>
 *   <li>R10.3 &amp; R9: Tenant-prefix delete wipes Alice's rememberers while another tenant still opens and identity plane is untouched.</li>
 *   <li>R10.4: SHARED namespace with no single parent account resolves correctly (rules out account-nesting, KI-4).</li>
 * </ul>
 * </p>
 */
@DisplayName("Task 4.8: ADR-0033 KI-5 Exit Criteria Integration Tests")
class CellHaNamespaceExitCriteriaIntegrationTest {

    private static final String TENANT_ACME = "018f9b8c000070008000000000000001";
    private static final String TENANT_GLOBEX = "018f9b8c000070008000000000000009";

    private static final String ALICE_ID = "018f9b8c000070008000000000000002";
    private static final String BOB_ID = "018f9b8c000070008000000000000003";

    private static final String NS_JIRA = "018f9b8c000070008000000000000010";
    private static final String NS_DOCS = "018f9b8c000070008000000000000020";
    private static final String NS_SHARED = "018f9b8c000070008000000000000030";
    private static final String NS_GLOBEX = "018f9b8c000070008000000000000090";

    @TempDir
    Path tempDir;

    private Path basePath;
    private Path dataDir;
    private AccountCatalog catalog;
    private SynapseProperties synapseProps;
    private EmbeddingProvider embedder;
    private SpectorRuntime runtime;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        basePath = tempDir.resolve("cognitive");
        dataDir = tempDir.resolve("data");
        objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        catalog = mock(AccountCatalog.class);
        embedder = mock(EmbeddingProvider.class);
        runtime = mock(SpectorRuntime.class);

        // Mock SpectorRuntime.attach to build unique mock SpectorMemory instances per namespaceId
        when(runtime.attach(anyString(), any())).thenAnswer(invocation -> {
            String nsId = invocation.getArgument(0);
            Consumer<SpectorMemoryBuilder> configurer = invocation.getArgument(1);

            SpectorMemory mockMemory = mock(SpectorMemory.class);
            SpectorMemoryAdmin mockAdmin = mock(SpectorMemoryAdmin.class);
            InsulaMemory mockInsula = mock(InsulaMemory.class);

            when(mockMemory.admin()).thenReturn(mockAdmin);
            when(mockAdmin.insularCortex()).thenReturn(mockInsula);
            when(mockInsula.get()).thenReturn(Optional.empty());

            // Run configurer against mock builder so persistence path is captured & layout marker is written
            SpectorMemoryBuilder builder = mock(SpectorMemoryBuilder.class);
            configurer.accept(builder);

            return mockMemory;
        });

        synapseProps = new SynapseProperties();
        synapseProps.setDataDir(dataDir.toString());
        synapseProps.getMemory().setPersistencePath(basePath.toString());
        synapseProps.getNamespace().getTenantRooted().setEnabled(true);
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

    private Account createAccount(String accountId, String tenantId) {
        return new Account(
                accountId, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                "Account " + accountId, AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO),
                AccountFlags.forProfile(AccountProfile.HUMAN_SOLO),
                accountId, Instant.now(), tenantId, false
        );
    }

    @Test
    @DisplayName("R10.1: Alice in tenant acme creates AGENT slugs jira and docs -> distinct dirs under tenant-rooted path")
    void r10_1_aliceCreatesAgentSlugs_filesLandUnderTenantRootedPath_distinctDirectories() throws IOException {
        Account alice = createAccount(ALICE_ID, TENANT_ACME);
        when(catalog.getOrCreateAccount(ALICE_ID)).thenReturn(alice);

        NamespaceRecord jiraRecord = new NamespaceRecord(
                NS_JIRA, "jira", ALICE_ID, NamespaceType.AGENT, NamespaceStatus.ACTIVE,
                "Jira Agent", "Jira tickets memory", null, Instant.now(), Instant.now()
        );
        NamespaceRecord docsRecord = new NamespaceRecord(
                NS_DOCS, "docs", ALICE_ID, NamespaceType.AGENT, NamespaceStatus.ACTIVE,
                "Docs Agent", "Documentation memory", null, Instant.now(), Instant.now()
        );

        when(catalog.resolve(ALICE_ID, "jira")).thenReturn(Optional.of(jiraRecord));
        when(catalog.resolve(ALICE_ID, "docs")).thenReturn(Optional.of(docsRecord));

        try (NamespaceResolver resolver = createResolver()) {
            SpectorMemory memJira = resolver.resolve(ALICE_ID, "jira");
            SpectorMemory memDocs = resolver.resolve(ALICE_ID, "docs");

            assertThat(memJira).isNotNull();
            assertThat(memDocs).isNotNull();
            assertThat(memJira).isNotSameAs(memDocs);

            Path expectedJiraDir = NamespacePathResolver.resolve(basePath, TENANT_ACME, NS_JIRA).dir();
            Path expectedDocsDir = NamespacePathResolver.resolve(basePath, TENANT_ACME, NS_DOCS).dir();

            // Both directories must land under tenant-rooted path
            assertThat(expectedJiraDir).startsWith(basePath.resolve("tenants"));
            assertThat(expectedDocsDir).startsWith(basePath.resolve("tenants"));

            // Must be distinct directories
            assertThat(expectedJiraDir).isNotEqualTo(expectedDocsDir);

            // Both directories must contain valid TENANT_SHA256 layout markers
            Path jiraMarker = expectedJiraDir.resolve(StoragePaths.FILE_NAMESPACE);
            Path docsMarker = expectedDocsDir.resolve(StoragePaths.FILE_NAMESPACE);
            assertThat(jiraMarker).exists();
            assertThat(docsMarker).exists();

            JsonNode jiraJson = objectMapper.readTree(jiraMarker.toFile());
            assertThat(jiraJson.get("layout").asText()).isIn("TENANT_SHA256", NamespacePathResolver.Layout.TENANT_SHA256.id());
            assertThat(jiraJson.get("tenantId").asText()).isEqualTo(TENANT_ACME);
            assertThat(jiraJson.get("namespaceId").asText()).isEqualTo(NS_JIRA);

            JsonNode docsJson = objectMapper.readTree(docsMarker.toFile());
            assertThat(docsJson.get("layout").asText()).isIn("TENANT_SHA256", NamespacePathResolver.Layout.TENANT_SHA256.id());
            assertThat(docsJson.get("tenantId").asText()).isEqualTo(TENANT_ACME);
            assertThat(docsJson.get("namespaceId").asText()).isEqualTo(NS_DOCS);
        }
    }

    @Test
    @DisplayName("R10.2: Bob with READER grant on jira opens the same directory and shares the same SpectorMemory instance (I4)")
    void r10_2_bobWithReaderGrant_opensSameDirectory_sharesSameMemoryInstance() {
        Account alice = createAccount(ALICE_ID, TENANT_ACME);
        Account bob = createAccount(BOB_ID, TENANT_ACME);

        when(catalog.getOrCreateAccount(ALICE_ID)).thenReturn(alice);
        when(catalog.getOrCreateAccount(BOB_ID)).thenReturn(bob);

        NamespaceRecord jiraRecord = new NamespaceRecord(
                NS_JIRA, "jira", ALICE_ID, NamespaceType.AGENT, NamespaceStatus.ACTIVE,
                "Jira Agent", "Jira tickets memory", null, Instant.now(), Instant.now()
        );

        when(catalog.getAccount(ALICE_ID)).thenReturn(alice);
        when(catalog.resolve(ALICE_ID, "jira")).thenReturn(Optional.of(jiraRecord));
        when(catalog.resolve(BOB_ID, NS_JIRA)).thenReturn(Optional.of(jiraRecord));

        try (NamespaceResolver resolver = createResolver()) {
            // Alice opens her namespace
            SpectorMemory aliceMemory = resolver.resolve(ALICE_ID, "jira");

            // Bob opens the namespace via granted namespace ID
            SpectorMemory bobMemory = resolver.resolve(BOB_ID, NS_JIRA);

            // Invariant I4: Must share identical instance reference
            assertSame(aliceMemory, bobMemory);
            assertThat(resolver.cachedInstanceCount()).isEqualTo(1);

            // Runtime attach was invoked only once for NS_JIRA
            verify(runtime, times(1)).attach(eq(NS_JIRA), any());
        }
    }

    @Test
    @DisplayName("R10.2 / R9.2: A grantee in a different tenant opening FIRST still lands on the owner's tenant path")
    void r10_2_granteeInDifferentTenantOpeningFirst_usesOwnerTenantPath() {
        // Alice owns the namespace and lives in acme. Bob holds a READER grant but lives in globex.
        Account alice = createAccount(ALICE_ID, TENANT_ACME);
        Account bob = createAccount(BOB_ID, TENANT_GLOBEX);

        when(catalog.getOrCreateAccount(ALICE_ID)).thenReturn(alice);
        when(catalog.getOrCreateAccount(BOB_ID)).thenReturn(bob);
        when(catalog.getAccount(ALICE_ID)).thenReturn(alice);
        when(catalog.getAccount(BOB_ID)).thenReturn(bob);

        NamespaceRecord jiraRecord = new NamespaceRecord(
                NS_JIRA, "jira", ALICE_ID, NamespaceType.AGENT, NamespaceStatus.ACTIVE,
                "Jira Agent", "Jira tickets memory", null, Instant.now(), Instant.now()
        );
        when(catalog.resolve(BOB_ID, NS_JIRA)).thenReturn(Optional.of(jiraRecord));
        when(catalog.resolve(ALICE_ID, "jira")).thenReturn(Optional.of(jiraRecord));

        Path ownerDir = NamespacePathResolver.resolve(basePath, TENANT_ACME, NS_JIRA).dir();
        Path granteeDir = NamespacePathResolver.resolve(basePath, TENANT_GLOBEX, NS_JIRA).dir();
        assertThat(ownerDir).isNotEqualTo(granteeDir);

        try (NamespaceResolver resolver = createResolver()) {
            // Bob opens FIRST. This is the ordering that exposes caller-derived placement: the cache
            // is keyed by namespaceId, so whoever opens first fixes the directory for everyone.
            SpectorMemory bobMemory = resolver.resolve(BOB_ID, NS_JIRA);

            ArgumentCaptor<Consumer<SpectorMemoryBuilder>> captor = ArgumentCaptor.forClass(Consumer.class);
            verify(runtime).attach(eq(NS_JIRA), captor.capture());
            SpectorMemoryBuilder builder = mock(SpectorMemoryBuilder.class);
            captor.getValue().accept(builder);

            verify(builder).persistence(ownerDir);
            verify(builder, never()).persistence(granteeDir);

            // Alice then opens the same namespace and must get the very same instance.
            SpectorMemory aliceMemory = resolver.resolve(ALICE_ID, "jira");
            assertSame(bobMemory, aliceMemory);
            assertThat(resolver.cachedInstanceCount()).isEqualTo(1);
            verify(runtime, times(1)).attach(eq(NS_JIRA), any());

            // R9.2: the namespace must sit inside the owner's tenant prefix and nowhere near Bob's,
            // otherwise a globex tenant wipe would delete an acme namespace.
            assertThat(ownerDir).startsWith(NamespacePathResolver.tenantPrefix(basePath, TENANT_ACME));
            assertThat(ownerDir.startsWith(NamespacePathResolver.tenantPrefix(basePath, TENANT_GLOBEX)))
                    .as("owner's namespace must not fall inside the grantee tenant's wipe prefix")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("C1: An unresolvable owner account fails loud rather than guessing a tenant")
    void unresolvableOwnerAccount_failsLoud() {
        Account bob = createAccount(BOB_ID, TENANT_GLOBEX);
        when(catalog.getOrCreateAccount(BOB_ID)).thenReturn(bob);
        when(catalog.getAccount("acct-missing"))
                .thenThrow(new NamespaceNotFoundException("account:acct-missing"));

        NamespaceRecord orphaned = new NamespaceRecord(
                NS_JIRA, "jira", "acct-missing", NamespaceType.AGENT, NamespaceStatus.ACTIVE,
                "Orphaned", "Owner no longer in catalog", null, Instant.now(), Instant.now()
        );
        when(catalog.resolve(BOB_ID, NS_JIRA)).thenReturn(Optional.of(orphaned));

        try (NamespaceResolver resolver = createResolver()) {
            assertThatThrownBy(() -> resolver.resolve(BOB_ID, NS_JIRA))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(NS_JIRA)
                    .hasMessageContaining("acct-missing");

            // Nothing may be opened or cached on the wrong tenant path.
            assertThat(resolver.cachedInstanceCount()).isZero();
            verify(runtime, never()).attach(eq(NS_JIRA), any());
        }
    }

    @Test
    @DisplayName("R10.4: SHARED namespace resolves under its owner's tenant, never nested under an account (KI-4)")
    void r10_4_sharedNamespace_resolvesUnderOwnerTenantNotAccount() {
        // A SHARED namespace is reached by several principals but still has an owning account, which
        // is what supplies its tenant. Bob is in a different tenant to prove placement follows the
        // owner rather than the opener.
        Account alice = createAccount(ALICE_ID, TENANT_ACME);
        Account bob = createAccount(BOB_ID, TENANT_GLOBEX);

        when(catalog.getOrCreateAccount(ALICE_ID)).thenReturn(alice);
        when(catalog.getOrCreateAccount(BOB_ID)).thenReturn(bob);
        when(catalog.getAccount(ALICE_ID)).thenReturn(alice);

        NamespaceRecord sharedRecord = new NamespaceRecord(
                NS_SHARED, "shared-pool", ALICE_ID, NamespaceType.SHARED, NamespaceStatus.ACTIVE,
                "Shared Team Pool", "Shared across org", null, Instant.now(), Instant.now()
        );
        when(catalog.resolve(ALICE_ID, "shared-pool")).thenReturn(Optional.of(sharedRecord));
        when(catalog.resolve(BOB_ID, "shared-pool")).thenReturn(Optional.of(sharedRecord));

        Path expectedDir = NamespacePathResolver.resolve(basePath, TENANT_ACME, NS_SHARED).dir();

        try (NamespaceResolver resolver = createResolver()) {
            SpectorMemory bobSharedMem = resolver.resolve(BOB_ID, "shared-pool");
            SpectorMemory aliceSharedMem = resolver.resolve(ALICE_ID, "shared-pool");

            assertSame(bobSharedMem, aliceSharedMem);

            ArgumentCaptor<Consumer<SpectorMemoryBuilder>> captor = ArgumentCaptor.forClass(Consumer.class);
            verify(runtime).attach(eq(NS_SHARED), captor.capture());
            SpectorMemoryBuilder builder = mock(SpectorMemoryBuilder.class);
            captor.getValue().accept(builder);
            verify(builder).persistence(expectedDir);

            assertThat(expectedDir).startsWith(basePath.resolve("tenants"));

            // KI-4: never nested under an account directory — that would break cross-account sharing.
            assertThat(expectedDir.toString()).doesNotContain("/accounts/" + ALICE_ID);
            assertThat(expectedDir.toString()).doesNotContain("/accounts/" + BOB_ID);
        }
    }

    @Test
    @DisplayName("C1: An ownerless record is placed deterministically on the flat layout, whoever opens it first")
    void ownerlessNamespace_placedDeterministicallyOnFlatLayout() {
        // NamespaceRecord carries no tenantId, so a record with no owner account has no tenant to
        // inherit. Placement then falls back to the flat layout, which is a function of namespaceId
        // alone and therefore identical regardless of who opens it first. The trade-off is that such
        // a namespace lies outside every tenant wipe prefix.
        Account alice = createAccount(ALICE_ID, TENANT_ACME);
        Account bob = createAccount(BOB_ID, TENANT_GLOBEX);

        when(catalog.getOrCreateAccount(ALICE_ID)).thenReturn(alice);
        when(catalog.getOrCreateAccount(BOB_ID)).thenReturn(bob);

        NamespaceRecord ownerless = new NamespaceRecord(
                NS_SHARED, "shared-pool", null, NamespaceType.SHARED, NamespaceStatus.ACTIVE,
                "Ownerless Pool", "No single parent account", null, Instant.now(), Instant.now()
        );
        when(catalog.resolve(ALICE_ID, "shared-pool")).thenReturn(Optional.of(ownerless));
        when(catalog.resolve(BOB_ID, "shared-pool")).thenReturn(Optional.of(ownerless));

        Path flatDir = NamespacePathResolver.resolve(basePath, null, NS_SHARED).dir();

        // Bob (globex) opens first.
        try (NamespaceResolver resolver = createResolver()) {
            resolver.resolve(BOB_ID, "shared-pool");
            ArgumentCaptor<Consumer<SpectorMemoryBuilder>> captor = ArgumentCaptor.forClass(Consumer.class);
            verify(runtime).attach(eq(NS_SHARED), captor.capture());
            SpectorMemoryBuilder builder = mock(SpectorMemoryBuilder.class);
            captor.getValue().accept(builder);
            verify(builder).persistence(flatDir);
        }

        // Clear counts but keep the attach stub, so the second open is verified independently.
        clearInvocations(runtime);

        // Alice (acme) opening first must produce the identical directory.
        try (NamespaceResolver resolver = createResolver()) {
            resolver.resolve(ALICE_ID, "shared-pool");
            ArgumentCaptor<Consumer<SpectorMemoryBuilder>> captor = ArgumentCaptor.forClass(Consumer.class);
            verify(runtime).attach(eq(NS_SHARED), captor.capture());
            SpectorMemoryBuilder builder = mock(SpectorMemoryBuilder.class);
            captor.getValue().accept(builder);
            verify(builder).persistence(flatDir);
        }
    }

    @Test
    @DisplayName("R9 & R10.3: Tenant-prefix delete wipes Alice's rememberers, Globex tenant still opens, and identity plane is untouched")
    void r9_and_r10_3_tenantPrefixDelete_wipesAliceRememberers_globexUntouched_identityPlaneUntouched() throws IOException {
        Account alice = createAccount(ALICE_ID, TENANT_ACME);
        Account globexUser = createAccount("018f9b8c000070008000000000000099", TENANT_GLOBEX);

        when(catalog.getOrCreateAccount(ALICE_ID)).thenReturn(alice);
        when(catalog.getOrCreateAccount(globexUser.id())).thenReturn(globexUser);

        NamespaceRecord aliceJira = new NamespaceRecord(
                NS_JIRA, "jira", ALICE_ID, NamespaceType.AGENT, NamespaceStatus.ACTIVE,
                "Jira", "", null, Instant.now(), Instant.now()
        );
        NamespaceRecord globexNs = new NamespaceRecord(
                NS_GLOBEX, "globex-ns", globexUser.id(), NamespaceType.DEFAULT, NamespaceStatus.ACTIVE,
                "Globex NS", "", null, Instant.now(), Instant.now()
        );

        when(catalog.resolve(ALICE_ID, "jira")).thenReturn(Optional.of(aliceJira));
        when(catalog.resolve(globexUser.id(), "globex-ns")).thenReturn(Optional.of(globexNs));

        // 1. Seed identity plane data (e.g. dataDir/identity/ or dataDir/accounts/)
        Path identityPlaneDir = dataDir.resolve("identity").resolve(TENANT_ACME);
        Files.createDirectories(identityPlaneDir);
        Path identityFile = identityPlaneDir.resolve("tenant.json");
        Files.writeString(identityFile, "{\"tenantId\": \"" + TENANT_ACME + "\", \"status\": \"ACTIVE\"}");

        // 2. Open rememberers for both tenants
        try (NamespaceResolver resolver = createResolver()) {
            resolver.resolve(ALICE_ID, "jira");
            resolver.resolve(globexUser.id(), "globex-ns");
        }

        Path acmeTenantRoot = NamespacePathResolver.tenantPrefix(basePath, TENANT_ACME);
        Path globexTenantRoot = NamespacePathResolver.tenantPrefix(basePath, TENANT_GLOBEX);

        assertThat(acmeTenantRoot).exists();
        assertThat(globexTenantRoot).exists();

        // 3. Perform tenant wipe of Acme rememberers: single recursive delete of tenantPrefix (R9.1)
        try (var walk = Files.walk(acmeTenantRoot)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
        assertThat(acmeTenantRoot).doesNotExist();

        // 4. Invariants assert:
        // A) Globex tenant rememberers are completely intact and untouched (R9.2)
        assertThat(globexTenantRoot).exists();
        Path globexDir = NamespacePathResolver.resolve(basePath, TENANT_GLOBEX, NS_GLOBEX).dir();
        assertThat(globexDir.resolve(StoragePaths.FILE_NAMESPACE)).exists();

        // B) Identity plane for Acme is completely untouched (R9.2, I3)
        assertThat(identityFile).exists();
        assertThat(Files.readString(identityFile)).contains(TENANT_ACME);

        // C) Both prefixes are derivable from tenantId alone without catalog lookup or directory scan (R9.4)
        Path derivedDataPlaneRoot = NamespacePathResolver.tenantPrefix(basePath, TENANT_ACME);
        assertThat(derivedDataPlaneRoot).isEqualTo(acmeTenantRoot);
    }
}
