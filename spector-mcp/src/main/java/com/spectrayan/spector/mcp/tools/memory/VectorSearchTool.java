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
package com.spectrayan.spector.mcp.tools.memory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.spectrayan.spector.commons.security.SpectorScopes;
import com.spectrayan.spector.engine.SpectorEngine;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.mcp.schema.ToolSchemaBuilder;
import com.spectrayan.spector.mcp.tools.McpToolHandler;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code vector_search} — semantic search against Spector memory
 * index with filters.
 *
 * <p>
 * Performs vector-based semantic search across all memory tiers with
 * configurable
 * filters for tier, time range, tags, and similarity threshold. Gives agents
 * fine-grained control over semantic memory retrieval.
 * </p>
 *
 * <h3>Parameters</h3>
 * <ul>
 * <li>{@code query} (required) — Natural language search query</li>
 * <li>{@code top_k} (optional, default: 10) — Maximum number of results</li>
 * <li>{@code min_similarity} (optional, default: 0.7) — Minimum confidence
 * threshold</li>
 * <li>{@code tier} (optional) — Filter by memory tier (WORKING, EPISODIC,
 * SEMANTIC, PROCEDURAL)</li>
 * <li>{@code time_range} (optional) — "last_24h", "last_7d", "last_30d"</li>
 * <li>{@code tags} (optional) — Comma-separated tags for Bloom filter
 * matching</li>
 * </ul>
 */
public final class VectorSearchTool extends MemoryToolHandler {

    /**
     * Constructs a handler with a fixed memory instance (standalone/OSS mode).
     *
     * @param memory the cognitive memory instance
     */
    public VectorSearchTool(SpectorMemory memory) {
        super(memory);
    }

    /**
     * Constructs a handler with a per-request memory resolver (enterprise mode).
     *
     * @param memoryResolver supplier that resolves the active memory per request
     */
    public VectorSearchTool(Supplier<SpectorMemory> memoryResolver) {
        super(memoryResolver);
    }

    @Override
    public String name() {
        return "vector_search";
    }

    @Override
    public Set<String> requiredScopes() {
        return Set.of(SpectorScopes.MEMORY_READ);
    }

    @Override
    public String description() {
        return "Perform semantic vector search against the Spector memory index with "
                + "fine-grained filtering. Supports tier filtering (WORKING, EPISODIC, "
                + "SEMANTIC, PROCEDURAL), time range filtering (last_24h, last_7d, last_30d), "
                + "tag filtering, and minimum similarity threshold. Use this when you need "
                + "precise, filtered retrieval from memory rather than full cognitive recall.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemaBuilder.object()
                // Required parameters
                .requiredString("query", "Natural language search query for semantic retrieval.")

                // Optional parameters with defaults
                .optionalInt("top_k",
                        "Maximum number of results to return (1-100).",
                        10)

                .optionalNumber("min_similarity",
                        "Minimum similarity threshold (0.0-1.0). Results below this are excluded.",
                        0.7)

                .optionalEnum("tier",
                        "Filter by memory tier. Valid values: WORKING, EPISODIC, SEMANTIC, PROCEDURAL.",
                        "", // default: empty string = all tiers
                        "WORKING", "EPISODIC", "SEMANTIC", "PROCEDURAL")

                .optionalString("time_range",
                        "Filter by time range. Valid values: last_24h, last_7d, last_30d.",
                        "")

                .optionalString("tags",
                        "Comma-separated tags for Bloom filter matching. "
                                + "Only memories containing ALL specified tags are returned. "
                                + "Example: 'debugging,database'.",
                        "")

                .build();
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
            SpectorEngine engine,
            Map<String, Object> args) throws Exception {

        // ── 1. Parse required parameters ──
        String query = requireString(args, "query");

        // ── 2. Parse optional parameters with defaults ──
        int topK = optionalInt(args, "top_k", 10);
        double minSimilarity = optionalDouble(args, "min_similarity", 0.7);
        String tierStr = optionalString(args, "tier", "");
        String timeRange = optionalString(args, "time_range", "");
        String tagsStr = optionalString(args, "tags", "");

        // ── 3. Build RecallOptions ──
        var builder = RecallOptions.builder()
                .topK(Math.max(1, Math.min(100, topK))); // clamp 1-100

        // ── 4. Apply tier filter ──
        if (!tierStr.isBlank()) {
            try {
                MemoryType tier = MemoryType.valueOf(tierStr.strip().toUpperCase());
                builder.memoryTypes(tier);
            } catch (IllegalArgumentException e) {
                // Invalid tier — ignore and search all tiers
            }
        }

        // ── 5. Apply time range filter ──
        if (!timeRange.isBlank()) {
            long[] range = parseTimeRange(timeRange);
            if (range != null) {
                builder.minTimestamp(range[0]);
                builder.maxTimestamp(range[1]);
            }
        }

        // ── 6. Apply tag filter ──
        if (!tagsStr.isBlank()) {
            String[] tags = tagsStr.split("\\s*,\\s*");
            builder.synapticFilter(tags);
        }

        // ── 7. Execute search ──
        RecallOptions options = builder.build();
        options.validate(); // logs warnings for conflicting combos

        long startNs = System.nanoTime();
        List<CognitiveResult> results = memory.recall(query, options);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        // ── 8. Apply min_similarity post-filter ──
        List<CognitiveResult> filtered = new ArrayList<>();
        for (CognitiveResult result : results) {
            // Use the cognitive score as similarity proxy
            if (result.score() >= minSimilarity) {
                filtered.add(result);
            }
        }

        // ── 9. Enforce topK after filtering ──
        if (filtered.size() > topK) {
            filtered = filtered.subList(0, topK);
        }

        // ── 10. Format and return results ──
        if (filtered.isEmpty()) {
            if (results.isEmpty()) {
                return textResult("No memories found for query: '" + query + "'");
            } else {
                return textResult(
                        "Found " + results.size() + " memories, but none met the minimum similarity threshold of "
                                + minSimilarity + ". Try lowering min_similarity.");
            }
        }

        return formatResults(query, filtered, elapsedMs, minSimilarity);
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * Parses optional double argument.
     */
    private static double optionalDouble(Map<String, Object> args, String key, double defaultValue) {
        Object val = args.get(key);
        if (val == null)
            return defaultValue;
        if (val instanceof Number n)
            return n.doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parses time_range string to [minTimestamp, maxTimestamp] array.
     *
     * @param timeRange one of: "last_24h", "last_7d", "last_30d"
     * @return long[] with [minTimestamp, maxTimestamp], or null if invalid
     */
    private static long[] parseTimeRange(String timeRange) {
        if (timeRange == null || timeRange.isBlank())
            return null;

        String trimmed = timeRange.strip().toLowerCase();
        Instant now = Instant.now();

        return switch (trimmed) {
            case "last_24h" -> new long[] {
                    now.minus(24, ChronoUnit.HOURS).toEpochMilli(),
                    now.toEpochMilli()
            };
            case "last_7d" -> new long[] {
                    now.minus(7, ChronoUnit.DAYS).toEpochMilli(),
                    now.toEpochMilli()
            };
            case "last_30d" -> new long[] {
                    now.minus(30, ChronoUnit.DAYS).toEpochMilli(),
                    now.toEpochMilli()
            };
            default -> null;
        };
    }

    /**
     * Formats results into a readable text response.
     */
    private static McpSchema.CallToolResult formatResults(String query,
            List<CognitiveResult> results,
            long elapsedMs,
            double minSimilarity) {
        var sb = new StringBuilder();
        sb.append("🔍 Vector Search Results\n");
        sb.append("Query: \"").append(query).append("\"\n");
        sb.append("Min Similarity: ").append(String.format("%.2f", minSimilarity)).append("\n");
        sb.append("Found ").append(results.size()).append(" results (").append(elapsedMs).append("ms)\n");
        sb.append("\n");

        for (int i = 0; i < results.size(); i++) {
            CognitiveResult r = results.get(i);
            sb.append("--- Result ").append(i + 1).append(" ---\n");
            sb.append("ID: ").append(r.id()).append("\n");
            sb.append("Text: ").append(r.text()).append("\n");
            sb.append("Score: ").append(String.format("%.4f", r.score())).append("\n");
            sb.append("Tier: ").append(r.memoryType()).append("\n");
            sb.append("Age: ").append(String.format("%.1f", r.ageDays())).append(" days\n");
            sb.append("Importance: ").append(String.format("%.2f", r.importance())).append("\n");

            // Show tags if present
            if (r.synapticTags() != null && r.synapticTags().length > 0) {
                sb.append("Tags: [").append(String.join(", ", r.synapticTags())).append("]\n");
            }

            // Show valence if notable
            if (r.valence() > 10) {
                sb.append("Valence: +").append(r.valence()).append(" (positive reinforcement)\n");
            } else if (r.valence() < -10) {
                sb.append("Valence: ").append(r.valence()).append(" (negative outcome)\n");
            }

            // Show retrieval mode
            sb.append("Retrieval: ").append(r.retrievalMode()).append("\n");
            sb.append("\n");
        }

        return textResult(sb.toString());
    }
}
