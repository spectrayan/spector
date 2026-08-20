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

/**
 * SYNTHESIZE node — consolidates all intermediate results and executed plan steps
 * into a cohesive, structured final answer that satisfies the original task.
 */
public final class SynthesizeNode implements NodeAction<CoordinatorState> {

    private static final Logger log = LoggerFactory.getLogger(SynthesizeNode.class);

    private final LlmBridge llmBridge;

    public SynthesizeNode(LlmBridge llmBridge) {
        this.llmBridge = Objects.requireNonNull(llmBridge, "llmBridge");
    }

    @Override
    public Map<String, Object> apply(CoordinatorState state) {
        String task = state.task().isEmpty() ? state.query() : state.task();
        List<PlanStep> completedSteps = state.completedSteps();
        log.info("[SynthesizeNode] Synthesizing final answer from {} completed steps for task: '{}'",
                completedSteps.size(), task);

        // If no completed steps or only 1 step with direct answer, check if simple pass-through works
        if (completedSteps.isEmpty()) {
            String fallback = state.stepResult().isEmpty()
                    ? state.executionResult().orElse("Task completed.")
                    : state.stepResult();
            return Map.of(
                    "answer", fallback,
                    "decision", "DONE",
                    "status", "DONE"
            );
        }

        if (completedSteps.size() == 1 && completedSteps.getFirst().result() != null
                && !completedSteps.getFirst().result().isBlank()) {
            String singleResult = completedSteps.getFirst().result();
            return Map.of(
                    "answer", singleResult,
                    "decision", "DONE",
                    "status", "DONE"
            );
        }

        StringBuilder completedSummary = new StringBuilder();
        for (PlanStep cs : completedSteps) {
            completedSummary.append(String.format("### Step %d: %s\n**Result:**\n%s\n\n",
                    cs.step(), cs.description(), cs.result()));
        }

        String promptTemplate = loadPromptTemplate("coordinator-synthesizer");
        String prompt = promptTemplate
                .replace("{{task}}", task)
                .replace("{{completed_steps}}", completedSummary.toString());

        String synthesizedAnswer = llmBridge.generate(prompt);
        log.info("[SynthesizeNode] Synthesized final answer ({} chars)", synthesizedAnswer.length());

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("answer", synthesizedAnswer);
        updates.put("decision", "DONE");
        updates.put("status", "DONE");

        return updates;
    }

    private String loadPromptTemplate(String name) {
        String path = "/prompts/" + name + ".txt";
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("[SynthesizeNode] Failed to load prompt: {}", name);
        }
        return """
                Synthesize a comprehensive final answer for the task based on the step findings below.
                
                TASK: {{task}}
                
                FINDINGS:
                {{completed_steps}}
                """;
    }
}
