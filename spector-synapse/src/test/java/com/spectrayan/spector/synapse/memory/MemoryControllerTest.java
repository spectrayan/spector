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
}
