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
package com.spectrayan.spector.synapse.agent.graph;

import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.agent.graph.coordinator.AgentSelector;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
import com.spectrayan.spector.synapse.bridge.LlmBridge;

import org.bsc.langgraph4j.CompiledGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DynamicGraphBuilder Reflection Flow Tests")
class DynamicGraphBuilderReflectionTest {

    @Mock LlmBridge llmBridge;
    @Mock ToolRegistry toolRegistry;
    @Mock CognitiveSoulService soulService;
    @Mock AgenticChatGraph agenticChatGraph;

    private AgentSelector agentSelector;
    private DynamicGraphBuilder dynamicGraphBuilder;

    @BeforeEach
    void setUp() {
        agentSelector = new AgentSelector(soulService);
        dynamicGraphBuilder = new DynamicGraphBuilder(llmBridge, toolRegistry, agentSelector, agenticChatGraph, soulService);
    }

    @Test
    @DisplayName("Should compile and execute flow with REFLECTION node self-correction loop")
    void testReflectionFlowExecution() throws Exception {
        AgentSoul defaultSoul = AgentSoul.builder()
                .id("generator")
                .name("Answer Generator")
                .build();
        when(soulService.getActiveSoul()).thenReturn(defaultSoul);

        String flowJson = """
                {
                  "version": "1.0",
                  "id": "test-reflection-flow",
                  "name": "Test Reflection Flow",
                  "entry_point": "gen_node",
                  "nodes": {
                    "gen_node": {
                      "type": "AGENT",
                      "description": "Generate answer"
                    },
                    "reflect_node": {
                      "type": "REFLECTION",
                      "description": "Self-reflection quality gate",
                      "retry_policy": {
                        "max_retries": 3
                      }
                    }
                  },
                  "edges": [
                    { "from": "gen_node", "to": "reflect_node", "condition": "always" }
                  ],
                  "conditional_edges": [
                    {
                      "from": "reflect_node",
                      "condition_field": "reflection_decision",
                      "condition_mapping": {
                        "ACCEPT": "END",
                        "RETRY_GENERATE": "gen_node"
                      },
                      "default_target": "END"
                    }
                  ]
                }
                """;

        AtomicInteger genCount = new AtomicInteger(0);
        when(agenticChatGraph.chat(any(AgentSoul.class), any(String.class))).thenAnswer(inv -> {
            int count = genCount.incrementAndGet();
            if (count == 1) {
                return "Draft response 1 without examples.";
            }
            return "Final revised response with comprehensive examples.";
        });

        AtomicInteger reflectCount = new AtomicInteger(0);
        when(llmBridge.generate(any(String.class))).thenAnswer(inv -> {
            int count = reflectCount.incrementAndGet();
            if (count == 1) {
                return """
                        DECISION: RETRY_GENERATE
                        CRITIQUE: Missing concrete code examples. Please include complete snippets.
                        REASON: Quality deficit.
                        """;
            }
            return """
                    DECISION: ACCEPT
                    REASON: Revised answer includes comprehensive examples.
                    """;
        });

        CompiledGraph<CognitiveState> compiled = dynamicGraphBuilder.buildFromJson(flowJson);

        Optional<CognitiveState> result = compiled.invoke(Map.of(
                "query", "Provide code examples for LangGraph4j",
                "original_query", "Provide code examples for LangGraph4j"
        ));

        assertThat(result).isPresent();
        CognitiveState finalState = result.get();

        assertThat(genCount.get()).isEqualTo(2);
        assertThat(reflectCount.get()).isEqualTo(2);
        assertThat(finalState.reflectionDecision()).isEqualTo("ACCEPT");
        assertThat(finalState.retryCount()).isEqualTo(1);
        assertThat(finalState.critiques()).hasSize(1);
        assertThat(finalState.critiques().getFirst()).contains("Missing concrete code examples");
    }
}
