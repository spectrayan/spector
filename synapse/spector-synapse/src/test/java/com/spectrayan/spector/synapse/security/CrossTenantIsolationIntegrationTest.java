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
package com.spectrayan.spector.synapse.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountProfile;
import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.PrincipalKind;
import com.spectrayan.spector.synapse.catalog.exception.CrossTenantAccessException;
import com.spectrayan.spector.synapse.catalog.jdbc.JdbcAccountCatalog;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.sql.SqlQueryLoader;
import com.spectrayan.spector.synapse.memory.MemoryBinding;
import com.spectrayan.spector.synapse.memory.MemoryRegistry;
import com.spectrayan.spector.synapse.memory.MemoryRequestBinder;
import com.spectrayan.spector.synapse.memory.NamespaceResolver;

/**
 * Integration tests verifying multi-tenant isolation, same-slug namespace coexistence without data bleed,
 * request tenant resolution from JWT/API-key claims, and cross-tenant access rejection (ADR-0070, Issue #1048).
 */
@DisplayName("Cross-Tenant Isolation Integration Tests")
class CrossTenantIsolationIntegrationTest {

    private JdbcAccountCatalog catalog;
    private MemoryRequestBinder binder;
    private SpectorMemory mockMemory;
    private NamespaceResolver mockResolver;

    private static final String TENANT_ALPHA = "tenant-alpha";
    private static final String TENANT_BETA = "tenant-beta";
    private static final String ACCOUNT_ALPHA = "01955000000A1";
    private static final String ACCOUNT_BETA = "01955000000B2";
    private static final String SHARED_SLUG = "project-shared";

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:crosstenant-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        JdbcClient jdbc = JdbcClient.create(dataSource);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SqlQueryLoader sqlLoader = new SqlQueryLoader();
        sqlLoader.prewarm();

        catalog = new JdbcAccountCatalog(jdbc, sqlLoader, objectMapper);

        // Provision accounts in distinct tenants
        catalog.getOrCreateAccount(ACCOUNT_ALPHA, AccountProfile.HUMAN_SOLO, PrincipalKind.HUMAN, TENANT_ALPHA);
        catalog.assignTenant(ACCOUNT_ALPHA, TENANT_ALPHA);

        catalog.getOrCreateAccount(ACCOUNT_BETA, AccountProfile.HUMAN_SOLO, PrincipalKind.HUMAN, TENANT_BETA);
        catalog.assignTenant(ACCOUNT_BETA, TENANT_BETA);

        mockMemory = mock(SpectorMemory.class);
        when(mockMemory.acquireLease()).thenReturn(mock(AutoCloseable.class));

        mockResolver = mock(NamespaceResolver.class);
        MemoryRegistry mockRegistry = mock(MemoryRegistry.class);
        when(mockRegistry.namespaceResolver()).thenReturn(mockResolver);

        SynapseProperties synapseProps = mock(SynapseProperties.class);
        var authProps = mock(com.spectrayan.spector.config.properties.AuthProperties.class);
        when(authProps.enabled()).thenReturn(true);
        when(synapseProps.auth()).thenReturn(authProps);

        ObjectProvider<SpectorMemory> memProvider = mock(ObjectProvider.class);
        when(memProvider.getIfAvailable()).thenReturn(mockMemory);

        binder = new MemoryRequestBinder(catalog, mockRegistry, synapseProps, memProvider, null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Two tenants coexist with identical namespace slugs without data bleed")
    void testSameNamespaceSlugZeroDataBleed() {
        // Both accounts create a namespace with the exact same slug
        NamespaceRecord nsA = catalog.createNamespace(ACCOUNT_ALPHA, SHARED_SLUG, NamespaceType.PROJECT);
        NamespaceRecord nsB = catalog.createNamespace(ACCOUNT_BETA, SHARED_SLUG, NamespaceType.PROJECT);

        assertThat(nsA.slug()).isEqualTo(SHARED_SLUG);
        assertThat(nsB.slug()).isEqualTo(SHARED_SLUG);
        assertThat(nsA.namespaceId()).isNotEqualTo(nsB.namespaceId());
        assertThat(nsA.ownerAccountId()).isEqualTo(ACCOUNT_ALPHA);
        assertThat(nsB.ownerAccountId()).isEqualTo(ACCOUNT_BETA);

        // Resolving by slug returns the respective tenant account's own namespace
        Optional<NamespaceRecord> resolvedA = catalog.resolve(ACCOUNT_ALPHA, SHARED_SLUG);
        Optional<NamespaceRecord> resolvedB = catalog.resolve(ACCOUNT_BETA, SHARED_SLUG);

        assertThat(resolvedA).isPresent();
        assertThat(resolvedA.get().namespaceId()).isEqualTo(nsA.namespaceId());
        assertThat(resolvedA.get().ownerAccountId()).isEqualTo(ACCOUNT_ALPHA);

        assertThat(resolvedB).isPresent();
        assertThat(resolvedB.get().namespaceId()).isEqualTo(nsB.namespaceId());
        assertThat(resolvedB.get().ownerAccountId()).isEqualTo(ACCOUNT_BETA);
    }

    @Test
    @DisplayName("Cross-tenant namespace resolution by ID is rejected with SPE-820-001")
    void testCrossTenantResolutionByIdRejected() {
        NamespaceRecord nsB = catalog.createNamespace(ACCOUNT_BETA, SHARED_SLUG, NamespaceType.PROJECT);

        // Account A attempts to resolve Account B's namespace by ID
        assertThatThrownBy(() -> catalog.resolve(ACCOUNT_ALPHA, nsB.namespaceId()))
                .isInstanceOf(CrossTenantAccessException.class)
                .satisfies(ex -> {
                    CrossTenantAccessException ctae = (CrossTenantAccessException) ex;
                    assertThat(ctae.errorCode()).isEqualTo(ErrorCode.CROSS_TENANT_ACCESS_DENIED);
                    assertThat(ctae.errorCode().id()).isEqualTo("SPE-820-001");
                    assertThat(CrossTenantAccessException.ERROR_CODE_ALIAS).isEqualTo("SPE-SEC-001");
                });
    }

    @Test
    @DisplayName("Cross-tenant grant attempt is rejected with CrossTenantAccessException")
    void testCrossTenantGrantRejected() {
        NamespaceRecord nsA = catalog.createNamespace(ACCOUNT_ALPHA, "tenant-a-ns", NamespaceType.PROJECT);

        // Account A attempts to grant Account B access across tenant boundaries
        assertThatThrownBy(() -> catalog.grantNamespace(ACCOUNT_ALPHA, nsA.namespaceId(), ACCOUNT_BETA,
                GrantRole.READER, null, null))
                .isInstanceOf(CrossTenantAccessException.class)
                .hasMessageContaining("SPE-820-001");
    }

    @Test
    @DisplayName("MemoryRequestBinder binds tenant from JWT claims and enforces cross-tenant checks")
    void testBinderResolvesTenantAndEnforcesIsolation() {
        NamespaceRecord nsA = catalog.createNamespace(ACCOUNT_ALPHA, SHARED_SLUG, NamespaceType.PROJECT);
        when(mockResolver.resolve(ACCOUNT_ALPHA, nsA.namespaceId())).thenReturn(mockMemory);

        // 1. Authenticated JWT matching tenant-alpha
        Jwt jwtAlpha = Jwt.withTokenValue("mock-jwt-alpha")
                .header("alg", "none")
                .subject(ACCOUNT_ALPHA)
                .claim("tenant_id", TENANT_ALPHA)
                .build();
        var authAlpha = new JwtAuthenticationToken(jwtAlpha, List.of());
        SecurityContextHolder.getContext().setAuthentication(authAlpha);

        // SecurityUtils resolves tenant
        assertThat(SecurityUtils.getTenantId()).isEqualTo(TENANT_ALPHA);

        // Request binding succeeds and carries effective tenant
        MemoryBinding binding = binder.bind(authAlpha, Optional.of(SHARED_SLUG));
        assertThat(binding).isNotNull();
        assertThat(binding.context().tenantId()).isEqualTo(TENANT_ALPHA);
        assertThat(binding.namespaceId()).isEqualTo(nsA.namespaceId());

        // 2. JWT with foreign tenant claims attempting to bind to account-alpha
        Jwt jwtForeign = Jwt.withTokenValue("mock-jwt-foreign")
                .header("alg", "none")
                .subject(ACCOUNT_ALPHA)
                .claim("tenant_id", "foreign-tenant")
                .build();
        var authForeign = new JwtAuthenticationToken(jwtForeign, List.of());

        assertThatThrownBy(() -> binder.bind(authForeign, Optional.of(SHARED_SLUG)))
                .isInstanceOf(CrossTenantAccessException.class)
                .hasMessageContaining("SPE-820-001");
    }

    @Test
    @DisplayName("MemoryScope binds tenant for request execution and unauthenticated defaults to 'default'")
    void testMemoryScopeAndDefaultTenant() {
        // Unauthenticated defaults to 'default'
        SecurityContextHolder.clearContext();
        assertThat(SecurityUtils.getTenantId()).isEqualTo("default");

        // Inside MemoryScope, tenant is active and returned
        MemoryScope.runWithScope(TENANT_ALPHA, "session-123", "ns-456", () -> {
            assertThat(MemoryScope.isTenantActive()).isTrue();
            assertThat(MemoryScope.tenantId()).isEqualTo(TENANT_ALPHA);
            assertThat(SecurityUtils.getTenantId()).isEqualTo(TENANT_ALPHA);
        });

        // Outside MemoryScope, back to default
        assertThat(MemoryScope.isTenantActive()).isFalse();
        assertThat(SecurityUtils.getTenantId()).isEqualTo("default");
    }

    @Test
    @DisplayName("MemoryRequestBinder correctly resolves numeric and nested JSON tenant claims")
    void testBinderResolvesNumericAndNestedTenantClaims() {
        String numTenant = "999";
        String numAccount = "0195500000099";
        catalog.getOrCreateAccount(numAccount, AccountProfile.HUMAN_SOLO, PrincipalKind.HUMAN, numTenant);
        NamespaceRecord ns = catalog.createNamespace(numAccount, "num-slug", NamespaceType.PROJECT);
        when(mockResolver.resolve(numAccount, ns.namespaceId())).thenReturn(mockMemory);

        // Numeric tenant in JWT claim (e.g. integer 999)
        Jwt jwtNum = Jwt.withTokenValue("mock-jwt-num")
                .header("alg", "none")
                .subject(numAccount)
                .claim("tenant_id", 999)
                .build();
        var authNum = new JwtAuthenticationToken(jwtNum, List.of());
        MemoryBinding bindingNum = binder.bind(authNum, Optional.of("num-slug"));
        assertThat(bindingNum.context().tenantId()).isEqualTo("999");

        // Nested OIDC claim in JWT (e.g. realm_access.tenant_id)
        try {
            SecurityUtils.setOidcTenantClaim("realm_access.tenant_id");
            Jwt jwtNested = Jwt.withTokenValue("mock-jwt-nested")
                    .header("alg", "none")
                    .subject(numAccount)
                    .claim("realm_access", java.util.Map.of("tenant_id", "999"))
                    .build();
            var authNested = new JwtAuthenticationToken(jwtNested, List.of());
            MemoryBinding bindingNested = binder.bind(authNested, Optional.of("num-slug"));
            assertThat(bindingNested.context().tenantId()).isEqualTo("999");
        } finally {
            SecurityUtils.setOidcTenantClaim(null);
        }
    }

    @Test
    @DisplayName("CrossTenantAccessException message contains both SPE-820-001 and SPE-SEC-001 taxonomy codes")
    void testTaxonomyMessageIncludesBothCodes() {
        var ex = new CrossTenantAccessException(ACCOUNT_ALPHA, "ns-1", TENANT_BETA);
        assertThat(ex.getMessage()).contains("SPE-820-001");
        assertThat(ex.getMessage()).contains("SPE-SEC-001");
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.CROSS_TENANT_ACCESS_DENIED);
        assertThat(ErrorCode.fromId("SPE-SEC-001")).isEqualTo(ErrorCode.CROSS_TENANT_ACCESS_DENIED);
        assertThat(ErrorCode.fromId("SPE-820-001")).isEqualTo(ErrorCode.CROSS_TENANT_ACCESS_DENIED);
    }

    @Test
    @DisplayName("MemoryRequestBinder correctly extracts tenant from List / Collection claims")
    void testBinderResolvesListTenantClaims() {
        NamespaceRecord nsA = catalog.createNamespace(ACCOUNT_ALPHA, "slug-list", NamespaceType.PROJECT);
        when(mockResolver.resolve(ACCOUNT_ALPHA, nsA.namespaceId())).thenReturn(mockMemory);

        // Single-element list
        Jwt jwtSingleList = Jwt.withTokenValue("mock-jwt-single-list")
                .header("alg", "none")
                .subject(ACCOUNT_ALPHA)
                .claim("tenant_id", List.of(TENANT_ALPHA))
                .build();
        var authSingle = new JwtAuthenticationToken(jwtSingleList, List.of());
        MemoryBinding bindingSingle = binder.bind(authSingle, Optional.of("slug-list"));
        assertThat(bindingSingle.context().tenantId()).isEqualTo(TENANT_ALPHA);

        // Multi-element list: resolves first non-blank
        Jwt jwtMultiList = Jwt.withTokenValue("mock-jwt-multi-list")
                .header("alg", "none")
                .subject(ACCOUNT_ALPHA)
                .claim("tenant_id", List.of(TENANT_ALPHA, "secondary-tenant"))
                .build();
        var authMulti = new JwtAuthenticationToken(jwtMultiList, List.of());
        MemoryBinding bindingMulti = binder.bind(authMulti, Optional.of("slug-list"));
        assertThat(bindingMulti.context().tenantId()).isEqualTo(TENANT_ALPHA);
    }

    @Test
    @DisplayName("Untenanted accounts cannot resolve or receive grants to tenanted namespaces")
    void testUntenantedAccountAccessAndGrantRejected() {
        String untenantedAccount = "01955000000U0";
        catalog.getOrCreateAccount(untenantedAccount, AccountProfile.HUMAN_SOLO, PrincipalKind.HUMAN, null);

        NamespaceRecord nsA = catalog.createNamespace(ACCOUNT_ALPHA, "private-tenanted", NamespaceType.PROJECT);

        // Untenanted account attempts to resolve tenanted namespace by ID
        assertThatThrownBy(() -> catalog.resolve(untenantedAccount, nsA.namespaceId()))
                .isInstanceOf(CrossTenantAccessException.class)
                .hasMessageContaining("SPE-820-001");

        // Tenanted account attempts to grant to untenanted account
        assertThatThrownBy(() -> catalog.grantNamespace(ACCOUNT_ALPHA, nsA.namespaceId(), untenantedAccount,
                GrantRole.READER, null, null))
                .isInstanceOf(CrossTenantAccessException.class)
                .hasMessageContaining("SPE-820-001");
    }

    @Test
    @DisplayName("Dynamic switching of OIDC tenant claim at runtime without restart")
    void testDynamicSwitchingOfOidcTenantClaimAtRuntime() {
        try {
            // Step 1: Claim set to "tenant_claim_v1"
            SecurityUtils.setOidcTenantClaim("tenant_claim_v1");
            assertThat(SecurityUtils.getOidcTenantClaim()).isEqualTo("tenant_claim_v1");

            Jwt jwt1 = Jwt.withTokenValue("mock-jwt-v1")
                    .header("alg", "none")
                    .subject(ACCOUNT_ALPHA)
                    .claim("tenant_claim_v1", TENANT_ALPHA)
                    .claim("tenant_claim_v2", TENANT_BETA)
                    .build();
            var auth1 = new JwtAuthenticationToken(jwt1, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth1);
            assertThat(SecurityUtils.getTenantId()).isEqualTo(TENANT_ALPHA);

            // Step 2: Dynamically switch to "tenant_claim_v2" at runtime
            SecurityUtils.setOidcTenantClaim("tenant_claim_v2");
            assertThat(SecurityUtils.getOidcTenantClaim()).isEqualTo("tenant_claim_v2");
            assertThat(SecurityUtils.getTenantId()).isEqualTo(TENANT_BETA);

            // Step 3: Clear custom claim and fall back to default candidate claims
            SecurityUtils.setOidcTenantClaim(null);
            assertThat(SecurityUtils.getOidcTenantClaim()).isNull();
            Jwt jwtDefault = Jwt.withTokenValue("mock-jwt-default")
                    .header("alg", "none")
                    .subject(ACCOUNT_ALPHA)
                    .claim("tid", TENANT_ALPHA)
                    .build();
            var authDefault = new JwtAuthenticationToken(jwtDefault, List.of());
            SecurityContextHolder.getContext().setAuthentication(authDefault);
            assertThat(SecurityUtils.getTenantId()).isEqualTo(TENANT_ALPHA);
        } finally {
            SecurityUtils.setOidcTenantClaim(null);
        }
    }
}
