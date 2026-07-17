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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EVALUATE node â€” asks the LLM to assess retrieved context sufficiency.
 *
 * <p>Sets the {@code decision} channel to one of:</p>
 * <ul>
 *   <li>{@code GENERATE} â€” context is sufficient, proceed to answer generation</li>
 *   <li>{@code REQUERY} â€” context is insufficient, refine query and retrieve again</li>
 *   <li>{@code USE_TOOLS} â€” context is insufficient, call tools to fetch more data</li>
 * </ul>
 *
 * <p>Uses the {@link LlmBridge} (Spring AI / LangChain4j) for LLM calls instead of
 * the enterprise {@code LlmProvider}.</p>
 */
public final class EvaluateNode implements NodeAction<CognitiveState> {

    private static final Logger log = LoggerFactory.getLogger(EvaluateNode.class);

    /** Maximum context entries to pass to the LLM (sliding window to prevent token bloat). */
    private static final int MAX_CONTEXT_ENTRIES = 20;

    private static final Pattern REQUERY_PATTERN = Pattern.compile(
            "DECISION:\\s*REQUERY.*?REFINED_QUERY:\\s*(.+?)(?:\\nREASON:|$)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern GENERATE_PATTERN = Pattern.compile(
            "DECISION:\\s*GENERATE",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern USE_TOOLS_PATTERN = Pattern.compile(
            "DECISION:\\s*USE_TOOLS.*?TOOL_CALLS:\\s*(.+?)(?:\\nREASON:|$)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final LlmBridge llmBridge;
    private final List<String> toolNames;

    /**
     * Creates an EvaluateNode without tool awareness (backward-compatible).
     *
     * @param llmBridge the LLM bridge for evaluation calls
     */
    public EvaluateNode(LlmBridge llmBridge) {
        this(llmBridge, List.of());
    }

    /**
     * Creates an EvaluateNode with tool awareness.
     *
     * <p>When tool names are provided, the evaluation prompt includes them
     * as available options, enabling the LLM to route to {@code USE_TOOLS}
     * when context retrieval alone is insufficient.</p>
     *
     * @param llmBridge the LLM bridge for evaluation calls
     * @param toolNames names of available tools (empty list disables tool routing)
     */
    public EvaluateNode(LlmBridge llmBridge, List<String> toolNames) {
        this.llmBridge = Objects.requireNonNull(llmBridge, "llmBridge");
        this.toolNames = toolNames != null ? List.copyOf(toolNames) : List.of();
    }

    @Override
    public Map<String, Object> apply(CognitiveState state) {
        List<String> contextEntries = state.context();

        // Phase 6 (P2): Context pruning â€” deduplicate and apply sliding window
        List<String> prunedContext = pruneContext(contextEntries);

        String contextText = prunedContext.isEmpty()
                ? "(No results retrieved)"
                : String.join("\n", prunedContext);

        log.info("[EvaluateNode] Evaluating {} context entries (pruned from {})",
                prunedContext.size(), contextEntries.size());

        // Build available tools string for the prompt
        String availableToolsStr = toolNames.isEmpty()
                ? "(No tools available)"
                : String.join(", ", toolNames);

        // Load prompt template and substitute placeholders
        String promptTemplate = loadPromptTemplate("agentic-rag-evaluate");
        String prompt = promptTemplate
                .replace("{{context}}", contextText)
                .replace("{{query}}", state.originalQuery())
                .replace("{{available_tools}}", availableToolsStr);

        String response = llmBridge.generate(prompt);
        log.debug("[EvaluateNode] LLM response: {}", truncate(response, 200));

        // Parse decision â€” check USE_TOOLS first (most specific)
        Matcher useToolsMatcher = USE_TOOLS_PATTERN.matcher(response);
        if (useToolsMatcher.find() && !toolNames.isEmpty()) {
            String toolCallsStr = useToolsMatcher.group(1).trim();
            List<String> toolCallsList = parseToolCalls(toolCallsStr);
            if (!toolCallsList.isEmpty()) {
                log.info("[EvaluateNode] USE_TOOLS â†’ {}", toolCallsList);
                return Map.of(
                        "decision", "USE_TOOLS",
                        "tool_calls", toolCallsList
                );
            }
        }

        Matcher requeryMatcher = REQUERY_PATTERN.matcher(response);
        if (requeryMatcher.find()) {
            String refinedQuery = requeryMatcher.group(1).trim();
            log.info("[EvaluateNode] REQUERY â†’ '{}'", refinedQuery);
            return Map.of(
                    "decision", "REQUERY",
                    "query", refinedQuery
            );
        }

        if (GENERATE_PATTERN.matcher(response).find()) {
            log.info("[EvaluateNode] GENERATE");
            return Map.of("decision", "GENERATE");
        }

        // Fallback: treat as GENERATE
        log.warn("[EvaluateNode] Could not parse decision, defaulting to GENERATE");
        return Map.of("decision", "GENERATE");
    }

    /**
     * Parses tool call specifications from the LLM response.
     *
     * <p>Expected format: comma-separated tool calls like
     * {@code toolName({"key": "val"})} or just {@code toolName}.</p>
     *
     * @param input raw tool calls string from LLM
     * @return list of parseable tool call specifications
     */
    static List<String> parseToolCalls(String input) {
        List<String> list = new ArrayList<>();
        if (input == null || input.isBlank()) {
            return list;
        }
        // Match toolName({"key": "val"}) or toolName() or toolName
        Pattern pattern = Pattern.compile("([a-zA-Z0-9_\\-]+(?:\\s*\\((?:\\{.*?\\})?\\))?)");
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            String call = matcher.group(1).trim();
            if (!call.isEmpty()) {
                list.add(call);
            }
        }
        return list;
    }

    /**
     * Prunes context entries to prevent LLM token bloat in iterative loops.
     *
     * <p>Deduplicates entries (preserving order) and applies a sliding window
     * keeping only the most recent {@link #MAX_CONTEXT_ENTRIES} entries.</p>
     *
     * @param entries raw accumulated context entries
     * @return pruned, deduplicated context entries
     */
    private static List<String> pruneContext(List<String> entries) {
        if (entries.isEmpty()) {
            return entries;
        }

        // Deduplicate while preserving insertion order
        var deduped = new ArrayList<>(new LinkedHashSet<>(entries));

        // Sliding window â€” keep only the last N entries
        if (deduped.size() > MAX_CONTEXT_ENTRIES) {
            int pruned = deduped.size() - MAX_CONTEXT_ENTRIES;
            log.debug("[EvaluateNode] Pruned {} duplicate/old context entries", pruned);
            return deduped.subList(deduped.size() - MAX_CONTEXT_ENTRIES, deduped.size());
        }
        return deduped;
    }

    private String loadPromptTemplate(String name) {
        String path = "/prompts/" + name + ".txt";
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("[EvaluateNode] Failed to load prompt template '{}': {}", name, e.getMessage());
        }
        // Fallback inline template
        return """
                You are evaluating whether the retrieved context is sufficient to answer the query.
                
                CONTEXT:
                {{context}}
                
                QUERY: {{query}}
                
                AVAILABLE TOOLS: {{available_tools}}
                
                Respond with EXACTLY one of:
                DECISION: GENERATE
                REASON: [why context is sufficient]
                
                OR:
                DECISION: REQUERY
                REFINED_QUERY: [a better search query]
                REASON: [why context is insufficient]
                
                OR:
                DECISION: USE_TOOLS
                TOOL_CALLS: toolName({"arg": "value"})
                REASON: [why tools are needed]
                """;
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
