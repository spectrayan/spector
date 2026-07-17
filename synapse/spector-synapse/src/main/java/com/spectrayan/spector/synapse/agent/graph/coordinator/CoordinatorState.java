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
package com.spectrayan.spector.synapse.agent.graph.coordinator;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * State for the coordinator meta-graph.
 *
 * <p>Extends the basic cognitive state with coordinator-specific channels:
 * task description, generated flow spec, execution results, iteration tracking.</p>
 */
public class CoordinatorState extends AgentState {

    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
            Map.entry("task",              Channels.base(() -> "")),
            Map.entry("query",             Channels.base(() -> "")),
            Map.entry("original_query",    Channels.base(() -> "")),
            Map.entry("context",           Channels.appender(ArrayList::new)),
            Map.entry("decision",          Channels.base(() -> "")),
            Map.entry("answer",            Channels.base(() -> "")),
            Map.entry("attempt",           Channels.base(() -> 0)),
            Map.entry("flow_spec_json",    Channels.base(() -> "")),
            Map.entry("execution_result",  Channels.base(() -> "")),
            Map.entry("iteration",         Channels.base(() -> 0)),
            Map.entry("max_iterations",    Channels.base(() -> 5)),
            Map.entry("status",            Channels.base(() -> "PLANNING")),
            Map.entry("child_results",     Channels.appender(ArrayList::new))
    );

    public CoordinatorState(Map<String, Object> initData) {
        super(initData);
    }

    // ── Typed accessors ──────────────────────────────────────

    public String task() {
        return this.<String>value("task").orElse("");
    }

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

    public Optional<String> flowSpecJson() {
        return this.<String>value("flow_spec_json").filter(s -> !s.isBlank());
    }

    public Optional<String> executionResult() {
        return this.<String>value("execution_result").filter(s -> !s.isBlank());
    }

    public int iteration() {
        return this.<Integer>value("iteration").orElse(0);
    }

    public int maxIterations() {
        return this.<Integer>value("max_iterations").orElse(5);
    }

    public String status() {
        return this.<String>value("status").orElse("PLANNING");
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> childResults() {
        return this.<List<Map<String, Object>>>value("child_results").orElse(List.of());
    }
}
