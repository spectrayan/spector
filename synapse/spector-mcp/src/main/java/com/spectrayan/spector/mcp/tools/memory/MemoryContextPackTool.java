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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.spectrayan.spector.commons.security.SpectorScopes;
import com.spectrayan.spector.mcp.schema.ToolSchemaBuilder;
import com.spectrayan.spector.mcp.util.ContextPackFormatter;
import com.spectrayan.spector.mcp.util.ContextPackFormatter.ContextPackInput;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.FactHistory;
import com.spectrayan.spector.memory.model.RecallOptions;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_context_pack} — sub-millisecond hierarchical memory context pack assembly.
 *
 * <p>Fuses Working, Procedural, Semantic, Episodic, and Graph memory into a structured,
 * token-budgeted bundle for direct injection into LLM system prompts.</p>
 */
public final class MemoryContextPackTool extends MemoryToolHandler {

    public static final String NAME = "memory_context_pack";

    public MemoryContextPackTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    public MemoryContextPackTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                     Map<String, Object> args) throws Exception {
        String query = requireString(args, "query");
        int tokenBudget = optionalInt(args, "token_budget", 3000);
        String profileStr = optionalString(args, "profile", "BALANCED");
        String personaId = optionalString(args, "persona_id", "");
        String asOf = optionalString(args, "as_of", "");

        // Build recall options
        int candidateTopK = Math.max(10, Math.min(50, tokenBudget / 100));
        var builder = RecallOptions.builder().topK(candidateTopK);

        CognitiveProfile profile = RecallOptions.parseProfile(profileStr);
        if (profile != null) {
            builder.profile(profile);
        }

        if (!asOf.isBlank()) {
            try {
                long maxTs = Instant.parse(asOf.strip()).toEpochMilli();
                builder.maxTimestamp(maxTs);
            } catch (DateTimeParseException ignored) {
                // Ignore invalid timestamp
            }
        }

        RecallOptions options = builder.build();
        List<CognitiveResult> results = memory.recall(query, options);

        // Check if query mentions entities for fact histories
        List<FactHistory> factHistories = new ArrayList<>();
        String[] queryWords = query.split("\\s+");
        for (String word : queryWords) {
            String clean = word.replaceAll("[^a-zA-Z0-9_]", "");
            if (clean.length() >= 3 && Character.isUpperCase(clean.charAt(0))) {
                try {
                    FactHistory fh = memory.factHistory(clean, "*");
                    if (fh != null && (fh.activeFact() != null || !fh.supersededFacts().isEmpty())) {
                        factHistories.add(fh);
                    }
                } catch (Exception ignored) {
                    // Ignore non-matching entity lookups
                }
            }
        }

        ContextPackInput input = new ContextPackInput(
                query,
                null,
                results,
                factHistories,
                tokenBudget,
                profileStr,
                personaId
        );

        String contextPack = ContextPackFormatter.format(input);
        return textResult(contextPack);
    }
}
