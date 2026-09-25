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

import java.util.Map;
import java.util.function.Supplier;

import com.spectrayan.spector.memory.SpectorMemory;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_fact_retract} — retracts a previously asserted bitemporal fact.
 */
public final class MemoryFactRetractTool extends MemoryToolHandler {

    public static final String NAME = "memory_fact_retract";

    public MemoryFactRetractTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    public MemoryFactRetractTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                     Map<String, Object> args) throws Exception {
        int factId = requireInt(args, "fact_id");

        long retractionId = memory.retractFact(factId);

        return textResult("Fact " + factId + " retracted successfully. Retraction Record ID: " + retractionId);
    }
}
