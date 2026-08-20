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

import com.spectrayan.spector.synapse.agent.graph.CognitiveState;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * REFLECTION node — evaluates generated answers and enforces metacognitive self-correction.
 *
 * <p>Assesses output on 4 core quality dimensions:
 * <ol>
 *   <li><b>Completeness</b> — Does the answer fully address the query?</li>
 *   <li><b>Groundedness</b> — Is the answer supported by retrieved context without hallucination?</li>
 *   <li><b>Relevance</b> — Is the answer strictly on-topic?</li>
 *   <li><b>Tool Success</b> — Did tool invocations succeed without unresolved errors or denials?</li>
 * </ol>
 *
 * <p>Sets {@code reflection_decision} to one of:
 * <ul>
 *   <li>{@code ACCEPT} — Quality verified; proceed to output/END.</li>
 *   <li>{@code RETRY_RETRIEVE} — Missing/ungrounded context; refine query and re-retrieve.</li>
 *   <li>{@code RETRY_GENERATE} — Context present but flawed reasoning/formatting; regenerate with critique.</li>
 * </ul>
 */
public final class ReflectionNode implements NodeAction<CognitiveState> {

    private static final Logger log = LoggerFactory.getLogger(ReflectionNode.class);

    private static final int DEFAULT_MAX_RETRIES = 3;

    private static final Pattern RETRY_RETRIEVE_PATTERN = Pattern.compile(
            "DECISION:\\s*RETRY_RETRIEVE.*?REFINED_QUERY:\\s*(.+?)(?:\\nCRITIQUE:\\s*(.+?))?(?:\\nREASON:|$)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern RETRY_GENERATE_PATTERN = Pattern.compile(
            "DECISION:\\s*RETRY_GENERATE.*?CRITIQUE:\\s*(.+?)(?:\\nREASON:|$)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern ACCEPT_PATTERN = Pattern.compile(
            "DECISION:\\s*ACCEPT",
            Pattern.CASE_INSENSITIVE);

    private final LlmBridge llmBridge;
    private final int maxRetries;
    private final boolean enabled;

    public ReflectionNode(LlmBridge llmBridge) {
        this(llmBridge, DEFAULT_MAX_RETRIES, true);
    }

    public ReflectionNode(LlmBridge llmBridge, int maxRetries, boolean enabled) {
        this.llmBridge = Objects.requireNonNull(llmBridge, "llmBridge");
        this.maxRetries = maxRetries > 0 ? maxRetries : DEFAULT_MAX_RETRIES;
        this.enabled = enabled;
    }

    @Override
    public Map<String, Object> apply(CognitiveState state) {
        if (!enabled) {
            log.debug("[ReflectionNode] Reflection disabled, passing through as ACCEPT");
            return Map.of("reflection_decision", "ACCEPT");
        }

        String answer = state.answer().orElse("");
        if (answer.isBlank()) {
            log.warn("[ReflectionNode] Blank answer detected, triggering RETRY_GENERATE");
            return Map.of(
                    "reflection_decision", "RETRY_GENERATE",
                    "critique", List.of("[Reflection Critique] No answer was produced during generation. Please generate a complete response."),
                    "retry_count", state.retryCount() + 1
            );
        }

        int currentRetries = state.retryCount();
        if (currentRetries >= maxRetries) {
            log.warn("[ReflectionNode] Max retries ({}) reached for query '{}', accepting best answer with warning",
                    maxRetries, state.originalQuery());
            String warningAnswer = answer + "\n\n*(Note: Generated with quality caveats after " + maxRetries + " reflection attempts)*";
            return Map.of(
                    "reflection_decision", "ACCEPT",
                    "answer", warningAnswer
            );
        }

        List<String> contextEntries = state.context();
        String contextText = contextEntries.isEmpty()
                ? "(No retrieved context available)"
                : String.join("\n", contextEntries);

        log.info("[ReflectionNode] Evaluating answer quality (attempt {}/{}) for query: '{}'",
                currentRetries + 1, maxRetries, truncate(state.originalQuery(), 60));

        String promptTemplate = loadPromptTemplate("cognitive-reflection");
        String prompt = promptTemplate
                .replace("{{query}}", state.originalQuery())
                .replace("{{context}}", contextText)
                .replace("{{answer}}", answer);

        String response = llmBridge.generate(prompt);
        log.debug("[ReflectionNode] LLM evaluation response: {}", truncate(response, 200));

        // 1. Check RETRY_RETRIEVE
        Matcher retrieveMatcher = RETRY_RETRIEVE_PATTERN.matcher(response);
        if (retrieveMatcher.find()) {
            String refinedQuery = retrieveMatcher.group(1).trim();
            String critique = retrieveMatcher.group(2) != null && !retrieveMatcher.group(2).isBlank()
                    ? retrieveMatcher.group(2).trim()
                    : "Additional retrieval needed for query: " + refinedQuery;

            log.info("[ReflectionNode] Decision: RETRY_RETRIEVE -> refinedQuery='{}', critique='{}'",
                    refinedQuery, truncate(critique, 80));

            return Map.of(
                    "reflection_decision", "RETRY_RETRIEVE",
                    "query", refinedQuery,
                    "critique", List.of("[Reflection Critique: Missing Facts] " + critique),
                    "retry_count", currentRetries + 1
            );
        }

        // 2. Check RETRY_GENERATE
        Matcher generateMatcher = RETRY_GENERATE_PATTERN.matcher(response);
        if (generateMatcher.find()) {
            String critique = generateMatcher.group(1).trim();
            log.info("[ReflectionNode] Decision: RETRY_GENERATE -> critique='{}'", truncate(critique, 80));

            return Map.of(
                    "reflection_decision", "RETRY_GENERATE",
                    "critique", List.of("[Reflection Critique: Reasoning/Structure] " + critique),
                    "retry_count", currentRetries + 1
            );
        }

        // 3. Check ACCEPT
        if (ACCEPT_PATTERN.matcher(response).find()) {
            log.info("[ReflectionNode] Decision: ACCEPT (Quality check passed)");
            return Map.of("reflection_decision", "ACCEPT");
        }

        // Default fallback to ACCEPT
        log.warn("[ReflectionNode] Could not parse reflection decision, defaulting to ACCEPT");
        return Map.of("reflection_decision", "ACCEPT");
    }

    private String loadPromptTemplate(String name) {
        String path = "/prompts/" + name + ".txt";
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("[ReflectionNode] Failed to load prompt template '{}': {}", name, e.getMessage());
        }
        // Inline fallback template
        return """
                You are an expert self-reflection evaluator assessing the quality of an AI agent's generated answer.
                
                ORIGINAL QUERY:
                {{query}}
                
                RETRIEVED CONTEXT & TOOL RESULTS:
                {{context}}
                
                GENERATED ANSWER:
                {{answer}}
                
                Evaluate on: COMPLETENESS, GROUNDEDNESS, RELEVANCE, TOOL_SUCCESS.
                
                Respond with EXACTLY one of:
                DECISION: ACCEPT
                REASON: [reason]
                
                OR:
                DECISION: RETRY_RETRIEVE
                REFINED_QUERY: [new query]
                CRITIQUE: [critique]
                REASON: [reason]
                
                OR:
                DECISION: RETRY_GENERATE
                CRITIQUE: [critique]
                REASON: [reason]
                """;
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
