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
package com.spectrayan.spector.mcp.prompts;

import java.util.List;
import java.util.Map;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.mcp.util.ResultFormatter;
import com.spectrayan.spector.memory.model.CognitiveResult;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Factory for Spector MCP prompt specifications.
 */
public final class SpectorPromptProvider {

    private static final String RAG_SYSTEM_INSTRUCTION =
            "You are a helpful assistant. Use the following context "
            + "retrieved from the Spector cognitive memory to answer the user's "
            + "question. Always cite your sources using the memory IDs provided. "
            + "If the context does not contain relevant information, say so.";

    private SpectorPromptProvider() {} // static factory

    /**
     * Creates all prompt specifications for MCP server registration.
     */
    public static List<McpServerFeatures.SyncPromptSpecification> create(SpectorMemory memory) {
        return List.of(
                createRagPrompt(memory)
        );
    }

    // ─────────────── RAG Prompt ───────────────

    private static McpServerFeatures.SyncPromptSpecification createRagPrompt(SpectorMemory memory) {
        var prompt = new McpSchema.Prompt(
                "rag_with_citations",
                "RAG prompt template that retrieves relevant context from Spector cognitive memory "
                        + "and formats results with source citations for grounded responses.",
                List.of(
                        new McpSchema.PromptArgument("query",
                                "The question or topic to search for", true),
                        new McpSchema.PromptArgument("top_k",
                                "Number of memories to retrieve (default: 5)", false)
                )
        );

        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, request) -> {
            String query = extractStringArg(request.arguments(), "query", "");
            int topK = extractIntArg(request.arguments(), "top_k", 5);

            String contextText = retrieveContext(memory, query, topK);

            String message = RAG_SYSTEM_INSTRUCTION + "\n\n"
                    + "--- RETRIEVED CONTEXT ---\n" + contextText + "\n--- END CONTEXT ---"
                    + "\n\nQuestion: " + query;

            return new McpSchema.GetPromptResult(
                    "RAG query with citations from Spector",
                    List.of(new McpSchema.PromptMessage(
                            McpSchema.Role.USER,
                            new McpSchema.TextContent(message)
                    ))
            );
        });
    }

    // ─────────────── Internal Helpers ───────────────

    private static String retrieveContext(SpectorMemory memory, String query, int topK) {
        if (memory == null) {
            return "[SpectorMemory not available]";
        }
        try {
            var options = com.spectrayan.spector.memory.model.RecallOptions.builder()
                    .topK(topK)
                    .build();
            List<CognitiveResult> results = memory.recall(query, options);
            return ResultFormatter.formatMemoryResults(results);
        } catch (Exception e) {
            return "[Recall failed: " + e.getMessage() + "]";
        }
    }

    private static String extractStringArg(Map<String, Object> args, String key,
                                            String defaultValue) {
        if (args == null) return defaultValue;
        Object val = args.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private static int extractIntArg(Map<String, Object> args, String key, int defaultValue) {
        if (args == null) return defaultValue;
        Object val = args.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
