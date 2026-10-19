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
package com.spectrayan.spector.synapse.admin.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.events.EventBus;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.synapse.admin.audit.dto.AdminAuditDto.AuditEventDto;
import com.spectrayan.spector.synapse.admin.tenant.TenantService;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.CreateTenantRequest;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.AccountProfile;
import com.spectrayan.spector.synapse.catalog.Grant;
import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.PrincipalKind;
import com.spectrayan.spector.synapse.security.SpectorAuthorityMapper;
import com.spectrayan.spector.synapse.security.UserAccountStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
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
 * Integration tests for {@link AdminAuditController} and {@link AuditLogService} (Issue #1059).
 *
 * <p>Verifies:
 * <ol>
 *   <li>Every {@code /api/v1/admin/**} mutating endpoint produces an append-only {@code audit_event} row.</li>
 *   <li>Privileged mutations on credentials, namespaces/grants, config overrides, and break-glass actions
 *       are recorded in {@code audit_event} with zero secret or memory content leakage.</li>
 *   <li>Tenant isolation on {@code GET /api/v1/admin/audit} ({@code super-admin} fleet-wide vs
 *       tenant {@code admin} scoped to own tenant, failing closed with HTTP 403 {@code SPE-820-001}).</li>
 *   <li>Namespace owners and {@code ADMIN} grant holders can view audit events affecting their namespace
 *       via {@code GET /api/v1/namespaces/{slug}/audit}, while unauthorized users receive HTTP 403.</li>
 *   <li>SHA-256 tamper-evident hash chain verification ({@code prev_hash} / {@code entry_hash}) via
 *       {@code GET /api/v1/admin/audit/verify} detects any post-hoc row tampering.</li>
 *   <li>Real-time SIEM export publishes {@link AuditSpectorEvent} onto Spector's {@code EventBus}.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spector.auth.enabled=true",
        "spector.auth.jwt.secret=01234567890123456789012345678901",
        "spector.catalog.type=jdbc",
        "spector.features.connectors-enabled=true"
})
@DirtiesContext
@DisplayName("AdminAuditControllerTest — Append-Only Audit Log & Hash Chain (Issue #1059)")
class AdminAuditControllerTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private AccountCatalog catalog;

    @Autowired
    private UserAccountStore userAccountStore;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private SpectorMemory spectorMemory;

    @MockitoBean
    private com.spectrayan.spector.synapse.memory.MemoryAccessObject mao;

    private MockMvc mvc;
    private String superAdminId;
    private String tenantAAdminId;
    private String tenantBAdminId;
    private String ownerUserId;
    private String otherUserId;

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        SpectorMemoryAdmin mockAdmin = mock(SpectorMemoryAdmin.class);
        MemoryIndex mockIndex = mock(MemoryIndex.class);
        when(spectorMemory.admin()).thenReturn(mockAdmin);
        when(mockAdmin.index()).thenReturn(mockIndex);
        when(spectorMemory.totalMemories()).thenReturn(5);
        when(spectorMemory.memoryCount(any())).thenReturn(1);

        // Clean test state across nested runs
        jdbc.update("DELETE FROM break_glass_requests");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM admin_audit_log");
        jdbc.update("DELETE FROM credentials WHERE name IN ('openai-prod-key', 'cov-api-key')");
        jdbc.update("DELETE FROM grants WHERE principal_id IN (SELECT user_id FROM users WHERE tenant_id IN ('tenant-alpha', 'tenant-beta', 'tenant-cov') OR username IN ('super-operator-01', 'admin-alpha-01', 'admin-beta-01', 'owner-alpha-01', 'other-alpha-02', 'cov-account-user'))");
        jdbc.update("DELETE FROM namespaces WHERE slug IN ('cov-ns-1', 'cov-ns-2', 'cov-ns-3', 'owner-vault')");
        jdbc.update("DELETE FROM namespaces WHERE owner_account_id IN (SELECT user_id FROM users WHERE tenant_id IN ('tenant-alpha', 'tenant-beta', 'tenant-cov') OR username IN ('super-operator-01', 'admin-alpha-01', 'admin-beta-01', 'owner-alpha-01', 'other-alpha-02', 'cov-account-user'))");
        jdbc.update("DELETE FROM users WHERE tenant_id IN ('tenant-alpha', 'tenant-beta', 'tenant-cov') OR username IN ('super-operator-01', 'admin-alpha-01', 'admin-beta-01', 'owner-alpha-01', 'other-alpha-02', 'cov-account-user')");
        jdbc.update("DELETE FROM tenants WHERE tenant_id IN ('tenant-alpha', 'tenant-beta', 'tenant-cov')");

        tenantService.createTenant(
                new CreateTenantRequest("tenant-alpha", "Alpha Corp", "Alpha Org", "ENTERPRISE", null, false, null, null),
                "system", "default");
        tenantService.createTenant(
                new CreateTenantRequest("tenant-beta", "Beta Ltd", "Beta Org", "TEAM", null, false, null, null),
                "system", "default");

        superAdminId = seedUser("super-operator-01", "default", "super-admin");
        tenantAAdminId = seedUser("admin-alpha-01", "tenant-alpha", "admin");
        tenantBAdminId = seedUser("admin-beta-01", "tenant-beta", "admin");
        ownerUserId = seedUser("owner-alpha-01", "tenant-alpha", "editor");
        otherUserId = seedUser("other-alpha-02", "tenant-alpha", "editor");

        // Reset audit_event so each test starts with a clean chain after fixture setup
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM admin_audit_log");
    }

    private String seedUser(String username, String tenantId, String role) {
        String uid = userAccountStore.createUser(
                username, "Password123!", username + "@example.com", username,
                Set.of(role), Set.of(), false, tenantId);
        catalog.getOrCreateAccount(uid, AccountProfile.HUMAN_TEAM, PrincipalKind.HUMAN, tenantId);
        return uid;
    }

    private int countAuditEvents() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class);
        return count != null ? count : 0;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. 100% /api/v1/admin/** MUTATING ENDPOINT AUDIT COVERAGE
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. 100% /api/v1/admin/** Mutating Endpoint Audit Coverage")
    class CompleteAdminMutationCoverageTests {

        @Test
        @DisplayName("Every mutating /api/v1/admin/** endpoint registered in Spring MVC produces an audit_event row")
        void everyAdminMutatingEndpointProducesAuditEventRow() throws Exception {
            // 1. Discover all mutating endpoints under /api/v1/admin/**
            Set<RequestMethod> mutatingMethods = Set.of(
                    RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH
            );
            Set<String> discoveredAdminMutations = new LinkedHashSet<>();

            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
                RequestMappingInfo info = entry.getKey();
                Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                Set<String> patterns = info.getPathPatternsCondition() != null
                        ? info.getPathPatternsCondition().getPatternValues()
                        : Set.of();

                for (String pattern : patterns) {
                    if (pattern.startsWith("/api/v1/admin")) {
                        for (RequestMethod method : methods) {
                            if (mutatingMethods.contains(method)) {
                                discoveredAdminMutations.add(method.name() + " " + pattern);
                            }
                        }
                    }
                }
            }

            assertThat(discoveredAdminMutations)
                    .as("Should discover all mutating /api/v1/admin/** endpoints")
                    .isNotEmpty();

            Set<String> verifiedAdminMutations = new LinkedHashSet<>();

            // ── A. AdminTenantController (3 mutating endpoints) ──
            assertProducesAuditEvent(verifiedAdminMutations, "POST /api/v1/admin/tenants", () ->
                    mvc.perform(post("/api/v1/admin/tenants")
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin")))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(mapper.writeValueAsString(Map.of(
                                            "tenantId", "tenant-cov",
                                            "name", "Coverage Tenant",
                                            "description", "Coverage Org",
                                            "plan", "TEAM"
                                    ))))
                            .andExpect(status().isCreated()));

            assertProducesAuditEvent(verifiedAdminMutations, "PUT /api/v1/admin/tenants/{tenantId}", () ->
                    mvc.perform(put("/api/v1/admin/tenants/tenant-cov")
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin")))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(mapper.writeValueAsString(Map.of(
                                            "name", "Coverage Tenant Updated",
                                            "plan", "ENTERPRISE"
                                    ))))
                            .andExpect(status().isOk()));

            assertProducesAuditEvent(verifiedAdminMutations, "DELETE /api/v1/admin/tenants/{tenantId}", () ->
                    mvc.perform(delete("/api/v1/admin/tenants/tenant-cov")
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin"))))
                            .andExpect(status().isOk()));

            // ── B. AdminAccountController (4 mutating endpoints) ──
            final String[] createdAccountId = new String[1];
            assertProducesAuditEvent(verifiedAdminMutations, "POST /api/v1/admin/accounts", () -> {
                MvcResult res = mvc.perform(post("/api/v1/admin/accounts")
                                .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(Map.of(
                                        "username", "cov-account-user",
                                        "password", "Password123!",
                                        "tenantId", "tenant-alpha",
                                        "kind", "HUMAN",
                                        "roles", List.of("editor")
                                ))))
                        .andExpect(status().isCreated())
                        .andReturn();
                createdAccountId[0] = mapper.readTree(res.getResponse().getContentAsString()).get("accountId").asText();
            });

            assertProducesAuditEvent(verifiedAdminMutations, "PUT /api/v1/admin/accounts/{accountId}", () ->
                    mvc.perform(put("/api/v1/admin/accounts/" + createdAccountId[0])
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin")))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(mapper.writeValueAsString(Map.of(
                                            "displayName", "Coverage Account Updated",
                                            "roles", List.of("editor")
                                    ))))
                            .andExpect(status().isOk()));

            assertProducesAuditEvent(verifiedAdminMutations, "POST /api/v1/admin/accounts/{accountId}/tenant", () ->
                    mvc.perform(post("/api/v1/admin/accounts/" + createdAccountId[0] + "/tenant")
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin")))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(mapper.writeValueAsString(Map.of(
                                            "tenantId", "tenant-beta"
                                    ))))
                            .andExpect(status().isOk()));

            assertProducesAuditEvent(verifiedAdminMutations,
                    "POST /api/v1/admin/accounts/{accountId}/reset-credentials", () ->
                            mvc.perform(post("/api/v1/admin/accounts/" + createdAccountId[0] + "/reset-credentials")
                                            .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin")))
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(mapper.writeValueAsString(Map.of(
                                                    "revokeRefreshTokens", true,
                                                    "revokeApiKeys", true
                                            ))))
                                    .andExpect(status().isOk()));

            // ── C. AdminNamespaceController (8 mutating endpoints) ──
            NamespaceRecord ns1 = catalog.createNamespace(
                    ownerUserId, "cov-ns-1", NamespaceType.PROJECT, "Coverage NS 1", "desc", null);
            NamespaceRecord ns2 = catalog.createNamespace(
                    ownerUserId, "cov-ns-2", NamespaceType.PROJECT, "Coverage NS 2", "desc", null);
            Grant grant = catalog.grantNamespace(ns1.ownerAccountId(), ns1.namespaceId(), otherUserId,
                    GrantRole.READER, null, null);

            assertProducesAuditEvent(verifiedAdminMutations, "PUT /api/v1/admin/namespaces/{namespaceId}/quotas", () ->
                    mvc.perform(put("/api/v1/admin/namespaces/" + ns1.namespaceId() + "/quotas")
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin")))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(mapper.writeValueAsString(Map.of(
                                            "maxMemories", 5000L,
                                            "maxStorageBytes", 10485760L,
                                            "maxOpsPerMinute", 300
                                    ))))
                            .andExpect(status().isOk()));

            assertProducesAuditEvent(verifiedAdminMutations, "POST /api/v1/admin/namespaces/{namespaceId}/archive", () ->
                    mvc.perform(post("/api/v1/admin/namespaces/" + ns1.namespaceId() + "/archive")
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin"))))
                            .andExpect(status().isOk()));

            assertProducesAuditEvent(verifiedAdminMutations, "POST /api/v1/admin/namespaces/{namespaceId}/restore", () ->
                    mvc.perform(post("/api/v1/admin/namespaces/" + ns1.namespaceId() + "/restore")
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin"))))
                            .andExpect(status().isOk()));

            assertProducesAuditEvent(verifiedAdminMutations, "POST /api/v1/admin/namespaces/{namespaceId}/legal-hold", () ->
                    mvc.perform(post("/api/v1/admin/namespaces/" + ns1.namespaceId() + "/legal-hold")
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin")))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(mapper.writeValueAsString(Map.of(
                                            "legalHold", false,
                                            "reason", "Release hold for test"
                                    ))))
                            .andExpect(status().isOk()));

            assertProducesAuditEvent(verifiedAdminMutations,
                    "POST /api/v1/admin/namespaces/{namespaceId}/transfer-ownership", () ->
                            mvc.perform(post("/api/v1/admin/namespaces/" + ns1.namespaceId() + "/transfer-ownership")
                                            .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin")))
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(mapper.writeValueAsString(Map.of(
                                                    "newOwnerAccountId", otherUserId,
                                                    "previousOwnerGrantRole", "READER",
                                                    "reason", "Team handoff"
                                            ))))
                                    .andExpect(status().isOk()));

            assertProducesAuditEvent(verifiedAdminMutations,
                    "DELETE /api/v1/admin/namespaces/{namespaceId}/grants/{grantId}", () ->
                            mvc.perform(delete("/api/v1/admin/namespaces/" + ns1.namespaceId() + "/grants/" + grant.grantId())
                                            .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin"))))
                                    .andExpect(status().isNoContent()));

            assertProducesAuditEvent(verifiedAdminMutations, "DELETE /api/v1/admin/namespaces/{namespaceId}", () ->
                    mvc.perform(delete("/api/v1/admin/namespaces/" + ns1.namespaceId())
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin"))))
                            .andExpect(status().isOk()));

            assertProducesAuditEvent(verifiedAdminMutations, "POST /api/v1/admin/namespaces/{namespaceId}/erase", () ->
                    mvc.perform(post("/api/v1/admin/namespaces/" + ns2.namespaceId() + "/erase")
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin")))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(mapper.writeValueAsString(Map.of(
                                            "confirmationSlug", "cov-ns-2",
                                            "reason", "GDPR erasure",
                                            "includeSearchIndex", false
                                    ))))
                            .andExpect(status().isOk()));

            // ── D. CacheController (3 mutating endpoints under /api/v1/admin/cache) ──
            String targetCache = com.spectrayan.spector.synapse.config.cache.SynapseCacheConstants.ALL_CACHES[0];

            assertProducesAuditEvent(verifiedAdminMutations, "DELETE /api/v1/admin/cache", () ->
                    mvc.perform(delete("/api/v1/admin/cache")
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin"))))
                            .andExpect(status().isOk()));

            assertProducesAuditEvent(verifiedAdminMutations, "DELETE /api/v1/admin/cache/{cacheName}", () ->
                    mvc.perform(delete("/api/v1/admin/cache/" + targetCache)
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin"))))
                            .andExpect(status().isOk()));

            assertProducesAuditEvent(verifiedAdminMutations, "DELETE /api/v1/admin/cache/{cacheName}/{key}", () ->
                    mvc.perform(delete("/api/v1/admin/cache/" + targetCache + "/sample-key")
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin"))))
                            .andExpect(status().isOk()));

            // ── E. AdminBreakGlassController (4 mutating endpoints under /api/v1/admin/break-glass) ──
            NamespaceRecord ns3 = catalog.createNamespace(
                    ownerUserId, "cov-ns-3", NamespaceType.PROJECT, "Coverage NS 3", "desc", null);
            final String[] bgId1 = new String[1];

            assertProducesAuditEvent(verifiedAdminMutations, "POST /api/v1/admin/break-glass", () -> {
                MvcResult res = mvc.perform(post("/api/v1/admin/break-glass")
                                .with(user(tenantAAdminId).authorities(SpectorAuthorityMapper.forRole("admin")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(Map.of(
                                        "namespaceId", ns3.namespaceId(),
                                        "justification", "SEV-1 incident diagnostics",
                                        "ticketRef", "INC-1060-COV",
                                        "durationMinutes", 30
                                ))))
                        .andExpect(status().isCreated())
                        .andReturn();
                bgId1[0] = mapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
            });

            assertProducesAuditEvent(verifiedAdminMutations, "POST /api/v1/admin/break-glass/{id}/approve", () ->
                    mvc.perform(post("/api/v1/admin/break-glass/" + bgId1[0] + "/approve")
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin")))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(mapper.writeValueAsString(Map.of(
                                            "comment", "Dual-control approved"
                                    ))))
                            .andExpect(status().isOk()));

            assertProducesAuditEvent(verifiedAdminMutations, "POST /api/v1/admin/break-glass/{id}/revoke", () ->
                    mvc.perform(post("/api/v1/admin/break-glass/" + bgId1[0] + "/revoke")
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin")))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(mapper.writeValueAsString(Map.of(
                                            "reason", "Investigation completed"
                                    ))))
                            .andExpect(status().isOk()));

            MvcResult bgReq2 = mvc.perform(post("/api/v1/admin/break-glass")
                            .with(user(tenantAAdminId).authorities(SpectorAuthorityMapper.forRole("admin")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of(
                                    "namespaceId", ns3.namespaceId(),
                                    "justification", "Second break-glass request to test rejection",
                                    "durationMinutes", 15
                            ))))
                    .andExpect(status().isCreated())
                    .andReturn();
            String bgId2 = mapper.readTree(bgReq2.getResponse().getContentAsString()).get("id").asText();

            assertProducesAuditEvent(verifiedAdminMutations, "POST /api/v1/admin/break-glass/{id}/reject", () ->
                    mvc.perform(post("/api/v1/admin/break-glass/" + bgId2 + "/reject")
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin")))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(mapper.writeValueAsString(Map.of(
                                            "reason", "Rejected due to missing ticket reference"
                                    ))))
                            .andExpect(status().isOk()));

            // ── F. AdminTenantKeyController (6 mutating endpoints under /api/v1/admin/tenants/{tenantId}/keys) ──
            assertProducesAuditEvent(verifiedAdminMutations, "PUT /api/v1/admin/tenants/{tenantId}/keys", () ->
                    mvc.perform(put("/api/v1/admin/tenants/tenant-alpha/keys")
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin")))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(mapper.writeValueAsString(Map.of(
                                            "provider", "local-file",
                                            "kekKeyId", "tenant-alpha-audit-kek-v1"
                                    ))))
                            .andExpect(status().isOk()));

            assertProducesAuditEvent(verifiedAdminMutations,
                    "POST /api/v1/admin/tenants/{tenantId}/keys/validate", () ->
                            mvc.perform(post("/api/v1/admin/tenants/tenant-alpha/keys/validate")
                                            .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin"))))
                                    .andExpect(status().isOk()));

            assertProducesAuditEvent(verifiedAdminMutations,
                    "POST /api/v1/admin/tenants/{tenantId}/keys/rotate-kek", () ->
                            mvc.perform(post("/api/v1/admin/tenants/tenant-alpha/keys/rotate-kek")
                                            .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin")))
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(mapper.writeValueAsString(Map.of(
                                                    "newKekKeyId", "tenant-alpha-audit-kek-v2",
                                                    "newKekVersion", "v2"
                                            ))))
                                    .andExpect(status().isOk()));

            assertProducesAuditEvent(verifiedAdminMutations,
                    "POST /api/v1/admin/tenants/{tenantId}/keys/rotate-dek", () ->
                            mvc.perform(post("/api/v1/admin/tenants/tenant-alpha/keys/rotate-dek")
                                            .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin")))
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(mapper.writeValueAsString(Map.of(
                                                    "namespaceId", "cov-ns-3"
                                            ))))
                                    .andExpect(status().isOk()));

            assertProducesAuditEvent(verifiedAdminMutations,
                    "POST /api/v1/admin/tenants/{tenantId}/keys/revoke", () ->
                            mvc.perform(post("/api/v1/admin/tenants/tenant-alpha/keys/revoke")
                                            .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin")))
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(mapper.writeValueAsString(Map.of(
                                                    "reason", "Audit coverage test revocation"
                                            ))))
                                    .andExpect(status().isOk()));

            assertProducesAuditEvent(verifiedAdminMutations, "DELETE /api/v1/admin/tenants/{tenantId}/keys", () ->
                    mvc.perform(delete("/api/v1/admin/tenants/tenant-alpha/keys")
                                    .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin"))))
                            .andExpect(status().isOk()));

            // Assert 100% coverage of all discovered /api/v1/admin/** mutating routes
            assertThat(verifiedAdminMutations)
                    .as("Every mutating /api/v1/admin/** endpoint must be tested and verified to write to audit_event")
                    .containsAll(discoveredAdminMutations);
        }

        private void assertProducesAuditEvent(
                Set<String> verifiedSet,
                String endpointSignature,
                ThrowingRunnable invocation
        ) throws Exception {
            int before = countAuditEvents();
            invocation.run();
            int after = countAuditEvents();
            assertThat(after)
                    .as("Endpoint '%s' must append at least one row to audit_event", endpointSignature)
                    .isGreaterThan(before);
            verifiedSet.add(endpointSignature);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. PRIVILEGED CREDENTIAL, NAMESPACE & CONFIG OVERRIDE AUDIT COVERAGE
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. Credential, Namespace & Config Override Audit Coverage")
    class PrivilegedNonAdminPrefixMutationTests {

        @Test
        @DisplayName("Credential creation, update, and deletion record content-free audit events")
        void credentialLifecycleRecordsAuditEventsWithoutSecrets() throws Exception {
            String rawSecretCanary = "sk_live_SUPER_SECRET_CANARY_VALUE_999";

            // Create credential
            mvc.perform(post("/api/v1/credentials")
                            .with(user(ownerUserId).authorities(SpectorAuthorityMapper.forRole("editor")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of(
                                    "name", "openai-prod-key",
                                    "category", "LLM",
                                    "provider", "openai",
                                    "credentialType", "API_KEY",
                                    "secret", rawSecretCanary
                            ))))
                    .andExpect(status().isCreated());

            // Update credential
            mvc.perform(put("/api/v1/credentials/openai-prod-key")
                            .with(user(ownerUserId).authorities(SpectorAuthorityMapper.forRole("editor")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of(
                                    "name", "openai-prod-key",
                                    "category", "LLM",
                                    "provider", "openai",
                                    "credentialType", "API_KEY",
                                    "secret", rawSecretCanary + "_rotated"
                            ))))
                    .andExpect(status().isOk());

            // Delete credential
            mvc.perform(delete("/api/v1/credentials/openai-prod-key")
                            .with(user(ownerUserId).authorities(SpectorAuthorityMapper.forRole("editor"))))
                    .andExpect(status().isNoContent());

            MvcResult auditRes = mvc.perform(get("/api/v1/admin/audit")
                            .param("tenantId", "tenant-alpha")
                            .param("actorId", ownerUserId)
                            .param("targetType", "CREDENTIAL")
                            .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].action", hasItem("CREATE_CREDENTIAL")))
                    .andExpect(jsonPath("$[*].action", hasItem("UPDATE_CREDENTIAL")))
                    .andExpect(jsonPath("$[*].action", hasItem("DELETE_CREDENTIAL")))
                    .andReturn();

            // Verify raw secret is NEVER written into audit_event
            assertThat(auditRes.getResponse().getContentAsString()).doesNotContain(rawSecretCanary);
        }

        @Test
        @DisplayName("Config override save and delete record audit events")
        void configOverrideMutationsRecordAuditEvents() throws Exception {
            mvc.perform(put("/api/v1/config/memory")
                            .with(user(tenantAAdminId).authorities(SpectorAuthorityMapper.forRole("admin")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of(
                                    "scope", "user",
                                    "values", Map.of("decayRate", 0.05)
                            ))))
                    .andExpect(status().isOk());

            mvc.perform(delete("/api/v1/config/memory")
                            .param("scope", "user")
                            .with(user(tenantAAdminId).authorities(SpectorAuthorityMapper.forRole("admin"))))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/v1/admin/audit")
                            .param("actorId", tenantAAdminId)
                            .param("targetType", "CONFIG")
                            .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].action", hasItem("SAVE_CONFIG_OVERRIDE")))
                    .andExpect(jsonPath("$[*].action", hasItem("DELETE_CONFIG_OVERRIDE")));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. TENANT ISOLATION ON GET /api/v1/admin/audit
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. Tenant Isolation on GET /api/v1/admin/audit")
    class TenantIsolationTests {

        @Test
        @DisplayName("Tenant Admin sees only own tenant's audit events and receives 403 SPE-820-001 on cross-tenant query")
        void tenantAdminScopedToOwnTenantAndBlockedFromCrossTenant() throws Exception {
            // Record an audit event in tenant-alpha and one in tenant-beta
            auditLogService.record(
                    "tenant-alpha", tenantAAdminId, "admin",
                    "SUSPEND_ACCOUNT", "ACCOUNT", "acc-alpha-1", null,
                    "Alpha reason", "req-alpha-1", AuditLogService.OUTCOME_SUCCESS,
                    Map.of("target", "alpha")
            );
            auditLogService.record(
                    "tenant-beta", tenantBAdminId, "admin",
                    "SUSPEND_ACCOUNT", "ACCOUNT", "acc-beta-1", null,
                    "Beta reason", "req-beta-1", AuditLogService.OUTCOME_SUCCESS,
                    Map.of("target", "beta")
            );

            // 1. Tenant A Admin querying without tenantId parameter receives ONLY tenant-alpha events
            MvcResult alphaRes = mvc.perform(get("/api/v1/admin/audit")
                            .with(user(tenantAAdminId).authorities(SpectorAuthorityMapper.forRole("admin"))))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode alphaArray = mapper.readTree(alphaRes.getResponse().getContentAsString());
            assertThat(alphaArray.isArray()).isTrue();
            assertThat(alphaArray.size()).isGreaterThanOrEqualTo(1);
            for (JsonNode node : alphaArray) {
                assertThat(node.get("tenantId").asText()).isEqualTo("tenant-alpha");
            }

            // 2. Tenant A Admin attempting to query tenant-beta fails closed with HTTP 403 SPE-820-001
            mvc.perform(get("/api/v1/admin/audit")
                            .param("tenantId", "tenant-beta")
                            .with(user(tenantAAdminId).authorities(SpectorAuthorityMapper.forRole("admin"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("SPE-820-001")));

            // Verify that the denied cross-tenant audit query attempt itself was logged with outcome=DENIED
            mvc.perform(get("/api/v1/admin/audit")
                            .param("tenantId", "tenant-alpha")
                            .param("actorId", tenantAAdminId)
                            .param("action", "QUERY_AUDIT_LOG")
                            .param("outcome", "DENIED")
                            .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));

            // 3. Super-Admin can query fleet-wide (sees both tenant-alpha and tenant-beta)
            MvcResult fleetRes = mvc.perform(get("/api/v1/admin/audit")
                            .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].tenantId", hasItem("tenant-alpha")))
                    .andExpect(jsonPath("$[*].tenantId", hasItem("tenant-beta")))
                    .andReturn();
            assertThat(mapper.readTree(fleetRes.getResponse().getContentAsString()).size()).isGreaterThanOrEqualTo(2);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. NAMESPACE OWNER VISIBILITY ON GET /api/v1/namespaces/{slug}/audit
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. Namespace Owner Visibility on GET /api/v1/namespaces/{slug}/audit")
    class NamespaceOwnerAuditVisibilityTests {

        @Test
        @DisplayName("Namespace owner sees admin actions and break-glass events affecting their namespace; non-owner gets 403")
        void namespaceOwnerSeesAuditEventsAndBreakGlassOnOwnNamespace() throws Exception {
            NamespaceRecord ns = catalog.createNamespace(
                    ownerUserId, "owner-vault", NamespaceType.PROJECT, "Owner Vault", "desc", null);

            // Admin updates quotas on owner's namespace
            mvc.perform(put("/api/v1/admin/namespaces/" + ns.namespaceId() + "/quotas")
                            .with(user(tenantAAdminId).authorities(SpectorAuthorityMapper.forRole("admin")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("maxMemories", 10000L))))
                    .andExpect(status().isOk());

            // Platform Operator performs a break-glass action on owner's namespace
            auditLogService.recordBreakGlass(
                    "BREAK_GLASS_INSPECT_METADATA",
                    "tenant-alpha",
                    ns.namespaceId(),
                    ns.namespaceId(),
                    "INC-2026-9912: P1 production incident investigation",
                    AuditLogService.OUTCOME_SUCCESS,
                    Map.of("ticketId", "INC-2026-9912")
            );

            // Namespace owner (ROLE_editor) queries GET /api/v1/namespaces/{slug}/audit -> 200 OK
            mvc.perform(get("/api/v1/namespaces/owner-vault/audit")
                            .with(user(ownerUserId).authorities(SpectorAuthorityMapper.forRole("editor"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(2)))
                    .andExpect(jsonPath("$[*].action", hasItem("UPDATE_NAMESPACE_QUOTAS")))
                    .andExpect(jsonPath("$[*].action", hasItem("BREAK_GLASS_INSPECT_METADATA")))
                    .andExpect(jsonPath("$[*].reason", hasItem("INC-2026-9912: P1 production incident investigation")));

            // Unrelated user (otherUserId) without grant querying by namespaceId receives HTTP 403 Forbidden
            mvc.perform(get("/api/v1/namespaces/" + ns.namespaceId() + "/audit")
                            .with(user(otherUserId).authorities(SpectorAuthorityMapper.forRole("editor"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("SPE-800-002")));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. SHA-256 HASH CHAIN VERIFICATION & TAMPER DETECTION
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("5. Tamper-Evident SHA-256 Hash Chain Verification")
    class HashChainTamperDetectionTests {

        @Test
        @DisplayName("GET /api/v1/admin/audit/verify passes on intact chain and detects tampered row")
        void verifyChainDetectsTampering() throws Exception {
            AuditEventDto e1 = auditLogService.record(
                    "tenant-alpha", tenantAAdminId, "admin",
                    "CREATE_ACCOUNT", "ACCOUNT", "acc-1", null,
                    "Initial onboarding", "req-1", AuditLogService.OUTCOME_SUCCESS,
                    Map.of("step", 1)
            );
            AuditEventDto e2 = auditLogService.record(
                    "tenant-alpha", tenantAAdminId, "admin",
                    "SUSPEND_ACCOUNT", "ACCOUNT", "acc-1", null,
                    "Security review", "req-2", AuditLogService.OUTCOME_SUCCESS,
                    Map.of("step", 2)
            );
            AuditEventDto e3 = auditLogService.record(
                    "tenant-alpha", tenantAAdminId, "admin",
                    "REACTIVATE_ACCOUNT", "ACCOUNT", "acc-1", null,
                    "Cleared review", "req-3", AuditLogService.OUTCOME_SUCCESS,
                    Map.of("step", 3)
            );

            assertThat(e1.prevHash()).isEqualTo(AuditLogService.GENESIS_HASH);
            assertThat(e2.prevHash()).isEqualTo(e1.entryHash());
            assertThat(e3.prevHash()).isEqualTo(e2.entryHash());

            // 1. Intact chain verifies cleanly via REST endpoint
            mvc.perform(get("/api/v1/admin/audit/verify")
                            .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid", is(true)))
                    .andExpect(jsonPath("$.checkedCount", greaterThanOrEqualTo(3)));

            // 2. Simulate rogue DB tampering on event e2's reason column
            jdbc.update("UPDATE audit_event SET reason = 'Tampered cover-up reason' WHERE id = ?", e2.id());

            // 3. Hash chain verification immediately detects the tampered event
            mvc.perform(get("/api/v1/admin/audit/verify")
                            .with(user(superAdminId).authorities(SpectorAuthorityMapper.forRole("super-admin"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid", is(false)))
                    .andExpect(jsonPath("$.firstInvalidEventId", is(e2.id())))
                    .andExpect(jsonPath("$.message", notNullValue()));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. SIEM EVENTBUS STREAMING & RETENTION POLICY
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("6. SIEM EventBus Streaming & Retention Policy")
    class SiemStreamingAndRetentionTests {

        @Test
        @DisplayName("Subscribing to auditEventBus() receives real-time AuditSpectorEvent for SIEM export")
        void siemSubscriberReceivesAuditSpectorEvents() {
            List<AuditSpectorEvent> received = new CopyOnWriteArrayList<>();
            EventBus.Subscription sub = auditLogService.auditEventBus()
                    .subscribe(AuditSpectorEvent.class, received::add);
            try {
                AuditEventDto dto = auditLogService.record(
                        "tenant-alpha", tenantAAdminId, "admin",
                        "ROTATE_BYOK_KEY", "TENANT", "tenant-alpha", null,
                        "Scheduled key rotation", "req-siem-1", AuditLogService.OUTCOME_SUCCESS,
                        Map.of("kmsProvider", "AWS_KMS")
                );

                assertThat(received).hasSize(1);
                AuditSpectorEvent siemEvent = received.getFirst();
                assertThat(siemEvent.auditEvent().id()).isEqualTo(dto.id());
                assertThat(siemEvent.auditEvent().action()).isEqualTo("ROTATE_BYOK_KEY");
                assertThat(siemEvent.auditEvent().entryHash()).isEqualTo(dto.entryHash());
                assertThat(siemEvent.auditEvent().tenantId()).isEqualTo("tenant-alpha");
            } finally {
                sub.cancel();
            }
        }
    }
}
