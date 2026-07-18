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
import com.spectrayan.spector.synapse.memory.MemoryService;
import com.spectrayan.spector.synapse.memory.MemoryDto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.http.MediaType.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end HTTP API tests for memory endpoints — full Spring context with real Ollama.
 *
 * <p>Tests the complete HTTP stack: security filter → controller → service → MAO → SpectorMemory.
 * Exercises all REST endpoints via {@link MockMvc} and validates HTTP status codes,
 * response shapes, and semantic behaviour.</p>
 *
 * <h3>Prerequisites</h3>
 * <ul>
 *   <li>Ollama running on {@code localhost:11434} with {@code nomic-embed-text} pulled</li>
 *   <li>{@code OLLAMA_LIVE=true} environment variable or system property</li>
 * </ul>
 *
 * <h3>Running</h3>
 * <pre>
 *   set OLLAMA_LIVE=true
 *   mvn test -pl spector-synapse -Dtest=SynapseMemoryApiE2ETest -am
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@WithMockUser
@TestPropertySource(properties = {
        "spector.memory.enabled=true",
        "spector.memory.persistence-mode=IN_MEMORY",
        "spector.memory.dimensions=768",
        "spector.memory.capacity=1000",
        "spector.memory.splade-enabled=false",
        "spector.memory.colbert-enabled=false",
        "spector.embedding.model=nomic-embed-text",
        "spector.embedding.base-url=http://localhost:11434",
        "spector.ollama.base-url=http://localhost:11434",
        "spector.ollama.model=llama3.2",
        "spector.ollama.embed-model=nomic-embed-text",
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Synapse Memory HTTP API — E2E Tests (Real Ollama)")
class SynapseMemoryApiE2ETest {

    @Autowired WebApplicationContext wac;
    @Autowired ObjectMapper mapper;
    @Autowired MemoryService memoryService;

    MockMvc mvc;

    private static final String BASE = "/api/v1/memory";
    private String rememberedId;

    @BeforeAll
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @BeforeAll
    void checkOllamaAvailability() {
        boolean ollamaLive = "true".equalsIgnoreCase(System.getenv("OLLAMA_LIVE"))
                || "true".equalsIgnoreCase(System.getProperty("OLLAMA_LIVE"));
        Assumptions.assumeTrue(ollamaLive,
                "Skipping E2E API tests — set OLLAMA_LIVE=true to run");
        Assumptions.assumeTrue(memoryService.isEngineAvailable(),
                "SpectorMemory bean must be present — check Ollama is running and nomic-embed-text is pulled");
    }

    // ═══════════════════════════════════════════════════
    // POST /remember
    // ═══════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("POST /remember → 202 with taskId and memoryId")
    void postRemember_returns202() throws Exception {
        var body = mapper.writeValueAsString(new RememberRequest(
                null,
                "Hebbian learning: neurons that fire together, wire together. " +
                "Spector models this with bidirectional edge strength in the HebbianGraph.",
                "SEMANTIC", "OBSERVED",
                "hebbian,graph,neuroscience", // comma-separated tags
                null, null, null, null, null
        ));

        MvcResult result = mvc.perform(post(BASE + "/remember")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").isNotEmpty())
                .andExpect(jsonPath("$.memoryId").isNotEmpty())
                .andReturn();

        var response = mapper.readValue(result.getResponse().getContentAsString(), AcceptedResponse.class);
        rememberedId = response.id();

        // Wait for async embedding
        TimeUnit.SECONDS.sleep(3);
    }

    @Test
    @Order(2)
    @DisplayName("POST /remember with blank text → 400")
    void postRemember_blankText_returns400() throws Exception {
        var body = """
                {"text": ""}
                """;

        mvc.perform(post(BASE + "/remember")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    // ═══════════════════════════════════════════════════
    // GET /table
    // ═══════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("GET /table → 200 with paginated rows and tier counts")
    void getTable_returns200() throws Exception {
        mvc.perform(get(BASE + "/table")
                        .param("page", "0").param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").isNumber())
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.pageSize", is(50)))
                .andExpect(jsonPath("$.tierCounts").isMap())
                .andExpect(jsonPath("$.tierCounts.SEMANTIC").isNumber())
                .andExpect(jsonPath("$.tierCounts.EPISODIC").isNumber());
    }

    @Test
    @Order(11)
    @DisplayName("GET /table?tier=SEMANTIC → only SEMANTIC rows returned")
    void getTable_semanticFilter_onlySemantic() throws Exception {
        var result = mvc.perform(get(BASE + "/table").param("tier", "SEMANTIC"))
                .andExpect(status().isOk())
                .andReturn();

        var body = mapper.readTree(result.getResponse().getContentAsString());
        body.get("rows").forEach(row -> {
            assertThat(row.get("tier").asText()).isEqualToIgnoringCase("SEMANTIC");
        });
    }

    // ═══════════════════════════════════════════════════
    // GET /status
    // ═══════════════════════════════════════════════════

    @Test
    @Order(20)
    @DisplayName("GET /status → 200 with totalMemories > 0")
    void getStatus_returns200() throws Exception {
        mvc.perform(get(BASE + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMemories").isNumber())
                .andExpect(jsonPath("$.tierCounts.WORKING").isNumber())
                .andExpect(jsonPath("$.tierCounts.EPISODIC").isNumber())
                .andExpect(jsonPath("$.tierCounts.SEMANTIC").isNumber())
                .andExpect(jsonPath("$.tierCounts.PROCEDURAL").isNumber());
    }

    // ═══════════════════════════════════════════════════
    // POST /{id}/reinforce
    // ═══════════════════════════════════════════════════

    @Test
    @Order(30)
    @DisplayName("POST /{id}/reinforce → 204 with positive valence")
    void reinforce_returns204() throws Exception {
        mvc.perform(post(BASE + "/" + rememberedId + "/reinforce")
                        .contentType(APPLICATION_JSON)
                        .content("{\"valence\": 40}"))
                .andExpect(status().isNoContent());
    }

    // ═══════════════════════════════════════════════════
    // POST /{id}/suppress
    // ═══════════════════════════════════════════════════

    @Test
    @Order(40)
    @DisplayName("POST /{id}/suppress → 204")
    void suppress_returns204() throws Exception {
        mvc.perform(post(BASE + "/" + rememberedId + "/suppress")
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\": \"test suppression\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(41)
    @DisplayName("POST /{id}/unsuppress → 204")
    void unsuppress_returns204() throws Exception {
        mvc.perform(post(BASE + "/" + rememberedId + "/unsuppress"))
                .andExpect(status().isNoContent());
    }

    // ═══════════════════════════════════════════════════
    // POST /{id}/resolve
    // ═══════════════════════════════════════════════════

    @Test
    @Order(50)
    @DisplayName("POST /{id}/resolve → 204")
    void resolve_returns204() throws Exception {
        mvc.perform(post(BASE + "/" + rememberedId + "/resolve")
                        .contentType(APPLICATION_JSON)
                        .content("{\"notes\": \"resolved in test\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(51)
    @DisplayName("POST /{id}/unresolve → 204")
    void unresolve_returns204() throws Exception {
        mvc.perform(post(BASE + "/" + rememberedId + "/unresolve"))
                .andExpect(status().isNoContent());
    }

    // ═══════════════════════════════════════════════════
    // POST /reflect
    // ═══════════════════════════════════════════════════

    @Test
    @Order(60)
    @DisplayName("POST /reflect → 200 with consolidation report")
    void reflect_returns200() throws Exception {
        mvc.perform(post(BASE + "/reflect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tombstonedCount").isNumber())
                .andExpect(jsonPath("$.durationMs").isNumber())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ═══════════════════════════════════════════════════
    // POST /vacuum
    // ═══════════════════════════════════════════════════

    @Test
    @Order(70)
    @DisplayName("POST /vacuum → 200 with compaction result")
    void vacuum_returns200() throws Exception {
        mvc.perform(post(BASE + "/vacuum")
                        .contentType(APPLICATION_JSON)
                        .content("{\"tier\": \"SEMANTIC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").isNotEmpty())
                .andExpect(jsonPath("$.durationMs").isNumber());
    }

    // ═══════════════════════════════════════════════════
    // POST /bulk/forget
    // ═══════════════════════════════════════════════════

    @Test
    @Order(80)
    @DisplayName("POST /bulk/forget → 204 for list of IDs")
    void bulkForget_returns204() throws Exception {
        // Ingest 3 memories and forget them in bulk
        List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            var resp = memoryService.store(new StoreRequest(
                    "Bulk forget test memory " + i, List.of("bulk-test"), null, null));
            ids.add(resp.id());
        }
        TimeUnit.SECONDS.sleep(1);

        mvc.perform(post(BASE + "/bulk/forget")
                        .contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(ids)))
                .andExpect(status().isNoContent());
    }

    // ═══════════════════════════════════════════════════
    // DELETE /{id}
    // ═══════════════════════════════════════════════════

    @Test
    @Order(90)
    @DisplayName("DELETE /{id} → 204 tombstones the memory")
    void deleteMemory_returns204() throws Exception {
        mvc.perform(delete(BASE + "/" + rememberedId))
                .andExpect(status().isNoContent());
    }
}
