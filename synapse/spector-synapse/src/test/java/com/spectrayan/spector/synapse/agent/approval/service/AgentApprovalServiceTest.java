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
package com.spectrayan.spector.synapse.agent.approval.service;

import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.synapse.agent.approval.model.AgentActionApproval;
import com.spectrayan.spector.synapse.agent.approval.model.ApprovalExecutionResult;
import com.spectrayan.spector.synapse.agent.approval.model.ApprovalStatus;
import com.spectrayan.spector.synapse.agent.approval.repository.AgentApprovalRepository;
import com.spectrayan.spector.synapse.agent.approval.repository.InMemoryAgentApprovalRepository;
import com.spectrayan.spector.synapse.platform.events.EventPublisher;
import com.spectrayan.spector.synapse.platform.events.SseEventConstants;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("AgentApprovalService Tests")
class AgentApprovalServiceTest {

    private AgentApprovalRepository repository;
    private EventPublisher eventPublisher;
    private DefaultAgentApprovalService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAgentApprovalRepository();
        eventPublisher = mock(EventPublisher.class);
        service = new DefaultAgentApprovalService(repository, eventPublisher);
        service.setTimeoutSeconds(5);
    }

    private McpToolHandler createMockTool(String name, boolean isWrite) {
        return new McpToolHandler() {
            @Override public String name() { return name; }
            @Override public String description() { return "Test tool " + name; }
            @Override public Map<String, Object> inputSchema() { return Map.of(); }
            @Override public McpToolCategory category() { return McpToolCategory.GENERAL; }
            @Override public boolean isWriteTool() { return isWrite; }
            @Override
            public McpSchema.CallToolResult execute(Map<String, Object> arguments) {
                return textResult("executed:" + name);
            }
        };
    }

    private void scheduleAction(Consumer<AgentActionApproval> action) {
        var executor = Executors.newSingleThreadScheduledExecutor();
        executor.submit(() -> {
            for (int i = 0; i < 100; i++) {
                var pending = repository.findPending();
                if (!pending.isEmpty()) {
                    action.accept(pending.getFirst());
                    return;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
        executor.shutdown();
    }

    @Test
    @DisplayName("Read-only tool should execute immediately without approval")
    void testReadOnlyToolExecutesImmediately() {
        McpToolHandler readTool = createMockTool("calc", false);
        AtomicBoolean executed = new AtomicBoolean(false);

        ApprovalExecutionResult result = service.evaluateAndExecute(
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
        assertThat(repository.findPending()).isEmpty();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Write tool should emit SSE, wait for approval, and execute when approved")
    void testWriteToolApproved() {
        McpToolHandler writeTool = createMockTool("file_write", true);

        scheduleAction(pending -> service.approve(pending.id()));

        ApprovalExecutionResult result = service.evaluateAndExecute(
                writeTool,
                Map.of("path", "foo.txt", "content", "hello"),
                "sess-1",
                "agent-1",
                args -> "wrote:" + args.get("content")
        );

        assertThat(result).isInstanceOf(ApprovalExecutionResult.Success.class);
        assertThat(((ApprovalExecutionResult.Success) result).output()).isEqualTo("wrote:hello");

        verify(eventPublisher, timeout(2000)).agentEvent(eq(SseEventConstants.EVENT_AGENT_APPROVAL_REQUIRED), any(AgentActionApproval.class));
        verify(eventPublisher, timeout(2000)).agentEvent(eq(SseEventConstants.EVENT_AGENT_APPROVAL_RESOLVED), any(AgentActionApproval.class));
    }

    @Test
    @DisplayName("Write tool should execute with modified parameters when operator modifies")
    void testWriteToolModified() {
        McpToolHandler writeTool = createMockTool("shell_exec", true);

        scheduleAction(pending -> service.modify(
                pending.id(),
                Map.of("cmd", "echo safe"),
                "Sanitized command"
        ));

        ApprovalExecutionResult result = service.evaluateAndExecute(
                writeTool,
                Map.of("cmd", "rm -rf /"),
                "sess-1",
                "agent-1",
                args -> "ran:" + args.get("cmd")
        );

        assertThat(result).isInstanceOf(ApprovalExecutionResult.Success.class);
        assertThat(((ApprovalExecutionResult.Success) result).output()).isEqualTo("ran:echo safe");
    }

    @Test
    @DisplayName("Write tool should return Denied result when operator rejects")
    void testWriteToolRejected() {
        McpToolHandler writeTool = createMockTool("file_write", true);

        scheduleAction(pending -> service.reject(pending.id(), "Untrusted destination"));

        ApprovalExecutionResult result = service.evaluateAndExecute(
                writeTool,
                Map.of("path", "/etc/passwd"),
                "sess-1",
                "agent-1",
                args -> "should-not-run"
        );

        assertThat(result).isInstanceOf(ApprovalExecutionResult.Denied.class);
        assertThat(((ApprovalExecutionResult.Denied) result).reason()).contains("Untrusted destination");
    }

    @Test
    @DisplayName("Write tool should return Denied when cancelled")
    void testWriteToolCancelled() {
        McpToolHandler writeTool = createMockTool("file_write", true);

        scheduleAction(pending -> service.cancel(pending.id(), "Operator cancelled run"));

        ApprovalExecutionResult result = service.evaluateAndExecute(
                writeTool,
                Map.of("path", "/tmp/file"),
                "sess-1",
                "agent-1",
                args -> "should-not-run"
        );

        assertThat(result).isInstanceOf(ApprovalExecutionResult.Denied.class);
        assertThat(((ApprovalExecutionResult.Denied) result).reason()).contains("Operator cancelled run");
    }

    @Test
    @DisplayName("Write tool should return Denied when approval times out")
    void testWriteToolTimeout() {
        service.setTimeoutSeconds(1);
        McpToolHandler writeTool = createMockTool("shell_exec", true);

        ApprovalExecutionResult result = service.evaluateAndExecute(
                writeTool,
                Map.of("cmd", "ls"),
                "sess-1",
                "agent-1",
                args -> "should-not-run"
        );

        assertThat(result).isInstanceOf(ApprovalExecutionResult.Denied.class);
        assertThat(((ApprovalExecutionResult.Denied) result).reason()).contains("timed out");
        verify(eventPublisher).agentEvent(eq(SseEventConstants.EVENT_AGENT_APPROVAL_TIMEOUT), any(AgentActionApproval.class));
    }

    @Test
    @DisplayName("Should throw exception when resolving non-existent or already resolved approval")
    void testInvalidResolutionTransitions() {
        assertThatThrownBy(() -> service.approve("non-existent"))
                .isInstanceOf(IllegalArgumentException.class);

        AgentActionApproval appr = AgentActionApproval.pending("appr-1", "tool", Map.of(), "CAT", null, null);
        repository.save(appr, new java.util.concurrent.CompletableFuture<>());
        service.approve("appr-1");

        assertThatThrownBy(() -> service.approve("appr-1"))
                .isInstanceOf(IllegalStateException.class);
    }
}
