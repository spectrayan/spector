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
package com.spectrayan.spector.synapse.agent.approval;

import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.runtime.SpectorRuntime;
import com.spectrayan.spector.synapse.agent.approval.ApprovalGate.ApprovalExecutionResult;
import com.spectrayan.spector.synapse.platform.events.EventPublisher;
import com.spectrayan.spector.synapse.platform.events.SseEventConstants;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("ApprovalGate Tests")
class ApprovalGateTest {

    private ApprovalStore store;
    private EventPublisher eventPublisher;
    private ApprovalGate gate;

    @BeforeEach
    void setUp() {
        store = new ApprovalStore();
        eventPublisher = mock(EventPublisher.class);
        gate = new ApprovalGate(store, eventPublisher);
        gate.setTimeoutSeconds(5);
    }

    private McpToolHandler createMockTool(String name, boolean isWrite) {
        return new McpToolHandler() {
            @Override public String name() { return name; }
            @Override public String description() { return "Test tool " + name; }
            @Override public Map<String, Object> inputSchema() { return Map.of(); }
            @Override public McpToolCategory category() { return McpToolCategory.GENERAL; }
            @Override public boolean isWriteTool() { return isWrite; }
            @Override
            public McpSchema.CallToolResult execute(SpectorRuntime runtime, Map<String, Object> arguments) {
                return textResult("executed:" + name);
            }
        };
    }

    @Test
    @DisplayName("Read-only tool should execute immediately without approval")
    void testReadOnlyToolExecutesImmediately() {
        McpToolHandler readTool = createMockTool("calc", false);
        AtomicBoolean executed = new AtomicBoolean(false);

        ApprovalExecutionResult result = gate.evaluateAndExecute(
                readTool,
                Map.of("expr", "2+2"),
                "sess-1",
                "agent-1",
                args -> {
                    executed.set(true);
                    return "4";
                }
        );

        assertThat(executed).isTrue();
        assertThat(result).isInstanceOf(ApprovalExecutionResult.Success.class);
        assertThat(((ApprovalExecutionResult.Success) result).output()).isEqualTo("4");
        assertThat(store.listPending()).isEmpty();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Write tool should emit SSE, wait for approval, and execute when approved")
    void testWriteToolApproved() {
        McpToolHandler writeTool = createMockTool("file_write", true);

        // Run in background thread to simulate asynchronous approval by operator
        var executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            var pending = store.listPending();
            if (!pending.isEmpty()) {
                gate.resolveApproval(pending.getFirst().id(), ApprovalDecision.APPROVE, null, null);
            }
        }, 100, TimeUnit.MILLISECONDS);

        ApprovalExecutionResult result = gate.evaluateAndExecute(
                writeTool,
                Map.of("path", "foo.txt", "content", "hello"),
                "sess-1",
                "agent-1",
                args -> "wrote:" + args.get("content")
        );

        executor.shutdown();

        assertThat(result).isInstanceOf(ApprovalExecutionResult.Success.class);
        assertThat(((ApprovalExecutionResult.Success) result).output()).isEqualTo("wrote:hello");

        // Verify SSE emissions
        verify(eventPublisher).agentEvent(eq(SseEventConstants.EVENT_AGENT_APPROVAL_REQUIRED), any(ApprovalRequest.class));
        verify(eventPublisher).agentEvent(eq(SseEventConstants.EVENT_AGENT_APPROVAL_RESOLVED), any(ApprovalRequest.class));
    }

    @Test
    @DisplayName("Write tool should execute with modified parameters when operator modifies")
    void testWriteToolModified() {
        McpToolHandler writeTool = createMockTool("shell_exec", true);

        var executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            var pending = store.listPending();
            if (!pending.isEmpty()) {
                gate.resolveApproval(
                        pending.getFirst().id(),
                        ApprovalDecision.MODIFY,
                        Map.of("cmd", "echo safe"),
                        "Sanitized command"
                );
            }
        }, 100, TimeUnit.MILLISECONDS);

        ApprovalExecutionResult result = gate.evaluateAndExecute(
                writeTool,
                Map.of("cmd", "rm -rf /"),
                "sess-1",
                "agent-1",
                args -> "ran:" + args.get("cmd")
        );

        executor.shutdown();

        assertThat(result).isInstanceOf(ApprovalExecutionResult.Success.class);
        assertThat(((ApprovalExecutionResult.Success) result).output()).isEqualTo("ran:echo safe");
    }

    @Test
    @DisplayName("Write tool should return Denied result when operator rejects")
    void testWriteToolRejected() {
        McpToolHandler writeTool = createMockTool("file_write", true);

        var executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            var pending = store.listPending();
            if (!pending.isEmpty()) {
                gate.resolveApproval(pending.getFirst().id(), ApprovalDecision.REJECT, null, "Untrusted destination");
            }
        }, 100, TimeUnit.MILLISECONDS);

        ApprovalExecutionResult result = gate.evaluateAndExecute(
                writeTool,
                Map.of("path", "/etc/passwd"),
                "sess-1",
                "agent-1",
                args -> "should-not-run"
        );

        executor.shutdown();

        assertThat(result).isInstanceOf(ApprovalExecutionResult.Denied.class);
        assertThat(((ApprovalExecutionResult.Denied) result).reason()).contains("Untrusted destination");
    }

    @Test
    @DisplayName("Write tool should return Denied when approval times out")
    void testWriteToolTimeout() {
        gate.setTimeoutSeconds(1); // 1 second timeout for test speed
        McpToolHandler writeTool = createMockTool("shell_exec", true);

        ApprovalExecutionResult result = gate.evaluateAndExecute(
                writeTool,
                Map.of("cmd", "ls"),
                "sess-1",
                "agent-1",
                args -> "should-not-run"
        );

        assertThat(result).isInstanceOf(ApprovalExecutionResult.Denied.class);
        assertThat(((ApprovalExecutionResult.Denied) result).reason()).contains("timed out");
        verify(eventPublisher).agentEvent(eq(SseEventConstants.EVENT_AGENT_APPROVAL_TIMEOUT), any(ApprovalRequest.class));
    }
}
