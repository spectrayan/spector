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
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
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
import java.util.Optional;

/**
 * PLAN_ADAPTER node — dynamically replans remaining steps when an intermediate step fails.
 *
 * <p>Preserves completed steps, takes failure critique into account, and asks the LLM
 * to provide a revised set of remaining steps to accomplish the task.</p>
 */
public final class PlanAdapterNode implements NodeAction<CoordinatorState> {

    private static final Logger log = LoggerFactory.getLogger(PlanAdapterNode.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmBridge llmBridge;
    private final List<String> availableTools;
    private final CognitiveSoulService soulService;

    public PlanAdapterNode(LlmBridge llmBridge,
                           List<String> availableTools,
                           CognitiveSoulService soulService) {
        this.llmBridge = Objects.requireNonNull(llmBridge, "llmBridge");
        this.availableTools = availableTools != null ? availableTools : List.of();
        this.soulService = Objects.requireNonNull(soulService, "soulService");
    }

    @Override
    public Map<String, Object> apply(CoordinatorState state) {
        String task = state.task().isEmpty() ? state.query() : state.task();
        int failedIdx = state.currentStepIndex();
        List<PlanStep> currentPlan = state.planSteps();
        int iteration = state.iteration();

        log.info("[PlanAdapterNode] Adapting plan after failure at step {} (iteration {})",
                failedIdx + 1, iteration);

        Optional<PlanStep> failedStepOpt = state.currentStep();
        String failedDesc = failedStepOpt.map(PlanStep::description).orElse("(unknown step)");
        String failedResult = state.stepResult();
        String critique = state.critique().isEmpty() ? "Execution failed" : state.critique();

        StringBuilder completedSummary = new StringBuilder();
        List<PlanStep> completedSteps = state.completedSteps();
        for (PlanStep cs : completedSteps) {
            if (cs.isCompleted()) {
                completedSummary.append(String.format("- Step %d: %s -> %s\n",
                        cs.step(), cs.description(), cs.result()));
            }
        }
        if (completedSummary.isEmpty()) {
            completedSummary.append("(no steps completed yet)\n");
        }

        StringBuilder remainingSummary = new StringBuilder();
        for (int i = failedIdx + 1; i < currentPlan.size(); i++) {
            PlanStep ps = currentPlan.get(i);
            remainingSummary.append(String.format("- Step %d: %s\n", ps.step(), ps.description()));
        }
        if (remainingSummary.isEmpty()) {
            remainingSummary.append("(no subsequent steps planned)\n");
        }

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

        String promptTemplate = loadPromptTemplate("coordinator-plan-adapter");
        String prompt = promptTemplate
                .replace("{{task}}", task)
                .replace("{{completed_steps}}", completedSummary.toString())
                .replace("{{failed_step_number}}", String.valueOf(failedIdx + 1))
                .replace("{{failed_step_description}}", failedDesc)
                .replace("{{critique}}", critique)
                .replace("{{failed_step_result}}", failedResult)
                .replace("{{remaining_steps}}", remainingSummary.toString())
                .replace("{{next_step_number}}", String.valueOf(failedIdx + 1))
                .replace("{{available_tools}}", String.join(", ", availableTools))
                .replace("{{available_agents}}", agentsList.toString());

        String response = llmBridge.generate(prompt);
        log.debug("[PlanAdapterNode] LLM adapter response length: {}", response.length());

        List<Map<String, Object>> revisedSteps = new ArrayList<>();
        // 1. Keep successfully completed steps
        for (int i = 0; i < failedIdx; i++) {
            revisedSteps.add(currentPlan.get(i).toMap());
        }

        // 2. Parse revised remaining steps from LLM
        String jsonArray = PlannerNode.extractJsonArray(response);
        if (jsonArray != null) {
            try {
                List<Map<String, Object>> parsed = MAPPER.readValue(jsonArray, new TypeReference<>() {});
                if (parsed != null && !parsed.isEmpty()) {
                    for (int i = 0; i < parsed.size(); i++) {
                        var stepMap = new LinkedHashMap<>(parsed.get(i));
                        stepMap.put("step", failedIdx + 1 + i);
                        stepMap.put("status", PlanStep.STATUS_PENDING);
                        revisedSteps.add(stepMap);
                    }
                    log.info("[PlanAdapterNode] Replaced remaining plan with {} revised steps", parsed.size());
                }
            } catch (Exception e) {
                log.warn("[PlanAdapterNode] Failed to parse revised plan array: {}", e.getMessage());
            }
        }

        // 3. Fallback if parsing failed: create alternative retry step
        if (revisedSteps.size() <= failedIdx) {
            PlanStep retryStep = PlanStep.of(failedIdx + 1, "Alternative approach for: " + failedDesc);
            revisedSteps.add(retryStep.toMap());
            log.info("[PlanAdapterNode] Added fallback alternative retry step");
        }

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("plan_steps", revisedSteps);
        updates.put("current_step_index", failedIdx);
        updates.put("iteration", iteration + 1);
        updates.put("status", "EXECUTING_STEP");

        return updates;
    }

    private String loadPromptTemplate(String name) {
        String path = "/prompts/" + name + ".txt";
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("[PlanAdapterNode] Failed to load prompt: {}", name);
        }
        return """
                Revise remaining plan steps to resolve the failure and achieve the task.
                TASK: {{task}}
                FAILED STEP: {{failed_step_description}}
                CRITIQUE: {{critique}}
                """;
    }
}
