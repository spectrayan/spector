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
import java.util.Map;
import java.util.Objects;

/**
 * EVALUATOR node — determines whether the task is complete or needs another iteration.
 *
 * <p>Sets the {@code decision} to either DONE (task complete) or CONTINUE
 * (needs another planning/execution cycle).</p>
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
        String executionResult = state.executionResult().orElse("(no result)");
        int iteration = state.iteration();
        int maxIterations = state.maxIterations();

        log.info("[ResultEvaluatorNode] Evaluating result (iteration {}/{})", iteration, maxIterations);

        // Force completion if max iterations reached
        if (iteration >= maxIterations) {
            log.warn("[ResultEvaluatorNode] Max iterations reached, forcing DONE");
            return Map.of(
                    "decision", "DONE",
                    "answer", executionResult,
                    "status", "DONE"
            );
        }

        // Check for errors — retry
        if (executionResult.startsWith("ERROR:")) {
            log.info("[ResultEvaluatorNode] Execution failed, CONTINUE for retry");
            return Map.of(
                    "decision", "CONTINUE",
                    "status", "PLANNING"
            );
        }

        String promptTemplate = loadPromptTemplate("coordinator-evaluator-system");
        String prompt = promptTemplate
                .replace("{{task}}", task)
                .replace("{{result}}", executionResult)
                .replace("{{iteration}}", String.valueOf(iteration));

        String response = llmBridge.generate(prompt);
        log.debug("[ResultEvaluatorNode] LLM response: {}", response);

        // Parse decision from LLM
        String upper = response.toUpperCase();
        if (upper.contains("DONE") || upper.contains("COMPLETE") || upper.contains("SUFFICIENT")) {
            log.info("[ResultEvaluatorNode] DONE");
            return Map.of(
                    "decision", "DONE",
                    "answer", executionResult,
                    "status", "DONE"
            );
        }

        log.info("[ResultEvaluatorNode] CONTINUE");
        return Map.of(
                "decision", "CONTINUE",
                "status", "PLANNING"
        );
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
        return """
                Evaluate whether the task has been completed satisfactorily.
                
                TASK: {{task}}
                RESULT: {{result}}
                ITERATION: {{iteration}}
                
                Respond with DONE if the result adequately addresses the task,
                or CONTINUE if another iteration is needed.
                """;
    }
}
