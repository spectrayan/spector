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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.commons.crypto.DekCache;
import com.spectrayan.spector.commons.crypto.DestroyableKeyMaterial;
import com.spectrayan.spector.commons.crypto.KeyHealthStatus;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.synapse.connector.model.CredentialCategory;
import com.spectrayan.spector.synapse.connector.model.CredentialType;
import com.spectrayan.spector.synapse.connector.api.dto.CreateCredentialRequest;
import com.spectrayan.spector.synapse.connector.service.CredentialService;
import com.spectrayan.spector.synapse.security.kms.LocalFileKeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AdminTenantKeyController} ({@code /api/v1/admin/tenants/{tenantId}/keys})
 * and {@link TenantKeyService} (ADR-0088, Issue #1070).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spector.auth.enabled=true",
        "spector.auth.jwt.secret=01234567890123456789012345678901",
        "spector.catalog.type=jdbc"
})
@DirtiesContext
@DisplayName("AdminTenantKeyController & TenantKeyService Integration Tests (Issue #1070)")
class AdminTenantKeyControllerTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantKeyService tenantKeyService;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private LocalFileKeyProvider localFileKeyProvider;

    @Autowired
    private DekCache dekCache;

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
        localFileKeyProvider.clearSimulatedKeyStates();
        dekCache.clear();

        jdbc.update("DELETE FROM namespace_encryption_key WHERE tenant_id IN ('acme-byok', 'globex-byok')", Map.of());
        jdbc.update("DELETE FROM tenant_kek_config WHERE tenant_id IN ('acme-byok', 'globex-byok')", Map.of());
        jdbc.update("DELETE FROM credentials WHERE tenant_id IN ('acme-byok', 'globex-byok')", Map.of());
        jdbc.update("DELETE FROM tenants WHERE tenant_id IN ('acme-byok', 'globex-byok')", Map.of());

        tenantService.createTenant(
                new com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.CreateTenantRequest(
                        "acme-byok", "Acme BYOK Corp", "Acme tenant", "ENTERPRISE", null, false, null, null),
                "super-op", "default");
        tenantService.createTenant(
                new com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.CreateTenantRequest(
                        "globex-byok", "Globex BYOK Corp", "Globex tenant", "ENTERPRISE", null, false, null, null),
                "super-op", "default");
    }

    private static UsernamePasswordAuthenticationToken tenantAdminAuth(String userId, String tenantId) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                userId,
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_admin"), new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of("tenant_id", tenantId));
        return token;
    }

    @Test
    @DisplayName("RBAC: super-admin accesses any tenant; tenant admin accesses own tenant only (403 on other tenant); viewer gets 403")
    void rbacEnforcementAcrossSuperAdminTenantAdminAndViewer() throws Exception {
        // 1. Viewer is rejected with 403 Forbidden
        mvc.perform(get("/api/v1/admin/tenants/acme-byok/keys")
                        .with(user("viewer-1").roles("viewer")))
                .andExpect(status().isForbidden());

        // 2. Tenant admin for acme-byok can inspect acme-byok keys (200 OK)
        mvc.perform(get("/api/v1/admin/tenants/acme-byok/keys")
                        .with(authentication(tenantAdminAuth("acme-admin", "acme-byok"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId", is("acme-byok")))
                .andExpect(jsonPath("$.byokEnabled", is(false)))
                .andExpect(jsonPath("$.status", is("UNCONFIGURED")))
                .andExpect(jsonPath("$.supportedProviders", hasItems(
                        "aws-kms", "gcp-kms", "azure-kv", "hashicorp-vault", "local-file", "platform-hkdf")));

        // 3. Tenant admin for acme-byok attempting to access or configure globex-byok receives 403 Forbidden (SPE-820-001)
        mvc.perform(get("/api/v1/admin/tenants/globex-byok/keys")
                        .with(authentication(tenantAdminAuth("acme-admin", "acme-byok"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("SPE-820-001")));

        mvc.perform(put("/api/v1/admin/tenants/globex-byok/keys")
                        .with(authentication(tenantAdminAuth("acme-admin", "acme-byok")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"local-file\",\"kekKeyId\":\"globex-kek-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("SPE-820-001")));

        // 4. Super-admin can access any tenant
        mvc.perform(get("/api/v1/admin/tenants/globex-byok/keys")
                        .with(user("super-op").roles("super-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId", is("globex-byok")));
    }

    @Test
    @DisplayName("Full BYOK lifecycle: credential vault binding, live KEK registration self-test, namespace DEK caching, revocation detection, and delete")
    void fullByokLifecycleWithLiveValidationAndRevocationEviction() throws Exception {
        // 1. Store KMS credential in the universal credentials vault for acme-byok
        credentialService.createCredential("acme-byok", "acme-admin", new CreateCredentialRequest(
                "acme-kms-cred",
                CredentialCategory.VAULT,
                "local-file",
                CredentialType.API_KEY,
                "super-secret-kek-seed-material-2026",
                Map.of("region", "us-east-1"),
                true,
                "Acme BYOK KEK Credential",
                null
        ));

        // 2. Tenant admin configures BYOK KEK via PUT /api/v1/admin/tenants/acme-byok/keys
        Map<String, Object> configureReq = Map.of(
                "provider", "local-file",
                "kekKeyId", "acme-master-kek-v1",
                "credentialName", "acme-kms-cred",
                "region", "us-east-1"
        );

        mvc.perform(put("/api/v1/admin/tenants/acme-byok/keys")
                        .with(authentication(tenantAdminAuth("acme-admin", "acme-byok")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(configureReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId", is("acme-byok")))
                .andExpect(jsonPath("$.byokEnabled", is(true)))
                .andExpect(jsonPath("$.provider", is("local-file")))
                .andExpect(jsonPath("$.kekKeyId", is("acme-master-kek-v1")))
                .andExpect(jsonPath("$.credentialName", is("acme-kms-cred")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.healthStatus", is("HEALTHY")))
                .andExpect(jsonPath("$.lastVerifiedAt", notNullValue()));

        // 3. Provision a per-namespace DEK for acme-byok / ns-research and verify DekCache population
        try (DestroyableKeyMaterial nsDek = tenantKeyService.getOrProvisionNamespaceDek("acme-byok", "ns-research")) {
            assertThat(nsDek.length()).isEqualTo(32);
            assertThat(dekCache.getIfPresent("acme-byok", "ns-research", 1)).isPresent();
        }

        mvc.perform(get("/api/v1/admin/tenants/acme-byok/keys")
                        .with(authentication(tenantAdminAuth("acme-admin", "acme-byok"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeNamespaceDekCount", is(1)));

        // 4. On-demand validate endpoint succeeds while KEK is healthy
        mvc.perform(post("/api/v1/admin/tenants/acme-byok/keys/validate")
                        .with(authentication(tenantAdminAuth("acme-admin", "acme-byok"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.healthStatus", is("HEALTHY")))
                .andExpect(jsonPath("$.roundTripVerified", is(true)))
                .andExpect(jsonPath("$.errorCode", nullValue()));

        // 5. Simulate external KMS KEK revocation -> validate endpoint detects REVOKED and evicts + zeroizes DekCache!
        localFileKeyProvider.setSimulatedKeyState("acme-byok", "acme-master-kek-v1", KeyHealthStatus.REVOKED);

        mvc.perform(post("/api/v1/admin/tenants/acme-byok/keys/validate")
                        .with(authentication(tenantAdminAuth("acme-admin", "acme-byok"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.healthStatus", is("REVOKED")))
                .andExpect(jsonPath("$.roundTripVerified", is(false)))
                .andExpect(jsonPath("$.evictedCachedDeks", is(1)))
                .andExpect(jsonPath("$.errorCode", is("SPE-820-010")));

        assertThat(dekCache.getIfPresent("acme-byok", "ns-research", 1)).isEmpty();

        // 6. Attempting to register a revoked KEK via PUT fails closed with HTTP 403 (SPE-820-010)
        localFileKeyProvider.setSimulatedKeyState("globex-byok", "revoked-globex-key", KeyHealthStatus.REVOKED);
        mvc.perform(put("/api/v1/admin/tenants/globex-byok/keys")
                        .with(user("super-op").roles("super-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"local-file\",\"kekKeyId\":\"revoked-globex-key\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("SPE-820-010")));

        // 7. Attempting to register an unreachable KEK via PUT fails closed with HTTP 503 (SPE-310-027)
        localFileKeyProvider.setSimulatedKeyState("globex-byok", "unreachable-globex-key", KeyHealthStatus.UNREACHABLE);
        mvc.perform(put("/api/v1/admin/tenants/globex-byok/keys")
                        .with(user("super-op").roles("super-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"local-file\",\"kekKeyId\":\"unreachable-globex-key\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error", is("SPE-310-027")));

        // 8. Delete tenant BYOK configuration
        mvc.perform(delete("/api/v1/admin/tenants/acme-byok/keys")
                        .with(authentication(tenantAdminAuth("acme-admin", "acme-byok"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byokEnabled", is(false)))
                .andExpect(jsonPath("$.status", is("UNCONFIGURED")));
    }

    @Test
    @DisplayName("Issue #1072: KEK rotation re-wraps DEKs without rewriting data files, and DEK rotation advances version to v2 while shredding v1")
    void kekRotationAndDekRotationLifecycle() throws Exception {
        // 1. Configure initial KEK (acme-kek-v1)
        mvc.perform(put("/api/v1/admin/tenants/acme-byok/keys")
                        .with(authentication(tenantAdminAuth("acme-admin", "acme-byok")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"local-file\",\"kekKeyId\":\"acme-kek-v1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kekKeyId", is("acme-kek-v1")));

        // 2. Provision v1 DEK for ns-alpha and capture its 32-byte key material + encrypt a test payload
        byte[] originalDekBytes;
        byte[] cipherUnderV1;
        try (var encV1 = tenantKeyService.createNamespaceDataEncryptor("acme-byok", "ns-alpha")) {
            try (DestroyableKeyMaterial dekV1 = tenantKeyService.getOrProvisionNamespaceDek("acme-byok", "ns-alpha")) {
                originalDekBytes = dekV1.bytes();
            }
            cipherUnderV1 = encV1.encryptText("confidential-alpha-payload".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        // 3. Rotate KEK to acme-kek-v2 via POST /rotate-kek -> re-wraps DEK without rewriting data files
        mvc.perform(post("/api/v1/admin/tenants/acme-byok/keys/rotate-kek")
                        .with(authentication(tenantAdminAuth("acme-admin", "acme-byok")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newKekKeyId\":\"acme-kek-v2\",\"newKekVersion\":\"v2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId", is("acme-byok")))
                .andExpect(jsonPath("$.previousKekKeyId", is("acme-kek-v1")))
                .andExpect(jsonPath("$.newKekKeyId", is("acme-kek-v2")))
                .andExpect(jsonPath("$.newKekVersion", is("v2")))
                .andExpect(jsonPath("$.rewrappedDekCount", is(1)))
                .andExpect(jsonPath("$.dataFilesRewritten", is(false)));

        // Evict cache and verify unwrapping from DB under acme-kek-v2 yields the exact same 32-byte DEK
        dekCache.clear();
        try (DestroyableKeyMaterial dekAfterKekRotate = tenantKeyService.getOrProvisionNamespaceDek("acme-byok", "ns-alpha");
             var encAfterKekRotate = tenantKeyService.createNamespaceDataEncryptor("acme-byok", "ns-alpha")) {
            assertThat(dekAfterKekRotate.bytes()).isEqualTo(originalDekBytes);
            byte[] decrypted = encAfterKekRotate.decryptText(cipherUnderV1);
            assertThat(new String(decrypted, java.nio.charset.StandardCharsets.UTF_8))
                    .isEqualTo("confidential-alpha-payload");
        }

        // 4. Rotate namespace DEK via POST /rotate-dek -> advances to dek_version = 2 and marks v1 ROTATED
        mvc.perform(post("/api/v1/admin/tenants/acme-byok/keys/rotate-dek")
                        .with(authentication(tenantAdminAuth("acme-admin", "acme-byok")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"namespaceId\":\"ns-alpha\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId", is("acme-byok")))
                .andExpect(jsonPath("$.rotatedNamespaceCount", is(1)))
                .andExpect(jsonPath("$.namespaces[0].namespaceId", is("ns-alpha")))
                .andExpect(jsonPath("$.namespaces[0].previousDekVersion", is(1)))
                .andExpect(jsonPath("$.namespaces[0].newDekVersion", is(2)));

        assertThat(dekCache.getIfPresent("acme-byok", "ns-alpha", 1)).isEmpty();
        assertThat(dekCache.getIfPresent("acme-byok", "ns-alpha", 2)).isPresent();

        String v1Status = jdbc.queryForObject(
                "SELECT status FROM namespace_encryption_key WHERE tenant_id = 'acme-byok' AND namespace_id = 'ns-alpha' AND dek_version = 1",
                Map.of(),
                String.class);
        assertThat(v1Status).isEqualTo("ROTATED");

        // 5. Cryptographic erasure hook destroys all DEK rows for ns-alpha
        List<String> destroyed = tenantKeyService.destroyNamespaceDeksForErasure("acme-admin", "ns-alpha", "dpo-op");
        assertThat(destroyed).hasSize(2);
        assertThat(dekCache.getIfPresent("acme-byok", "ns-alpha", 2)).isEmpty();
    }

    @Test
    @DisplayName("Issue #1072: KEK revocation (crypto-shred) evicts and zeroizes DekCache, marks KEY_REVOKED, and fails closed with SPE-820-010")
    void revokeTenantKeyCryptoShredsDeksAndFailsClosedWithSpe820010() throws Exception {
        // 1. Configure BYOK KEK and provision a namespace DEK + active EnvelopeDataEncryptor
        mvc.perform(put("/api/v1/admin/tenants/acme-byok/keys")
                        .with(authentication(tenantAdminAuth("acme-admin", "acme-byok")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"local-file\",\"kekKeyId\":\"acme-shred-kek\"}"))
                .andExpect(status().isOk());

        var activeEncryptor = tenantKeyService.createNamespaceDataEncryptor("acme-byok", "ns-shred");
        assertThat(dekCache.getIfPresent("acme-byok", "ns-shred", 1)).isPresent();
        assertThat(activeEncryptor.isDestroyed()).isFalse();

        // 2. Revoke / crypto-shred tenant key via POST /api/v1/admin/tenants/acme-byok/keys/revoke
        mvc.perform(post("/api/v1/admin/tenants/acme-byok/keys/revoke")
                        .with(authentication(tenantAdminAuth("acme-admin", "acme-byok")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Emergency tenant offboarding crypto-shred\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId", is("acme-byok")))
                .andExpect(jsonPath("$.status", is("KEY_REVOKED")))
                .andExpect(jsonPath("$.healthStatus", is("REVOKED")))
                .andExpect(jsonPath("$.revokedNamespaceDekCount", is(1)))
                .andExpect(jsonPath("$.evictedCachedDeks", is(1)));

        // 3. Cached DEK is evicted and active encryptor key material is immediately zeroized
        assertThat(dekCache.getIfPresent("acme-byok", "ns-shred", 1)).isEmpty();
        assertThat(activeEncryptor.isDestroyed()).isTrue();

        // 4. Subsequent DEK provisioning or encryptor operations fail closed with ErrorCode.BYOK_KEY_REVOKED (SPE-820-010)
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        tenantKeyService.getOrProvisionNamespaceDek("acme-byok", "ns-shred"))
                .isInstanceOf(com.spectrayan.spector.commons.error.SpectorMemoryException.class)
                .satisfies(ex -> assertThat(((com.spectrayan.spector.commons.error.SpectorMemoryException) ex).errorCode())
                        .isEqualTo(com.spectrayan.spector.commons.error.ErrorCode.BYOK_KEY_REVOKED));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        activeEncryptor.encryptText("post-revocation-write".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(com.spectrayan.spector.memory.error.SpectorEncryptionException.class)
                .satisfies(ex -> assertThat(((com.spectrayan.spector.memory.error.SpectorEncryptionException) ex).errorCode())
                        .isEqualTo(com.spectrayan.spector.commons.error.ErrorCode.BYOK_KEY_REVOKED));

        activeEncryptor.close();
    }
}
