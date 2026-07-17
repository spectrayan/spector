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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.synapse.agent.AgentTool;

/**
 * Agent tool: {@code vector_search} — semantic search against Spector memory index with filters.
 *
 * <p>Performs vector-based semantic search across all memory tiers with configurable
 * filters for tier, time range, tags, and similarity threshold. Gives agents
 * fine-grained control over semantic memory retrieval.</p>
 */
@Component
public class VectorSearchTool implements AgentTool {

    private final ObjectProvider<SpectorMemory> memoryProvider;

    public VectorSearchTool(ObjectProvider<SpectorMemory> memoryProvider) {
        this.memoryProvider = memoryProvider;
    }

    @Override
    public String name() {
        return "vector_search";
    }

    @Override
    public String description() {
        return "Perform semantic vector search against the Spector memory index with "
                + "fine-grained filtering. Supports tier filtering (WORKING, EPISODIC, "
                + "SEMANTIC, PROCEDURAL), time range filtering (last_24h, last_7d, last_30d), "
                + "tag filtering, and minimum similarity threshold.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        // query - required
        Map<String, Object> queryProp = new LinkedHashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "Natural language search query.");
        properties.put("query", queryProp);

        // top_k - optional with default
        Map<String, Object> topKProp = new LinkedHashMap<>();
        topKProp.put("type", "integer");
        topKProp.put("description", "Maximum number of results (1-100).");
        topKProp.put("default", 10);
        properties.put("top_k", topKProp);

        // min_similarity - optional with default
        Map<String, Object> minSimProp = new LinkedHashMap<>();
        minSimProp.put("type", "number");
        minSimProp.put("description", "Minimum similarity threshold (0.0-1.0).");
        minSimProp.put("default", 0.7);
        properties.put("min_similarity", minSimProp);

        // tier - optional enum
        Map<String, Object> tierProp = new LinkedHashMap<>();
        tierProp.put("type", "string");
        tierProp.put("description", "Filter by tier: WORKING, EPISODIC, SEMANTIC, PROCEDURAL.");
        tierProp.put("enum", List.of("WORKING", "EPISODIC", "SEMANTIC", "PROCEDURAL"));
        properties.put("tier", tierProp);

        // time_range - optional enum
        Map<String, Object> timeRangeProp = new LinkedHashMap<>();
        timeRangeProp.put("type", "string");
        timeRangeProp.put("description", "Filter by time: last_24h, last_7d, last_30d.");
        timeRangeProp.put("enum", List.of("last_24h", "last_7d", "last_30d"));
        properties.put("time_range", timeRangeProp);

        // tags - optional
        Map<String, Object> tagsProp = new LinkedHashMap<>();
        tagsProp.put("type", "string");
        tagsProp.put("description", "Comma-separated tags: 'debugging,database'.");
        properties.put("tags", tagsProp);

        schema.put("properties", properties);
        schema.put("required", List.of("query"));

        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        try {
            SpectorMemory memory = memoryProvider.getIfAvailable();
            if (memory == null) {
                return "Error: Spector memory engine is not available (running in stub mode).";
            }

            String query = getRequiredString(arguments, "query");
            int topK = getOptionalInt(arguments, "top_k", 10);
            double minSimilarity = getOptionalDouble(arguments, "min_similarity", 0.7);
            String tierStr = getOptionalString(arguments, "tier", "");
            String timeRange = getOptionalString(arguments, "time_range", "");
            String tagsStr = getOptionalString(arguments, "tags", "");

            var builder = RecallOptions.builder()
                    .topK(Math.max(1, Math.min(100, topK)));

            if (!tierStr.isBlank()) {
                try {
                    builder.memoryTypes(MemoryType.valueOf(tierStr.strip().toUpperCase()));
                } catch (IllegalArgumentException ignored) {}
            }

            if (!timeRange.isBlank()) {
                long[] range = parseTimeRange(timeRange);
                if (range != null) {
                    builder.minTimestamp(range[0]);
                    builder.maxTimestamp(range[1]);
                }
            }

            if (!tagsStr.isBlank()) {
                builder.synapticFilter(tagsStr.split("\\s*,\\s*"));
            }

            List<CognitiveResult> results = memory.recall(query, builder.build());

            List<CognitiveResult> filtered = new ArrayList<>();
            for (CognitiveResult r : results) {
                if (r.score() >= minSimilarity) {
                    filtered.add(r);
                }
            }

            if (filtered.size() > topK) {
                filtered = filtered.subList(0, topK);
            }

            if (filtered.isEmpty()) {
                if (results.isEmpty()) {
                    return "No memories found for query: '" + query + "'";
                } else {
                    return "Found " + results.size() + " memories, but none met min_similarity=" + minSimilarity;
                }
            }

            return formatResults(query, filtered);

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.MEMORY;
    }

    @Override
    public boolean isWriteTool() {
        return false;
    }

    private String getRequiredString(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val == null || val.toString().isBlank()) {
            throw new IllegalArgumentException("'" + key + "' is required");
        }
        return val.toString();
    }

    private String getOptionalString(Map<String, Object> args, String key, String defaultValue) {
        Object val = args.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private int getOptionalInt(Map<String, Object> args, String key, int defaultValue) {
        Object val = args.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { return defaultValue; }
    }

    private double getOptionalDouble(Map<String, Object> args, String key, double defaultValue) {
        Object val = args.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (NumberFormatException e) { return defaultValue; }
    }

    private static long[] parseTimeRange(String timeRange) {
        if (timeRange == null || timeRange.isBlank()) return null;
        Instant now = Instant.now();
        return switch (timeRange.strip().toLowerCase()) {
            case "last_24h" -> new long[] { now.minus(24, ChronoUnit.HOURS).toEpochMilli(), now.toEpochMilli() };
            case "last_7d" -> new long[] { now.minus(7, ChronoUnit.DAYS).toEpochMilli(), now.toEpochMilli() };
            case "last_30d" -> new long[] { now.minus(30, ChronoUnit.DAYS).toEpochMilli(), now.toEpochMilli() };
            default -> null;
        };
    }

    private static String formatResults(String query, List<CognitiveResult> results) {
        var sb = new StringBuilder();
        sb.append("Vector Search Results\n");
        sb.append("Query: \"").append(query).append("\"\n");
        sb.append("Found ").append(results.size()).append(" results\n\n");

        for (int i = 0; i < results.size(); i++) {
            CognitiveResult r = results.get(i);
            sb.append("--- Result ").append(i + 1).append(" ---\n");
            sb.append("Text: ").append(r.text()).append("\n");
            sb.append("Score: ").append(String.format("%.4f", r.score())).append("\n");
            sb.append("Tier: ").append(r.memoryType()).append("\n");
            if (r.synapticTags() != null && r.synapticTags().length > 0) {
                sb.append("Tags: [").append(String.join(", ", r.synapticTags())).append("]\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
