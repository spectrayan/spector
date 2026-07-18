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
package com.spectrayan.spector.synapse.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.engine.EngineDto.*;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.*;

/**
 * Spring MVC slice tests for {@link EngineController}.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser
@DisplayName("EngineController — MVC Slice Tests")
class EngineControllerTest {

    @Autowired WebApplicationContext wac;
    @Autowired ObjectMapper mapper;
    @MockitoBean EngineService engineService;

    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/engine/search — returns 200 with search results")
    void search_returns200() throws Exception {
        var request = new SearchRequest("test query", null, 5, "HYBRID");
        var result = new SearchResult("doc1", "test content", "test title", 0.95f, "HYBRID");
        var response = new SearchResponse(List.of(result), 1, 12L, "HYBRID");

        when(engineService.search(any(SearchRequest.class))).thenReturn(response);

        mvc.perform(post("/api/v1/engine/search")
                        .contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits", is(1)))
                .andExpect(jsonPath("$.results[0].id", is("doc1")))
                .andExpect(jsonPath("$.results[0].content", is("test content")))
                .andExpect(jsonPath("$.queryTimeMs", is(12)));
    }

    @Test
    @DisplayName("POST /api/v1/engine/ingest — returns 201 Created")
    void ingest_returns201() throws Exception {
        var request = new IngestRequest("doc2", "ingest content", "ingest title", new float[]{0.1f, 0.2f});

        mvc.perform(post("/api/v1/engine/ingest")
                        .contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("ingested")))
                .andExpect(jsonPath("$.id", is("doc2")));

        verify(engineService).ingest(any(IngestRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/engine/ingest/auto — returns 201 Created")
    void autoIngest_returns201() throws Exception {
        var request = new IngestRequest("doc3", "auto content", "auto title", null);

        mvc.perform(post("/api/v1/engine/ingest/auto")
                        .contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("ingested")))
                .andExpect(jsonPath("$.id", is("doc3")));

        verify(engineService).autoIngest(any(IngestRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/engine/ingest/bulk — returns 200 OK with summary")
    void bulkIngest_returns200() throws Exception {
        var doc = new IngestRequest("doc4", "bulk content", "bulk title", null);
        var request = new BulkIngestRequest(List.of(doc));
        var response = new BulkIngestResponse(1, 1, 0);

        when(engineService.bulkIngest(any(BulkIngestRequest.class))).thenReturn(response);

        mvc.perform(post("/api/v1/engine/ingest/bulk")
                        .contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(1)))
                .andExpect(jsonPath("$.success", is(1)))
                .andExpect(jsonPath("$.failed", is(0)));
    }

    @Test
    @DisplayName("DELETE /api/v1/engine/documents/{id} — returns 200 OK")
    void delete_returns200() throws Exception {
        when(engineService.delete("doc5")).thenReturn(true);

        mvc.perform(delete("/api/v1/engine/documents/doc5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("doc5")))
                .andExpect(jsonPath("$.deleted", is(true)));

        verify(engineService).delete("doc5");
    }

    @Test
    @DisplayName("POST /api/v1/engine/rag — returns 200 OK with context")
    void rag_returns200() throws Exception {
        var request = new RagRequest("what is spector?", 5, 2048, true);
        var response = new RagResponse("Spector context...", List.of(Map.of("documentId", "doc6", "chunkOffset", 0)), null);

        when(engineService.retrieveContext(any(RagRequest.class))).thenReturn(response);

        mvc.perform(post("/api/v1/engine/rag")
                        .contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context", is("Spector context...")))
                .andExpect(jsonPath("$.attributions[0].documentId", is("doc6")));
    }

    @Test
    @DisplayName("GET /api/v1/engine/status — returns 200 OK")
    void status_returns200() throws Exception {
        var status = new EngineStatus(384, 1000L, 1000L, true, Map.of());

        when(engineService.getStatus()).thenReturn(status);

        mvc.perform(get("/api/v1/engine/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dimensions", is(384)))
                .andExpect(jsonPath("$.documentCount", is(1000)))
                .andExpect(jsonPath("$.embeddingProviderAvailable", is(true)));
    }
}
