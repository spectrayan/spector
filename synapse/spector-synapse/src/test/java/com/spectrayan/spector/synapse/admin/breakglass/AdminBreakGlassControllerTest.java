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
package com.spectrayan.spector.synapse.admin.breakglass;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.synapse.admin.audit.AuditLogService;
import com.spectrayan.spector.synapse.admin.tenant.TenantService;
import com.spectrayan.spector.synapse.admin.tenant.TenantStatus;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.CreateTenantRequest;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.TenantQuotasDto;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.TenantSettingsDto;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.AccountProfile;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.PrincipalKind;
import com.spectrayan.spector.synapse.mcp.McpRequestMemory;
import com.spectrayan.spector.synapse.memory.FederatedRecallService;
import com.spectrayan.spector.synapse.memory.MemoryDto.GraphNodeDto;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryGraphResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableRow;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallResult;
import com.spectrayan.spector.synapse.memory.MemoryRequestBinder;
import com.spectrayan.spector.synapse.memory.MemoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * Integration tests for Break-Glass Namespace Access (Issue #1060):
 * <ul>
 *   <li>Hard deny (`403 SPE-820-004` / `SPE-SEC-BYOK-NO-BREAK-GLASS`) on BYOK tenants for both {@code admin} and {@code super-admin}.</li>
 *   <li>Dual-control approval requiring a second distinct administrator ({@code approver != requester}); self-approval fails with {@code 403 SPE-820-005}.</li>
 *   <li>Time-boxed, read-only, non-exportable, no-MCP temporary grant (`GrantRole.READER`).</li>
 *   <li>Per-read `BREAK_GLASS_READ` audit logging with accessed `memoryIds` visible in both `/api/v1/admin/audit` and the owner's `/api/v1/namespaces/{slugOrId}/audit`.</li>
 *   <li>Auto-revocation on expiry (`expires_at <= now()`) with `BREAK_GLASS_EXPIRED` audit and `403` on subsequent read.</li>
 *   <li>Manual revocation by tenant admin and by namespace owner with `BREAK_GLASS_REVOKED` audit.</li>
 *   <li>Owner notifications on request, approval, rejection, revocation, and expiry.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spector.auth.enabled=true",
        "spector.auth.jwt.secret=01234567890123456789012345678901",
        "spector.catalog.type=jdbc"
})
@DirtiesContext
@DisplayName("AdminBreakGlassControllerTest — Break-Glass Namespace Access (Issue #1060)")
class AdminBreakGlassControllerTest {

    private static final String TENANT_STD = "tenant-standard";
    private static final String TENANT_BYOK = "tenant-byok";

    private static final String ADMIN_1 = "admin-req-001";
    private static final String ADMIN_2 = "admin-app-002";
    private static final String SUPER_ADMIN = "sup-admin-001";
    private static final String OWNER_STD = "owner-std-001";
    private static final String OWNER_BYOK = "owner-byk-001";
    private static final String OTHER_USER = "other-usr-001";
    private static final String BYOK_ADMIN = "adm-byok-001";

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private AccountCatalog catalog;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private BreakGlassService breakGlassService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private MemoryRequestBinder binder;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private MemoryService memoryService;

    @MockitoBean
    private FederatedRecallService federatedRecallService;

    @MockitoBean
    private SpectorMemory spectorMemory;

    private MockMvc mvc;
    private String stdNamespaceId;
    private String byokNamespaceId;

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        breakGlassService.setClock(Clock.systemUTC());
        breakGlassService.clearDeliveredNotifications();

        // Clean test state across test methods
        jdbc.sql("DELETE FROM break_glass_requests").update();
        jdbc.sql("DELETE FROM audit_event").update();
        jdbc.sql("DELETE FROM admin_audit_log").update();
        jdbc.sql("DELETE FROM grants WHERE principal_id IN ('sup-admin-001', 'admin-req-001', 'admin-app-002', 'owner-std-001', 'owner-byk-001', 'other-usr-001', 'adm-byok-001') OR granted_by IN ('sup-admin-001', 'admin-req-001', 'admin-app-002', 'owner-std-001', 'owner-byk-001', 'other-usr-001', 'adm-byok-001')").update();
        jdbc.sql("DELETE FROM namespaces WHERE owner_account_id IN ('sup-admin-001', 'admin-req-001', 'admin-app-002', 'owner-std-001', 'owner-byk-001', 'other-usr-001', 'adm-byok-001')").update();
        jdbc.sql("DELETE FROM users WHERE user_id IN ('sup-admin-001', 'admin-req-001', 'admin-app-002', 'owner-std-001', 'owner-byk-001', 'other-usr-001', 'adm-byok-001')").update();
        jdbc.sql("DELETE FROM tenants WHERE tenant_id IN ('tenant-standard', 'tenant-byok')").update();

        // Provision standard (non-BYOK) tenant with max break-glass duration of 180 minutes
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        SUPER_ADMIN, "N/A", List.of(new SimpleGrantedAuthority("ROLE_super-admin"))
                )
        );
        tenantService.createTenant(new CreateTenantRequest(
                TENANT_STD, "Standard Tenant", "Standard non-BYOK tenant", "ENTERPRISE",
                TenantStatus.ACTIVE, false,
                new TenantSettingsDto(false, true, 180),
                TenantQuotasDto.unlimited()
        ), SUPER_ADMIN, "default");
        tenantService.registerByokKeyConfigured(TENANT_BYOK);
        tenantService.createTenant(new CreateTenantRequest(
                TENANT_BYOK, "BYOK Tenant", "Customer-managed key tenant", "ENTERPRISE",
                TenantStatus.ACTIVE, false,
                new TenantSettingsDto(true, true, 180),
                TenantQuotasDto.unlimited()
        ), SUPER_ADMIN, "default");
        SecurityContextHolder.clearContext();

        // Provision accounts in TENANT_STD and TENANT_BYOK
        catalog.getOrCreateAccount(SUPER_ADMIN, AccountProfile.UNLIMITED, PrincipalKind.HUMAN, "default");
        catalog.getOrCreateAccount(ADMIN_1, AccountProfile.HUMAN_TEAM, PrincipalKind.HUMAN, TENANT_STD);
        catalog.getOrCreateAccount(ADMIN_2, AccountProfile.HUMAN_TEAM, PrincipalKind.HUMAN, TENANT_STD);
        catalog.getOrCreateAccount(OWNER_STD, AccountProfile.HUMAN_SOLO, PrincipalKind.HUMAN, TENANT_STD);
        catalog.getOrCreateAccount(OTHER_USER, AccountProfile.HUMAN_SOLO, PrincipalKind.HUMAN, TENANT_STD);
        catalog.getOrCreateAccount(OWNER_BYOK, AccountProfile.HUMAN_SOLO, PrincipalKind.HUMAN, TENANT_BYOK);

        NamespaceRecord stdNs = catalog.resolve(OWNER_STD, "default").orElseThrow();
        this.stdNamespaceId = stdNs.namespaceId();

        NamespaceRecord byokNs = catalog.resolve(OWNER_BYOK, "default").orElseThrow();
        this.byokNamespaceId = byokNs.namespaceId();

        // Configure mock SpectorMemory and MemoryService responses for read-only verification
        var mockAdmin = org.mockito.Mockito.mock(SpectorMemoryAdmin.class);
        when(spectorMemory.admin()).thenReturn(mockAdmin);
        when(spectorMemory.totalMemories()).thenReturn(2);
        when(spectorMemory.memoryCount(any())).thenReturn(2);

        var row1 = new MemoryTableRow(
                "mem-bg-101", "Incident diagnostic trace 101", "Details 101", "SEMANTIC", "OBSERVED",
                0.9f, 5, 128, System.currentTimeMillis(), 1, 1,
                false, false, false, false, false,
                List.of("incident"), 0L, 1.0f, "2026-01-01T00:00:00Z", null
        );
        var row2 = new MemoryTableRow(
                "mem-bg-102", "Incident diagnostic trace 102", "Details 102", "EPISODIC", "OBSERVED",
                0.8f, 4, 128, System.currentTimeMillis(), 1, 1,
                false, false, false, false, false,
                List.of("incident"), 0L, 1.0f, "2026-01-01T00:00:00Z", null
        );
        var tableResponse = new MemoryTableResponse(List.of(row1, row2), 2, 0, 50, Map.of(), Map.of());
        when(memoryService.getMemoryTable(anyInt(), anyInt(), any(), anyBoolean())).thenReturn(tableResponse);

        var recallRes = new RecallResult(
                "mem-bg-101", "Incident diagnostic trace 101", "SEMANTIC",
                0.95, "SEMANTIC", "1.0 days", List.of("incident"), false
        );
        when(memoryService.recall(any())).thenReturn(List.of(recallRes));

        var graphNode = new GraphNodeDto(
                "mem-bg-101", "SEMANTIC", "Incident diagnostic trace 101",
                0.9, 5, System.currentTimeMillis(), List.of("incident")
        );
        when(memoryService.getGraphOverview(anyInt())).thenReturn(new MemoryGraphResponse(null, List.of(graphNode), List.of()));
    }

    @AfterEach
    void tearDown() {
        McpRequestMemory.clear();
        SecurityContextHolder.clearContext();
        breakGlassService.setClock(Clock.systemUTC());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. BYOK TENANT HARD DENY (BOTH ADMIN & SUPER-ADMIN)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("1. Break-glass request on BYOK tenant returns 403 SPE-820-004 (SPE-SEC-BYOK-NO-BREAK-GLASS) even for super-admin")
    void breakGlassRequestOnByokTenantFailsWith403ForSuperAdminAndAdmin() throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "namespaceId", byokNamespaceId,
                "justification", "SEV-1 incident investigation on BYOK tenant",
                "ticketRef", "INC-9001",
                "durationMinutes", 30
        ));

        // 1a. Platform Operator (super-admin) -> 403 Forbidden with SPE-820-004 / SPE-SEC-BYOK-NO-BREAK-GLASS
        mvc.perform(post("/api/v1/admin/break-glass")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(user(SUPER_ADMIN).roles("super-admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("SPE-820-004"))
                .andExpect(jsonPath("$.details.policyCode").value("SPE-SEC-BYOK-NO-BREAK-GLASS"));

        // 1b. Tenant Admin in TENANT_BYOK -> 403 Forbidden with SPE-820-004 / SPE-SEC-BYOK-NO-BREAK-GLASS
        catalog.getOrCreateAccount(BYOK_ADMIN, AccountProfile.HUMAN_TEAM, PrincipalKind.HUMAN, TENANT_BYOK);

        mvc.perform(post("/api/v1/admin/break-glass")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(user(BYOK_ADMIN).roles("admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("SPE-820-004"))
                .andExpect(jsonPath("$.details.policyCode").value("SPE-SEC-BYOK-NO-BREAK-GLASS"));

        // Verify DENIED audit events were recorded for BYOK break-glass attempts
        var events = auditLogService.listNamespaceAuditEvents(byokNamespaceId, 20).stream()
                .filter(e -> "BREAK_GLASS_REQUESTED".equals(e.action()) && AuditLogService.OUTCOME_DENIED.equals(e.outcome()))
                .toList();
        assertThat(events).hasSize(2);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. DUAL-CONTROL APPROVAL & SELF-APPROVAL REJECTION
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("2. Self-approval fails with 403 SPE-820-005; approval by second distinct admin activates session")
    void dualControlApprovalEnforcedAndSelfApprovalRejected() throws Exception {
        // Admin 1 requests break-glass access (duration 500 capped to tenant max 180)
        String reqBody = mapper.writeValueAsString(Map.of(
                "namespaceId", stdNamespaceId,
                "justification", "Investigating customer-reported recall latency spike",
                "ticketRef", "JIRA-4242",
                "durationMinutes", 500
        ));

        MvcResult createRes = mvc.perform(post("/api/v1/admin/break-glass")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqBody)
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.requesterAccountId").value(ADMIN_1))
                .andExpect(jsonPath("$.ownerAccountId").value(OWNER_STD))
                .andExpect(jsonPath("$.durationMinutes").value(180))
                .andReturn();

        JsonNode created = mapper.readTree(createRes.getResponse().getContentAsString());
        String bgId = created.get("id").asText();

        // While PENDING_APPROVAL, Admin 1 cannot read the namespace
        mvc.perform(get("/api/v1/memory/table")
                        .header("X-Spector-Namespace", stdNamespaceId)
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SPE-820-005"));

        // Self-approval by Admin 1 -> 403 Forbidden (SPE-820-005)
        mvc.perform(post("/api/v1/admin/break-glass/{id}/approve", bgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Attempting self-approval\"}")
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("SPE-820-005"));

        // Second distinct admin (Admin 2) approves -> 200 OK, status ACTIVE
        mvc.perform(post("/api/v1/admin/break-glass/{id}/approve", bgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Verified incident ticket JIRA-4242\"}")
                        .with(user(ADMIN_2).roles("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.approverAccountId").value(ADMIN_2))
                .andExpect(jsonPath("$.approvalComment").value("Verified incident ticket JIRA-4242"))
                .andExpect(jsonPath("$.grantId").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        // Verify owner notifications were dispatched for both request and approval
        var notifications = breakGlassService.getDeliveredNotifications();
        assertThat(notifications)
                .extracting(BreakGlassService.OwnerNotification::eventType)
                .contains("break_glass.requested", "break_glass.approved");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. READ-ONLY ACCESS, WRITE/EXPORT/MCP DENIAL, AND PER-READ AUDIT LOGGING
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("3. Active break-glass session allows read-only endpoints, audits BREAK_GLASS_READ with memoryIds, and denies writes/MCP")
    void activeBreakGlassAllowsReadsAuditsMemoryIdsAndDeniesWritesAndMcp() throws Exception {
        String bgId = createAndApproveBreakGlassSession(60);

        // 3a. Read endpoints succeed for ADMIN_1 during active session:
        // GET /api/v1/memory/table
        mvc.perform(get("/api/v1/memory/table")
                        .header("X-Spector-Namespace", stdNamespaceId)
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].id").value("mem-bg-101"));

        // POST /api/v1/memory/recall
        mvc.perform(post("/api/v1/memory/recall")
                        .header("X-Spector-Namespace", stdNamespaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"incident trace\",\"topK\":5}")
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("mem-bg-101"));

        // GET /api/v1/memory/graph/overview
        mvc.perform(get("/api/v1/memory/graph/overview")
                        .header("X-Spector-Namespace", stdNamespaceId)
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[0].id").value("mem-bg-101"));

        // 3b. Write / delete / remember / forget / edit endpoints fail with 403 Forbidden (SPE-820-005):
        mvc.perform(post("/api/v1/memory/remember")
                        .header("X-Spector-Namespace", stdNamespaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Unauthorized write attempt\"}")
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SPE-820-005"));

        mvc.perform(post("/api/v1/memory")
                        .header("X-Spector-Namespace", stdNamespaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Unauthorized store attempt\"}")
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SPE-820-005"));

        mvc.perform(delete("/api/v1/memory/mem-bg-101")
                        .header("X-Spector-Namespace", stdNamespaceId)
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SPE-820-005"));

        mvc.perform(put("/api/v1/memory/mem-bg-101")
                        .header("X-Spector-Namespace", stdNamespaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Edited text\",\"tags\":[\"edited\"]}")
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SPE-820-005"));

        mvc.perform(post("/api/v1/memory/mem-bg-101/reinforce")
                        .header("X-Spector-Namespace", stdNamespaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valence\":1}")
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SPE-820-005"));

        // 3c. MCP invocation is denied during break-glass access
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        ADMIN_1, "N/A", List.of(new SimpleGrantedAuthority("ROLE_admin"))
                )
        );
        Optional<McpRequestMemory.DenyReason> mcpDeny =
                McpRequestMemory.bindForCurrentRequest(binder, true, stdNamespaceId, "mcp-conn-1");
        assertThat(mcpDeny).contains(McpRequestMemory.DenyReason.ACCESS_DENIED);
        SecurityContextHolder.clearContext();

        // 3d. Verify per-read BREAK_GLASS_READ events with memoryIds in both /api/v1/admin/audit
        // and the namespace owner's /api/v1/namespaces/{slugOrId}/audit
        MvcResult adminAuditRes = mvc.perform(get("/api/v1/admin/audit")
                        .param("action", "BREAK_GLASS_READ")
                        .param("namespaceId", stdNamespaceId)
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode readEvents = mapper.readTree(adminAuditRes.getResponse().getContentAsString());
        assertThat(readEvents.size()).isEqualTo(3);

        // First read (graph_overview, recall, table ordered DESC by occurred_at)
        boolean foundTableReadWithBothIds = false;
        for (JsonNode ev : readEvents) {
            assertThat(ev.get("action").asText()).isEqualTo("BREAK_GLASS_READ");
            assertThat(ev.get("targetId").asText()).isEqualTo(bgId);
            JsonNode details = ev.get("details");
            assertThat(details.get("breakGlassId").asText()).isEqualTo(bgId);
            JsonNode memoryIds = details.get("memoryIds");
            assertThat(memoryIds).isNotNull();
            if ("table".equals(details.get("operation").asText())) {
                assertThat(memoryIds.toString()).contains("mem-bg-101", "mem-bg-102");
                foundTableReadWithBothIds = true;
            }
        }
        assertThat(foundTableReadWithBothIds).isTrue();

        // Namespace owner sees BREAK_GLASS_REQUESTED, BREAK_GLASS_APPROVED, and BREAK_GLASS_READ in their audit log
        MvcResult ownerAuditRes = mvc.perform(get("/api/v1/namespaces/{slugOrId}/audit", stdNamespaceId)
                        .with(user(OWNER_STD).roles("editor")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode ownerEvents = mapper.readTree(ownerAuditRes.getResponse().getContentAsString());
        List<String> ownerActions = new java.util.ArrayList<>();
        for (JsonNode ev : ownerEvents) {
            ownerActions.add(ev.get("action").asText());
        }
        assertThat(ownerActions).contains("BREAK_GLASS_REQUESTED", "BREAK_GLASS_APPROVED", "BREAK_GLASS_READ");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. AUTO-EXPIRY ON NEXT READ AFTER EXPIRES_AT
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("4. Next read after expires_at transitions session to EXPIRED, records BREAK_GLASS_EXPIRED, and returns 403")
    void autoExpiryOnNextReadAfterExpiresAtReturns403AndAuditsExpired() throws Exception {
        Instant t0 = Instant.now();
        breakGlassService.setClock(Clock.fixed(t0, ZoneOffset.UTC));

        String bgId = createAndApproveBreakGlassSession(30);

        // Read before expiry succeeds
        mvc.perform(get("/api/v1/memory/table")
                        .header("X-Spector-Namespace", stdNamespaceId)
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isOk());

        // Advance clock 31 minutes past approval (session was 30 minutes)
        Instant afterExpiry = t0.plus(Duration.ofMinutes(31));
        breakGlassService.setClock(Clock.fixed(afterExpiry, ZoneOffset.UTC));
        // Also ensure DB grant expires_at is in the past relative to CURRENT_TIMESTAMP for H2 SQL queries
        jdbc.sql("UPDATE grants SET expires_at = :past WHERE object_id = :nsId AND principal_id = :adminId")
                .param("past", Timestamp.from(Instant.now().minus(Duration.ofMinutes(5))))
                .param("nsId", stdNamespaceId)
                .param("adminId", ADMIN_1)
                .update();

        // Next read by ADMIN_1 -> 403 Forbidden (SPE-820-005)
        mvc.perform(get("/api/v1/memory/table")
                        .header("X-Spector-Namespace", stdNamespaceId)
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("SPE-820-005"));

        // Session status is now EXPIRED
        mvc.perform(get("/api/v1/admin/break-glass/{id}", bgId)
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));

        // BREAK_GLASS_EXPIRED is recorded in audit_event
        var expiredEvents = auditLogService.listNamespaceAuditEvents(stdNamespaceId, 20).stream()
                .filter(e -> "BREAK_GLASS_EXPIRED".equals(e.action()) && bgId.equals(e.targetId()))
                .toList();
        assertThat(expiredEvents).hasSize(1);

        // Owner notification break_glass.expired was emitted
        assertThat(breakGlassService.getDeliveredNotifications())
                .extracting(BreakGlassService.OwnerNotification::eventType)
                .contains("break_glass.expired");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. MANUAL REVOCATION BY ADMIN AND BY NAMESPACE OWNER
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("5a. Manual revocation by admin immediately revokes grant, records BREAK_GLASS_REVOKED, and blocks subsequent reads")
    void manualRevocationByAdminImmediatelyBlocksReads() throws Exception {
        String bgId = createAndApproveBreakGlassSession(60);

        // Revoke by ADMIN_2
        mvc.perform(post("/api/v1/admin/break-glass/{id}/revoke", bgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Incident resolved ahead of schedule\"}")
                        .with(user(ADMIN_2).roles("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.revokedBy").value(ADMIN_2))
                .andExpect(jsonPath("$.revokeReason").value("Incident resolved ahead of schedule"));

        // Subsequent read by ADMIN_1 returns 403 Forbidden (SPE-820-005)
        mvc.perform(get("/api/v1/memory/table")
                        .header("X-Spector-Namespace", stdNamespaceId)
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SPE-820-005"));

        // Verify BREAK_GLASS_REVOKED audit event
        var revokedEvents = auditLogService.listNamespaceAuditEvents(stdNamespaceId, 20).stream()
                .filter(e -> "BREAK_GLASS_REVOKED".equals(e.action()) && bgId.equals(e.targetId()))
                .toList();
        assertThat(revokedEvents).hasSize(1);

        assertThat(breakGlassService.getDeliveredNotifications())
                .extracting(BreakGlassService.OwnerNotification::eventType)
                .contains("break_glass.revoked");
    }

    @Test
    @DisplayName("5b. Namespace owner (non-admin) can revoke an active break-glass session on their own namespace; unrelated user cannot")
    void namespaceOwnerCanRevokeBreakGlassSessionOnOwnNamespace() throws Exception {
        String bgId = createAndApproveBreakGlassSession(60);

        // Unrelated non-admin user (OTHER_USER) cannot revoke -> 403 Forbidden
        mvc.perform(post("/api/v1/admin/break-glass/{id}/revoke", bgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Unauthorized revoke attempt\"}")
                        .with(user(OTHER_USER).roles("editor")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SPE-820-005"));

        // Namespace owner (OWNER_STD, role 'editor') CAN revoke -> 200 OK
        mvc.perform(post("/api/v1/admin/break-glass/{id}/revoke", bgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Owner revoking emergency access\"}")
                        .with(user(OWNER_STD).roles("editor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.revokedBy").value(OWNER_STD));

        // Subsequent read by ADMIN_1 fails with 403
        mvc.perform(get("/api/v1/memory/table")
                        .header("X-Spector-Namespace", stdNamespaceId)
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SPE-820-005"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. REJECTION WORKFLOW & LISTING
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("6. Rejecting a pending break-glass request transitions status to REJECTED and records BREAK_GLASS_REJECTED")
    void rejectPendingBreakGlassRequest() throws Exception {
        String reqBody = mapper.writeValueAsString(Map.of(
                "namespaceId", stdNamespaceId,
                "justification", "Routine check without ticket",
                "durationMinutes", 30
        ));

        MvcResult createRes = mvc.perform(post("/api/v1/admin/break-glass")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqBody)
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isCreated())
                .andReturn();
        String bgId = mapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/api/v1/admin/break-glass/{id}/reject", bgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Missing SEV-1 ticket reference\"}")
                        .with(user(ADMIN_2).roles("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.revokedBy").value(ADMIN_2))
                .andExpect(jsonPath("$.revokeReason").value("Missing SEV-1 ticket reference"));

        // List filtered by status=REJECTED
        mvc.perform(get("/api/v1/admin/break-glass")
                        .param("status", "REJECTED")
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(bgId));

        assertThat(breakGlassService.getDeliveredNotifications())
                .extracting(BreakGlassService.OwnerNotification::eventType)
                .contains("break_glass.rejected");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helper
    // ══════════════════════════════════════════════════════════════════════════

    private String createAndApproveBreakGlassSession(int durationMinutes) throws Exception {
        String reqBody = mapper.writeValueAsString(Map.of(
                "namespaceId", stdNamespaceId,
                "justification", "SEV-1 production memory corruption investigation",
                "ticketRef", "INC-1060",
                "durationMinutes", durationMinutes
        ));

        MvcResult createRes = mvc.perform(post("/api/v1/admin/break-glass")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqBody)
                        .with(user(ADMIN_1).roles("admin")))
                .andExpect(status().isCreated())
                .andReturn();

        String bgId = mapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/api/v1/admin/break-glass/{id}/approve", bgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Approved by second admin\"}")
                        .with(user(ADMIN_2).roles("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        return bgId;
    }
}
