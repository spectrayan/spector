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
package com.spectrayan.spector.synapse.agent.graph.nodes;

import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.runtime.SpectorRuntime;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.agent.approval.ApprovalDecision;
import com.spectrayan.spector.synapse.agent.approval.ApprovalGate;
import com.spectrayan.spector.synapse.agent.approval.ApprovalStore;
import com.spectrayan.spector.synapse.agent.graph.CognitiveState;
import com.spectrayan.spector.synapse.platform.events.EventPublisher;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("ToolExecutionNode Approval Integration Tests")
class ToolExecutionNodeApprovalTest {

    private ToolRegistry toolRegistry;
    private ApprovalStore store;
    private ApprovalGate gate;
    private ToolExecutionNode node;

    @BeforeEach
    void setUp() {
        store = new ApprovalStore();
        EventPublisher eventPublisher = mock(EventPublisher.class);
        gate = new ApprovalGate(store, eventPublisher);
        gate.setTimeoutSeconds(5);

        McpToolHandler readTool = new McpToolHandler() {
            @Override public String name() { return "calc"; }
            @Override public String description() { return "Calculator"; }
            @Override public Map<String, Object> inputSchema() { return Map.of(); }
            @Override public McpToolCategory category() { return McpToolCategory.GENERAL; }
            @Override public boolean isWriteTool() { return false; }
            @Override public McpSchema.CallToolResult execute(SpectorRuntime runtime, Map<String, Object> args) {
                return textResult("Result: 42");
            }
        };

        McpToolHandler writeTool = new McpToolHandler() {
            @Override public String name() { return "delete_file"; }
            @Override public String description() { return "Delete a file"; }
            @Override public Map<String, Object> inputSchema() { return Map.of(); }
            @Override public McpToolCategory category() { return McpToolCategory.FILESYSTEM; }
            @Override public boolean isWriteTool() { return true; }
            @Override public McpSchema.CallToolResult execute(SpectorRuntime runtime, Map<String, Object> args) {
                return textResult("Deleted: " + args.get("path"));
            }
        };

        toolRegistry = new ToolRegistry(List.of(readTool, writeTool));
        node = new ToolExecutionNode(toolRegistry, gate);
    }

    @Test
    @DisplayName("Read tool executes without approval intervention")
    void testReadToolExecution() {
        CognitiveState state = new CognitiveState(Map.of(
                "tool_calls", List.of("calc({\"expr\": \"40+2\"})")
        ));

        Map<String, Object> result = node.apply(state);

        @SuppressWarnings("unchecked")
        List<String> toolResults = (List<String>) result.get("tool_results");
        assertThat(toolResults).hasSize(1);
        assertThat(toolResults.getFirst()).contains("Result: 42");
    }

    @Test
    @DisplayName("Write tool intercepts, awaits approval, and succeeds when approved")
    void testWriteToolApprovedExecution() {
        var executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            var pending = store.listPending();
            if (!pending.isEmpty()) {
                gate.resolveApproval(pending.getFirst().id(), ApprovalDecision.APPROVE, null, null);
            }
        }, 100, TimeUnit.MILLISECONDS);

        CognitiveState state = new CognitiveState(Map.of(
                "tool_calls", List.of("delete_file({\"path\": \"/tmp/test.txt\"})")
        ));

        Map<String, Object> result = node.apply(state);
        executor.shutdown();

        @SuppressWarnings("unchecked")
        List<String> toolResults = (List<String>) result.get("tool_results");
        assertThat(toolResults).hasSize(1);
        assertThat(toolResults.getFirst()).contains("Deleted: /tmp/test.txt");
    }

    @Test
    @DisplayName("Write tool intercepts, returns DENIED in state when rejected by human")
    void testWriteToolRejectedExecution() {
        var executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            var pending = store.listPending();
            if (!pending.isEmpty()) {
                gate.resolveApproval(pending.getFirst().id(), ApprovalDecision.REJECT, null, "File is protected");
            }
        }, 100, TimeUnit.MILLISECONDS);

        CognitiveState state = new CognitiveState(Map.of(
                "tool_calls", List.of("delete_file({\"path\": \"/etc/shadow\"})")
        ));

        Map<String, Object> result = node.apply(state);
        executor.shutdown();

        @SuppressWarnings("unchecked")
        List<String> toolResults = (List<String>) result.get("tool_results");
        assertThat(toolResults).hasSize(1);
        assertThat(toolResults.getFirst()).contains("[DENIED]").contains("File is protected");

        @SuppressWarnings("unchecked")
        List<String> context = (List<String>) result.get("context");
        assertThat(context).hasSize(1);
        assertThat(context.getFirst()).contains("DENIED");
    }
}
