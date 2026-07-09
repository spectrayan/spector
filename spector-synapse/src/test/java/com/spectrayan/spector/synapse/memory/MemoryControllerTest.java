/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.memory.MemoryDto.*;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.*;

/**
 * Spring MVC slice tests for {@link MemoryController}.
 *
 * <p>Uses {@code @WebMvcTest} — loads only the web layer (controller +
 * security). {@link MemoryService} is mocked via {@code @MockitoBean}.</p>
 *
 * <p>No Ollama, no real SpectorMemory, no DB. Runs on every CI build.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser    // satisfies Spring Security filter without a real auth setup
@DisplayName("MemoryController — MVC Slice Tests")
class MemoryControllerTest {

    @Autowired WebApplicationContext wac;
    @Autowired ObjectMapper mapper;
    @MockitoBean MemoryService memoryService;

    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    // ═══════════════════════════════════════════════════
    // GET /api/v1/memory/table
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("GET /memory/table — returns 200 with paginated rows")
    void getTable_returns200() throws Exception {
        var row = new MemoryTableRow(
                "id1", "Java concurrency fundamentals", "Java concurrency…",
                "SEMANTIC", "OBSERVED", 0.8f, 5, 128,
                System.currentTimeMillis(), 3, 3,
                false, false, false, false, false,
                List.of("java", "concurrency"), 0L,
                "2026-07-01T00:00:00Z", null
        );
        var response = new MemoryTableResponse(List.of(row), 1, 0, 50,
                Map.of("SEMANTIC", 1), Map.of("SEMANTIC", 0.0f));
        when(memoryService.getMemoryTable(0, 50, null, false)).thenReturn(response);

        mvc.perform(get("/api/v1/memory/table")
                        .param("page", "0").param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(1)))
                .andExpect(jsonPath("$.rows[0].id", is("id1")))
                .andExpect(jsonPath("$.rows[0].tier", is("SEMANTIC")));
    }

    @Test
    @DisplayName("GET /memory/table — tier filter is forwarded")
    void getTable_tierFilter_forwarded() throws Exception {
        var empty = new MemoryTableResponse(List.of(), 0, 0, 50, Map.of(), Map.of());
        when(memoryService.getMemoryTable(0, 50, "EPISODIC", false)).thenReturn(empty);

        mvc.perform(get("/api/v1/memory/table").param("tier", "EPISODIC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(0)));

        verify(memoryService).getMemoryTable(0, 50, "EPISODIC", false);
    }

    // ═══════════════════════════════════════════════════
    // POST /api/v1/memory/remember
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("POST /memory/remember — returns 202 Accepted")
    void remember_returns202() throws Exception {
        var request = new RememberRequest(null, "Spector Cortex uses Panama FFM for off-heap memory",
                null, null, null, null, null, null, null, null);
        var accepted = AcceptedResponse.forRemember("task-abc", "auto-id-1");
        when(memoryService.remember(any())).thenReturn(accepted);

        mvc.perform(post("/api/v1/memory/remember")
                        .contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId", is("task-abc")))
                .andExpect(jsonPath("$.id", is("auto-id-1")));
    }

    @Test
    @DisplayName("POST /memory/remember — missing text returns 400")
    void remember_missingText_returns400() throws Exception {
        when(memoryService.remember(any()))
                .thenThrow(new IllegalArgumentException("text must not be blank"));

        mvc.perform(post("/api/v1/memory/remember")
                        .contentType(APPLICATION_JSON)
                        .content("{\"text\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ═══════════════════════════════════════════════════
    // POST /api/v1/memory/{id}/reinforce
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("POST /memory/{id}/reinforce — returns 204")
    void reinforce_returns204() throws Exception {
        mvc.perform(post("/api/v1/memory/mem-1/reinforce")
                        .contentType(APPLICATION_JSON)
                        .content("{\"valence\": 20}"))
                .andExpect(status().isOk());

        verify(memoryService).reinforce(eq("mem-1"), Mockito.anyInt());
    }

    // ═══════════════════════════════════════════════════
    // POST /api/v1/memory/{id}/suppress
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("POST /memory/{id}/suppress — returns 204")
    void suppress_returns204() throws Exception {
        mvc.perform(post("/api/v1/memory/mem-2/suppress")
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\": \"outdated info\"}"))
                .andExpect(status().isOk());

        verify(memoryService).suppress(eq("mem-2"), Mockito.any(SuppressRequest.class));
    }

    // ═══════════════════════════════════════════════════
    // DELETE /api/v1/memory/{id}
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("DELETE /memory/{id} — returns 204")
    void forget_returns204() throws Exception {
        mvc.perform(delete("/api/v1/memory/mem-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("forgotten")));

        verify(memoryService).forget("mem-3");
    }

    // ═══════════════════════════════════════════════════
    // GET /api/v1/memory/status
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("GET /memory/status — returns 200 with tier counts")
    void getStatus_returns200() throws Exception {
        var status = new MemoryStatusResponse(100,
                Map.of("SEMANTIC", 40, "EPISODIC", 35, "WORKING", 15, "PROCEDURAL", 10),
                500, 200, 30, 60);
        when(memoryService.getStatus()).thenReturn(status);

        mvc.perform(get("/api/v1/memory/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMemories", is(100)))
                .andExpect(jsonPath("$.tierCounts.SEMANTIC", is(40)));
    }

    // ═══════════════════════════════════════════════════
    // POST /api/v1/memory/reflect
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("POST /memory/reflect — returns 200 with report")
    void reflect_returns200() throws Exception {
        when(memoryService.reflect())
                .thenReturn(new ReflectResponse(7, 250L, "Consolidation complete"));

        mvc.perform(post("/api/v1/memory/reflect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tombstonedCount", is(7)))
                .andExpect(jsonPath("$.durationMs", is(250)));
    }

    // ═══════════════════════════════════════════════════
    // POST /api/v1/memory/bulk/forget
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("POST /memory/bulk/forget — returns 204 for all IDs")
    void bulkForget_returns204() throws Exception {
        mvc.perform(post("/api/v1/memory/bulk/forget")
                        .contentType(APPLICATION_JSON)
                        .content("[\"id1\", \"id2\", \"id3\"]"))
                .andExpect(status().isOk());

        verify(memoryService, times(3)).forget(anyString());
    }

    // ═══════════════════════════════════════════════════
    // GRAPH API
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("GET /memory/graph/overview — returns 200 with nodes+edges (default maxNodes)")
    void getGraphOverview_defaultMaxNodes_returns200() throws Exception {
        var node = new MemoryDto.GraphNodeDto("id1", "SEMANTIC", "hello world",
                0.8, 10, 1_700_000_000_000L, List.of("Entity1"));
        var edge = new MemoryDto.GraphEdgeDto("id1", "id2", "HEBBIAN", null, 0.5, null, null);
        var response = new MemoryGraphResponse(null, List.of(node), List.of(edge));

        when(memoryService.getGraphOverview(100)).thenReturn(response);

        mvc.perform(get("/api/v1/memory/graph/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memoryId", nullValue()))
                .andExpect(jsonPath("$.nodes", hasSize(1)))
                .andExpect(jsonPath("$.nodes[0].id", is("id1")))
                .andExpect(jsonPath("$.nodes[0].tier", is("SEMANTIC")))
                .andExpect(jsonPath("$.edges", hasSize(1)))
                .andExpect(jsonPath("$.edges[0].type", is("HEBBIAN")));

        verify(memoryService).getGraphOverview(100);
    }

    @Test
    @DisplayName("GET /memory/graph/overview?maxNodes=25 — passes maxNodes to service")
    void getGraphOverview_customMaxNodes_passesParam() throws Exception {
        when(memoryService.getGraphOverview(25)).thenReturn(MemoryGraphResponse.empty(null));

        mvc.perform(get("/api/v1/memory/graph/overview").param("maxNodes", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes", hasSize(0)));

        verify(memoryService).getGraphOverview(25);
    }

    @Test
    @DisplayName("GET /memory/{id}/graph — returns 200 with neighborhood graph")
    void getMemoryGraph_returnsNeighborhoodGraph() throws Exception {
        var node = new MemoryDto.GraphNodeDto("mem-42", "EPISODIC", "some event",
                0.6, -5, 1_700_000_000_001L, List.of());
        var response = new MemoryGraphResponse("mem-42", List.of(node), List.of());

        when(memoryService.getMemoryGraph("mem-42", 2)).thenReturn(response);

        mvc.perform(get("/api/v1/memory/mem-42/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memoryId", is("mem-42")))
                .andExpect(jsonPath("$.nodes", hasSize(1)))
                .andExpect(jsonPath("$.nodes[0].tier", is("EPISODIC")));

        verify(memoryService).getMemoryGraph("mem-42", 2);
    }

    @Test
    @DisplayName("GET /memory/topology-stats — returns entity + relation type stats")
    void getTopologyStats_returnsStats() throws Exception {
        var entityStat = new MemoryDto.EntityTypeStatsDto("PERSON", 5, 3, 12);
        var relStat = new MemoryDto.RelationTypeStatsDto("WORKS_AT", 3, 6, 8);
        var response = new MemoryDto.TopologyStatsResponse(List.of(entityStat), List.of(relStat));

        when(memoryService.getTopologyStats()).thenReturn(response);

        mvc.perform(get("/api/v1/memory/topology-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityTypes", hasSize(1)))
                .andExpect(jsonPath("$.entityTypes[0].type", is("PERSON")))
                .andExpect(jsonPath("$.entityTypes[0].nodes", is(5)))
                .andExpect(jsonPath("$.relationTypes", hasSize(1)))
                .andExpect(jsonPath("$.relationTypes[0].type", is("WORKS_AT")))
                .andExpect(jsonPath("$.relationTypes[0].edges", is(3)));

        verify(memoryService).getTopologyStats();
    }

    @Test
    @DisplayName("GET /memory/graph/overview — path is NOT captured by /{id}/graph route")
    void graphOverview_notCapturedByIdRoute() throws Exception {
        // If Spring mistakenly routes /graph/overview to /{id}/graph,
        // it would call getMemoryGraph("graph", ...) instead of getGraphOverview().
        when(memoryService.getGraphOverview(anyInt())).thenReturn(MemoryGraphResponse.empty(null));

        mvc.perform(get("/api/v1/memory/graph/overview"))
                .andExpect(status().isOk());

        // Must NOT have called getMemoryGraph with id="graph"
        verify(memoryService, never()).getMemoryGraph(eq("graph"), anyInt());
        verify(memoryService).getGraphOverview(anyInt());
    }
}

