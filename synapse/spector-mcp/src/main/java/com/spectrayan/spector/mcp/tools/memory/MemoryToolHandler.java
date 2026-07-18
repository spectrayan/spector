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
import java.util.Set;
import java.util.function.Supplier;

import com.spectrayan.spector.engine.SpectorEngine;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.mcp.tools.McpToolHandler;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Base handler for memory-aware MCP tools.
 *
 * <p>Memory tools need both the {@link SpectorEngine} (for embedding) and
 * {@link SpectorMemory} (for cognitive operations). Subclasses implement
 * {@link #executeMemory(SpectorMemory, SpectorEngine, Map)} instead of
 * the standard {@code execute()} method.</p>
 *
 * <h3>Memory Resolution</h3>
 * <p>The memory instance is resolved per-request via a {@code Supplier<SpectorMemory>}
 * (the "memory resolver"). In standalone/OSS mode, this returns the single shared
 * memory instance. In enterprise mode, the supplier reads {@code AuthContextHolder}
 * and routes to the authenticated user's tenant-isolated memory workspace.</p>
 */
public abstract class MemoryToolHandler extends McpToolHandler {

    private final Supplier<SpectorMemory> memoryResolver;

    /**
     * Constructs a handler with a fixed memory instance (standalone/OSS mode).
     *
     * @param memory the cognitive memory instance (may be null if not configured)
     */
    protected MemoryToolHandler(SpectorMemory memory) {
        this.memoryResolver = memory != null ? () -> memory : () -> null;
    }

    /**
     * Constructs a handler with a per-request memory resolver (enterprise mode).
     *
     * <p>The resolver is invoked on every tool call, allowing the enterprise layer
     * to route to the authenticated user's tenant-scoped memory workspace via
     * {@code AuthContextHolder}.</p>
     *
     * @param memoryResolver supplier that resolves the active memory per request
     */
    protected MemoryToolHandler(Supplier<SpectorMemory> memoryResolver) {
        this.memoryResolver = memoryResolver != null ? memoryResolver : () -> null;
    }

    /**
     * Executes the memory tool logic.
     *
     * @param memory the cognitive memory instance
     * @param engine the search engine (for embedding provider access)
     * @param args   the parsed MCP request arguments
     * @return the tool result
     */
    protected abstract McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                               SpectorEngine engine,
                                                               Map<String, Object> args) throws Exception;

    @Override
    public final McpSchema.CallToolResult execute(SpectorEngine engine,
                                                    Map<String, Object> args) throws Exception {
        SpectorMemory memory = memoryResolver.get();
        if (memory == null) {
            return errorResult("SpectorMemory is not configured. Start the server with --memory-enabled.");
        }
        return executeMemory(memory, engine, args);
    }

    /**
     * Extracts an optional float argument.
     */
    protected static float optionalFloat(Map<String, Object> args, String key, float defaultValue) {
        Object val = args.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number n) return n.floatValue();
        try {
            return Float.parseFloat(val.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Extracts an optional byte argument.
     */
    protected static byte optionalByte(Map<String, Object> args, String key, byte defaultValue) {
        Object val = args.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number n) return n.byteValue();
        try {
            return Byte.parseByte(val.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Extracts an optional string array argument (comma-separated).
     */
    protected static String[] optionalTags(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val == null) return new String[0];
        String str = val.toString().trim();
        if (str.isEmpty()) return new String[0];
        return str.split("\\s*,\\s*");
    }
}
