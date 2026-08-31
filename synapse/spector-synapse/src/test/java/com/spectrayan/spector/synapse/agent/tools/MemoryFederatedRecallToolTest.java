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
package com.spectrayan.spector.synapse.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.memory.FederatedRecallHit;
import com.spectrayan.spector.synapse.memory.FederatedRecallRequest;
import com.spectrayan.spector.synapse.memory.FederatedRecallResponse;
import com.spectrayan.spector.synapse.memory.FederatedRecallService;
import com.spectrayan.spector.synapse.memory.FederatedRecallSummary;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryFederatedRecallTool — Unit Tests")
class MemoryFederatedRecallToolTest {

    @Mock
    private FederatedRecallService federatedRecallService;

    private ObjectMapper objectMapper;
    private MemoryFederatedRecallTool tool;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        tool = new MemoryFederatedRecallTool(federatedRecallService, objectMapper);
    }

    @Test
    @DisplayName("Executes federated recall tool with JSON serialization")
    void testExecuteTool() throws Exception {
        CognitiveResult cr = new CognitiveResult(
                "mem-123",
                "Cross-rememberer knowledge",
                0.92f,
                0.85f,
                0.1f,
                (short) 1,
                (byte) 0,
                MemoryType.SEMANTIC,
                com.spectrayan.spector.memory.cortex.MemorySource.OBSERVED,
                new String[]{"federation"},
                1.0f,
                1.0f
        );
        FederatedRecallHit hit = new FederatedRecallHit(
                "01JXYZNS00001",
                "default",
                GrantRole.OWNER,
                1,
                1,
                cr
        );
        FederatedRecallSummary summary = new FederatedRecallSummary(
                1,
                List.of("default"),
                List.of(),
                List.of(),
                List.of(),
                25L
        );
        FederatedRecallResponse response = new FederatedRecallResponse(List.of(hit), summary);

        when(federatedRecallService.federatedRecall(anyString(), any(FederatedRecallRequest.class)))
                .thenReturn(response);

        Map<String, Object> arguments = Map.of(
                "query", "cross-rememberer knowledge",
                "namespaces", List.of("default"),
                "topK", 5
        );

        McpSchema.CallToolResult result = tool.execute(arguments);

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);

        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("mem-123", "01JXYZNS00001", "Cross-rememberer knowledge");
    }
}
