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
package com.spectrayan.spector.synapse.admin.namespaces;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.synapse.admin.tenant.TenantService;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.CreateTenantRequest;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.TenantQuotasDto;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.AccountProfile;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.PrincipalKind;
import com.spectrayan.spector.synapse.catalog.exception.AccountQuotaExceededException;
import com.spectrayan.spector.synapse.catalog.exception.TenantQuotaExceededException;
import com.spectrayan.spector.synapse.security.UserAccountStore;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AdminNamespaceController} and the Admin Namespace Lifecycle API
 * at {@code /api/v1/admin/namespaces} (ADR-0087, Issue #1057).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spector.auth.enabled=true",
        "spector.auth.jwt.secret=01234567890123456789012345678901",
        "spector.catalog.type=jdbc"
})
@DirtiesContext
@DisplayName("AdminNamespaceController Lifecycle & Quota Governance Integration Tests (Issue #1057)")
class AdminNamespaceControllerTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private AccountCatalog catalog;

    @Autowired
    private UserAccountStore userAccountStore;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @MockitoBean
    private SpectorMemory spectorMemory;

    private MockMvc mvc;
    private String acmeAdminId;
    private String acmeColleagueId;
    private String betaAdminId;
    private NamespaceRecord acmeProjectNs;
    private NamespaceRecord betaProjectNs;

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        SpectorMemoryAdmin mockAdmin = mock(SpectorMemoryAdmin.class);
        MemoryIndex mockIndex = mock(MemoryIndex.class);
        when(spectorMemory.admin()).thenReturn(mockAdmin);
        when(mockAdmin.index()).thenReturn(mockIndex);
        when(spectorMemory.totalMemories()).thenReturn(3);
        when(spectorMemory.memoryCount(any())).thenReturn(1);

        jdbc.update("DELETE FROM memory_analytics_snapshot WHERE namespace_id IN (SELECT namespace_id FROM namespaces WHERE owner_account_id IN (SELECT user_id FROM users WHERE tenant_id IN ('ns-acme', 'ns-beta', 'ns-quota-t')))", Map.of());
        jdbc.update("DELETE FROM grants WHERE principal_id IN (SELECT user_id FROM users WHERE tenant_id IN ('ns-acme', 'ns-beta', 'ns-quota-t'))", Map.of());
        jdbc.update("DELETE FROM namespaces WHERE owner_account_id IN (SELECT user_id FROM users WHERE tenant_id IN ('ns-acme', 'ns-beta', 'ns-quota-t'))", Map.of());
        jdbc.update("DELETE FROM users WHERE tenant_id IN ('ns-acme', 'ns-beta', 'ns-quota-t') OR username IN ('ns-acme-admin', 'ns-acme-colleague', 'ns-beta-admin', 'ns-quota-user')", Map.of());
        jdbc.update("DELETE FROM tenants WHERE tenant_id IN ('ns-acme', 'ns-beta', 'ns-quota-t')", Map.of());

        tenantService.createTenant(
                new CreateTenantRequest("ns-acme", "Namespace Acme", "Acme Org", "ENTERPRISE", null, false, null, null),
                "system", "default");
        tenantService.createTenant(
                new CreateTenantRequest("ns-beta", "Namespace Beta", "Beta Org", "TEAM", null, false, null, null),
                "system", "default");

        acmeAdminId = userAccountStore.createUser(
                "ns-acme-admin", "Pass1234!", "admin@ns-acme.test", "Acme Admin",
                Set.of("admin"), Set.of("admin:read", "admin:write"), false, "ns-acme");
        catalog.getOrCreateAccount(acmeAdminId, AccountProfile.HUMAN_TEAM, PrincipalKind.HUMAN, "ns-acme");

        acmeColleagueId = userAccountStore.createUser(
                "ns-acme-colleague", "Pass1234!", "colleague@ns-acme.test", "Acme Colleague",
                Set.of("editor"), Set.of("memory:read", "memory:write"), false, "ns-acme");
        catalog.getOrCreateAccount(acmeColleagueId, AccountProfile.HUMAN_TEAM, PrincipalKind.HUMAN, "ns-acme");

        betaAdminId = userAccountStore.createUser(
                "ns-beta-admin", "Pass1234!", "admin@ns-beta.test", "Beta Admin",
                Set.of("admin"), Set.of("admin:read", "admin:write"), false, "ns-beta");
        catalog.getOrCreateAccount(betaAdminId, AccountProfile.HUMAN_TEAM, PrincipalKind.HUMAN, "ns-beta");

        acmeProjectNs = catalog.createNamespace(
                acmeAdminId, "alpha-research", NamespaceType.PROJECT, "Alpha Research", "R&D project memory", null);
        betaProjectNs = catalog.createNamespace(
                betaAdminId, "beta-confidential", NamespaceType.PROJECT, "Beta Confidential", "Beta secret project", null);

        jdbc.update(
                "INSERT INTO memory_analytics_snapshot (snapshot_time, namespace_id, instance_id, account_id, total_count, working_count, episodic_count, semantic_count, procedural_count, hebbian_edges, temporal_links, entity_nodes, entity_edges, avg_latency_ms, recall_count, remember_count, consolidations_run) VALUES (:ts, :nsId, 'local', :accId, 3, 1, 1, 1, 0, 0, 0, 0, 0, 1.5, 0, 3, 0)",
                Map.of("ts", Timestamp.from(Instant.now()), "nsId", acmeProjectNs.namespaceId(), "accId", acmeAdminId));
    }

    // ══════════════════════════════════════════════════════════════
    // 1. TENANT ISOLATION, ROLE GATING & CONTENT-FREE METADATA
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. Tenant Isolation, Role Gating & Content-Free Inspection")
    class TenantIsolationAndInspectTests {

        @Test
        @DisplayName("Unprivileged roles (viewer, editor, agent) are rejected with 403 Forbidden")
        void nonAdminRolesRejectedWith403() throws Exception {
            for (String role : List.of("viewer", "editor", "agent")) {
                mvc.perform(get("/api/v1/admin/namespaces")
                                .with(user("user-1").roles(role)))
                        .andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("Tenant admin lists only own tenant's namespaces with metadata/counts and gets 403 on cross-tenant access")
        void tenantAdminIsolationAndMetadataInspection() throws Exception {
            // 1. Acme admin lists namespaces -> sees acmeProjectNs, never betaProjectNs
            mvc.perform(get("/api/v1/admin/namespaces")
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].namespaceId", hasItem(acmeProjectNs.namespaceId())))
                    .andExpect(jsonPath("$[*].namespaceId", not(hasItem(betaProjectNs.namespaceId()))));

            // 2. Inspect acmeProjectNs -> returns metadata, memoryCount, storageBytes, grantsCount, quotas (no content)
            mvc.perform(get("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId())
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.namespaceId", is(acmeProjectNs.namespaceId())))
                    .andExpect(jsonPath("$.slug", is("alpha-research")))
                    .andExpect(jsonPath("$.displayName", is("Alpha Research")))
                    .andExpect(jsonPath("$.tenantId", is("ns-acme")))
                    .andExpect(jsonPath("$.ownerAccountId", is(acmeAdminId)))
                    .andExpect(jsonPath("$.ownerUsername", is("ns-acme-admin")))
                    .andExpect(jsonPath("$.status", is("ACTIVE")))
                    .andExpect(jsonPath("$.legalHold", is(false)))
                    .andExpect(jsonPath("$.memoryCount", is(3)))
                    .andExpect(jsonPath("$.grantsCount", is(1)))
                    .andExpect(jsonPath("$.quotas", notNullValue()));

            // 3. Cross-tenant filter or inspection returns 403 SPE-820-001
            mvc.perform(get("/api/v1/admin/namespaces")
                            .param("tenantId", "ns-beta")
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("SPE-820-001")));

            mvc.perform(get("/api/v1/admin/namespaces/" + betaProjectNs.namespaceId())
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("SPE-820-001")));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 2. QUOTA MANAGEMENT, BREACH TAXONOMY & MICROMETER METRICS
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. Per-Namespace, Account & Tenant Quotas + Micrometer Breach Metric")
    class QuotaAndMetricTests {

        @Test
        @DisplayName("PUT /{namespaceId}/quotas updates limits; breaching maxMemories returns 429 SPE-800-003 and increments spector.quota.breach.total")
        void namespaceQuotaUpdateAndWriteBreach() throws Exception {
            // Set maxMemories = 3 (current snapshot memoryCount is 3)
            Map<String, Object> quotaReq = Map.of(
                    "maxMemories", 3,
                    "maxStorageBytes", 10485760L,
                    "maxOpsPerMinute", 120
            );

            mvc.perform(put("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId() + "/quotas")
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(quotaReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quotas.maxMemories", is(3)))
                    .andExpect(jsonPath("$.quotas.maxStorageBytes", is(10485760)))
                    .andExpect(jsonPath("$.quotas.maxOpsPerMinute", is(120)));

            Counter counterBefore = meterRegistry.find("spector.quota.breach.total")
                    .tag("level", "namespace")
                    .tag("resource", "memories")
                    .counter();
            double beforeCount = counterBefore != null ? counterBefore.count() : 0.0;

            // Attempting to store a new memory in acmeProjectNs when memoryCount(3) >= maxMemories(3) returns 429 SPE-800-003
            mvc.perform(post("/api/v1/memory")
                            .header("X-Spector-Namespace", acmeProjectNs.namespaceId())
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"New observation exceeding namespace quota\"}"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error", is("SPE-800-003")));

            Counter counterAfter = meterRegistry.find("spector.quota.breach.total")
                    .tag("level", "namespace")
                    .tag("resource", "memories")
                    .counter();
            assertThat(counterAfter).isNotNull();
            assertThat(counterAfter.count()).isEqualTo(beforeCount + 1.0);

            // Raise maxMemories to 10 -> write succeeds and updates lastWriteAt
            mvc.perform(put("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId() + "/quotas")
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"maxMemories\":10,\"maxStorageBytes\":10485760,\"maxOpsPerMinute\":120}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quotas.maxMemories", is(10)));

            mvc.perform(post("/api/v1/memory")
                            .header("X-Spector-Namespace", acmeProjectNs.namespaceId())
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"Permitted observation after quota raise\"}"))
                    .andExpect(status().isCreated());

            mvc.perform(get("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId())
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lastWriteAt", notNullValue()));
        }

        @Test
        @DisplayName("Breaching tenant maxNamespaces and account maxNamespaces emits spector.quota.breach.total metrics")
        void tenantAndAccountNamespaceCreationQuotaMetrics() {
            // 1. Create tenant with maxNamespaces = 1
            tenantService.createTenant(
                    new CreateTenantRequest(
                            "ns-quota-t", "Quota Tenant", "1 namespace max", "FREE", null, false, null,
                            new TenantQuotasDto(5, 1, 10485760L)),
                    "system", "default");

            String quotaUserId = userAccountStore.createUser(
                    "ns-quota-user", "Pass1234!", "u@quota.test", "Quota User",
                    Set.of("editor"), Set.of(), false, "ns-quota-t");
            // Provisioning account creates its 1st ("default") namespace
            catalog.getOrCreateAccount(quotaUserId, AccountProfile.HUMAN_TEAM, PrincipalKind.HUMAN, "ns-quota-t");

            Counter tenantCounterBefore = meterRegistry.find("spector.quota.breach.total")
                    .tag("level", "tenant")
                    .tag("tenant_id", "ns-quota-t")
                    .tag("resource", "namespaces")
                    .counter();
            double beforeTenantBreach = tenantCounterBefore != null ? tenantCounterBefore.count() : 0.0;

            // Creating a 2nd namespace in ns-quota-t breaches tenant maxNamespaces=1
            assertThatThrownBy(() -> catalog.createNamespace(quotaUserId, "second-ns", NamespaceType.PROJECT))
                    .isInstanceOf(TenantQuotaExceededException.class);

            Counter tenantCounterAfter = meterRegistry.find("spector.quota.breach.total")
                    .tag("level", "tenant")
                    .tag("tenant_id", "ns-quota-t")
                    .tag("resource", "namespaces")
                    .counter();
            assertThat(tenantCounterAfter).isNotNull();
            assertThat(tenantCounterAfter.count()).isEqualTo(beforeTenantBreach + 1.0);

            // 2. Account quota breach on HUMAN_SOLO profile (maxNamespaces = 4: 1 default + 3 extra)
            String soloAccId = "01955SOLOQU01";
            catalog.getOrCreateAccount(soloAccId, AccountProfile.HUMAN_SOLO, PrincipalKind.HUMAN, "ns-acme");
            catalog.createNamespace(soloAccId, "solo-ns-1", NamespaceType.PROJECT);
            catalog.createNamespace(soloAccId, "solo-ns-2", NamespaceType.PROJECT);
            catalog.createNamespace(soloAccId, "solo-ns-3", NamespaceType.PROJECT);

            Counter accountCounterBefore = meterRegistry.find("spector.quota.breach.total")
                    .tag("level", "account")
                    .tag("tenant_id", "ns-acme")
                    .tag("resource", "namespaces")
                    .counter();
            double beforeAccountBreach = accountCounterBefore != null ? accountCounterBefore.count() : 0.0;

            assertThatThrownBy(() -> catalog.createNamespace(soloAccId, "solo-ns-overflow", NamespaceType.PROJECT))
                    .isInstanceOf(AccountQuotaExceededException.class);

            Counter accountCounterAfter = meterRegistry.find("spector.quota.breach.total")
                    .tag("level", "account")
                    .tag("tenant_id", "ns-acme")
                    .tag("resource", "namespaces")
                    .counter();
            assertThat(accountCounterAfter).isNotNull();
            assertThat(accountCounterAfter.count()).isEqualTo(beforeAccountBreach + 1.0);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 3. ARCHIVE, RESTORE, TOMBSTONE & READ-ONLY ENFORCEMENT
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. Archive (Read-Only), Restore & Tombstone Lifecycle")
    class ArchiveRestoreTombstoneTests {

        @Test
        @DisplayName("Archive places namespace in read-only mode (rejects writes with 403); restore re-enables writes; tombstone marks TOMBSTONED")
        void archiveRestoreAndTombstoneLifecycle() throws Exception {
            // 1. Archive acmeProjectNs
            mvc.perform(post("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId() + "/archive")
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("ARCHIVED")));

            // 2. Writing to an ARCHIVED namespace via POST /api/v1/memory is rejected with 403 SPE-800-002 (read-only)
            mvc.perform(post("/api/v1/memory")
                            .header("X-Spector-Namespace", acmeProjectNs.namespaceId())
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"Write to archived namespace\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("SPE-800-002")));

            // 3. Restore acmeProjectNs -> status returns to ACTIVE
            mvc.perform(post("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId() + "/restore")
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("ACTIVE")));

            // 4. Tombstone acmeProjectNs via DELETE -> status becomes TOMBSTONED
            mvc.perform(delete("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId())
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("TOMBSTONED")));

            // 5. Restoring a TOMBSTONED namespace is rejected with 404 SPE-800-007 (NamespaceTombstonedException)
            mvc.perform(post("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId() + "/restore")
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error", is("SPE-800-007")));

            // 6. Default namespace cannot be archived or tombstoned -> 409 SPE-800-011
            NamespaceRecord defaultNs = catalog.resolve(acmeAdminId, "default").orElseThrow();
            mvc.perform(post("/api/v1/admin/namespaces/" + defaultNs.namespaceId() + "/archive")
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error", is("SPE-800-011")));

            mvc.perform(delete("/api/v1/admin/namespaces/" + defaultNs.namespaceId())
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error", is("SPE-800-011")));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 4. LEGAL HOLD GOVERNANCE & DESTRUCTIVE REFUSAL
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. Legal Hold Governance & Refusal of Archive/Tombstone/Transfer/Erasure")
    class LegalHoldGovernanceTests {

        @Test
        @DisplayName("Enabling legal hold blocks archive, tombstone, ownership transfer, and erasure with 409 SPE-800-008")
        void legalHoldBlocksDestructiveAndMutatingLifecycleActions() throws Exception {
            // 1. Place acmeProjectNs under legal hold
            mvc.perform(post("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId() + "/legal-hold")
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"legalHold\":true,\"reason\":\"Subpoena preservation #2026-99\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.legalHold", is(true)));

            // 2. Archive refused with 409 SPE-800-008
            mvc.perform(post("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId() + "/archive")
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error", is("SPE-800-008")));

            // 3. Tombstone refused with 409 SPE-800-008
            mvc.perform(delete("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId())
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error", is("SPE-800-008")));

            // 4. Ownership transfer refused with 409 SPE-800-008
            mvc.perform(post("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId() + "/transfer-ownership")
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newOwnerAccountId\":\"" + acmeColleagueId + "\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error", is("SPE-800-008")));

            // 5. Compliance erasure refused with 409 SPE-800-008
            mvc.perform(post("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId() + "/erase")
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"propagateReplicas\":false,\"reason\":\"GDPR erasure\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error", is("SPE-800-008")));

            // 6. Release legal hold
            mvc.perform(post("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId() + "/legal-hold")
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"legalHold\":false,\"reason\":\"Matter resolved\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.legalHold", is(false)));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 5. OWNERSHIP TRANSFER, GRANTS OVERSIGHT & COMPLIANCE ERASURE
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("5. Ownership Transfer, Grants Revocation & Audited Compliance Erasure")
    class OwnershipGrantsAndErasureTests {

        @Test
        @DisplayName("Transfer namespace ownership, inspect/revoke grants, execute compliance erasure, and verify audit log")
        void ownershipTransferGrantsAndErasure() throws Exception {
            // 1. Transfer ownership of acmeProjectNs from acmeAdminId to acmeColleagueId, retaining WRITER for previous owner
            Map<String, Object> transferReq = Map.of(
                    "newOwnerAccountId", acmeColleagueId,
                    "previousOwnerGrantRole", "WRITER",
                    "reason", "Team handoff"
            );

            mvc.perform(post("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId() + "/transfer-ownership")
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(transferReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ownerAccountId", is(acmeColleagueId)))
                    .andExpect(jsonPath("$.ownerUsername", is("ns-acme-colleague")))
                    .andExpect(jsonPath("$.grantsCount", is(2)));

            // 2. Cross-tenant ownership transfer is rejected with 403 SPE-820-001
            mvc.perform(post("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId() + "/transfer-ownership")
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newOwnerAccountId\":\"" + betaAdminId + "\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("SPE-820-001")));

            // 3. List grants on acmeProjectNs -> shows OWNER (acmeColleagueId) and WRITER (acmeAdminId)
            MvcResult grantsRes = mvc.perform(get("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId() + "/grants")
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", is(2)))
                    .andExpect(jsonPath("$[*].role", hasItem("OWNER")))
                    .andExpect(jsonPath("$[*].role", hasItem("WRITER")))
                    .andReturn();

            JsonNode grantsArray = mapper.readTree(grantsRes.getResponse().getContentAsString());
            String writerGrantId = null;
            String ownerGrantId = null;
            for (JsonNode g : grantsArray) {
                if ("WRITER".equals(g.get("role").asText())) {
                    writerGrantId = g.get("grantId").asText();
                } else if ("OWNER".equals(g.get("role").asText())) {
                    ownerGrantId = g.get("grantId").asText();
                }
            }
            assertThat(writerGrantId).isNotNull();
            assertThat(ownerGrantId).isNotNull();

            // 4. Attempting to revoke the OWNER grant returns 409 Conflict
            mvc.perform(delete("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId() + "/grants/" + ownerGrantId)
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isConflict());

            // 5. Revoke the WRITER grant -> returns 204 No Content
            mvc.perform(delete("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId() + "/grants/" + writerGrantId)
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isNoContent());

            // 6. Execute compliance erasure -> invokes TenantErasureService, returns ErasureAuditReport, tombstones namespace
            mvc.perform(post("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId() + "/erase")
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"propagateReplicas\":false,\"reason\":\"GDPR Art. 17 right to erasure\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.namespaceId", is(acmeProjectNs.namespaceId())))
                    .andExpect(jsonPath("$.accountId", is(acmeColleagueId)))
                    .andExpect(jsonPath("$.erasedAt", notNullValue()))
                    .andExpect(jsonPath("$.erasureMechanism", is("FILESYSTEM_UNLINK_AND_OBJECT_PREFIX_DELETION")));

            // 7. Verify namespace is now TOMBSTONED
            mvc.perform(get("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId())
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("TOMBSTONED")));

            // 8. Verify audit trail contains TRANSFER_NAMESPACE_OWNERSHIP, REVOKE_NAMESPACE_GRANT, and ERASE_NAMESPACE
            mvc.perform(get("/api/v1/admin/namespaces/" + acmeProjectNs.namespaceId() + "/audit")
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(3)))
                    .andExpect(jsonPath("$[*].action", hasItem("TRANSFER_NAMESPACE_OWNERSHIP")))
                    .andExpect(jsonPath("$[*].action", hasItem("REVOKE_NAMESPACE_GRANT")))
                    .andExpect(jsonPath("$[*].action", hasItem("ERASE_NAMESPACE")));
        }
    }
}
