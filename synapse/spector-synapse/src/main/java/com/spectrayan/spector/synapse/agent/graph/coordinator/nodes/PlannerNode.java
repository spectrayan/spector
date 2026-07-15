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
package com.spectrayan.spector.synapse.agent.graph.coordinator.nodes;

import com.spectrayan.spector.synapse.agent.graph.coordinator.CoordinatorState;
import com.spectrayan.spector.synapse.bridge.LlmBridge;

import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PLANNER node — asks the LLM to generate a FlowSpec JSON for the current task.
 *
 * <p>The LLM receives the task description, available tools, and context,
 * then outputs a valid FlowSpec JSON that the SubgraphExecutorNode will compile
 * and execute.</p>
 */
public final class PlannerNode implements NodeAction<CoordinatorState> {

    private static final Logger log = LoggerFactory.getLogger(PlannerNode.class);

    private final LlmBridge llmBridge;
    private final List<String> availableTools;
    private final com.spectrayan.spector.synapse.agent.service.CognitiveSoulService soulService;

    public PlannerNode(LlmBridge llmBridge,
                       List<String> availableTools,
                       com.spectrayan.spector.synapse.agent.service.CognitiveSoulService soulService) {
        this.llmBridge = Objects.requireNonNull(llmBridge, "llmBridge");
        this.availableTools = availableTools != null ? availableTools : List.of();
        this.soulService = Objects.requireNonNull(soulService, "soulService");
    }

    @Override
    public Map<String, Object> apply(CoordinatorState state) {
        String task = state.task().isEmpty() ? state.query() : state.task();
        int iteration = state.iteration();

        log.info("[PlannerNode] Planning iteration {} for task: '{}'", iteration, task);

        String context = state.context().isEmpty()
                ? "(no previous context)"
                : String.join("\n", state.context());

        String executionResult = state.executionResult().orElse("(no previous execution)");

        List<com.spectrayan.spector.synapse.agent.AgentSoul> agents = soulService.listAllAgents();
        StringBuilder agentsList = new StringBuilder();
        if (agents.isEmpty()) {
            agentsList.append("- No specialized child agents available (use default system assistant)\n");
        } else {
            for (var agent : agents) {
                agentsList.append(String.format("- ID: %s | Name: %s | Purpose: %s | Tools: %s\n",
                        agent.id(), agent.name(), agent.purpose(), String.join(", ", agent.tools())));
            }
        }

        String promptTemplate = loadPromptTemplate("coordinator-planner-system");
        String prompt = promptTemplate
                .replace("{{task}}", task)
                .replace("{{available_tools}}", String.join(", ", availableTools))
                .replace("{{available_agents}}", agentsList.toString())
                .replace("{{context}}", context)
                .replace("{{previous_result}}", executionResult)
                .replace("{{iteration}}", String.valueOf(iteration));

        String response = llmBridge.generate(prompt);
        log.debug("[PlannerNode] LLM response length: {}", response.length());

        // Extract JSON from response
        String flowJson = extractJson(response);
        if (flowJson == null) {
            log.warn("[PlannerNode] Failed to extract FlowSpec JSON, using raw response");
            flowJson = response;
        }

        return Map.of(
                "flow_spec_json", flowJson,
                "iteration", iteration + 1,
                "status", "EXECUTING"
        );
    }

    /** Extract JSON object from LLM response (handles ```json blocks, thinking tags). */
    private static String extractJson(String response) {
        if (response == null) return null;

        // Strip thinking tags
        String cleaned = response.replaceAll("(?s)<think>.*?</think>", "").strip();

        // Try to find JSON in markdown code block
        int fenceStart = cleaned.indexOf("```json");
        if (fenceStart >= 0) {
            int jsonStart = cleaned.indexOf('\n', fenceStart) + 1;
            int fenceEnd = cleaned.indexOf("```", jsonStart);
            if (fenceEnd > jsonStart) {
                return cleaned.substring(jsonStart, fenceEnd).strip();
            }
        }

        // Try plain ``` block
        fenceStart = cleaned.indexOf("```");
        if (fenceStart >= 0) {
            int jsonStart = cleaned.indexOf('\n', fenceStart) + 1;
            int fenceEnd = cleaned.indexOf("```", jsonStart);
            if (fenceEnd > jsonStart) {
                String candidate = cleaned.substring(jsonStart, fenceEnd).strip();
                if (candidate.startsWith("{")) return candidate;
            }
        }

        // Try to find raw JSON object
        int braceStart = cleaned.indexOf('{');
        int braceEnd = cleaned.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return cleaned.substring(braceStart, braceEnd + 1);
        }

        return null;
    }

    private String loadPromptTemplate(String name) {
        String path = "/prompts/" + name + ".txt";
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("[PlannerNode] Failed to load prompt: {}", name);
        }
        return """
                You are a task planner. Generate a FlowSpec JSON to accomplish the following task.
                
                TASK: {{task}}
                AVAILABLE TOOLS: {{available_tools}}
                PREVIOUS CONTEXT: {{context}}
                PREVIOUS RESULT: {{previous_result}}
                ITERATION: {{iteration}}
                
                Generate a valid FlowSpec JSON with nodes, edges, and agents.
                """;
    }
}
