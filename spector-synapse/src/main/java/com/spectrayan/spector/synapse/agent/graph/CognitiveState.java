/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.synapse.agent.graph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cognitive agent state that flows through the LangGraph4j graph.
 *
 * <h3>Channels</h3>
 * <ul>
 *   <li>{@code query} — current search query (overwrite semantics)</li>
 *   <li>{@code original_query} — original user query (overwrite)</li>
 *   <li>{@code context} — retrieved cognitive results as text (appender — accumulates)</li>
 *   <li>{@code decision} — LLM routing decision: GENERATE, REQUERY (overwrite)</li>
 *   <li>{@code answer} — final generated answer (overwrite)</li>
 *   <li>{@code attempt} — retrieval attempt counter (overwrite)</li>
 *   <li>{@code tool_calls} — pending tool call requests (appender)</li>
 *   <li>{@code tool_results} — tool execution results (appender)</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>Each graph invocation creates a fresh state. {@link AgentState} itself
 * is mutable within a single graph run but not shared across runs.</p>
 */
public class CognitiveState extends AgentState {

    /** State schema — defines channels with their merge semantics. */
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            "query",          Channels.base(() -> ""),
            "original_query", Channels.base(() -> ""),
            "context",        Channels.appender(ArrayList::new),
            "decision",       Channels.base(() -> ""),
            "answer",         Channels.base(() -> ""),
            "attempt",        Channels.base(() -> 0),
            "tool_calls",     Channels.appender(ArrayList::new),
            "tool_results",   Channels.appender(ArrayList::new)
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
}
