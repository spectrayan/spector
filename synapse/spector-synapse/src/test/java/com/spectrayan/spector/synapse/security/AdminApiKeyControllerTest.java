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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.spectrayan.spector.synapse.memory.MemoryDto.ErrorResponse;
import com.spectrayan.spector.synapse.security.ApiKeyStore.ApiKeyRow;
import com.spectrayan.spector.synapse.security.AuthDto.ApiKeySummary;

/**
 * Unit tests for {@link AdminApiKeyController} verifying administrative oversight
 * of API keys across tenant and fleet-wide scopes (Requirement R3, Issue #1050).
 */
@DisplayName("AdminApiKeyController — Unit Tests")
class AdminApiKeyControllerTest {

    private ApiKeyStore apiKeyStore;
    private UserAccountStore userAccountStore;
    private AdminApiKeyController controller;

    @BeforeEach
    void setUp() {
        apiKeyStore = mock(ApiKeyStore.class);
        userAccountStore = mock(UserAccountStore.class);
        controller = new AdminApiKeyController(apiKeyStore, userAccountStore);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void bindPrincipal(String userId, String tenantId, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        Authentication authenticated = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticated);
        SecurityContextHolder.setContext(context);
    }

    private static UserRow user(String userId, String username, Set<String> roles, String tenantId) {
        Instant now = Instant.now();
        return new UserRow(userId, username, "{pbkdf2}hash", null, null,
                roles, Set.of("memory:read"),
                false, true, 0, null, null, now, now, tenantId);
    }

    private static ApiKeyRow key(String keyId, String userId, String name) {
        return new ApiKeyRow(keyId, userId, "hash", Set.of("memory:read"),
                null, false, Instant.now(), name, "spk_test123", null);
    }

    // ── Platform Operator (super-admin) ──

    @Test
    @DisplayName("Super-admin retrieves all API keys fleet-wide when no filter specified")
    void listApiKeysSuperAdminReturnsAllKeys() {
        bindPrincipal("SUPER_ADMIN_ID", "platform", "SUPER_ADMIN");
        when(userAccountStore.findByUserId("SUPER_ADMIN_ID")).thenReturn(Optional.of(
                user("SUPER_ADMIN_ID", "superadmin", Set.of("SUPER_ADMIN"), "platform")));

        ApiKeyRow key1 = key("KEY0000000001", "USER_T1", "Key Tenant 1");
        ApiKeyRow key2 = key("KEY0000000002", "USER_T2", "Key Tenant 2");
        when(apiKeyStore.findAll()).thenReturn(List.of(key1, key2));

        ResponseEntity<?> response = controller.listApiKeys(null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<ApiKeySummary> summaries = (List<ApiKeySummary>) response.getBody();
        assertThat(summaries).hasSize(2);
        assertThat(summaries).extracting(ApiKeySummary::keyId)
                .containsExactly("KEY0000000001", "KEY0000000002");
    }

    @Test
    @DisplayName("Super-admin retrieves keys filtered by accountId across any tenant")
    void listApiKeysSuperAdminWithAccountIdFilterReturnsAccountKeys() {
        bindPrincipal("SUPER_ADMIN_ID", "platform", "SUPER_ADMIN");
        when(userAccountStore.findByUserId("SUPER_ADMIN_ID")).thenReturn(Optional.of(
                user("SUPER_ADMIN_ID", "superadmin", Set.of("SUPER_ADMIN"), "platform")));

        ApiKeyRow key1 = key("KEY0000000001", "USER_T2", "Key Tenant 2");
        when(userAccountStore.findByUserId("USER_T2")).thenReturn(Optional.of(
                user("USER_T2", "usert2", Set.of("USER"), "tenant-beta")));
        when(apiKeyStore.findByUserId("USER_T2")).thenReturn(List.of(key1));

        ResponseEntity<?> response = controller.listApiKeys("USER_T2", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<ApiKeySummary> summaries = (List<ApiKeySummary>) response.getBody();
        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().keyId()).isEqualTo("KEY0000000001");
    }

    @Test
    @DisplayName("Super-admin filtering by nonexistent accountId returns HTTP 404")
    void listApiKeysSuperAdminNonexistentAccountReturns404() {
        bindPrincipal("SUPER_ADMIN_ID", "platform", "SUPER_ADMIN");
        when(userAccountStore.findByUserId("SUPER_ADMIN_ID")).thenReturn(Optional.of(
                user("SUPER_ADMIN_ID", "superadmin", Set.of("SUPER_ADMIN"), "platform")));
        when(userAccountStore.findByUserId("NONEXISTENT_USER")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.listApiKeys("NONEXISTENT_USER", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) response.getBody();
        assertThat(err.status()).isEqualTo(404);
        assertThat(err.message()).contains("User not found: NONEXISTENT_USER");
    }

    // ── Tenant Admin (admin) ──

    @Test
    @DisplayName("Tenant admin retrieves keys within their tenant only (omits cross-tenant keys)")
    void listApiKeysTenantAdminSameTenantReturnsTenantKeysOnly() {
        bindPrincipal("ADMIN_T1", "tenant-alpha", "ADMIN");
        UserRow adminUser = user("ADMIN_T1", "admin1", Set.of("ADMIN"), "tenant-alpha");
        UserRow userT1 = user("USER_T1", "usert1", Set.of("USER"), "tenant-alpha");
        UserRow userT2 = user("USER_T2", "usert2", Set.of("USER"), "tenant-beta");

        when(userAccountStore.findByUserId("ADMIN_T1")).thenReturn(Optional.of(adminUser));
        when(userAccountStore.listUsers()).thenReturn(List.of(adminUser, userT1, userT2));

        ApiKeyRow key1 = key("KEY0000000001", "USER_T1", "Key Alpha");
        ApiKeyRow key2 = key("KEY0000000002", "USER_T2", "Key Beta");
        when(apiKeyStore.findAll()).thenReturn(List.of(key1, key2));

        ResponseEntity<?> response = controller.listApiKeys(null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<ApiKeySummary> summaries = (List<ApiKeySummary>) response.getBody();
        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().keyId()).isEqualTo("KEY0000000001");
    }

    @Test
    @DisplayName("Tenant admin filters by accountId within their tenant returns account keys")
    void listApiKeysTenantAdminWithAccountIdInSameTenantReturnsKeys() {
        bindPrincipal("ADMIN_T1", "tenant-alpha", "ADMIN");
        UserRow adminUser = user("ADMIN_T1", "admin1", Set.of("ADMIN"), "tenant-alpha");
        UserRow userT1 = user("USER_T1", "usert1", Set.of("USER"), "tenant-alpha");

        when(userAccountStore.findByUserId("ADMIN_T1")).thenReturn(Optional.of(adminUser));
        when(userAccountStore.listUsers()).thenReturn(List.of(adminUser, userT1));

        ApiKeyRow key1 = key("KEY0000000001", "USER_T1", "Key Alpha");
        when(apiKeyStore.findByUserId("USER_T1")).thenReturn(List.of(key1));

        ResponseEntity<?> response = controller.listApiKeys("USER_T1", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<ApiKeySummary> summaries = (List<ApiKeySummary>) response.getBody();
        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().keyId()).isEqualTo("KEY0000000001");
    }

    @Test
    @DisplayName("Tenant admin filtering by accountId from different tenant returns HTTP 403")
    void listApiKeysTenantAdminWithAccountIdInOtherTenantReturns403() {
        bindPrincipal("ADMIN_T1", "tenant-alpha", "ADMIN");
        UserRow adminUser = user("ADMIN_T1", "admin1", Set.of("ADMIN"), "tenant-alpha");
        UserRow userT1 = user("USER_T1", "usert1", Set.of("USER"), "tenant-alpha");
        UserRow userT2 = user("USER_T2", "usert2", Set.of("USER"), "tenant-beta");

        when(userAccountStore.findByUserId("ADMIN_T1")).thenReturn(Optional.of(adminUser));
        when(userAccountStore.listUsers()).thenReturn(List.of(adminUser, userT1));
        when(userAccountStore.findByUserId("USER_T2")).thenReturn(Optional.of(userT2));

        ResponseEntity<?> response = controller.listApiKeys("USER_T2", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) response.getBody();
        assertThat(err.status()).isEqualTo(403);
        assertThat(err.message()).contains("SPE-820-001");
    }

    @Test
    @DisplayName("Tenant admin filtering by nonexistent accountId returns HTTP 404 (not false cross-tenant 403)")
    void listApiKeysTenantAdminNonexistentAccountReturns404() {
        bindPrincipal("ADMIN_T1", "tenant-alpha", "ADMIN");
        UserRow adminUser = user("ADMIN_T1", "admin1", Set.of("ADMIN"), "tenant-alpha");
        when(userAccountStore.findByUserId("ADMIN_T1")).thenReturn(Optional.of(adminUser));
        when(userAccountStore.listUsers()).thenReturn(List.of(adminUser));
        when(userAccountStore.findByUserId("NONEXISTENT_USER")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.listApiKeys("NONEXISTENT_USER", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
        ErrorResponse err = (ErrorResponse) response.getBody();
        assertThat(err.status()).isEqualTo(404);
        assertThat(err.message()).contains("User not found");
    }

    @Test
    @DisplayName("Tenant admin oversight uses DB tenant when security context diverges")
    void listApiKeysTenantAdminDbTenantTakesPrecedenceOverJwt() {
        // Security context principal says tenant-old, DB says tenant-new
        bindPrincipal("ADMIN_T1", "tenant-old", "ADMIN");
        UserRow adminUser = user("ADMIN_T1", "admin1", Set.of("ADMIN"), "tenant-new");
        UserRow userNew = user("USER_NEW", "usernew", Set.of("USER"), "tenant-new");

        when(userAccountStore.findByUserId("ADMIN_T1")).thenReturn(Optional.of(adminUser));
        when(userAccountStore.listUsers()).thenReturn(List.of(adminUser, userNew));
        ApiKeyRow key1 = key("KEY0000000001", "USER_NEW", "Key New");
        when(apiKeyStore.findByUserId("USER_NEW")).thenReturn(List.of(key1));

        ResponseEntity<?> response = controller.listApiKeys("USER_NEW", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<ApiKeySummary> summaries = (List<ApiKeySummary>) response.getBody();
        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().keyId()).isEqualTo("KEY0000000001");
    }
}
