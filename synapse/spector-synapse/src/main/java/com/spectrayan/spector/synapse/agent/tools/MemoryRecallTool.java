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
package com.spectrayan.spector.synapse.agent.tools;

import com.spectrayan.spector.synapse.agent.AgentTool;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreResponse;
import com.spectrayan.spector.synapse.memory.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Memory recall tool — agents can recall relevant memories from the Spector engine.
 */
@Component
public class MemoryRecallTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(MemoryRecallTool.class);
    private final MemoryService memoryService;

    public MemoryRecallTool(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public String name() { return "memory_recall"; }

    @Override
    public String description() {
        return "Recall relevant memories from the cognitive memory engine using semantic search.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "What to recall"),
                        "topK", Map.of("type", "integer", "description", "Max results", "default", 5)
                ),
                "required", List.of("query")
        );
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.MEMORY;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String query = (String) arguments.get("query");
        int topK = arguments.containsKey("topK") ? ((Number) arguments.get("topK")).intValue() : 5;

        List<RecallResult> results = memoryService.recall(new RecallRequest(query, topK, 1));
        if (results.isEmpty()) {
            return "No relevant memories found for: " + query;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(results.size()).append(" relevant memories:\n\n");
        for (RecallResult r : results) {
            sb.append("- [").append(r.tier()).append("] ").append(r.text()).append("\n");
        }
        return sb.toString();
    }
}
