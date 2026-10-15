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
package com.spectrayan.spector.synapse.admin.account;

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
import com.spectrayan.spector.synapse.admin.tenant.TenantService;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.CreateTenantRequest;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.TenantQuotasDto;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.AccountProfile;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.PrincipalKind;
import com.spectrayan.spector.synapse.connector.model.CredentialCategory;
import com.spectrayan.spector.synapse.connector.model.CredentialRecord;
import com.spectrayan.spector.synapse.connector.repository.CredentialRepository;
import com.spectrayan.spector.synapse.security.RefreshTokenStore;
import com.spectrayan.spector.synapse.security.UserAccountStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AdminAccountController} and the Admin Account & Agent-Principal
 * Registry at {@code /api/v1/admin/accounts} (ADR-0087, Issue #1056).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spector.auth.enabled=true",
        "spector.auth.jwt.secret=01234567890123456789012345678901",
        "spector.catalog.type=jdbc"
})
@DirtiesContext
@DisplayName("AdminAccountController & Agent-Principal Registry Integration Tests (Issue #1056)")
class AdminAccountControllerTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private AccountCatalog catalog;

    @Autowired
    private UserAccountStore userAccountStore;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @MockitoBean
    private SpectorMemory spectorMemory;

    private MockMvc mvc;
    private String acmeAdminId;
    private String betaAdminId;

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        jdbc.update("DELETE FROM grants WHERE principal_id IN (SELECT user_id FROM users WHERE tenant_id IN ('acme-t', 'beta-t', 'quota-t'))", Map.of());
        jdbc.update("DELETE FROM namespaces WHERE owner_account_id IN (SELECT user_id FROM users WHERE tenant_id IN ('acme-t', 'beta-t', 'quota-t'))", Map.of());
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT user_id FROM users WHERE tenant_id IN ('acme-t', 'beta-t', 'quota-t'))", Map.of());
        jdbc.update("DELETE FROM credentials WHERE tenant_id IN ('acme-t', 'beta-t', 'quota-t')", Map.of());
        jdbc.update("DELETE FROM users WHERE tenant_id IN ('acme-t', 'beta-t', 'quota-t') OR username IN ('acme-admin', 'beta-admin', 'alice-human', 'bob-beta', 'forge-agent', 'sentinel-svc', 'quota-u1', 'quota-u2')", Map.of());
        jdbc.update("DELETE FROM tenants WHERE tenant_id IN ('acme-t', 'beta-t', 'quota-t')", Map.of());

        tenantService.createTenant(
                new CreateTenantRequest("acme-t", "Acme Tenant", "Acme", "ENTERPRISE", null, false, null, null),
                "system", "default");
        tenantService.createTenant(
                new CreateTenantRequest("beta-t", "Beta Tenant", "Beta", "TEAM", null, false, null, null),
                "system", "default");

        acmeAdminId = userAccountStore.createUser(
                "acme-admin", "Pass1234!", "admin@acme.test", "Acme Admin",
                Set.of("admin"), Set.of("admin:read", "admin:write"), false, "acme-t");
        catalog.getOrCreateAccount(acmeAdminId, AccountProfile.HUMAN_TEAM, PrincipalKind.HUMAN, "acme-t");

        betaAdminId = userAccountStore.createUser(
                "beta-admin", "Pass1234!", "admin@beta.test", "Beta Admin",
                Set.of("admin"), Set.of("admin:read", "admin:write"), false, "beta-t");
        catalog.getOrCreateAccount(betaAdminId, AccountProfile.HUMAN_TEAM, PrincipalKind.HUMAN, "beta-t");
    }

    // ══════════════════════════════════════════════════════════════
    // 1. TENANT ISOLATION & ROLE BOUNDARIES
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. Tenant Admin Isolation & Role Boundaries")
    class TenantIsolationTests {

        @Test
        @DisplayName("Unprivileged roles (viewer, editor, agent) are rejected with 403 Forbidden")
        void nonAdminRolesRejectedWith403() throws Exception {
            mvc.perform(get("/api/v1/admin/accounts")
                            .with(user("viewer-1").roles("viewer")))
                    .andExpect(status().isForbidden());

            mvc.perform(get("/api/v1/admin/accounts")
                            .with(user("editor-1").roles("editor")))
                    .andExpect(status().isForbidden());

            mvc.perform(get("/api/v1/admin/accounts")
                            .with(user("agent-1").roles("agent")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Tenant admin sees only accounts in its own tenant and gets 403 on cross-tenant access/mutation")
        void tenantAdminScopedStrictlyToOwnTenant() throws Exception {
            // 1. Acme admin lists accounts -> sees only acme-t accounts, never beta-t accounts
            mvc.perform(get("/api/v1/admin/accounts")
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].accountId", hasItem(acmeAdminId)))
                    .andExpect(jsonPath("$[*].accountId", not(hasItem(betaAdminId))));

            // 2. Acme admin attempting to filter by tenantId=beta-t gets 403 SPE-820-001
            mvc.perform(get("/api/v1/admin/accounts")
                            .param("tenantId", "beta-t")
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("SPE-820-001")));

            // 3. Acme admin attempting to GET betaAdminId gets 403 SPE-820-001
            mvc.perform(get("/api/v1/admin/accounts/" + betaAdminId)
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("SPE-820-001")));

            // 4. Acme admin attempting to PUT betaAdminId gets 403 SPE-820-001
            mvc.perform(put("/api/v1/admin/accounts/" + betaAdminId)
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":false}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("SPE-820-001")));

            // 5. Acme admin attempting to create an account in beta-t gets 403 SPE-820-001
            mvc.perform(post("/api/v1/admin/accounts")
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"cross-tenant-user\",\"tenantId\":\"beta-t\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("SPE-820-001")));

            // 6. Acme admin attempting to reassign tenant (super-admin only) gets 403
            mvc.perform(post("/api/v1/admin/accounts/" + acmeAdminId + "/tenant")
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"beta-t\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 2. HUMAN & AGENT PRINCIPAL REGISTRY, OWNER LINKAGE & GRANTS
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. Human & Agent Principals, Owner Linkage, Grants & Aggregates")
    class AgentPrincipalAndGrantsTests {

        @Test
        @DisplayName("Create human account and agent principal with ownerAccountId and initialGrants; verify linkage and aggregates")
        void createHumanAndAgentWithOwnerAndGrants() throws Exception {
            // 1. Create a HUMAN account in acme-t
            Map<String, Object> createHumanReq = Map.of(
                    "username", "alice-human",
                    "password", "AliceSecret123!",
                    "displayName", "Alice Developer",
                    "email", "alice@acme.test",
                    "kind", "HUMAN",
                    "profile", "HUMAN_TEAM",
                    "maxNamespaces", 15,
                    "maxHotNamespaces", 5
            );

            MvcResult humanRes = mvc.perform(post("/api/v1/admin/accounts")
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(createHumanReq)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username", is("alice-human")))
                    .andExpect(jsonPath("$.displayName", is("Alice Developer")))
                    .andExpect(jsonPath("$.tenantId", is("acme-t")))
                    .andExpect(jsonPath("$.kind", is("HUMAN")))
                    .andExpect(jsonPath("$.profile", is("HUMAN_TEAM")))
                    .andExpect(jsonPath("$.active", is(true)))
                    .andExpect(jsonPath("$.quotas.maxNamespaces", is(15)))
                    .andExpect(jsonPath("$.quotas.maxHotNamespaces", is(5)))
                    .andExpect(jsonPath("$.namespaceCount", is(1)))
                    .andReturn();

            JsonNode humanJson = mapper.readTree(humanRes.getResponse().getContentAsString());
            String aliceId = humanJson.get("accountId").asText();

            // Create a PROJECT namespace owned by Alice so the agent can be granted access to it
            NamespaceRecord projectNs = catalog.createNamespace(
                    aliceId, "project-alpha", NamespaceType.PROJECT, "Project Alpha", "Shared project memory", null);

            // 2. Create an AGENT principal owned by Alice with an initial grant on project-alpha
            Map<String, Object> createAgentReq = Map.of(
                    "username", "forge-agent",
                    "displayName", "Forge Coding Agent",
                    "kind", "AGENT",
                    "profile", "AGENT",
                    "ownerAccountId", aliceId,
                    "initialGrants", List.of(
                            Map.of(
                                    "namespaceId", projectNs.namespaceId(),
                                    "role", "AGENT"
                            )
                    )
            );

            MvcResult agentRes = mvc.perform(post("/api/v1/admin/accounts")
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(createAgentReq)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username", is("forge-agent")))
                    .andExpect(jsonPath("$.kind", is("AGENT")))
                    .andExpect(jsonPath("$.profile", is("AGENT")))
                    .andExpect(jsonPath("$.tenantId", is("acme-t")))
                    .andExpect(jsonPath("$.ownerAccountId", is(aliceId)))
                    .andExpect(jsonPath("$.ownerUsername", is("alice-human")))
                    .andExpect(jsonPath("$.ownerDisplayName", is("Alice Developer")))
                    .andExpect(jsonPath("$.namespaceGrants[*].namespaceId", hasItem(projectNs.namespaceId())))
                    .andExpect(jsonPath("$.namespaceGrants[*].namespaceSlug", hasItem("project-alpha")))
                    .andExpect(jsonPath("$.namespaceGrants[*].role", hasItem("WRITER")))
                    .andReturn();

            String agentId = mapper.readTree(agentRes.getResponse().getContentAsString()).get("accountId").asText();

            // 3. Verify Alice's account shows ownedAgents and 2 owned namespaces (default + project-alpha)
            mvc.perform(get("/api/v1/admin/accounts/" + aliceId)
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.namespaceCount", is(2)))
                    .andExpect(jsonPath("$.ownedAgents.length()", is(1)))
                    .andExpect(jsonPath("$.ownedAgents[0].accountId", is(agentId)))
                    .andExpect(jsonPath("$.ownedAgents[0].username", is("forge-agent")))
                    .andExpect(jsonPath("$.ownedAgents[0].kind", is("AGENT")));

            // 4. Verify GET /api/v1/admin/accounts/{aliceId}/agents endpoint
            mvc.perform(get("/api/v1/admin/accounts/" + aliceId + "/agents")
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", is(1)))
                    .andExpect(jsonPath("$[0].accountId", is(agentId)))
                    .andExpect(jsonPath("$[0].username", is("forge-agent")));

            // 5. Filter accounts by kind=AGENT and ownerAccountId=aliceId
            mvc.perform(get("/api/v1/admin/accounts")
                            .param("kind", "AGENT")
                            .param("ownerAccountId", aliceId)
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", is(1)))
                    .andExpect(jsonPath("$[0].accountId", is(agentId)));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 3. LIFECYCLE: SUSPEND, REACTIVATE, PROFILE, TENANT REASSIGN & RESET CREDENTIALS
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. Account Lifecycle, Credentials Reset, Quotas & Tenant Reassignment")
    class LifecycleAndCredentialsTests {

        @Test
        @DisplayName("Suspend/reactivate account, change profile, reset credentials, and reassign tenant (super-admin)")
        void accountLifecycleAndCredentialReset() throws Exception {
            // Create account
            MvcResult createRes = mvc.perform(post("/api/v1/admin/accounts")
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"sentinel-svc\",\"kind\":\"SERVICE\",\"profile\":\"AGENT\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            String svcId = mapper.readTree(createRes.getResponse().getContentAsString()).get("accountId").asText();

            // Issue a refresh token and an AUTH API key for svcId
            String rawRefresh = refreshTokenStore.create(svcId, Instant.now().plusSeconds(3600));
            credentialRepository.save(CredentialRecord.builder(
                            "cred-svc-1", "acme-t", "svc-api-key", CredentialCategory.AUTH, "spector")
                    .userId(svcId)
                    .keyHash("deadbeef0123456789")
                    .ciphertext("enc")
                    .iv("iv")
                    .authTag("tag")
                    .maskedPreview("sk_spec_***")
                    .lastUsedAt(Instant.now())
                    .build());

            // Verify apiKeyCount == 1 and lastActivityAt is populated
            mvc.perform(get("/api/v1/admin/accounts/" + svcId)
                            .with(user(acmeAdminId).roles("admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.apiKeyCount", is(1)))
                    .andExpect(jsonPath("$.lastActivityAt", notNullValue()));

            // Suspend account and change profile to UNLIMITED
            mvc.perform(put("/api/v1/admin/accounts/" + svcId)
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":false,\"profile\":\"UNLIMITED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active", is(false)))
                    .andExpect(jsonPath("$.profile", is("UNLIMITED")));

            // Suspending the account automatically revoked its active refresh token
            assertThat(refreshTokenStore.findActive(RefreshTokenStore.sha256Hex(rawRefresh))).isEmpty();

            // Reactivate account
            mvc.perform(put("/api/v1/admin/accounts/" + svcId)
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active", is(true)));

            // Reset credentials (set new password + revoke API keys)
            mvc.perform(post("/api/v1/admin/accounts/" + svcId + "/reset-credentials")
                            .with(user(acmeAdminId).roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newPassword\":\"NewRotatedPass99!\",\"revokeRefreshTokens\":true,\"revokeApiKeys\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId", is(svcId)))
                    .andExpect(jsonPath("$.passwordReset", is(true)))
                    .andExpect(jsonPath("$.refreshTokensRevoked", is(true)))
                    .andExpect(jsonPath("$.revokedApiKeyCount", is(1)));

            // Reassign tenant as super-admin
            mvc.perform(post("/api/v1/admin/accounts/" + svcId + "/tenant")
                            .with(user("super-op").roles("super-admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"beta-t\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tenantId", is("beta-t")));

            // Verify audit trail for svcId
            mvc.perform(get("/api/v1/admin/accounts/" + svcId + "/audit")
                            .with(user("super-op").roles("super-admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(4)))
                    .andExpect(jsonPath("$[*].action", hasItem("CREATE_ACCOUNT")))
                    .andExpect(jsonPath("$[*].action", hasItem("SUSPEND_ACCOUNT")))
                    .andExpect(jsonPath("$[*].action", hasItem("REACTIVATE_ACCOUNT")))
                    .andExpect(jsonPath("$[*].action", hasItem("RESET_ACCOUNT_CREDENTIALS")))
                    .andExpect(jsonPath("$[*].action", hasItem("REASSIGN_ACCOUNT_TENANT")));
        }

        @Test
        @DisplayName("Enforces tenant maxAccounts quota on account creation (returns 429 SPE-800-010)")
        void enforcesTenantMaxAccountsQuota() throws Exception {
            tenantService.createTenant(
                    new CreateTenantRequest(
                            "quota-t", "Quota Tenant", "Strict 1-account limit", "FREE", null, false, null,
                            new TenantQuotasDto(1, 5, 104857600L)),
                    "super-op", "default");

            // First account in quota-t succeeds
            mvc.perform(post("/api/v1/admin/accounts")
                            .with(user("super-op").roles("super-admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"quota-u1\",\"tenantId\":\"quota-t\"}"))
                    .andExpect(status().isCreated());

            // Second account in quota-t exceeds maxAccounts=1 -> 429 Too Many Requests
            mvc.perform(post("/api/v1/admin/accounts")
                            .with(user("super-op").roles("super-admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"quota-u2\",\"tenantId\":\"quota-t\"}"))
                    .andExpect(status().isTooManyRequests());
        }
    }
}
