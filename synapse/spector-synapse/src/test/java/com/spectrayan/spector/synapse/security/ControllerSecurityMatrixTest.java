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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.memory.FederatedRecallService;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableRow;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableResponse;
import com.spectrayan.spector.synapse.memory.MemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import com.spectrayan.spector.synapse.connector.model.CredentialCategory;
import com.spectrayan.spector.synapse.connector.model.CredentialRecord;
import com.spectrayan.spector.synapse.connector.repository.CredentialRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Requirement R3 Role-Endpoint Security Matrix Test Suite.
 *
 * <p>Enforces two-tier admin model and namespace grant isolation across endpoints:</p>
 * <ul>
 *   <li>1. Endpoint Introspection: All admin and infrastructure controller endpoints are protected
 *       with {@link PreAuthorize} or security filter chain rules.</li>
 *   <li>2. Memory Content Isolation: Admin calling {@code /api/v1/memory/**} on ungranted namespace
 *       returns HTTP 403 Forbidden.</li>
 *   <li>3. Infra/Admin Protection: Non-admin roles (viewer, agent, editor) receive HTTP 403 on infra endpoints.</li>
 *   <li>4. Two-Tier Admin Differentiation: Platform Operator ({@code super-admin}) vs Tenant Admin ({@code admin}).</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spector.auth.enabled=true",
        "spector.auth.jwt.secret=01234567890123456789012345678901",
        "spector.catalog.type=jdbc",
        "spector.features.connectors-enabled=true",
        "spector.features.agent-chat-enabled=true"
})
@DirtiesContext
@DisplayName("ControllerSecurityMatrixTest — Role and Scope Enforcement Matrix")
class ControllerSecurityMatrixTest {

    private static final String ADMIN_USER = "0195500000001";
    private static final String OTHER_USER = "0195500000002";

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private AccountCatalog catalog;

    @Autowired
    private ObjectMapper mapper;

    @Autowired(required = false)
    private CredentialRepository credentialRepository;

    @Autowired(required = false)
    private UserAccountStore userAccountStore;

    @MockitoBean
    private MemoryService memoryService;

    @MockitoBean
    private FederatedRecallService federatedRecallService;

    @MockitoBean
    private SpectorMemory spectorMemory;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        // Provision accounts: admin-user owns ADMIN_USER namespace; other-user owns OTHER_USER namespace
        catalog.getOrCreateAccount(ADMIN_USER);
        catalog.getOrCreateAccount(OTHER_USER);

        // Configure mock SpectorMemory for Observability stats
        var mockAdmin = org.mockito.Mockito.mock(com.spectrayan.spector.memory.SpectorMemoryAdmin.class);
        when(spectorMemory.admin()).thenReturn(mockAdmin);
        when(spectorMemory.totalMemories()).thenReturn(10);
        when(spectorMemory.memoryCount(any())).thenReturn(2);

        // Configure mock MemoryService responses
        var row = new MemoryTableRow(
                "mem-1", "Java Memory Model", "Details...", "SEMANTIC", "OBSERVED",
                0.9f, 5, 128, System.currentTimeMillis(), 1, 1,
                false, false, false, false, false,
                List.of("java"), 0L, 1.0f, "2026-01-01T00:00:00Z", null
        );
        var tableResponse = new MemoryTableResponse(List.of(row), 1, 0, 50, Map.of(), Map.of());
        when(memoryService.getMemoryTable(anyInt(), anyInt(), any(), anyBoolean())).thenReturn(tableResponse);
        when(memoryService.recall(any())).thenReturn(List.of());
    }

    // ══════════════════════════════════════════════════════════════
    // 1. ENDPOINT INTROSPECTION TEST
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. Endpoint Security Introspection")
    class EndpointIntrospectionTests {

        @Test
        @DisplayName("All admin and infrastructure controllers declare PreAuthorize or security constraints")
        void testAdminAndInfraControllersDeclareSecurity() {
            Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
            assertThat(handlerMethods).isNotEmpty();

            Set<String> adminControllerNames = Set.of(
                    "CacheController",
                    "TaskManagementController",
                    "ProviderController",
                    "MigrationController",
                    "CredentialController",
                    "TokenUsageController",
                    "ConnectorController",
                    "PluginManager",
                    "AgentApprovalController",
                    "ObservabilityController",
                    "SystemController"
            );

            int checkedAdminMethods = 0;
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
                HandlerMethod method = entry.getValue();
                Class<?> beanType = method.getBeanType();
                String simpleName = beanType.getSimpleName();

                if (adminControllerNames.contains(simpleName)) {
                    boolean classHasPreAuth = beanType.isAnnotationPresent(PreAuthorize.class);
                    boolean methodHasPreAuth = method.hasMethodAnnotation(PreAuthorize.class);
                    assertThat(classHasPreAuth || methodHasPreAuth)
                            .as("Controller method %s.%s must have @PreAuthorize", simpleName, method.getMethod().getName())
                            .isTrue();
                    checkedAdminMethods++;
                }

                if ("ConfigController".equals(simpleName)) {
                    String methodName = method.getMethod().getName();
                    if ("saveOverride".equals(methodName) || "deleteOverride".equals(methodName)) {
                        assertThat(method.hasMethodAnnotation(PreAuthorize.class))
                                .as("ConfigController mutation method %s must have @PreAuthorize", methodName)
                                .isTrue();
                    }
                }
            }

            assertThat(checkedAdminMethods)
                    .as("Should have inspected multiple admin and infrastructure controller endpoints")
                    .isGreaterThan(15);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 2. UNGRANTED NAMESPACE ISOLATION TEST (Admins have NO bypass)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. Ungranted Namespace Grant Isolation for Admins")
    class UngrantedNamespaceTests {

        @Test
        @DisplayName("Admin accessing memory table on ungranted namespace via header returns 403 Forbidden")
        void testAdminOnUngrantedNamespaceViaHeaderReturns403() throws Exception {
            mvc.perform(get("/api/v1/memory/table")
                            .header("X-Spector-Namespace", OTHER_USER)
                            .with(user(ADMIN_USER).roles("admin")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("SPE-800-002"));
        }

        @Test
        @DisplayName("Super-Admin accessing memory table on ungranted namespace returns 403 Forbidden")
        void testSuperAdminOnUngrantedNamespaceReturns403() throws Exception {
            mvc.perform(get("/api/v1/memory/table")
                            .header("X-Spector-Namespace", OTHER_USER)
                            .with(user(ADMIN_USER).roles("super-admin")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("SPE-800-002"));
        }

        @Test
        @DisplayName("Admin accessing memory table on ungranted namespace via query param returns 403 Forbidden")
        void testAdminOnUngrantedNamespaceViaParamReturns403() throws Exception {
            mvc.perform(get("/api/v1/memory/table")
                            .param("namespace", OTHER_USER)
                            .with(user(ADMIN_USER).roles("admin")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("SPE-800-002"));
        }

        @Test
        @DisplayName("Admin accessing memory recall on ungranted namespace returns 403 Forbidden")
        void testAdminOnRecallUngrantedNamespaceReturns403() throws Exception {
            String recallJson = mapper.writeValueAsString(Map.of("query", "test recall", "limit", 5));
            mvc.perform(post("/api/v1/memory/recall")
                            .header("X-Spector-Namespace", OTHER_USER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(recallJson)
                            .with(user(ADMIN_USER).roles("admin")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("SPE-800-002"));
        }

        @Test
        @DisplayName("Admin accessing remember on ungranted namespace returns 403 Forbidden")
        void testAdminOnRememberUngrantedNamespaceReturns403() throws Exception {
            String rememberJson = mapper.writeValueAsString(Map.of("text", "admin memory write"));
            mvc.perform(post("/api/v1/memory")
                            .header("X-Spector-Namespace", OTHER_USER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rememberJson)
                            .with(user(ADMIN_USER).roles("admin")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("SPE-800-002"));
        }

        @Test
        @DisplayName("Admin accessing graph overview on ungranted namespace returns 403 Forbidden")
        void testAdminOnGraphOverviewUngrantedNamespaceReturns403() throws Exception {
            mvc.perform(get("/api/v1/memory/graph/overview")
                            .header("X-Spector-Namespace", OTHER_USER)
                            .with(user(ADMIN_USER).roles("admin")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("SPE-800-002"));
        }

        @Test
        @DisplayName("Admin accessing delete memory on ungranted namespace returns 403 Forbidden")
        void testAdminOnDeleteMemoryUngrantedNamespaceReturns403() throws Exception {
            mvc.perform(delete("/api/v1/memory/mem-1")
                            .header("X-Spector-Namespace", OTHER_USER)
                            .with(user(ADMIN_USER).roles("admin")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("SPE-800-002"));
        }

        @Test
        @DisplayName("Admin accessing memory table on their own granted namespace returns 200 OK")
        void testAdminOnOwnedNamespaceReturns200() throws Exception {
            mvc.perform(get("/api/v1/memory/table")
                            .header("X-Spector-Namespace", ADMIN_USER)
                            .with(user(ADMIN_USER).roles("admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(1));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 3. INFRA/ADMIN ENDPOINTS REJECT NON-ADMIN ROLES
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. Non-Admin Roles Rejected on Infra & Admin Endpoints")
    class NonAdminRejectionTests {

        private final List<String> nonAdminRoles = List.of("viewer", "agent", "editor");

        @Test
        @DisplayName("Non-admins rejected with 403 on cache admin endpoints")
        void testNonAdminsRejectedOnCache() throws Exception {
            for (String role : nonAdminRoles) {
                mvc.perform(get("/api/v1/admin/cache").with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
                mvc.perform(get("/api/v1/cache").with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("Non-admins rejected with 403 on task management endpoints")
        void testNonAdminsRejectedOnTasks() throws Exception {
            for (String role : nonAdminRoles) {
                mvc.perform(get("/tasks").with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
                mvc.perform(get("/api/v1/tasks").with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("Non-admins rejected with 403 on provider management endpoints")
        void testNonAdminsRejectedOnProviders() throws Exception {
            for (String role : nonAdminRoles) {
                mvc.perform(get("/providers").with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
                mvc.perform(get("/api/v1/providers").with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("Non-admins rejected with 403 on config mutations")
        void testNonAdminsRejectedOnConfigMutations() throws Exception {
            String configJson = mapper.writeValueAsString(Map.of("values", Map.of("key", "val")));
            for (String role : nonAdminRoles) {
                mvc.perform(put("/api/v1/config/memory")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(configJson)
                                .with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());

                mvc.perform(delete("/api/v1/config/memory")
                                .with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("Non-admins rejected with 403 on salience mutations")
        void testNonAdminsRejectedOnSalienceMutations() throws Exception {
            String interestsJson = mapper.writeValueAsString(Map.of("interests", List.of()));
            for (String role : nonAdminRoles) {
                mvc.perform(put("/api/v1/config/salience/interests")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(interestsJson)
                                .with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());

                mvc.perform(put("/api/v1/config/salience/rescore")
                                .with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("Non-admins rejected with 403 on usage, connectors, plugins, auth/users, approvals")
        void testNonAdminsRejectedOnOtherAdminSurfaces() throws Exception {
            for (String role : nonAdminRoles) {
                mvc.perform(get("/api/v1/usage/summary").with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
                mvc.perform(get("/api/v1/connectors").with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
                mvc.perform(get("/api/v1/plugins").with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
                mvc.perform(get("/api/v1/auth/users").with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
                mvc.perform(get("/api/v1/agent/approvals").with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("Non-admins authorized with 200 on /api/v1/credentials (user-scoped listing)")
        void testNonAdminsAuthorizedOnCredentialsList() throws Exception {
            for (String role : nonAdminRoles) {
                mvc.perform(get("/api/v1/credentials").with(user("user1").roles(role)))
                        .andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("Non-admins rejected with 403 on Prometheus actuator metrics")
        void testNonAdminsRejectedOnPrometheus() throws Exception {
            for (String role : nonAdminRoles) {
                mvc.perform(get("/actuator/prometheus").with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("Non-admins rejected with 403 on observability stats and timeline")
        void testNonAdminsRejectedOnObservability() throws Exception {
            for (String role : nonAdminRoles) {
                mvc.perform(get("/api/v1/observability/stats").with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
                mvc.perform(get("/api/v1/observability/timeline").with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("Non-admins rejected with 403 on salience rescoring and user profile mutations")
        void testNonAdminsRejectedOnUserSalienceMutations() throws Exception {
            for (String role : nonAdminRoles) {
                mvc.perform(post("/api/v1/salience/rescore").with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
                mvc.perform(put("/api/v1/salience/user/default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                                .with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
                mvc.perform(delete("/api/v1/salience/user/default").with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("Non-admins rejected with 403 on migration export")
        void testNonAdminsRejectedOnMigration() throws Exception {
            for (String role : nonAdminRoles) {
                mvc.perform(post("/api/v1/migration/export")
                                .param("outputPath", "/tmp/export")
                                .with(user("user1").roles(role)))
                        .andExpect(status().isForbidden());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 4. TWO-TIER ADMIN DIFFERENTIATION (super-admin vs admin)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. Two-Tier Admin Model: Platform Operator vs Tenant Admin")
    class TwoTierAdminTests {

        @Test
        @DisplayName("Tenant admin (ROLE_admin) rejected with 403 on Platform Operator endpoints")
        void testTenantAdminRejectedOnPlatformOperatorEndpoints() throws Exception {
            // Hardware diagnostics requires super-admin
            mvc.perform(get("/api/v1/system/hardware").with(user("admin1").roles("admin")))
                    .andExpect(status().isForbidden());

            // JVM metrics requires super-admin
            mvc.perform(get("/api/v1/system/metrics").with(user("admin1").roles("admin")))
                    .andExpect(status().isForbidden());

            // Batch migration requires super-admin
            mvc.perform(post("/api/v1/migration/export")
                            .param("outputPath", "/tmp/export")
                            .with(user("admin1").roles("admin")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Platform Operator role variants authorized on Platform Operator endpoints via SpectorAuthorityMapper")
        void testSuperAdminAuthorizedOnPlatformEndpoints() throws Exception {
            for (String role : List.of("super-admin", "SUPER_ADMIN", "SUPER-ADMIN", "super_admin", "SuperAdmin", "superAdmin")) {
                mvc.perform(get("/api/v1/system/hardware").with(user("super1").authorities(SpectorAuthorityMapper.forRole(role))))
                        .andExpect(status().isOk());

                mvc.perform(get("/api/v1/system/metrics").with(user("super1").authorities(SpectorAuthorityMapper.forRole(role))))
                        .andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("Tenant Admin rejected with 403 on global/system configuration mutation")
        void testTenantAdminRejectedOnGlobalConfig() throws Exception {
            String configJson = mapper.writeValueAsString(Map.of("scope", "system", "values", Map.of("key", "val")));
            mvc.perform(put("/api/v1/config/memory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(configJson)
                            .with(user("admin1").roles("admin")))
                    .andExpect(status().isForbidden());

            mvc.perform(delete("/api/v1/config/memory")
                            .param("scope", "system")
                            .with(user("admin1").roles("admin")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Platform Operator authorized on global/system configuration mutation")
        void testSuperAdminAuthorizedOnGlobalConfig() throws Exception {
            String configJson = mapper.writeValueAsString(Map.of("scope", "system", "values", Map.of("key", "val")));
            mvc.perform(put("/api/v1/config/memory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(configJson)
                            .with(user("super1").roles("super-admin")))
                    .andExpect(status().isOk());

            mvc.perform(delete("/api/v1/config/memory")
                            .param("scope", "system")
                            .with(user("super1").roles("super-admin")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Tenant Admin rejected with 403 when attempting privilege escalation to super-admin")
        void testPrivilegeEscalationPrevented() throws Exception {
            String registerJson = mapper.writeValueAsString(Map.of(
                    "username", "escalatedUser",
                    "password", "Password123!",
                    "roles", List.of("super-admin")
            ));
            mvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerJson)
                            .with(user("admin1").roles("admin")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Privilege escalation rejected with 403 when role name contains leading/trailing whitespace")
        void testPrivilegeEscalationPreventedWithWhitespace() throws Exception {
            String registerJson = mapper.writeValueAsString(Map.of(
                    "username", "escalatedUserWs",
                    "password", "Password123!",
                    "roles", List.of(" super-admin ")
            ));
            mvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerJson)
                            .with(user("admin1").roles("admin")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Privilege escalation rejected with 403 when super-admin scope is requested on registration")
        void testPrivilegeEscalationPreventedWithSuperAdminScopeOnRegister() throws Exception {
            String registerJson = mapper.writeValueAsString(Map.of(
                    "username", "escalatedScopeUser",
                    "password", "Password123!",
                    "roles", List.of("editor"),
                    "scopes", List.of("spector:admin")
            ));
            mvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerJson)
                            .with(user("admin1").roles("admin")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Privilege escalation rejected with 403 when non-super-admin assigns super-admin role or scope in updateUser")
        void testPrivilegeEscalationPreventedOnUpdateUser() throws Exception {
            String updateRoleJson = mapper.writeValueAsString(Map.of(
                    "roles", List.of(" super-admin ")
            ));
            mvc.perform(put("/api/v1/auth/users/0195500000002")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateRoleJson)
                            .with(user("admin1").roles("admin")))
                    .andExpect(status().isForbidden());

            String updateScopeJson = mapper.writeValueAsString(Map.of(
                    "scopes", List.of("spector:admin")
            ));
            mvc.perform(put("/api/v1/auth/users/0195500000002")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateScopeJson)
                            .with(user("admin1").roles("admin")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Privilege escalation rejected with 403 when non-super-admin requests super-admin API key")
        void testPrivilegeEscalationPreventedOnApiKeyCreation() throws Exception {
            for (String scope : List.of("spector:admin", "scope_spector:admin", "SCOPE-spector:admin", "super-admin")) {
                String apiKeyJson = mapper.writeValueAsString(Map.of(
                        "name", "admin-key-" + System.nanoTime(),
                        "category", "AUTH",
                        "provider", "spector",
                        "credentialType", "API_KEY",
                        "properties", Map.of("scopes", List.of(scope))
                ));
                mvc.perform(post("/api/v1/credentials")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(apiKeyJson)
                                .with(user("admin1").roles("admin")))
                        .andExpect(status().isForbidden());
            }

            for (String scope : List.of("admin", "spector:namespace:admin")) {
                String nonAdminApiKeyJson = mapper.writeValueAsString(Map.of(
                        "name", "viewer-key-" + System.nanoTime(),
                        "category", "AUTH",
                        "provider", "spector",
                        "credentialType", "API_KEY",
                        "properties", Map.of("scopes", List.of(scope))
                ));
                mvc.perform(post("/api/v1/credentials")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(nonAdminApiKeyJson)
                                .with(user("user1").roles("viewer")))
                        .andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("Both admin and super-admin authorized on Tenant Admin infra endpoints")
        void testAdminAndSuperAdminAuthorizedOnTenantEndpoints() throws Exception {
            for (String role : List.of("admin", "super-admin", "ADMIN", "Admin")) {
                mvc.perform(get("/api/v1/admin/cache").with(user("adm").authorities(SpectorAuthorityMapper.forRole(role))))
                        .andExpect(status().isOk());

                mvc.perform(get("/tasks").with(user("adm").authorities(SpectorAuthorityMapper.forRole(role))))
                        .andExpect(status().isOk());

                mvc.perform(get("/providers").with(user("adm").authorities(SpectorAuthorityMapper.forRole(role))))
                        .andExpect(status().isOk());

                mvc.perform(get("/api/v1/system/status").with(user("adm").authorities(SpectorAuthorityMapper.forRole(role))))
                        .andExpect(status().isOk());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 5. UNAUTHENTICATED REQUESTS RECEIVE 401
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("5. Unauthenticated Request Gating")
    class UnauthenticatedRejectionTests {

        @Test
        @DisplayName("Unauthenticated requests to admin and memory endpoints return 401 Unauthorized")
        void testUnauthenticatedRequestsRejected() throws Exception {
            mvc.perform(get("/api/v1/admin/cache")).andExpect(status().isUnauthorized());
            mvc.perform(get("/tasks")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/observability/stats")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/memory/table")).andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 6. API KEY PRINCIPAL ROLE MAPPING & ISOLATION
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("6. API Key Principal Role Mapping & Grant Isolation")
    class ApiKeySecurityTests {

        private static String sha256Hex(String input) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(digest);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 algorithm missing", e);
            }
        }

        @Test
        @DisplayName("API key principal inheriting admin role is authorized on admin endpoints")
        void testApiKeyAdminAuthorizedOnAdminEndpoints() throws Exception {
            if (credentialRepository == null || userAccountStore == null) {
                return;
            }
            String adminUid = userAccountStore.findByUsername("apiKeyAdmin")
                    .map(UserRow::userId)
                    .orElseGet(() -> userAccountStore.createUser("apiKeyAdmin", "Password123!", null, null,
                            Set.of("admin"), Set.of(), false));
            String rawKey = "sk_spec_admin_test_12345678901234567890";
            credentialRepository.save(CredentialRecord.builder("cred-admin-1", "default", "admin-key", CredentialCategory.AUTH, "spector")
                    .userId(adminUid)
                    .keyHash(sha256Hex(rawKey))
                    .ciphertext("dummy")
                    .iv("dummy")
                    .authTag("dummy")
                    .maskedPreview("sk_spec_...admin")
                    .properties(Map.of("scopes", List.of("admin")))
                    .build());

            // API key with admin role can access cache admin and tasks
            mvc.perform(get("/api/v1/admin/cache")
                            .header("Authorization", "Bearer " + rawKey))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/v1/tasks")
                            .header("Authorization", "Bearer " + rawKey))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("API key principal with admin role receives 403 on ungranted memory namespace")
        void testApiKeyAdminRejectedOnUngrantedNamespace() throws Exception {
            if (credentialRepository == null || userAccountStore == null) {
                return;
            }
            String adminUid = userAccountStore.findByUsername("apiKeyAdmin2")
                    .map(UserRow::userId)
                    .orElseGet(() -> userAccountStore.createUser("apiKeyAdmin2", "Password123!", null, null,
                            Set.of("admin"), Set.of(), false));
            catalog.getOrCreateAccount(adminUid);
            String rawKey = "sk_spec_admin2_test_12345678901234567890";
            credentialRepository.save(CredentialRecord.builder("cred-admin-2", "default", "admin-key-2", CredentialCategory.AUTH, "spector")
                    .userId(adminUid)
                    .keyHash(sha256Hex(rawKey))
                    .ciphertext("dummy")
                    .iv("dummy")
                    .authTag("dummy")
                    .maskedPreview("sk_spec_...admin2")
                    .properties(Map.of("scopes", List.of("admin")))
                    .build());

            // Content isolation: Admin API key calling OTHER_USER's namespace receives 403
            mvc.perform(get("/api/v1/memory/table")
                            .header("X-Spector-Namespace", OTHER_USER)
                            .header("Authorization", "Bearer " + rawKey))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("SPE-800-002"));
        }

        @Test
        @DisplayName("API key principal without admin role rejected with 403 on admin endpoints")
        void testNonAdminApiKeyRejectedOnAdminEndpoints() throws Exception {
            if (credentialRepository == null || userAccountStore == null) {
                return;
            }
            String viewerUid = userAccountStore.findByUsername("apiKeyViewer")
                    .map(UserRow::userId)
                    .orElseGet(() -> userAccountStore.createUser("apiKeyViewer", "Password123!", null, null,
                            Set.of("viewer"), Set.of("memory:read"), false));
            String rawKey = "sk_spec_viewer_test_12345678901234567890";
            credentialRepository.save(CredentialRecord.builder("cred-viewer-1", "default", "viewer-key", CredentialCategory.AUTH, "spector")
                    .userId(viewerUid)
                    .keyHash(sha256Hex(rawKey))
                    .ciphertext("dummy")
                    .iv("dummy")
                    .authTag("dummy")
                    .maskedPreview("sk_spec_...viewer")
                    .properties(Map.of("scopes", List.of("memory:read")))
                    .build());

            mvc.perform(get("/api/v1/admin/cache")
                            .header("Authorization", "Bearer " + rawKey))
                    .andExpect(status().isForbidden());
        }
    }
}
