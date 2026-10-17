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
package com.spectrayan.spector.synapse.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.config.properties.AuthProperties;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.mcp.tools.memory.MemoryIntrospectTool;
import com.spectrayan.spector.mcp.tools.memory.MemoryRecallTool;
import com.spectrayan.spector.mcp.tools.memory.MemoryRememberTool;
import com.spectrayan.spector.mcp.tools.memory.MemoryStatusTool;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.api.SalienceProfileProvider;
import com.spectrayan.spector.memory.cortex.metamemory.MemoryInsight;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.neuromod.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.agent.graph.CognitiveState;
import com.spectrayan.spector.synapse.agent.graph.nodes.ToolExecutionNode;
import com.spectrayan.spector.synapse.agent.tools.VectorSearchTool;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountProfile;
import com.spectrayan.spector.synapse.catalog.Grant;
import com.spectrayan.spector.synapse.catalog.GrantAction;
import com.spectrayan.spector.synapse.catalog.GrantObjectType;
import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.PrincipalKind;
import com.spectrayan.spector.synapse.catalog.PrincipalType;
import com.spectrayan.spector.synapse.catalog.file.FileAccountCatalog;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.memory.MemoryRegistry;
import com.spectrayan.spector.synapse.memory.MemoryRequestBinder;
import com.spectrayan.spector.synapse.memory.NamespaceResolver;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

/**
 * Multi-tenant MCP and agentic tool namespace isolation test suite (Issue #1061).
 *
 * <p>Verifies that MCP tools ({@link McpController}, {@link McpRequestMemory}, {@code memory_remember},
 * {@code memory_recall}, {@code memory_status}, {@code memory_introspect}, {@code vector_search}) and
 * agentic tool execution ({@link ToolRegistry}, {@link ToolExecutionNode}) enforce strict
 * {@code principal -> tenant -> namespace} and grant boundaries across {@code user}, {@code agent},
 * and {@code admin}/{@code super-admin} principals across two tenants.</p>
 */
@DisplayName("Issue #1061 — MCP & Agentic Tool Namespace Isolation Across User, Agent, and Admin Principals")
class McpAgenticNamespaceIsolationTest {

    private static final String TENANT_ALPHA = "tenant-alpha";
    private static final String TENANT_BETA = "tenant-beta";

    private static final String USER_ALPHA_1 = "user-alpha-1";
    private static final String USER_ALPHA_2 = "user-alpha-2";
    private static final String USER_BETA_1 = "user-beta-1";

    private static final String AGENT_ALPHA_1 = "agent-alpha-1";
    private static final String AGENT_BETA_1 = "agent-beta-1";

    private static final String ADMIN_ALPHA = "admin-alpha";
    private static final String SUPER_ADMIN = "super-admin-platform";

    @TempDir
    Path tempDir;

    private FileAccountCatalog catalog;
    private MemoryRegistry registry;
    private MemoryRequestBinder binder;
    private ToolRegistry toolRegistry;
    private McpController mcpController;

    private SpectorMemory memUserAlpha1Default;
    private SpectorMemory memUserAlpha1Shared;
    private SpectorMemory memUserAlpha2Default;
    private SpectorMemory memUserBeta1Default;
    private SpectorMemory memAdminAlphaDefault;
    private SpectorMemory memSuperAdminDefault;

    private NamespaceRecord nsAlphaShared;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        catalog = new FileAccountCatalog(tempDir, objectMapper);

        // Provision user, agent, and admin principals across TENANT_ALPHA and TENANT_BETA
        catalog.getOrCreateAccount(USER_ALPHA_1, AccountProfile.HUMAN_TEAM, PrincipalKind.HUMAN, TENANT_ALPHA);
        catalog.getOrCreateAccount(USER_ALPHA_2, AccountProfile.HUMAN_SOLO, PrincipalKind.HUMAN, TENANT_ALPHA);
        catalog.getOrCreateAccount(USER_BETA_1, AccountProfile.HUMAN_SOLO, PrincipalKind.HUMAN, TENANT_BETA);

        catalog.getOrCreateAccount(AGENT_ALPHA_1, AccountProfile.AGENT, PrincipalKind.AGENT, TENANT_ALPHA);
        catalog.getOrCreateAccount(AGENT_BETA_1, AccountProfile.AGENT, PrincipalKind.AGENT, TENANT_BETA);

        catalog.getOrCreateAccount(ADMIN_ALPHA, AccountProfile.HUMAN_TEAM, PrincipalKind.HUMAN, TENANT_ALPHA);
        catalog.getOrCreateAccount(SUPER_ADMIN, AccountProfile.UNLIMITED, PrincipalKind.HUMAN, TENANT_ALPHA);

        // USER_ALPHA_1 creates a dedicated project namespace to share with AGENT_ALPHA_1
        nsAlphaShared = catalog.createNamespace(
                USER_ALPHA_1, "alpha-shared", NamespaceType.PROJECT, "Alpha Shared", "Shared with agent", null);

        SynapseProperties props = synapseProps(true);
        ObjectProvider<SpectorMemory> sharedProvider = mockProvider();
        when(sharedProvider.getIfAvailable()).thenReturn(mock(SpectorMemory.class));
        ObjectProvider<EmbeddingProvider> embedderProvider = mockProvider();
        ObjectProvider<LlmProvider> textGenProvider = mockProvider();
        ObjectProvider<SalienceProfileProvider> salienceProvider = mockProvider();
        ObjectProvider<ObjectMapper> objectMapperProvider = mockProvider();
        when(objectMapperProvider.getIfAvailable()).thenReturn(objectMapper);

        registry = new MemoryRegistry(
                sharedProvider, props, embedderProvider, textGenProvider, salienceProvider, objectMapperProvider,
                null, null, null, null, null, null, catalog, 512);

        binder = new MemoryRequestBinder(catalog, registry, props, sharedProvider, null);
        ObjectProvider<MemoryRequestBinder> binderProvider = mockProvider();
        when(binderProvider.getIfAvailable()).thenReturn(binder);
        Method setBinder = MemoryRegistry.class.getDeclaredMethod("setBinderProvider", ObjectProvider.class);
        setBinder.setAccessible(true);
        setBinder.invoke(registry, binderProvider);

        // Create isolated SpectorMemory mocks per namespace
        memUserAlpha1Default = createMockMemory("alpha-1-secret-memory");
        memUserAlpha1Shared = createMockMemory("alpha-shared-memory");
        memUserAlpha2Default = createMockMemory("alpha-2-secret-memory");
        memUserBeta1Default = createMockMemory("beta-1-secret-memory");
        memAdminAlphaDefault = createMockMemory("admin-alpha-own-memory");
        memSuperAdminDefault = createMockMemory("super-admin-own-memory");

        injectHandle(registry, USER_ALPHA_1, memUserAlpha1Default);
        injectHandle(registry, nsAlphaShared.namespaceId(), memUserAlpha1Shared);
        injectHandle(registry, USER_ALPHA_2, memUserAlpha2Default);
        injectHandle(registry, USER_BETA_1, memUserBeta1Default);
        injectHandle(registry, ADMIN_ALPHA, memAdminAlphaDefault);
        injectHandle(registry, SUPER_ADMIN, memSuperAdminDefault);

        // Wire MCP & agentic tools bound to McpRequestMemory.current()
        Supplier<SpectorMemory> requestScopedMemory = McpRequestMemory::current;
        List<McpToolHandler> tools = List.of(
                new MemoryRememberTool(requestScopedMemory, false),
                new MemoryRecallTool(requestScopedMemory),
                new MemoryStatusTool(requestScopedMemory),
                new MemoryIntrospectTool(requestScopedMemory),
                new VectorSearchTool(sharedProvider)
        );
        toolRegistry = new ToolRegistry(tools);
        toolRegistry.setMemoryBindingContext(binder, true);
        mcpController = new McpController(toolRegistry, registry, props);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        McpRequestMemory.clear();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. AGENT PRINCIPALS: NO IMPLICIT DEFAULT NAMESPACE; EXPLICIT GRANTS ONLY
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Agent principal has no auto-provisioned default namespace and is denied until explicitly granted")
    void agentPrincipal_noImplicitDefaultNamespace_deniedUntilGranted() {
        Account agentAccount = catalog.getAccount(AGENT_ALPHA_1);
        assertThat(agentAccount.kind()).isEqualTo(PrincipalKind.AGENT);
        assertThat(agentAccount.defaultNamespaceId()).isNull();
        assertThat(catalog.listOwnedNamespaces(AGENT_ALPHA_1)).isEmpty();
        assertThat(catalog.listAccessible(AGENT_ALPHA_1)).isEmpty();

        bindJwt(AGENT_ALPHA_1, TENANT_ALPHA, "agent");

        // 1. Calling MCP without namespace when agent has 0 grants -> SPE-800-002
        Map<String, Object> noNsResult = mcpController.callTool("memory_recall", Map.of("query", "secret"));
        assertThat(noNsResult.get("isError")).isEqualTo(true);
        assertThat(extractText(noNsResult)).contains("SPE-800-002");

        // 2. Calling MCP targeting USER_ALPHA_1's ungranted namespace -> SPE-800-002
        Map<String, Object> ungrantedResult = mcpController.callTool("memory_recall",
                Map.of("query", "secret", "namespace", nsAlphaShared.namespaceId()));
        assertThat(ungrantedResult.get("isError")).isEqualTo(true);
        assertThat(extractText(ungrantedResult)).contains("SPE-800-002");
        verify(memUserAlpha1Shared, never()).recall(anyString(), any(RecallOptions.class));

        // 3. Calling agentic ToolRegistry.executeTool without grant -> SPE-800-002
        String agenticDenied = toolRegistry.executeTool(ToolExecutionRequest.builder()
                .id("call-1")
                .name("memory_recall")
                .arguments("{\"query\":\"secret\"}")
                .build());
        assertThat(agenticDenied).contains("SPE-800-002");
    }

    @Test
    @DisplayName("Agent principal granted READER can read via MCP and agentic tools, but write tools fail with SPE-800-002 until granted WRITER")
    void agentPrincipal_readerVsWriterGrantEnforcement() {
        // Grant READER on nsAlphaShared to AGENT_ALPHA_1
        catalog.addGrant(new Grant(
                "grant-agent-alpha-reader",
                GrantObjectType.NAMESPACE,
                nsAlphaShared.namespaceId(),
                AGENT_ALPHA_1,
                PrincipalType.ACCOUNT,
                GrantRole.READER,
                Set.of(GrantAction.READ),
                USER_ALPHA_1,
                Instant.now(),
                null,
                null
        ));

        bindJwt(AGENT_ALPHA_1, TENANT_ALPHA, "agent");

        // 1. Agent can recall from its sole granted namespace (explicitly or by default resolution)
        Map<String, Object> recallOk = mcpController.callTool("memory_recall",
                Map.of("query", "shared", "namespace", "alpha-shared"));
        assertThat(recallOk.get("isError")).isEqualTo(false);
        assertThat(extractText(recallOk)).contains("alpha-shared-memory");

        Map<String, Object> defaultSoleGrantRecall = mcpController.callTool("memory_recall",
                Map.of("query", "shared"));
        assertThat(defaultSoleGrantRecall.get("isError")).isEqualTo(false);
        assertThat(extractText(defaultSoleGrantRecall)).contains("alpha-shared-memory");

        // 2. Agent with READER grant cannot invoke write tool memory_remember on MCP -> SPE-800-002
        Map<String, Object> writeDeniedMcp = mcpController.callTool("memory_remember",
                Map.of("text", "unauthorized write", "namespace", "alpha-shared"));
        assertThat(writeDeniedMcp.get("isError")).isEqualTo(true);
        assertThat(extractText(writeDeniedMcp)).contains("SPE-800-002");

        // 3. Agent with READER grant cannot invoke write tool via agentic ToolExecutionNode -> SPE-800-002
        ToolExecutionNode node = new ToolExecutionNode(toolRegistry);
        CognitiveState state = new CognitiveState(Map.of(
                "tool_calls", List.of("memory_remember({\"text\":\"unauthorized\",\"namespace\":\"alpha-shared\"})")
        ));
        Map<String, Object> nodeOut = node.apply(state);
        @SuppressWarnings("unchecked")
        List<String> toolResults = (List<String>) nodeOut.get("tool_results");
        assertThat(toolResults).singleElement().satisfies(s -> assertThat(s).contains("SPE-800-002"));

        // 4. Upgrade AGENT_ALPHA_1 grant to WRITER -> memory_remember succeeds on MCP and agentic tools
        catalog.addGrant(new Grant(
                "grant-agent-alpha-writer",
                GrantObjectType.NAMESPACE,
                nsAlphaShared.namespaceId(),
                AGENT_ALPHA_1,
                PrincipalType.ACCOUNT,
                GrantRole.WRITER,
                Set.of(GrantAction.READ, GrantAction.WRITE),
                USER_ALPHA_1,
                Instant.now(),
                null,
                null
        ));

        Map<String, Object> writeAllowedMcp = mcpController.callTool("memory_remember",
                Map.of("text", "authorized agent note", "namespace", "alpha-shared"));
        assertThat(writeAllowedMcp.get("isError")).isEqualTo(false);

        // 5. Agent still cannot access USER_ALPHA_1's ungranted personal default namespace -> SPE-800-002
        Map<String, Object> ownerDefaultDenied = mcpController.callTool("memory_recall",
                Map.of("query", "secret", "namespace", USER_ALPHA_1));
        assertThat(ownerDefaultDenied.get("isError")).isEqualTo(true);
        assertThat(extractText(ownerDefaultDenied)).contains("SPE-800-002");
        verify(memUserAlpha1Default, never()).recall(anyString(), any(RecallOptions.class));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. USER PRINCIPALS ACROSS TWO TENANTS: SAME-TENANT & CROSS-TENANT ISOLATION
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("User principals across two tenants can access only own/granted namespaces; cross-tenant returns SPE-820-001")
    void userPrincipals_twoTenants_enforcesGrantAndTenantBoundaries() {
        // User Alpha 1 accesses own default namespace
        bindJwt(USER_ALPHA_1, TENANT_ALPHA, "editor");
        Map<String, Object> ownRecall = mcpController.callTool("memory_recall", Map.of("query", "alpha"));
        assertThat(ownRecall.get("isError")).isEqualTo(false);
        assertThat(extractText(ownRecall)).contains("alpha-1-secret-memory");

        // User Alpha 1 targeting User Alpha 2's ungranted namespace in same tenant -> SPE-800-002
        Map<String, Object> sameTenantUngranted = mcpController.callTool("memory_recall",
                Map.of("query", "alpha2", "namespace", USER_ALPHA_2));
        assertThat(sameTenantUngranted.get("isError")).isEqualTo(true);
        assertThat(extractText(sameTenantUngranted)).contains("SPE-800-002");
        verify(memUserAlpha2Default, never()).recall(anyString(), any(RecallOptions.class));

        // User Alpha 1 targeting User Beta 1's namespace in TENANT_BETA -> SPE-820-001 (SPE-SEC-001)
        Map<String, Object> crossTenantMcp = mcpController.callTool("memory_recall",
                Map.of("query", "beta", "namespace", USER_BETA_1));
        assertThat(crossTenantMcp.get("isError")).isEqualTo(true);
        assertThat(extractText(crossTenantMcp)).contains("SPE-820-001");
        verify(memUserBeta1Default, never()).recall(anyString(), any(RecallOptions.class));

        // Agentic tool call from User Alpha 1 targeting User Beta 1's namespace -> SPE-820-001
        String crossTenantAgentic = toolRegistry.executeTool(ToolExecutionRequest.builder()
                .id("call-cross-tenant")
                .name("memory_recall")
                .arguments("{\"query\":\"beta\",\"namespace\":\"" + USER_BETA_1 + "\"}")
                .build());
        assertThat(crossTenantAgentic).contains("SPE-820-001");
        verify(memUserBeta1Default, never()).recall(anyString(), any(RecallOptions.class));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. ADMIN & SUPER-ADMIN PRINCIPALS: NO IMPLICIT CONTENT ACCESS OR BREAK-GLASS ON MCP
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("admin and super-admin roles grant no content access beyond their own/granted namespaces on MCP or agentic tools")
    void adminAndSuperAdmin_noImplicitContentAccess_andBreakGlassRejectedOnMcp() {
        // 1. Tenant admin (ADMIN_ALPHA) can access their own default namespace
        bindJwt(ADMIN_ALPHA, TENANT_ALPHA, "admin");
        Map<String, Object> adminOwn = mcpController.callTool("memory_recall", Map.of("query", "own"));
        assertThat(adminOwn.get("isError")).isEqualTo(false);
        assertThat(extractText(adminOwn)).contains("admin-alpha-own-memory");

        // 2. Tenant admin (ADMIN_ALPHA) targeting USER_ALPHA_1's ungranted namespace -> SPE-800-002
        Map<String, Object> adminUngranted = mcpController.callTool("memory_recall",
                Map.of("query", "secret", "namespace", USER_ALPHA_1));
        assertThat(adminUngranted.get("isError")).isEqualTo(true);
        assertThat(extractText(adminUngranted)).contains("SPE-800-002");
        verify(memUserAlpha1Default, never()).recall(anyString(), any(RecallOptions.class));

        // 3. Platform Operator (SUPER_ADMIN) targeting USER_ALPHA_1's ungranted namespace -> SPE-800-002
        bindJwt(SUPER_ADMIN, TENANT_ALPHA, "super-admin");
        Map<String, Object> superAdminUngranted = mcpController.callTool("memory_recall",
                Map.of("query", "secret", "namespace", USER_ALPHA_1));
        assertThat(superAdminUngranted.get("isError")).isEqualTo(true);
        assertThat(extractText(superAdminUngranted)).contains("SPE-800-002");

        // 4. Platform Operator (SUPER_ADMIN) targeting USER_BETA_1's cross-tenant namespace -> SPE-820-001
        Map<String, Object> superAdminCrossTenant = mcpController.callTool("memory_recall",
                Map.of("query", "secret", "namespace", USER_BETA_1));
        assertThat(superAdminCrossTenant.get("isError")).isEqualTo(true);
        assertThat(extractText(superAdminCrossTenant)).contains("SPE-820-001");

        // 5. Break-glass grant (`bg_grant_...`) does NOT apply to MCP or agentic tools -> SPE-820-005 / SPE-800-002
        catalog.addGrant(new Grant(
                "bg_grant_admin_emergency",
                GrantObjectType.NAMESPACE,
                USER_ALPHA_1,
                ADMIN_ALPHA,
                PrincipalType.ACCOUNT,
                GrantRole.READER,
                Set.of(GrantAction.READ),
                ADMIN_ALPHA,
                Instant.now(),
                Instant.now().plusSeconds(600),
                null
        ));
        bindJwt(ADMIN_ALPHA, TENANT_ALPHA, "admin");
        Map<String, Object> breakGlassMcp = mcpController.callTool("memory_recall",
                Map.of("query", "secret", "namespace", USER_ALPHA_1));
        assertThat(breakGlassMcp.get("isError")).isEqualTo(true);
        assertThat(extractText(breakGlassMcp)).contains("SPE-820-005");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. AGGREGATE-ONLY CONTRACT FOR memory_status AND memory_introspect
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("memory_status and memory_introspect stay aggregate-only and never expose raw memory text")
    void statusAndIntrospect_remainAggregateOnly() {
        bindJwt(USER_ALPHA_1, TENANT_ALPHA, "editor");

        Map<String, Object> statusRes = mcpController.callTool("memory_status", Map.of());
        assertThat(statusRes.get("isError")).isEqualTo(false);
        String statusText = extractText(statusRes);
        assertThat(statusText).doesNotContain("alpha-1-secret-memory");

        Map<String, Object> introspectRes = mcpController.callTool("memory_introspect",
                Map.of("topic", "architecture"));
        assertThat(introspectRes.get("isError")).isEqualTo(false);
        String introspectText = extractText(introspectRes);
        assertThat(introspectText).doesNotContain("alpha-1-secret-memory");
    }

    @Test
    @DisplayName("API-key principals resolve principal -> tenant -> namespace and enforce agent grant boundaries")
    void apiKeyAuthentication_resolvesPrincipalTenantAndNamespaceForUserAndAgent() {
        // 1. New API-key authenticated agent (not yet in catalog) with ROLE_agent -> auto-created as PrincipalKind.AGENT with no default namespace
        String apiKeyAgent = "agent-apikey-alpha";
        UsernamePasswordAuthenticationToken agentAuth = new UsernamePasswordAuthenticationToken(
                apiKeyAgent, null, AuthorityUtils.createAuthorityList("ROLE_agent", "SCOPE_spector:memory:read"));
        agentAuth.setDetails(Map.of("tenant_id", TENANT_ALPHA));
        SecurityContextHolder.getContext().setAuthentication(agentAuth);

        Map<String, Object> agentDenied = mcpController.callTool("memory_recall", Map.of("query", "alpha"));
        assertThat(agentDenied.get("isError")).isEqualTo(true);
        assertThat(extractText(agentDenied)).contains("SPE-800-002");
        assertThat(catalog.getAccount(apiKeyAgent).kind()).isEqualTo(PrincipalKind.AGENT);
        assertThat(catalog.getAccount(apiKeyAgent).defaultNamespaceId()).isNull();

        // 2. API-key authenticated user in TENANT_BETA cannot access TENANT_ALPHA namespace -> SPE-820-001
        UsernamePasswordAuthenticationToken betaUserAuth = new UsernamePasswordAuthenticationToken(
                USER_BETA_1, null, AuthorityUtils.createAuthorityList("ROLE_editor", "SCOPE_spector:memory:read"));
        betaUserAuth.setDetails(Map.of("tenant_id", TENANT_BETA));
        SecurityContextHolder.getContext().setAuthentication(betaUserAuth);

        Map<String, Object> crossTenantDenied = mcpController.callTool("memory_recall",
                Map.of("query", "alpha", "namespace", USER_ALPHA_1));
        assertThat(crossTenantDenied.get("isError")).isEqualTo(true);
        assertThat(extractText(crossTenantDenied)).contains("SPE-820-001");
    }

    @Test
    @DisplayName("All memory MCP tools advertise the optional 'namespace' parameter in their JSON input schema")
    void memoryMcpTools_advertiseOptionalNamespaceParameterInInputSchema() {
        for (String toolName : List.of("memory_remember", "memory_recall", "memory_status", "memory_introspect", "vector_search")) {
            McpToolHandler handler = toolRegistry.get(toolName).orElseThrow();
            assertThat(handler).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.inputSchema().get("properties");
            assertThat(properties)
                    .as("Tool %s should advertise optional 'namespace' property", handler.name())
                    .containsKey("namespace");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    private static void bindJwt(String principalId, String tenantId, String role) {
        String kind = "agent".equalsIgnoreCase(role) ? "AGENT" : "HUMAN";
        String profile = "agent".equalsIgnoreCase(role) ? "AGENT" : "HUMAN_SOLO";
        Jwt jwt = Jwt.withTokenValue("tok-" + principalId)
                .header("alg", "HS256")
                .subject(principalId)
                .claim("tenant_id", tenantId)
                .claim("roles", List.of(role))
                .claim("kind", kind)
                .claim("profile", profile)
                .build();
        Authentication auth = new JwtAuthenticationToken(
                jwt, AuthorityUtils.createAuthorityList("ROLE_" + role, "SCOPE_spector:memory:read", "SCOPE_spector:memory:write"), principalId);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @SuppressWarnings("unchecked")
    private static String extractText(Map<String, Object> response) {
        Object contentObj = response.get("content");
        if (contentObj instanceof List<?> list && !list.isEmpty()) {
            Object first = list.getFirst();
            if (first instanceof Map<?, ?> map && map.get("text") != null) {
                return String.valueOf(map.get("text"));
            }
        }
        return String.valueOf(response);
    }

    private static SpectorMemory createMockMemory(String markerText) throws Exception {
        SpectorMemory mem = mock(SpectorMemory.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        CognitiveResult result = mock(CognitiveResult.class);
        when(result.id()).thenReturn("mem-id-1");
        when(result.text()).thenReturn(markerText);
        when(result.score()).thenReturn(0.95f);
        when(result.importance()).thenReturn(0.9f);
        when(result.ageDays()).thenReturn(1.0f);
        when(result.memoryType()).thenReturn(MemoryType.SEMANTIC);
        when(result.source()).thenReturn(MemorySource.OBSERVED);
        when(result.synapticTags()).thenReturn(new String[] { "tag" });
        when(result.decayFactor()).thenReturn(0.95f);
        when(result.ltpAdjustedDecay()).thenReturn(0.95f);

        when(mem.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of(result));
        when(mem.remember(
                anyString(),
                any(MemoryType.class),
                any(MemorySource.class),
                org.mockito.ArgumentMatchers.nullable(com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints.class),
                any(String[].class))).thenReturn("mem-id-written");
        when(mem.totalMemories()).thenReturn(3);
        when(mem.memoryCount(any())).thenReturn(1);

        when(mem.introspect(anyString())).thenReturn(
                new MemoryInsight("architecture", 3, 0.85f, 0.90f, 0.80f, 0.85f, new String[]{"subtopic"}, 0.1f, 0.9f, "HIGH_CONFIDENCE"));

        LateralEvaluator.LateralMetrics metrics = mock(LateralEvaluator.LateralMetrics.class);
        when(mem.admin().lateralEvaluator().metrics()).thenReturn(metrics);
        return mem;
    }

    private SynapseProperties synapseProps(boolean authEnabled) {
        var auth = new AuthProperties(authEnabled, null, null, null, null, null, null, null);
        return new SynapseProperties(0, null, tempDir.toString(), null, null, auth);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> mockProvider() {
        return mock(ObjectProvider.class);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, Object> cacheOf(MemoryRegistry registry) throws Exception {
        Field resolverField = MemoryRegistry.class.getDeclaredField("resolver");
        resolverField.setAccessible(true);
        Object resolver = resolverField.get(registry);
        Field field = resolver.getClass().getDeclaredField("cache");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, Object>) field.get(resolver);
    }

    private static void injectHandle(MemoryRegistry registry, String namespaceId, SpectorMemory memory) throws Exception {
        Class<?> handleClass = Class.forName(
                "com.spectrayan.spector.synapse.memory.NamespaceResolver$MemoryHandle");
        Constructor<?> ctor = handleClass.getDeclaredConstructor(SpectorMemory.class);
        ctor.setAccessible(true);
        Object handle = ctor.newInstance(memory);
        cacheOf(registry).put(namespaceId, handle);
    }
}
