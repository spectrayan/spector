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

import com.spectrayan.spector.mcp.util.McpTemplateEngine;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.metamemory.MemoryInsight;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_introspect} — metamemory confidence/gaps analysis.
 *
 * <p>Lets the agent reason about what it knows and doesn't know.
 * Instead of hallucinating, the agent can say: "I don't have strong
 * memories about Kubernetes RBAC — let me ask you about that."</p>
 */
public final class MemoryIntrospectTool extends MemoryToolHandler {

    public static final String NAME = "memory_introspect";

    public MemoryIntrospectTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public MemoryIntrospectTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                       Map<String, Object> args) throws Exception {
        String topic = requireString(args, "topic");
        MemoryInsight insight = memory.introspect(topic);

        var model = Map.of(
                "topic", topic,
                "insight", insight
        );

        return textResult(McpTemplateEngine.render("memory-introspect", model));
    }
}
