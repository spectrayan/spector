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
import java.util.Map;
import java.util.function.Supplier;

import com.spectrayan.spector.memory.SpectorMemory;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_fact_assert} — asserts a bitemporal fact into the memory graph.
 */
public final class MemoryFactAssertTool extends MemoryToolHandler {

    public static final String NAME = "memory_fact_assert";

    public MemoryFactAssertTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    public MemoryFactAssertTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                     Map<String, Object> args) throws Exception {
        String subject = requireString(args, "subject");
        String predicate = requireString(args, "predicate");
        String object = requireString(args, "object");
        long validFrom = optionalLong(args, "valid_from", Instant.now().getEpochSecond());
        long validTo = optionalLong(args, "valid_to", Long.MAX_VALUE);
        float confidence = optionalFloat(args, "confidence", 1.0f);
        boolean allowCoexisting = optionalBoolean(args, "allow_coexisting", false);

        long factId = memory.assertFact(subject, predicate, object, validFrom, validTo, confidence, allowCoexisting);

        return textResult("Fact asserted successfully. Fact ID: " + factId);
    }
}
