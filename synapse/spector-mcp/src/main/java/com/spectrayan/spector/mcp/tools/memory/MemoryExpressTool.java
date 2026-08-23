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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.commons.security.SpectorScopes;
import com.spectrayan.spector.mcp.schema.ToolSchemaBuilder;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;
import com.spectrayan.spector.memory.express.relay.ExpressReport;
import com.spectrayan.spector.memory.express.relay.ExpressSignal;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_express} — multi-modal expressive synthesis compiling
 * idiolect prompt directives, affective vocal prosody, and ARKit blendshape weights.
 */
public final class MemoryExpressTool extends MemoryToolHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public MemoryExpressTool(SpectorMemory memory) {
        super(memory);
    }

    public MemoryExpressTool(Supplier<SpectorMemory> memoryResolver) {
        super(memoryResolver);
    }

    @Override
    public String name() {
        return "memory_express";
    }

    @Override
    public Set<String> requiredScopes() {
        return Set.of(SpectorScopes.MEMORY_READ);
    }

    @Override
    public String description() {
        return "Multi-modal expressive persona synthesis compiling idiolect prompt directives, "
                + "real-time affective vocal prosody (SSML), ARKit 52 facial blendshapes, "
                + "and introspective monologues for sovereign digital persona interaction.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemaBuilder.object()
                .requiredString("query", "User query or stimulus for expressive synthesis")
                .optionalString("userId", "Target user ID for persona resolution", null)
                .optionalInt("limit", "Maximum candidate memories to recall", 5)
                .build();
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory, Map<String, Object> args) throws Exception {
        String query = (String) args.get("query");
        int limit = args.get("limit") instanceof Number n ? n.intValue() : 5;

        List<CognitiveResult> candidates = memory.recall(query != null ? query : "", RecallOptions.builder().topK(limit).build());

        ExpressSignal signal = ExpressSignal.forQuery(query, InteroceptiveState.NEUTRAL, null)
                .candidates(candidates)
                .build();

        ExpressReport report = memory.express(signal);

        Map<String, Object> response = new HashMap<>();
        response.put("query", query != null ? query : "");
        response.put("promptDirectives", report.promptDirectives() != null ? report.promptDirectives() : "");
        response.put("internalMonologue", report.internalMonologue() != null ? report.internalMonologue() : "");
        response.put("ssmlTags", report.ssmlTags() != null ? report.ssmlTags() : "");
        if (report.prosodyVector() != null) {
            response.put("prosodyVector", report.prosodyVector());
        }
        if (report.blendshapeVector() != null) {
            response.put("blendshapeVector", report.blendshapeVector());
        }
        response.put("elapsedMillis", report.elapsed() != null ? report.elapsed().toMillis() : 0L);

        return textResult(MAPPER.writeValueAsString(response));
    }
}
