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
import com.spectrayan.spector.synapse.agent.graph.coordinator.model.PlanStep;
import com.spectrayan.spector.synapse.bridge.LlmBridge;

import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * EVALUATOR node — evaluates per-step execution quality and determines routing:
 * <ul>
 *   <li><b>NEXT_STEP</b>: Current step succeeded, advance to next step.</li>
 *   <li><b>REPLAN</b>: Step failed or output insufficient; adapt remaining plan.</li>
 *   <li><b>SYNTHESIZE</b>: All steps completed or goal reached; consolidate final answer.</li>
 * </ul>
 */
public final class ResultEvaluatorNode implements NodeAction<CoordinatorState> {

    private static final Logger log = LoggerFactory.getLogger(ResultEvaluatorNode.class);

    private final LlmBridge llmBridge;

    public ResultEvaluatorNode(LlmBridge llmBridge) {
        this.llmBridge = Objects.requireNonNull(llmBridge, "llmBridge");
    }

    @Override
    public Map<String, Object> apply(CoordinatorState state) {
        String task = state.task().isEmpty() ? state.query() : state.task();
        String stepResult = state.stepResult().isEmpty()
                ? state.executionResult().orElse("(no result)")
                : state.stepResult();

        int iteration = state.iteration();
        int maxIterations = state.maxIterations();
        int currentStepIdx = state.currentStepIndex();
        List<PlanStep> planSteps = state.planSteps();
        int totalSteps = planSteps.isEmpty() ? 1 : planSteps.size();
        Optional<PlanStep> currentStepOpt = state.currentStep();
        String stepDesc = currentStepOpt.map(PlanStep::description).orElse(task);
        String toolAgent = currentStepOpt.map(ps ->
                (ps.tool() != null ? "Tool: " + ps.tool() + " " : "")
                + (ps.assignedAgent() != null ? "Agent: " + ps.assignedAgent() : "")).orElse("Default");

        log.info("[ResultEvaluatorNode] Evaluating step {}/{} (iteration {}/{})",
                currentStepIdx + 1, totalSteps, iteration, maxIterations);

        Map<String, Object> updates = new LinkedHashMap<>();

        // 1. Force completion if max iterations reached
        if (iteration >= maxIterations) {
            log.warn("[ResultEvaluatorNode] Max iterations ({}) reached, routing to SYNTHESIZE", maxIterations);
            updates.put("decision", "DONE");
            updates.put("plan_decision", "SYNTHESIZE");
            updates.put("status", "SYNTHESIZING");
            return updates;
        }

        // 2. Check for errors — trigger adaptive replan
        if (stepResult.startsWith("ERROR:") || (currentStepOpt.isPresent() && currentStepOpt.get().isFailed())) {
            log.info("[ResultEvaluatorNode] Step {} failed, routing to REPLAN", currentStepIdx + 1);
            updates.put("decision", "REPLAN");
            updates.put("plan_decision", "REPLAN");
            updates.put("critique", "Step execution resulted in error: " + stepResult);
            updates.put("status", "REPLANNING");
            return updates;
        }

        // 3. Prompt LLM for step assessment
        String promptTemplate = loadPromptTemplate("coordinator-step-evaluator");
        if (promptTemplate == null || promptTemplate.isBlank()) {
            promptTemplate = loadPromptTemplate("coordinator-evaluator-system");
        }

        StringBuilder completedStepsSummary = new StringBuilder();
        for (PlanStep cs : state.completedSteps()) {
            completedStepsSummary.append(String.format("- Step %d: %s -> %s\n",
                    cs.step(), cs.description(),
                    cs.result().length() > 100 ? cs.result().substring(0, 100) + "..." : cs.result()));
        }

        String prompt = promptTemplate
                .replace("{{task}}", task)
                .replace("{{step_number}}", String.valueOf(currentStepIdx + 1))
                .replace("{{total_steps}}", String.valueOf(totalSteps))
                .replace("{{step_description}}", stepDesc)
                .replace("{{tool_agent}}", toolAgent)
                .replace("{{step_result}}", stepResult)
                .replace("{{completed_steps}}", completedStepsSummary.toString())
                .replace("{{result}}", stepResult)
                .replace("{{iteration}}", String.valueOf(iteration));

        String response = llmBridge.generate(prompt);
        log.debug("[ResultEvaluatorNode] LLM response: {}", response);

        String upper = response.toUpperCase();

        // 4. Parse decision
        if (upper.contains("REPLAN") || upper.contains("FAILED") || upper.contains("INSUFFICIENT")) {
            log.info("[ResultEvaluatorNode] Decision: REPLAN");
            updates.put("decision", "REPLAN");
            updates.put("plan_decision", "REPLAN");
            updates.put("critique", extractCritique(response));
            updates.put("status", "REPLANNING");
            return updates;
        }

        boolean hasMoreSteps = (currentStepIdx + 1) < totalSteps;

        if (hasMoreSteps && !upper.contains("SYNTHESIZE") && !upper.contains("DONE")) {
            log.info("[ResultEvaluatorNode] Step {} succeeded, advancing to next step", currentStepIdx + 1);
            updates.put("decision", "NEXT_STEP");
            updates.put("plan_decision", "NEXT_STEP");
            updates.put("current_step_index", currentStepIdx + 1);
            updates.put("status", "EXECUTING_STEP");
        } else {
            log.info("[ResultEvaluatorNode] All steps completed or task satisfied, routing to SYNTHESIZE");
            updates.put("decision", "DONE");
            updates.put("plan_decision", "SYNTHESIZE");
            updates.put("status", "SYNTHESIZING");
        }

        return updates;
    }

    private static String extractCritique(String response) {
        int idx = response.indexOf("CRITIQUE:");
        if (idx >= 0) {
            return response.substring(idx + 9).strip();
        }
        return response.strip();
    }

    private String loadPromptTemplate(String name) {
        String path = "/prompts/" + name + ".txt";
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("[ResultEvaluatorNode] Failed to load prompt: {}", name);
        }
        return "";
    }
}
