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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.agent.graph.coordinator.CoordinatorState;
import com.spectrayan.spector.synapse.agent.graph.coordinator.model.PlanStep;
import com.spectrayan.spector.synapse.bridge.LlmBridge;

import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PLANNER node — decomposes complex tasks into an ordered sequence of PlanSteps
 * or generates dynamic FlowSpec graphs for multi-agent workflows.
 */
public final class PlannerNode implements NodeAction<CoordinatorState> {

    private static final Logger log = LoggerFactory.getLogger(PlannerNode.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

        log.info("[PlannerNode] Planning decomposition (iteration {}) for task: '{}'", iteration, task);

        String context = state.context().isEmpty()
                ? "(no previous context)"
                : String.join("\n", state.context());

        String executionResult = state.executionResult().orElse("(no previous execution)");

        List<com.spectrayan.spector.memory.model.AgentSoul> agents = soulService.listAllAgents();
        StringBuilder agentsList = new StringBuilder();
        if (agents.isEmpty()) {
            agentsList.append("- No specialized child agents available (use default system assistant)\n");
        } else {
            for (var agent : agents) {
                agentsList.append(String.format("- ID: %s | Name: %s | Purpose: %s | Tools: %s\n",
                        agent.id(), agent.name(), agent.purpose(), String.join(", ", agent.tools())));
            }
        }

        String promptTemplate = loadPromptTemplate("coordinator-planner-decompose");
        if (promptTemplate == null || promptTemplate.isBlank()) {
            promptTemplate = loadPromptTemplate("coordinator-planner-system");
        }

        String prompt = promptTemplate
                .replace("{{task}}", task)
                .replace("{{available_tools}}", String.join(", ", availableTools))
                .replace("{{available_agents}}", agentsList.toString())
                .replace("{{context}}", context)
                .replace("{{previous_result}}", executionResult)
                .replace("{{iteration}}", String.valueOf(iteration));

        String response = llmBridge.generate(prompt);
        log.debug("[PlannerNode] LLM response length: {}", response.length());

        List<Map<String, Object>> planStepMaps = new ArrayList<>();
        String flowJson = null;

        // 1. Try to extract JSON array of PlanSteps
        String jsonArray = extractJsonArray(response);
        if (jsonArray != null) {
            try {
                List<Map<String, Object>> parsed = MAPPER.readValue(jsonArray, new TypeReference<>() {});
                if (parsed != null && !parsed.isEmpty()) {
                    for (int i = 0; i < parsed.size(); i++) {
                        var stepMap = new LinkedHashMap<>(parsed.get(i));
                        if (!stepMap.containsKey("step")) {
                            stepMap.put("step", i + 1);
                        }
                        if (!stepMap.containsKey("status")) {
                            stepMap.put("status", PlanStep.STATUS_PENDING);
                        }
                        planStepMaps.add(stepMap);
                    }
                    log.info("[PlannerNode] Decomposed task into {} plan steps", planStepMaps.size());
                }
            } catch (Exception e) {
                log.warn("[PlannerNode] Failed to parse plan steps array: {}", e.getMessage());
            }
        }

        // 2. Try to extract FlowSpec JSON object (backward compatibility / dynamic workflows)
        if (planStepMaps.isEmpty()) {
            flowJson = extractJsonObject(response);
            if (flowJson != null) {
                PlanStep singleStep = PlanStep.of(1, task).withFlowSpec(flowJson);
                planStepMaps.add(singleStep.toMap());
                log.info("[PlannerNode] Created 1-step plan from FlowSpec JSON");
            }
        }

        // 3. Fallback: single direct execution step
        if (planStepMaps.isEmpty()) {
            PlanStep fallbackStep = PlanStep.of(1, task);
            planStepMaps.add(fallbackStep.toMap());
            log.info("[PlannerNode] Created 1-step fallback plan");
        }

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("plan_steps", planStepMaps);
        updates.put("current_step_index", 0);
        updates.put("iteration", iteration + 1);
        updates.put("status", "EXECUTING_STEP");
        if (flowJson != null) {
            updates.put("flow_spec_json", flowJson);
        }

        return updates;
    }

    /** Extracts JSON array [...] from LLM response. */
    static String extractJsonArray(String response) {
        if (response == null) return null;
        String cleaned = response.replaceAll("(?s)<think>.*?</think>", "").strip();

        // 1. Markdown code block ```json ... ```
        int fenceStart = cleaned.indexOf("```json");
        if (fenceStart < 0) {
            fenceStart = cleaned.indexOf("```");
        }
        if (fenceStart >= 0) {
            int jsonStart = cleaned.indexOf('\n', fenceStart) + 1;
            int fenceEnd = cleaned.indexOf("```", jsonStart);
            if (fenceEnd > jsonStart) {
                String candidate = cleaned.substring(jsonStart, fenceEnd).strip();
                if (candidate.startsWith("[") && candidate.endsWith("]")) {
                    return candidate;
                }
            }
        }

        // 2. Direct top-level array
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start >= 0 && end > start) {
            // Ensure no leading '{' before '[' (which would mean '[' is inside an object)
            int braceStart = cleaned.indexOf('{');
            if (braceStart < 0 || braceStart > start) {
                return cleaned.substring(start, end + 1).strip();
            }
        }
        return null;
    }

    /** Extracts JSON object {...} from LLM response. */
    static String extractJsonObject(String response) {
        if (response == null) return null;
        String cleaned = response.replaceAll("(?s)<think>.*?</think>", "").strip();

        // 1. Markdown code block ```json ... ```
        int fenceStart = cleaned.indexOf("```json");
        if (fenceStart < 0) {
            fenceStart = cleaned.indexOf("```");
        }
        if (fenceStart >= 0) {
            int jsonStart = cleaned.indexOf('\n', fenceStart) + 1;
            int fenceEnd = cleaned.indexOf("```", jsonStart);
            if (fenceEnd > jsonStart) {
                String candidate = cleaned.substring(jsonStart, fenceEnd).strip();
                if (candidate.startsWith("{") && candidate.endsWith("}")) {
                    return candidate;
                }
            }
        }

        // 2. Direct top-level object
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            // Ensure no leading '[' before '{' (which would mean '{' is inside an array)
            int bracketStart = cleaned.indexOf('[');
            if (bracketStart < 0 || bracketStart > start) {
                return cleaned.substring(start, end + 1).strip();
            }
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
        return "";
    }
}
