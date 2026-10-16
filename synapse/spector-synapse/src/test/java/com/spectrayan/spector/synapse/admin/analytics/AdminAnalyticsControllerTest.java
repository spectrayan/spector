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
package com.spectrayan.spector.synapse.admin.analytics;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.synapse.admin.tenant.TenantService;
import com.spectrayan.spector.synapse.admin.tenant.TenantStatus;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.CreateTenantRequest;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.TenantQuotasDto;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.TenantSettingsDto;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.AccountProfile;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.PrincipalKind;
import com.spectrayan.spector.synapse.provider.usage.TokenUsageCategory;
import com.spectrayan.spector.synapse.provider.usage.TokenUsageEvent;
import com.spectrayan.spector.synapse.provider.usage.TokenUsageTracker;
import com.spectrayan.spector.synapse.security.ServerAccessTokenMinter;
import com.spectrayan.spector.synapse.security.UserAccountStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AdminAnalyticsController} and {@link AdminAnalyticsService} (Issue #1058, ADR-0083 §5.8).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spector.auth.enabled=true",
        "spector.auth.jwt.secret=01234567890123456789012345678901",
        "spector.catalog.type=jdbc"
})
@DirtiesContext
@DisplayName("AdminAnalyticsController Integration Tests (Issue #1058)")
class AdminAnalyticsControllerTest {

    private static final String TENANT_ACME = "acme-analytics";
    private static final String TENANT_GLOBEX = "globex-analytics";

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private AccountCatalog catalog;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private UserAccountStore userAccountStore;

    @Autowired
    private ServerAccessTokenMinter tokenMinter;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired(required = false)
    private TokenUsageTracker tokenUsageTracker;

    @MockitoBean
    private SpectorMemory spectorMemory;

    private MockMvc mvc;
    private String acmeAdminUserId;
    private String globexAdminUserId;
    private String nsAcme1;
    private String nsAcme2;
    private String nsGlobex1;
    private String acmeAdminJwt;
    private String globexAdminJwt;
    private String superAdminJwt;

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        jdbc.update("DELETE FROM memory_analytics_snapshot WHERE tenant_id IN (:t1, :t2)",
                Map.of("t1", TENANT_ACME, "t2", TENANT_GLOBEX));
        jdbc.update("DELETE FROM namespaces WHERE owner_account_id IN (SELECT user_id FROM users WHERE tenant_id IN (:t1, :t2))",
                Map.of("t1", TENANT_ACME, "t2", TENANT_GLOBEX));
        jdbc.update("DELETE FROM users WHERE tenant_id IN (:t1, :t2) OR username = 'platform-op-analytics'",
                Map.of("t1", TENANT_ACME, "t2", TENANT_GLOBEX));
        jdbc.update("DELETE FROM admin_audit_log WHERE target_id IN (:t1, :t2)",
                Map.of("t1", TENANT_ACME, "t2", TENANT_GLOBEX));
        jdbc.update("DELETE FROM tenants WHERE tenant_id IN (:t1, :t2)",
                Map.of("t1", TENANT_ACME, "t2", TENANT_GLOBEX));

        tenantService.createTenant(
                new CreateTenantRequest(
                        TENANT_ACME,
                        "Acme Analytics Corp",
                        "Acme tenant for analytics rollup tests",
                        "ENTERPRISE",
                        TenantStatus.ACTIVE,
                        false,
                        TenantSettingsDto.defaults(),
                        new TenantQuotasDto(20, 10, 100 * 1024 * 1024L)
                ),
                "system",
                "default"
        );
        tenantService.createTenant(
                new CreateTenantRequest(
                        TENANT_GLOBEX,
                        "Globex Analytics Inc",
                        "Globex tenant for cross-tenant isolation tests",
                        "PRO",
                        TenantStatus.ACTIVE,
                        false,
                        TenantSettingsDto.defaults(),
                        new TenantQuotasDto(10, 5, 50 * 1024 * 1024L)
                ),
                "system",
                "default"
        );

        this.acmeAdminUserId = userAccountStore.createUser(
                "acme-admin-analytics", "Password123!", "admin@acme.test", "Acme Admin",
                Set.of("admin"), Set.of(), false, TENANT_ACME);
        this.globexAdminUserId = userAccountStore.createUser(
                "globex-admin-analytics", "Password123!", "admin@globex.test", "Globex Admin",
                Set.of("admin"), Set.of(), false, TENANT_GLOBEX);
        String superAdminUserId = userAccountStore.createUser(
                "platform-op-analytics", "Password123!", "op@spector.test", "Platform Operator",
                Set.of("super-admin"), Set.of(), false, "default");

        catalog.getOrCreateAccount(acmeAdminUserId, AccountProfile.HUMAN_TEAM, PrincipalKind.HUMAN, TENANT_ACME);
        catalog.getOrCreateAccount(globexAdminUserId, AccountProfile.HUMAN_TEAM, PrincipalKind.HUMAN, TENANT_GLOBEX);

        // getOrCreateAccount provisions the default namespace (id == userId, slug == "default"); create a second namespace for Acme
        this.nsAcme1 = acmeAdminUserId;
        NamespaceRecord acmeStaging = catalog.createNamespace(
                acmeAdminUserId, "acme-staging", NamespaceType.SHARED, "Acme Staging", "Staging", null);
        this.nsAcme2 = acmeStaging.namespaceId();
        this.nsGlobex1 = globexAdminUserId;

        // Seed analytics snapshots (24h ago and current)
        Instant now = Instant.now();
        Instant dayAgo = now.minus(23, ChronoUnit.HOURS);
        insertSnapshot(nsAcme1, acmeAdminUserId, TENANT_ACME, dayAgo, 100L, 10L, 40L, 35L, 15L, 500L, 1200L, 2L, 0L);
        insertSnapshot(nsAcme1, acmeAdminUserId, TENANT_ACME, now, 160L, 15L, 65L, 55L, 25L, 800L, 1950L, 5L, 1L);
        insertSnapshot(nsAcme2, acmeAdminUserId, TENANT_ACME, now, 40L, 5L, 15L, 15L, 5L, 200L, 400L, 1L, 0L);
        insertSnapshot(nsGlobex1, globexAdminUserId, TENANT_GLOBEX, now, 75L, 5L, 30L, 25L, 15L, 300L, 600L, 3L, 2L);

        // Seed token usage
        if (tokenUsageTracker != null) {
            tokenUsageTracker.record(TokenUsageEvent.ofGeneration(
                    TokenUsageCategory.CHAT, "openai", "gpt-4o-mini", acmeAdminUserId, "sess-1", 1200, 400));
            tokenUsageTracker.record(TokenUsageEvent.ofGeneration(
                    TokenUsageCategory.CHAT, "anthropic", "claude-3-5-sonnet", globexAdminUserId, "sess-2", 800, 300));
        }

        this.acmeAdminJwt = tokenMinter.mint(acmeAdminUserId, Set.of(), Set.of("admin"), TENANT_ACME).token();
        this.globexAdminJwt = tokenMinter.mint(globexAdminUserId, Set.of(), Set.of("admin"), TENANT_GLOBEX).token();
        this.superAdminJwt = tokenMinter.mint(superAdminUserId, Set.of(), Set.of("super-admin"), "default").token();
    }

    private void insertSnapshot(
            String namespaceId,
            String accountId,
            String tenantId,
            Instant recordedAt,
            long total,
            long working,
            long episodic,
            long semantic,
            long procedural,
            long rememberOps,
            long recallOps,
            long dreamCycles,
            long errorCount) {
        jdbc.update("""
                INSERT INTO memory_analytics_snapshot (
                    namespace_id, account_id, tenant_id, snapshot_time,
                    total_count, working_count, episodic_count, semantic_count, procedural_count,
                    hebbian_edges, temporal_links, entity_nodes, entity_edges, avg_latency_ms,
                    remember_count, recall_count, consolidations_run, storage_bytes, dream_cycles_run, error_count
                ) VALUES (
                    :nsId, :accId, :tenantId, :recordedAt,
                    :total, :working, :episodic, :semantic, :procedural,
                    :edges, 0, :nodes, :edges, 1.5,
                    :remOps, :recOps, 1, :storageBytes, :dreams, :errors
                )
                """,
                Map.ofEntries(
                        Map.entry("nsId", namespaceId),
                        Map.entry("accId", accountId),
                        Map.entry("tenantId", tenantId),
                        Map.entry("recordedAt", Timestamp.from(recordedAt)),
                        Map.entry("total", total),
                        Map.entry("working", working),
                        Map.entry("episodic", episodic),
                        Map.entry("semantic", semantic),
                        Map.entry("procedural", procedural),
                        Map.entry("nodes", total),
                        Map.entry("edges", total * 2),
                        Map.entry("remOps", rememberOps),
                        Map.entry("recOps", recallOps),
                        Map.entry("storageBytes", total * 2048L),
                        Map.entry("dreams", dreamCycles),
                        Map.entry("errors", errorCount)
                ));
    }

    // ══════════════════════════════════════════════════════════════
    // 1. FLEET ANALYTICS (super-admin ONLY)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. Fleet Analytics Rollup (/api/v1/admin/analytics/fleet)")
    class FleetAnalyticsTests {

        @Test
        @DisplayName("super-admin can query fleet analytics and receives cross-tenant summary")
        void superAdminCanQueryFleetAnalytics() throws Exception {
            MvcResult res = mvc.perform(get("/api/v1/admin/analytics/fleet")
                            .header("Authorization", "Bearer " + superAdminJwt))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tenantCount", greaterThanOrEqualTo(2)))
                    .andExpect(jsonPath("$.activeTenantCount", greaterThanOrEqualTo(2)))
                    .andExpect(jsonPath("$.accountCount", greaterThanOrEqualTo(2)))
                    .andExpect(jsonPath("$.namespaceCount", greaterThanOrEqualTo(3)))
                    .andExpect(jsonPath("$.tiers.total", greaterThanOrEqualTo(275)))
                    .andExpect(jsonPath("$.growth", notNullValue()))
                    .andExpect(jsonPath("$.throughput", notNullValue()))
                    .andExpect(jsonPath("$.consolidation", notNullValue()))
                    .andExpect(jsonPath("$.tokenUsage", notNullValue()))
                    .andExpect(jsonPath("$.quotas", notNullValue()))
                    .andExpect(jsonPath("$.tenants[*].tenantId", hasItem(TENANT_ACME)))
                    .andExpect(jsonPath("$.tenants[*].tenantId", hasItem(TENANT_GLOBEX)))
                    .andReturn();

            assertContentFree(res.getResponse().getContentAsString());
        }

        @Test
        @DisplayName("Tenant admin receives 403 Forbidden (SPE-820-001) on fleet analytics")
        void tenantAdminRejectedOnFleetAnalytics() throws Exception {
            mvc.perform(get("/api/v1/admin/analytics/fleet")
                            .header("Authorization", "Bearer " + acmeAdminJwt))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("SPE-820-001")));
        }

        @Test
        @DisplayName("Non-admin roles receive 403 Forbidden on fleet and tenant analytics")
        void nonAdminRolesRejected() throws Exception {
            mvc.perform(get("/api/v1/admin/analytics/fleet")
                            .with(user("viewer-1").roles("viewer")))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/admin/analytics/tenant")
                            .with(user("editor-1").roles("editor")))
                    .andExpect(status().isForbidden());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 2. TENANT ANALYTICS & CROSS-TENANT ISOLATION
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. Tenant Analytics Rollup & Cross-Tenant Isolation")
    class TenantAnalyticsTests {

        @Test
        @DisplayName("Tenant admin can query own tenant via /tenant and /tenants/{tenantId}")
        void tenantAdminCanQueryOwnTenant() throws Exception {
            mvc.perform(get("/api/v1/admin/analytics/tenant")
                            .header("Authorization", "Bearer " + acmeAdminJwt))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tenantId", is(TENANT_ACME)))
                    .andExpect(jsonPath("$.displayName", is("Acme Analytics Corp")))
                    .andExpect(jsonPath("$.accountCount", is(1)))
                    .andExpect(jsonPath("$.namespaceCount", is(2)))
                    .andExpect(jsonPath("$.tiers.total", is(200)))
                    .andExpect(jsonPath("$.tiers.working", is(20)))
                    .andExpect(jsonPath("$.tiers.episodic", is(80)))
                    .andExpect(jsonPath("$.tiers.semantic", is(70)))
                    .andExpect(jsonPath("$.tiers.procedural", is(30)))
                    .andExpect(jsonPath("$.growth.delta24hMemories", is(60)))
                    .andExpect(jsonPath("$.consolidation.dreamCyclesRun", is(8)))
                    .andExpect(jsonPath("$.tokenUsage.totalTokens", greaterThanOrEqualTo(1600)))
                    .andExpect(jsonPath("$.quotas.namespaceCountUsed", is(2)))
                    .andExpect(jsonPath("$.quotas.namespaceCountQuota", is(10)))
                    .andExpect(jsonPath("$.namespaces.length()", is(2)));

            mvc.perform(get("/api/v1/admin/analytics/tenants/" + TENANT_ACME)
                            .header("Authorization", "Bearer " + acmeAdminJwt))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tenantId", is(TENANT_ACME)));
        }

        @Test
        @DisplayName("Tenant admin querying another tenant receives 403 Forbidden with SPE-820-001")
        void tenantAdminRejectedOnCrossTenantQuery() throws Exception {
            mvc.perform(get("/api/v1/admin/analytics/tenants/" + TENANT_GLOBEX)
                            .header("Authorization", "Bearer " + acmeAdminJwt))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status", is(403)))
                    .andExpect(jsonPath("$.error", is("SPE-820-001")));
        }

        @Test
        @DisplayName("super-admin can query any tenant rollup")
        void superAdminCanQueryAnyTenant() throws Exception {
            mvc.perform(get("/api/v1/admin/analytics/tenants/" + TENANT_GLOBEX)
                            .header("Authorization", "Bearer " + superAdminJwt))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tenantId", is(TENANT_GLOBEX)))
                    .andExpect(jsonPath("$.tiers.total", is(75)));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 3. ACCOUNT & NAMESPACE ROLLUPS
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. Account & Namespace Analytics Rollups")
    class AccountAndNamespaceRollupTests {

        @Test
        @DisplayName("Tenant admin can query account rollup within own tenant and is rejected on cross-tenant account")
        void accountAnalyticsEnforcesTenantBoundary() throws Exception {
            MvcResult res = mvc.perform(get("/api/v1/admin/analytics/accounts/" + acmeAdminUserId)
                            .header("Authorization", "Bearer " + acmeAdminJwt))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId", is(acmeAdminUserId)))
                    .andExpect(jsonPath("$.tenantId", is(TENANT_ACME)))
                    .andExpect(jsonPath("$.namespaceCount", is(2)))
                    .andExpect(jsonPath("$.tiers.total", is(200)))
                    .andExpect(jsonPath("$.tokenUsage.totalTokens", greaterThanOrEqualTo(1600)))
                    .andReturn();

            assertContentFree(res.getResponse().getContentAsString());

            // Cross-tenant account query -> 403 SPE-820-001
            mvc.perform(get("/api/v1/admin/analytics/accounts/" + globexAdminUserId)
                            .header("Authorization", "Bearer " + acmeAdminJwt))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("SPE-820-001")));
        }

        @Test
        @DisplayName("Tenant admin can query namespace rollup within own tenant and is rejected on cross-tenant namespace")
        void namespaceAnalyticsEnforcesTenantBoundary() throws Exception {
            MvcResult res = mvc.perform(get("/api/v1/admin/analytics/namespaces/" + nsAcme1)
                            .header("Authorization", "Bearer " + acmeAdminJwt))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.namespaceId", is(nsAcme1)))
                    .andExpect(jsonPath("$.slug", is("default")))
                    .andExpect(jsonPath("$.tenantId", is(TENANT_ACME)))
                    .andExpect(jsonPath("$.tiers.total", is(160)))
                    .andExpect(jsonPath("$.growth.delta24hMemories", is(60)))
                    .andReturn();

            assertContentFree(res.getResponse().getContentAsString());

            // Cross-tenant namespace query -> 403 SPE-820-001
            mvc.perform(get("/api/v1/admin/analytics/namespaces/" + nsGlobex1)
                            .header("Authorization", "Bearer " + acmeAdminJwt))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("SPE-820-001")));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 4. HISTORICAL TIMESERIES
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. Historical Analytics Time-Series (/api/v1/admin/analytics/timeseries)")
    class TimeseriesTests {

        @Test
        @DisplayName("Time-series supports FLEET, TENANT, ACCOUNT, and NAMESPACE scopes with tenant isolation")
        void timeseriesSupportsAllScopesWithIsolation() throws Exception {
            // 1. FLEET timeseries: super-admin allowed, tenant admin rejected
            mvc.perform(get("/api/v1/admin/analytics/timeseries")
                            .param("scope", "FLEET")
                            .header("Authorization", "Bearer " + superAdminJwt))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scope", is("FLEET")))
                    .andExpect(jsonPath("$.points", notNullValue()));

            mvc.perform(get("/api/v1/admin/analytics/timeseries")
                            .param("scope", "FLEET")
                            .header("Authorization", "Bearer " + acmeAdminJwt))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("SPE-820-001")));

            // 2. TENANT timeseries: own tenant allowed, other tenant rejected
            mvc.perform(get("/api/v1/admin/analytics/timeseries")
                            .param("scope", "TENANT")
                            .param("tenantId", TENANT_ACME)
                            .header("Authorization", "Bearer " + acmeAdminJwt))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scope", is("TENANT")))
                    .andExpect(jsonPath("$.tenantId", is(TENANT_ACME)))
                    .andExpect(jsonPath("$.points.length()", greaterThanOrEqualTo(1)));

            mvc.perform(get("/api/v1/admin/analytics/timeseries")
                            .param("scope", "TENANT")
                            .param("tenantId", TENANT_GLOBEX)
                            .header("Authorization", "Bearer " + acmeAdminJwt))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("SPE-820-001")));

            // 3. ACCOUNT timeseries: own tenant account allowed, cross-tenant account rejected
            mvc.perform(get("/api/v1/admin/analytics/timeseries")
                            .param("scope", "ACCOUNT")
                            .param("accountId", acmeAdminUserId)
                            .header("Authorization", "Bearer " + acmeAdminJwt))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scope", is("ACCOUNT")))
                    .andExpect(jsonPath("$.accountId", is(acmeAdminUserId)))
                    .andExpect(jsonPath("$.points.length()", greaterThanOrEqualTo(1)));

            mvc.perform(get("/api/v1/admin/analytics/timeseries")
                            .param("scope", "ACCOUNT")
                            .param("accountId", globexAdminUserId)
                            .header("Authorization", "Bearer " + acmeAdminJwt))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("SPE-820-001")));

            // 4. NAMESPACE timeseries: own tenant namespace allowed, cross-tenant namespace rejected
            mvc.perform(get("/api/v1/admin/analytics/timeseries")
                            .param("scope", "NAMESPACE")
                            .param("namespaceId", nsAcme1)
                            .header("Authorization", "Bearer " + acmeAdminJwt))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scope", is("NAMESPACE")))
                    .andExpect(jsonPath("$.namespaceId", is(nsAcme1)))
                    .andExpect(jsonPath("$.points.length()", is(2)));

            mvc.perform(get("/api/v1/admin/analytics/timeseries")
                            .param("scope", "NAMESPACE")
                            .param("namespaceId", nsGlobex1)
                            .header("Authorization", "Bearer " + acmeAdminJwt))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("SPE-820-001")));
        }
    }

    private void assertContentFree(String body) {
        assertThat(body)
                .doesNotContain("\"text\"")
                .doesNotContain("\"vector\"")
                .doesNotContain("\"quantizedVector\"")
                .doesNotContain("\"payload\"")
                .doesNotContain("\"tags\"")
                .doesNotContain("\"metadata\"");
    }
}
