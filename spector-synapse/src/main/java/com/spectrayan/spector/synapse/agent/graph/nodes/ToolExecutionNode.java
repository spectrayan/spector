/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.synapse.agent.graph.nodes;

import com.spectrayan.spector.synapse.agent.AgentTool;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.agent.graph.CognitiveState;

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
 * executes each via the {@link ToolRegistry}, and writes results to
 * {@code tool_results} and {@code context} (appender channels).</p>
 *
 * <p>Uses the OSS {@link ToolRegistry} (Spring component with auto-discovery)
 * instead of the enterprise's raw Map-based registry.</p>
 */
public final class ToolExecutionNode implements NodeAction<CognitiveState> {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionNode.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final ToolRegistry toolRegistry;

    public ToolExecutionNode(ToolRegistry toolRegistry) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
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

                AgentTool tool = toolRegistry.get(toolName.trim()).orElse(null);
                if (tool == null) {
                    String error = String.format("Tool '%s' not found in registry", toolName);
                    log.warn("[ToolExecutionNode] {}", error);
                    results.add(error);
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

                String result = tool.execute(args);
                log.debug("[ToolExecutionNode] {}({}) → {}", toolName, args,
                        result.length() > 100 ? result.substring(0, 100) + "..." : result);

                results.add(String.format("[Tool: %s] %s", toolName, result));
                contextEntries.add(String.format("[tool_result | %s] %s", toolName, result));

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
}
