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
package com.spectrayan.spector.synapse.admin.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.AccountProfile;
import com.spectrayan.spector.synapse.catalog.PrincipalKind;
import com.spectrayan.spector.synapse.connector.model.CredentialCategory;
import com.spectrayan.spector.synapse.connector.model.CredentialRecord;
import com.spectrayan.spector.synapse.connector.repository.CredentialRepository;
import com.spectrayan.spector.synapse.security.RefreshTokenStore;
import com.spectrayan.spector.synapse.security.ServerAccessTokenMinter;
import com.spectrayan.spector.synapse.security.UserAccountStore;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AdminTenantController} and tenant lifecycle enforcement (Issue #1055).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spector.auth.enabled=true",
        "spector.auth.jwt.secret=01234567890123456789012345678901",
        "spector.catalog.type=jdbc"
})
@DirtiesContext
@DisplayName("AdminTenantController & Tenant Lifecycle Integration Tests (Issue #1055)")
class AdminTenantControllerTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private AccountCatalog catalog;

    @Autowired
    private UserAccountStore userAccountStore;

    @Autowired
    private ServerAccessTokenMinter tokenMinter;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @MockitoBean
    private SpectorMemory spectorMemory;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT user_id FROM users WHERE tenant_id IN ('acme-corp', 'suspended-org') OR username IN ('acme-user-1', 'org-member-1'))", Map.of());
        jdbc.update("DELETE FROM namespaces WHERE owner_account_id IN (SELECT user_id FROM users WHERE tenant_id IN ('acme-corp', 'suspended-org') OR username IN ('acme-user-1', 'org-member-1'))", Map.of());
        jdbc.update("DELETE FROM users WHERE tenant_id IN ('acme-corp', 'suspended-org') OR username IN ('acme-user-1', 'org-member-1')", Map.of());
        jdbc.update("DELETE FROM credentials WHERE tenant_id IN ('acme-corp', 'suspended-org')", Map.of());
        jdbc.update("DELETE FROM admin_audit_log WHERE target_id IN ('acme-corp', 'suspended-org')", Map.of());
        jdbc.update("DELETE FROM tenants WHERE tenant_id IN ('acme-corp', 'suspended-org')", Map.of());
    }

    // ══════════════════════════════════════════════════════════════
    // 1. ROLE BOUNDARIES (super-admin vs admin vs viewer)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. Role Authorization Matrix")
    class RoleAuthorizationTests {

        @Test
        @DisplayName("Tenant admin (admin) receives 403 Forbidden on all /api/v1/admin/tenants endpoints")
        void tenantAdminRejectedWith403() throws Exception {
            mvc.perform(get("/api/v1/admin/tenants")
                            .with(user("tenant-admin-1").roles("admin")))
                    .andExpect(status().isForbidden());

            mvc.perform(get("/api/v1/admin/tenants/default")
                            .with(user("tenant-admin-1").roles("admin")))
                    .andExpect(status().isForbidden());

            mvc.perform(post("/api/v1/admin/tenants")
                            .with(user("tenant-admin-1").roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"unauthorized-t\",\"name\":\"Unauthorized\"}"))
                    .andExpect(status().isForbidden());

            mvc.perform(put("/api/v1/admin/tenants/default")
                            .with(user("tenant-admin-1").roles("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Modified\"}"))
                    .andExpect(status().isForbidden());

            mvc.perform(delete("/api/v1/admin/tenants/default")
                            .with(user("tenant-admin-1").roles("admin")))
                    .andExpect(status().isForbidden());

            mvc.perform(get("/api/v1/admin/tenants/default/audit")
                            .with(user("tenant-admin-1").roles("admin")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Platform operator (super-admin) is permitted on /api/v1/admin/tenants")
        void superAdminPermitted() throws Exception {
            mvc.perform(get("/api/v1/admin/tenants")
                            .with(user("platform-op").roles("super-admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$[*].tenantId", hasItem("default")));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 2. CRUD LIFECYCLE, QUOTAS, BYOK & AUDIT LOG
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. Tenant CRUD, Settings, BYOK Gate & Audit Trail")
    class CrudAndSettingsTests {

        @Test
        @DisplayName("Full tenant CRUD lifecycle with quotas, break-glass policy, BYOK gate, and audit log")
        void fullCrudLifecycle() throws Exception {
            // 1. Create tenant
            Map<String, Object> createReq = Map.of(
                    "tenantId", "acme-corp",
                    "name", "Acme Corporation",
                    "plan", "ENTERPRISE",
                    "settings", Map.of(
                            "breakGlassApproverRequired", true,
                            "breakGlassMaxDurationMinutes", 120
                    ),
                    "quotas", Map.of(
                            "maxAccounts", 250,
                            "maxNamespaces", 1000,
                            "maxStorageBytes", 53687091200L
                    )
            );

            mvc.perform(post("/api/v1/admin/tenants")
                            .with(user("super-op").roles("super-admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(createReq)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tenantId", is("acme-corp")))
                    .andExpect(jsonPath("$.name", is("Acme Corporation")))
                    .andExpect(jsonPath("$.status", is("ACTIVE")))
                    .andExpect(jsonPath("$.plan", is("ENTERPRISE")))
                    .andExpect(jsonPath("$.settings.byokEnabled", is(false)))
                    .andExpect(jsonPath("$.settings.breakGlassApproverRequired", is(true)))
                    .andExpect(jsonPath("$.settings.breakGlassMaxDurationMinutes", is(120)))
                    .andExpect(jsonPath("$.quotas.maxAccounts", is(250)))
                    .andExpect(jsonPath("$.quotas.maxNamespaces", is(1000)))
                    .andExpect(jsonPath("$.quotas.maxStorageBytes", is(53687091200L)))
                    .andExpect(jsonPath("$.accountCount", is(0)))
                    .andExpect(jsonPath("$.namespaceCount", is(0)))
                    .andExpect(jsonPath("$.storageBytes", is(0)));

            // 2. Provision an account in acme-corp and verify aggregate counts
            String acmeUserId = userAccountStore.createUser(
                    "acme-user-1", "Password123!", "user@acme.test", "Acme User",
                    Set.of("editor"), Set.of("memory:read"), false);
            jdbc.update("UPDATE users SET tenant_id = 'acme-corp' WHERE user_id = :uid", Map.of("uid", acmeUserId));
            catalog.getOrCreateAccount(acmeUserId, AccountProfile.HUMAN_TEAM, PrincipalKind.HUMAN, "acme-corp");

            mvc.perform(get("/api/v1/admin/tenants/acme-corp")
                            .with(user("super-op").roles("super-admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountCount", is(1)))
                    .andExpect(jsonPath("$.namespaceCount", is(1)));

            // 3. Reject enabling BYOK when no BYOK credential is configured for acme-corp
            mvc.perform(put("/api/v1/admin/tenants/acme-corp")
                            .with(user("super-op").roles("super-admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"settings\":{\"byokEnabled\":true}}"))
                    .andExpect(status().isBadRequest());

            // 4. Configure a BYOK/KMS credential for acme-corp, then enable BYOK
            credentialRepository.save(CredentialRecord.builder(
                            "byok-acme-1", "acme-corp", "acme-byok-kek", CredentialCategory.VAULT, "aws-kms")
                    .userId("super-op")
                    .ciphertext("encrypted-kek")
                    .iv("iv-1")
                    .authTag("tag-1")
                    .maskedPreview("arn:aws:kms:...")
                    .build());

            mvc.perform(put("/api/v1/admin/tenants/acme-corp")
                            .with(user("super-op").roles("super-admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Acme Global\",\"settings\":{\"byokEnabled\":true},\"quotas\":{\"maxNamespaces\":2000}}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Acme Global")))
                    .andExpect(jsonPath("$.settings.byokEnabled", is(true)))
                    .andExpect(jsonPath("$.quotas.maxNamespaces", is(2000)));

            // 5. Reject break-glass duration exceeding 1440m (24 hours)
            mvc.perform(put("/api/v1/admin/tenants/acme-corp")
                            .with(user("super-op").roles("super-admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"settings\":{\"breakGlassMaxDurationMinutes\":2880}}"))
                    .andExpect(status().isBadRequest());

            // 6. Soft-delete acme-corp
            mvc.perform(delete("/api/v1/admin/tenants/acme-corp")
                            .with(user("super-op").roles("super-admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("DELETED")))
                    .andExpect(jsonPath("$.deletedAt", notNullValue()));

            // 7. Verify audit trail records CREATE_TENANT, UPDATE_TENANT, and DELETE_TENANT
            MvcResult auditRes = mvc.perform(get("/api/v1/admin/tenants/acme-corp/audit")
                            .with(user("super-op").roles("super-admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(3)))
                    .andReturn();

            JsonNode entries = mapper.readTree(auditRes.getResponse().getContentAsString());
            List<String> actions = entries.findValuesAsText("action");
            assertThat(actions).contains("CREATE_TENANT", "UPDATE_TENANT", "DELETE_TENANT");
        }

        @Test
        @DisplayName("Default tenant cannot be soft-deleted or suspended")
        void defaultTenantProtected() throws Exception {
            mvc.perform(delete("/api/v1/admin/tenants/default")
                            .with(user("super-op").roles("super-admin")))
                    .andExpect(status().isConflict());

            mvc.perform(put("/api/v1/admin/tenants/default")
                            .with(user("super-op").roles("super-admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"SUSPENDED\"}"))
                    .andExpect(status().isConflict());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 3. TENANT SUSPENSION BLOCKS LOGIN, REFRESH, JWT & API KEYS
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. Tenant Suspension Enforcement (SPE-820-003 / SPE-SEC-003)")
    class TenantSuspensionEnforcementTests {

        private static String sha256Hex(String input) throws Exception {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }

        @Test
        @DisplayName("Suspending a tenant blocks login, refresh, JWT, and API key auth with 403 and SPE-820-003 (SPE-SEC-003)")
        void suspendingTenantBlocksAllPrincipalAuth() throws Exception {
            String tenantId = "suspended-org";

            // 1. Create tenant via super-admin
            mvc.perform(post("/api/v1/admin/tenants")
                            .with(user("super-op").roles("super-admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"" + tenantId + "\",\"name\":\"Suspended Org\"}"))
                    .andExpect(status().isCreated());

            // 2. Create user account assigned to suspended-org
            String username = "org-member-1";
            String password = "Password123!";
            String userId = userAccountStore.createUser(
                    username, password, "member@org.test", "Org Member",
                    Set.of("editor"), Set.of("memory:read", "memory:write"), false);
            jdbc.update("UPDATE users SET tenant_id = :tenantId WHERE user_id = :userId",
                    Map.of("tenantId", tenantId, "userId", userId));
            catalog.getOrCreateAccount(userId, AccountProfile.HUMAN_TEAM, PrincipalKind.HUMAN, tenantId);

            // Mint a valid JWT and refresh token before suspension, and create an API key
            String jwtToken = tokenMinter.mint(userId, Set.of("memory:read"), Set.of("editor"), tenantId).token();
            String refreshToken = refreshTokenStore.create(userId, Instant.now().plusSeconds(3600));
            String rawApiKey = "sk_spec_org_member_key_12345678901234567890";
            credentialRepository.save(CredentialRecord.builder(
                            "cred-org-1", tenantId, "member-key", CredentialCategory.AUTH, "spector")
                    .userId(userId)
                    .keyHash(sha256Hex(rawApiKey))
                    .ciphertext("dummy")
                    .iv("dummy")
                    .authTag("dummy")
                    .maskedPreview("sk_spec_...org")
                    .properties(Map.of("scopes", List.of("memory:read")))
                    .build());

            // 3. Verify login works while tenant is ACTIVE
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.access_token", notNullValue()));

            // 4. Suspend the tenant via super-admin
            mvc.perform(put("/api/v1/admin/tenants/" + tenantId)
                            .with(user("super-op").roles("super-admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"SUSPENDED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("SUSPENDED")));

            // 5. Login is blocked with 403 and SPE-820-003 / SPE-SEC-003
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.details.code", is("SPE-820-003")))
                    .andExpect(jsonPath("$.details.alias", is("SPE-SEC-003")));

            // 6. Refresh token exchange is blocked with 403 and SPE-820-003 / SPE-SEC-003
            mvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refresh_token\":\"" + refreshToken + "\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.details.code", is("SPE-820-003")))
                    .andExpect(jsonPath("$.details.alias", is("SPE-SEC-003")));

            // 7. Existing JWT bearer token is blocked by TenantStatusFilter with 403 and SPE-820-003 / SPE-SEC-003
            mvc.perform(get("/api/v1/credentials")
                            .header("Authorization", "Bearer " + jwtToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code", is("SPE-820-003")))
                    .andExpect(jsonPath("$.alias", is("SPE-SEC-003")));

            // 8. API key authentication is blocked by TenantStatusFilter with 403 and SPE-820-003 / SPE-SEC-003
            mvc.perform(get("/api/v1/credentials")
                            .header("Authorization", "Bearer " + rawApiKey))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code", is("SPE-820-003")))
                    .andExpect(jsonPath("$.alias", is("SPE-SEC-003")));

            // 9. Reactivate tenant via super-admin and verify login works again
            mvc.perform(put("/api/v1/admin/tenants/" + tenantId)
                            .with(user("super-op").roles("super-admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("ACTIVE")));

            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.access_token", notNullValue()));
        }
    }
}
