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
package com.spectrayan.spector.synapse.agent.graph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Map.entry;

/**
 * Cognitive agent state that flows through the LangGraph4j graph.
 *
 * <h3>Channels</h3>
 * <ul>
 *   <li>{@code query} — current search query (overwrite semantics)</li>
 *   <li>{@code original_query} — original user query (overwrite)</li>
 *   <li>{@code context} — retrieved cognitive results as text (appender — accumulates)</li>
 *   <li>{@code decision} — LLM routing decision: GENERATE, REQUERY, USE_TOOLS (overwrite)</li>
 *   <li>{@code answer} — final generated answer (overwrite)</li>
 *   <li>{@code attempt} — retrieval attempt counter (overwrite)</li>
 *   <li>{@code tool_calls} — pending tool call requests (appender)</li>
 *   <li>{@code tool_results} — tool execution results (appender)</li>
 *   <li>{@code child_results} — aggregated subtask results (appender)</li>
 *   <li>{@code reflection_decision} — post-generation quality decision: ACCEPT, RETRY_GENERATE, RETRY_RETRIEVE (overwrite)</li>
 *   <li>{@code critique} — accumulated reflection feedback critiques across retries (appender)</li>
 *   <li>{@code retry_count} — counter for self-correction retry attempts (overwrite)</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>Each graph invocation creates a fresh state. {@link AgentState} itself
 * is mutable within a single graph run but not shared across runs.</p>
 */
public class CognitiveState extends AgentState {

    /** State schema — defines channels with their merge semantics. */
    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
            entry("query",               Channels.base(() -> "")),
            entry("original_query",      Channels.base(() -> "")),
            entry("context",             Channels.appender(ArrayList::new)),
            entry("decision",            Channels.base(() -> "")),
            entry("answer",              Channels.base(() -> "")),
            entry("attempt",             Channels.base(() -> 0)),
            entry("tool_calls",          Channels.appender(ArrayList::new)),
            entry("tool_results",        Channels.appender(ArrayList::new)),
            entry("child_results",       Channels.appender(ArrayList::new)),
            entry("reflection_decision", Channels.base(() -> "ACCEPT")),
            entry("critique",            Channels.appender(ArrayList::new)),
            entry("retry_count",         Channels.base(() -> 0))
    );

    public CognitiveState(Map<String, Object> initData) {
        super(initData);
    }

    // ── Typed accessors ──────────────────────────────────────────

    public String query() {
        return this.<String>value("query").orElse("");
    }

    public String originalQuery() {
        return this.<String>value("original_query").orElse("");
    }

    @SuppressWarnings("unchecked")
    public List<String> context() {
        return this.<List<String>>value("context").orElse(List.of());
    }

    public String decision() {
        return this.<String>value("decision").orElse("");
    }

    public Optional<String> answer() {
        return this.<String>value("answer").filter(s -> !s.isBlank());
    }

    public int attempt() {
        return this.<Integer>value("attempt").orElse(0);
    }

    @SuppressWarnings("unchecked")
    public List<String> toolCalls() {
        return this.<List<String>>value("tool_calls").orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public List<String> toolResults() {
        return this.<List<String>>value("tool_results").orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> childResults() {
        return this.<List<Map<String, Object>>>value("child_results").orElse(List.of());
    }

    public String reflectionDecision() {
        return this.<String>value("reflection_decision").orElse("ACCEPT");
    }

    @SuppressWarnings("unchecked")
    public List<String> critiques() {
        return this.<List<String>>value("critique").orElse(List.of());
    }

    public int retryCount() {
        return this.<Integer>value("retry_count").orElse(0);
    }
}
