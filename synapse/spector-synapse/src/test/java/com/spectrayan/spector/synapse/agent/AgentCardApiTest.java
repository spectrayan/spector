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
package com.spectrayan.spector.synapse.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.agent.graph.AgenticChatGraph;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring MVC slice integration tests for Agent Cards and Invoke endpoints.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser
@DisplayName("AgentCard API — REST integration Tests")
class AgentCardApiTest {

    @Autowired WebApplicationContext wac;
    @Autowired ObjectMapper mapper;

    @MockitoBean CognitiveSoulService soulService;
    @MockitoBean AgenticChatGraph agenticChatGraph;

    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/agents/soul/card — returns active agent card")
    void getSoulCard_returnsActiveAgentCard() throws Exception {
        var soul = AgentSoul.builder()
                .id("default")
                .name("Aria")
                .description("Warm cognitive assistant")
                .tools(List.of("web_search", "memory_recall"))
                .build();

        when(soulService.getActiveSoul()).thenReturn(soul);

        mvc.perform(get("/api/v1/agents/soul/card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Aria")))
                .andExpect(jsonPath("$.description", is("Warm cognitive assistant")))
                .andExpect(jsonPath("$.version", is("1.0")))
                .andExpect(jsonPath("$.capabilities[0]", is("web_search")))
                .andExpect(jsonPath("$.capabilities[1]", is("memory_recall")))
                .andExpect(jsonPath("$.endpoint", is("/api/v1/agents/default/invoke")));
    }

    @Test
    @DisplayName("GET /api/v1/agents/{id}/card — returns agent card by ID")
    void getAgentCard_returnsAgentCardById() throws Exception {
        var soul = AgentSoul.builder()
                .id("coder-1")
                .name("CodeAgent")
                .description("Writes clean Java 25 code")
                .tools(List.of("shell_execute"))
                .build();

        when(soulService.loadAgentSoul("coder-1")).thenReturn(Optional.of(soul));

        mvc.perform(get("/api/v1/agents/coder-1/card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("CodeAgent")))
                .andExpect(jsonPath("$.description", is("Writes clean Java 25 code")))
                .andExpect(jsonPath("$.tools[0]", is("shell_execute")));
    }

    @Test
    @DisplayName("GET /.well-known/agent.json — returns default active agent card (A2A)")
    void getWellKnownAgent_returnsDefaultAgentCard() throws Exception {
        var soul = AgentSoul.builder()
                .id("default")
                .name("Default Assistant")
                .description("System assistant")
                .tools(List.of())
                .build();

        when(soulService.getActiveSoul()).thenReturn(soul);

        mvc.perform(get("/.well-known/agent.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Default Assistant")))
                .andExpect(jsonPath("$.endpoint", is("/api/v1/agents/default/invoke")));
    }

    @Test
    @DisplayName("POST /api/v1/agents/{id}/invoke — invokes agent execution and returns response")
    void invokeAgent_runsExecutionAndReturnsResponse() throws Exception {
        var soul = AgentSoul.builder()
                .id("coder-1")
                .name("CodeAgent")
                .build();

        when(soulService.loadAgentSoul("coder-1")).thenReturn(Optional.of(soul));
        when(agenticChatGraph.chat(eq(soul), eq("Generate binary search"))).thenReturn("Here is the binary search implementation.");

        mvc.perform(post("/api/v1/agents/coder-1/invoke")
                        .contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("prompt", "Generate binary search"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response", is("Here is the binary search implementation.")));
    }
}
