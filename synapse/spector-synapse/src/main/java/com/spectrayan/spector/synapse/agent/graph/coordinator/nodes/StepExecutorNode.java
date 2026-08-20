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

import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.synapse.agent.graph.AgenticChatGraph;
import com.spectrayan.spector.synapse.agent.graph.CognitiveState;
import com.spectrayan.spector.synapse.agent.graph.DynamicGraphBuilder;
import com.spectrayan.spector.synapse.agent.graph.coordinator.CoordinatorState;
import com.spectrayan.spector.synapse.agent.graph.coordinator.model.PlanStep;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
import com.spectrayan.spector.synapse.bridge.LlmBridge;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * STEP_EXECUTOR node — executes the current plan step in the Plan-and-Execute pipeline.
 *
 * <p>Supports executing steps via dynamic subgraphs (FlowSpec), specialized agent delegation,
 * or direct LLM execution.</p>
 */
public final class StepExecutorNode implements NodeAction<CoordinatorState> {

    private static final Logger log = LoggerFactory.getLogger(StepExecutorNode.class);

    private final DynamicGraphBuilder dynamicBuilder;
    private final LlmBridge llmBridge;
    private final CognitiveSoulService soulService;
    private final AgenticChatGraph agenticChatGraph;

    public StepExecutorNode(DynamicGraphBuilder dynamicBuilder) {
        this(dynamicBuilder, null, null, null);
    }

    public StepExecutorNode(DynamicGraphBuilder dynamicBuilder,
                            LlmBridge llmBridge,
                            CognitiveSoulService soulService,
                            AgenticChatGraph agenticChatGraph) {
        this.dynamicBuilder = Objects.requireNonNull(dynamicBuilder, "dynamicBuilder");
        this.llmBridge = llmBridge;
        this.soulService = soulService;
        this.agenticChatGraph = agenticChatGraph;
    }

    @Override
    public Map<String, Object> apply(CoordinatorState state) {
        List<PlanStep> steps = state.planSteps();
        int stepIdx = state.currentStepIndex();

        if (steps.isEmpty() || stepIdx < 0 || stepIdx >= steps.size()) {
            log.warn("[StepExecutorNode] No active step at index {} (total steps: {})", stepIdx, steps.size());
            return Map.of(
                    "step_result", "No remaining steps to execute",
                    "execution_result", "No remaining steps to execute",
                    "status", "EVALUATING_STEP"
            );
        }

        PlanStep currentStep = steps.get(stepIdx);
        log.info("[StepExecutorNode] Executing Step {}/{}: '{}'",
                currentStep.step(), steps.size(), currentStep.description());

        String resultText;
        String status = PlanStep.STATUS_COMPLETED;
        List<Map<String, Object>> childResultList = new ArrayList<>();
        List<String> contextList = new ArrayList<>();

        try {
            // 1. Check if step has an explicit FlowSpec JSON
            String flowJson = currentStep.flowSpecJson();
            if (flowJson == null || flowJson.isBlank()) {
                flowJson = state.flowSpecJson().orElse(null);
            }

            if (flowJson != null && !flowJson.isBlank()) {
                log.info("[StepExecutorNode] Executing step via dynamic subgraph");
                CompiledGraph<CognitiveState> subgraph = dynamicBuilder.buildFromJson(flowJson);
                var subResult = subgraph.invoke(Map.of(
                        "query", currentStep.description().isEmpty() ? state.query() : currentStep.description(),
                        "original_query", state.originalQuery()
                ));

                if (subResult.isPresent()) {
                    CognitiveState subState = subResult.get();
                    resultText = subState.answer().orElse("(no answer produced by subgraph)");
                    contextList.addAll(subState.context());
                    var subChildren = subState.value("child_results")
                            .filter(List.class::isInstance)
                            .map(l -> (List<Map<String, Object>>) l)
                            .orElse(List.of());
                    childResultList.addAll(subChildren);
                } else {
                    resultText = "Subgraph returned empty state";
                }
            }
            // 2. Check if step delegates to a specialized child agent
            else if (currentStep.assignedAgent() != null && !currentStep.assignedAgent().isBlank()
                    && soulService != null && agenticChatGraph != null) {
                String agentId = currentStep.assignedAgent().strip();
                log.info("[StepExecutorNode] Delegating step to specialized agent: '{}'", agentId);
                Optional<AgentSoul> soul = soulService.loadAgentSoul(agentId);
                if (soul.isPresent()) {
                    String agentAnswer = agenticChatGraph.chat(soul.get(), currentStep.description());
                    resultText = agentAnswer;
                    childResultList.add(Map.of(
                            "agent", agentId,
                            "step", currentStep.step(),
                            "result", agentAnswer
                    ));
                } else {
                    resultText = "Assigned agent '" + agentId + "' not found, executed default analysis: "
                            + executeFallbackLlm(currentStep.description(), state);
                }
            }
            // 3. Direct LLM step execution
            else {
                resultText = executeFallbackLlm(currentStep.description(), state);
            }

        } catch (Exception e) {
            log.error("[StepExecutorNode] Step {} failed: {}", currentStep.step(), e.getMessage(), e);
            resultText = "ERROR: " + e.getMessage();
            status = PlanStep.STATUS_FAILED;
        }

        // Update step in plan
        PlanStep updatedStep = currentStep.withStatus(status, resultText);
        List<Map<String, Object>> updatedPlanMaps = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            if (i == stepIdx) {
                updatedPlanMaps.add(updatedStep.toMap());
            } else {
                updatedPlanMaps.add(steps.get(i).toMap());
            }
        }

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("step_result", resultText);
        updates.put("execution_result", resultText);
        updates.put("plan_steps", updatedPlanMaps);
        updates.put("completed_steps", List.of(updatedStep.toMap()));
        updates.put("status", "EVALUATING_STEP");
        if (!childResultList.isEmpty()) {
            updates.put("child_results", childResultList);
        }
        if (!contextList.isEmpty()) {
            updates.put("context", contextList);
        }

        return updates;
    }

    private String executeFallbackLlm(String stepDesc, CoordinatorState state) {
        if (llmBridge == null) {
            return "Completed step: " + stepDesc;
        }
        String prompt = "Perform the following step for the task:\n\n"
                + "TASK: " + state.task() + "\n"
                + "STEP: " + stepDesc + "\n"
                + "PREVIOUS FINDINGS:\n" + String.join("\n", state.context()) + "\n\n"
                + "Provide a detailed, accurate response for this step.";
        return llmBridge.generate(prompt);
    }
}
