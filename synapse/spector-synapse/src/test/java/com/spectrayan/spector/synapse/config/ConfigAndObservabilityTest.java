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
package com.spectrayan.spector.synapse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.index.MemoryIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the dynamic settings configuration and memory observability REST controllers.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser
@DisplayName("ConfigAndObservabilityTest — Integration Tests")
class ConfigAndObservabilityTest {

    @Autowired WebApplicationContext wac;
    @Autowired ObjectMapper mapper;

    @MockitoBean SpectorMemory memory;
    @MockitoBean SpectorMemoryAdmin memoryAdmin;
    @MockitoBean MemoryIndex memoryIndex;

    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        // Configure mock SpectorMemory behavior
        when(memory.totalMemories()).thenReturn(10);
        when(memory.memoryCount(any())).thenReturn(2);
        when(memory.admin()).thenReturn(memoryAdmin);
        when(memoryAdmin.index()).thenReturn(memoryIndex);
        when(memoryIndex.size()).thenReturn(10);
        when(memoryIndex.locationMap()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());

        var rec = new com.spectrayan.spector.memory.model.CognitiveRecord(
                "mem-1", "Test memory content", com.spectrayan.spector.memory.model.MemoryType.SEMANTIC,
                com.spectrayan.spector.memory.cortex.MemorySource.USER_STATED, new String[]{"test"},
                System.currentTimeMillis(), 0L, 1.0f, 5.0f, 1, 1, (short)0,
                (byte)0, (byte)0, 1.0f, (byte)0, (byte)0, null, -1, 0L, Map.of(), false
        );
        when(memoryAdmin.listAll()).thenReturn(new java.util.ArrayList<>(List.of(rec)));
    }

    @Test
    @DisplayName("GET /api/v1/config/categories — returns 200 with categories list")
    void getCategories_returns200() throws Exception {
        mvc.perform(get("/api/v1/config/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", hasSize(5)))
                .andExpect(jsonPath("$.categories[0].key", is("llm_provider")))
                .andExpect(jsonPath("$.categories[1].key", is("ingestion")))
                .andExpect(jsonPath("$.categories[2].key", is("rag")))
                .andExpect(jsonPath("$.categories[3].key", is("salience")))
                .andExpect(jsonPath("$.categories[4].key", is("soul")));
    }

    @Test
    @DisplayName("GET /api/v1/config/llm_provider — returns defaults")
    void getLlmProvider_returnsDefaults() throws Exception {
        mvc.perform(get("/api/v1/config/llm_provider"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider", is("ollama")))
                .andExpect(jsonPath("$.model", is("llama3.2")));
    }

    @Test
    @DisplayName("PUT /api/v1/config/llm_provider — saves override and applies it")
    void saveLlmProvider_savesAndApplies() throws Exception {
        Map<String, Object> override = Map.of(
                "scope", "tenant",
                "values", Map.of(
                        "provider", "ollama",
                        "model", "mistral",
                        "temperature", 0.5
                )
        );

        mvc.perform(put("/api/v1/config/llm_provider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(override)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("saved")))
                .andExpect(jsonPath("$.category", is("llm_provider")));

        // Verify save changed the effective values
        mvc.perform(get("/api/v1/config/llm_provider"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model", is("mistral")))
                .andExpect(jsonPath("$.temperature", is(0.5)));

        // Delete override to clean up
        mvc.perform(delete("/api/v1/config/llm_provider").param("scope", "tenant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("deleted")));
    }

    @Test
    @DisplayName("GET /api/v1/observability/stats — returns 200 with memory counts")
    void getObservabilityStats_returns200() throws Exception {
        mvc.perform(get("/api/v1/observability/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMemories", notNullValue()))
                .andExpect(jsonPath("$.indexedMemories", notNullValue()))
                .andExpect(jsonPath("$.tierDistribution.WORKING", notNullValue()))
                .andExpect(jsonPath("$.tierDistribution.EPISODIC", notNullValue()))
                .andExpect(jsonPath("$.tierDistribution.SEMANTIC", notNullValue()))
                .andExpect(jsonPath("$.tierDistribution.PROCEDURAL", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/observability/timeline — returns chronological list of events")
    void getObservabilityTimeline_returns200() throws Exception {
        mvc.perform(get("/api/v1/observability/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events", notNullValue()))
                .andExpect(jsonPath("$.totalEvents", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/observability/age-distribution — returns histogram bins")
    void getObservabilityAgeDistribution_returns200() throws Exception {
        mvc.perform(get("/api/v1/observability/age-distribution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets", hasSize(6)))
                .andExpect(jsonPath("$.buckets[0].label", is("< 1 hour")))
                .andExpect(jsonPath("$.totalMemories", notNullValue()));
    }

    @Test
    @DisplayName("GET and PUT /api/v1/salience/user/default — handles unified profile endpoints")
    void getAndPutUserSalienceProfile_success() throws Exception {
        // Reset state first to ensure clean test context
        mvc.perform(delete("/api/v1/salience/user/default"))
                .andExpect(status().isOk());

        // GET profile
        mvc.perform(get("/api/v1/salience/user/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interestsList", hasSize(0)))
                .andExpect(jsonPath("$.disinterestsList", hasSize(0)))
                .andExpect(jsonPath("$.icnuWeights.interest", is(0.25)))
                .andExpect(jsonPath("$.alpha", is(0.6)))
                .andExpect(jsonPath("$.beta", is(0.4)));

        // PUT profile
        Map<String, Object> request = Map.of(
                "interests", Map.of("java", "HIGH"),
                "disinterests", Map.of("bugs", "IGNORE"),
                "icnuWeights", Map.of(
                        "interest", 0.3,
                        "challenge", 0.1,
                        "novelty", 0.4,
                        "urgency", 0.2
                ),
                "alpha", 0.5,
                "beta", 0.5
        );

        mvc.perform(put("/api/v1/salience/user/default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")));

        // Verify changes applied to GET
        mvc.perform(get("/api/v1/salience/user/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interestsList", hasSize(1)))
                .andExpect(jsonPath("$.interestsList[0].topic", is("java")))
                .andExpect(jsonPath("$.interestsList[0].level", is("HIGH")))
                .andExpect(jsonPath("$.disinterestsList", hasSize(1)))
                .andExpect(jsonPath("$.disinterestsList[0].topic", is("bugs")))
                .andExpect(jsonPath("$.disinterestsList[0].level", is("IGNORE")))
                .andExpect(jsonPath("$.icnuWeights.interest", is(0.3)))
                .andExpect(jsonPath("$.alpha", is(0.5)))
                .andExpect(jsonPath("$.beta", is(0.5)));
    }

    @Test
    @DisplayName("POST and GET /api/v1/salience/rescore — runs memory rescore status")
    void rescoreMemories_success() throws Exception {
        mvc.perform(post("/api/v1/salience/rescore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("completed")));

        mvc.perform(get("/api/v1/salience/rescore/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("completed")));
    }
}
