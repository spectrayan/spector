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
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.agent.approval.model.ApprovalExecutionResult;
import com.spectrayan.spector.synapse.agent.approval.service.AgentApprovalService;
import com.spectrayan.spector.synapse.agent.graph.CognitiveState;
import com.spectrayan.spector.synapse.security.injection.InjectionInterceptor;
import com.spectrayan.spector.synapse.security.pii.PiiInterceptor;
import com.spectrayan.spector.synapse.security.toolaccess.ToolAccessPolicy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * TOOLS node — executes registered agent tools and appends results to state.
 *
 * <p>Reads pending tool calls from the {@code tool_calls} channel,
 * enforces write-tool approval through {@link AgentApprovalService}, executes approved tools via the
 * {@link ToolRegistry}, and writes results to {@code tool_results} and {@code context} (appender channels).</p>
 */
public final class ToolExecutionNode implements NodeAction<CognitiveState> {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionNode.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final ToolRegistry toolRegistry;
    private final AgentApprovalService approvalService;
    private final InjectionInterceptor injectionInterceptor;
    private final PiiInterceptor piiInterceptor;
    private final List<String> soulToolsHint;

    public ToolExecutionNode(ToolRegistry toolRegistry) {
        this(toolRegistry, null, null, null, null);
    }

    public ToolExecutionNode(ToolRegistry toolRegistry, AgentApprovalService approvalService) {
        this(toolRegistry, approvalService, null, null, null);
    }

    public ToolExecutionNode(ToolRegistry toolRegistry,
                             AgentApprovalService approvalService,
                             InjectionInterceptor injectionInterceptor) {
        this(toolRegistry, approvalService, injectionInterceptor, null, null);
    }

    public ToolExecutionNode(ToolRegistry toolRegistry,
                             AgentApprovalService approvalService,
                             InjectionInterceptor injectionInterceptor,
                             PiiInterceptor piiInterceptor) {
        this(toolRegistry, approvalService, injectionInterceptor, piiInterceptor, null);
    }

    public ToolExecutionNode(ToolRegistry toolRegistry,
                             AgentApprovalService approvalService,
                             InjectionInterceptor injectionInterceptor,
                             PiiInterceptor piiInterceptor,
                             List<String> soulToolsHint) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.approvalService = approvalService;
        this.injectionInterceptor = injectionInterceptor;
        this.piiInterceptor = piiInterceptor;
        this.soulToolsHint = soulToolsHint != null ? List.copyOf(soulToolsHint) : List.of();
    }

    @Override
    public Map<String, Object> apply(CognitiveState state) {
        List<String> pendingCalls = state.toolCalls();
        log.info("[ToolExecutionNode] Executing {} tool calls", pendingCalls.size());

        List<String> results = new ArrayList<>();
        List<String> contextEntries = new ArrayList<>();

        for (String callSpec : pendingCalls) {
            try {
                // Expected format: "toolName(jsonArgs)" or just "toolName"
                String toolName = callSpec.contains("(")
                        ? callSpec.substring(0, callSpec.indexOf('('))
                        : callSpec;

                String trimmedName = toolName.trim();
                McpToolHandler tool = toolRegistry.get(trimmedName).orElse(null);
                if (tool == null) {
                    String error = String.format("Tool '%s' not found in registry", toolName);
                    log.warn("[ToolExecutionNode] {}", error);
                    results.add(error);
                    continue;
                }

                String agentId = state.actingSoulId();
                if (!toolRegistry.isToolAllowed(agentId, trimmedName, soulToolsHint)) {
                    String error = ToolAccessPolicy.permissionDeniedMessage(trimmedName);
                    log.warn("[ToolExecutionNode] Permission denied for tool '{}' agentId={}",
                            trimmedName, agentId != null ? agentId : "");
                    results.add(error);
                    contextEntries.add(String.format("[tool_result | %s | DENIED] %s",
                            trimmedName, error));
                    continue;
                }

                // Parse JSON args from call spec
                Map<String, Object> args;
                if (callSpec.contains("(") && callSpec.contains(")")) {
                    String jsonArgs = callSpec.substring(callSpec.indexOf('(') + 1, callSpec.lastIndexOf(')'));
                    args = mapper.readValue(jsonArgs, new TypeReference<HashMap<String, Object>>() {});
                } else {
                    args = Map.of();
                }

                String result;
                if (approvalService != null && approvalService.isApprovalRequired(tool)) {
                    ApprovalExecutionResult gateResult = approvalService.evaluateAndExecute(
                            tool,
                            args,
                            null,
                            null,
                            effectiveArgs -> executeToolInternal(tool, effectiveArgs)
                    );

                    if (gateResult instanceof ApprovalExecutionResult.Success success) {
                        result = sanitizeToolResult(success.output());
                        log.debug("[ToolExecutionNode] {}(approved) → {}", toolName,
                                result.length() > 100 ? result.substring(0, 100) + "..." : result);
                        results.add(String.format("[Tool: %s] %s", toolName, result));
                        contextEntries.add(String.format("[tool_result | %s] %s", toolName, result));
                    } else if (gateResult instanceof ApprovalExecutionResult.Denied denied) {
                        result = denied.reason();
                        log.warn("[ToolExecutionNode] {}(denied) → {}", toolName, result);
                        results.add(String.format("[Tool: %s] [DENIED] %s", toolName, result));
                        contextEntries.add(String.format("[tool_result | %s | DENIED] %s", toolName, result));
                    }
                } else {
                    result = sanitizeToolResult(executeToolInternal(tool, args));
                    log.debug("[ToolExecutionNode] {} → {}", toolName,
                            result.length() > 100 ? result.substring(0, 100) + "..." : result);
                    results.add(String.format("[Tool: %s] %s", toolName, result));
                    contextEntries.add(String.format("[tool_result | %s] %s", toolName, result));
                }

            } catch (Exception e) {
                String error = String.format("Tool execution failed: %s — %s", callSpec, e.getMessage());
                log.error("[ToolExecutionNode] {}", error, e);
                results.add(error);
            }
        }

        return Map.of(
                "tool_results", results,
                "context", contextEntries
        );
    }

    private String sanitizeToolResult(String result) {
        if (result == null) {
            return null;
        }
        if (injectionInterceptor != null) {
            result = injectionInterceptor.interceptToolOutput(result);
        }
        if (piiInterceptor != null) {
            result = piiInterceptor.redactUsingActiveSession(result);
        }
        return result;
    }

    private static String executeToolInternal(McpToolHandler tool, Map<String, Object> args) throws Exception {
        io.modelcontextprotocol.spec.McpSchema.CallToolResult toolResult = tool.execute(args);
        StringBuilder sb = new StringBuilder();
        if (toolResult != null && toolResult.content() != null) {
            for (var content : toolResult.content()) {
                if (content instanceof io.modelcontextprotocol.spec.McpSchema.TextContent textContent) {
                    sb.append(textContent.text());
                }
            }
        }
        return sb.toString();
    }
}
