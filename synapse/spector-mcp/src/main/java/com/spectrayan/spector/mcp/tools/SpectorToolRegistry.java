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
package com.spectrayan.spector.mcp.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.runtime.SpectorRuntime;

import com.spectrayan.spector.mcp.tools.memory.MemoryRememberTool;
import com.spectrayan.spector.mcp.tools.memory.MemoryScratchpadTool;
import com.spectrayan.spector.mcp.tools.memory.MemoryRecallTool;
import com.spectrayan.spector.mcp.tools.memory.MemoryReinforceTool;
import com.spectrayan.spector.mcp.tools.memory.MemoryForgetTool;
import com.spectrayan.spector.mcp.tools.memory.MemoryStatusTool;
import com.spectrayan.spector.mcp.tools.memory.MemoryIntrospectTool;
import com.spectrayan.spector.mcp.tools.memory.MemorySuppressTool;
import com.spectrayan.spector.mcp.tools.memory.MemoryResolveTool;
import com.spectrayan.spector.mcp.tools.memory.MemoryReminderTool;
import com.spectrayan.spector.mcp.tools.memory.MemoryWhyNotTool;
import com.spectrayan.spector.mcp.tools.memory.MemoryComputeImportanceTool;
import com.spectrayan.spector.mcp.tools.memory.MemoryInspectTool;
import com.spectrayan.spector.mcp.tools.memory.MemoryExportTool;
import com.spectrayan.spector.mcp.tools.memory.MemoryBrowseTool;
import com.spectrayan.spector.mcp.tools.memory.MemorySalienceTool;

import io.modelcontextprotocol.server.McpServerFeatures;

/**
 * Central registry for Spector MCP tool handlers.
 *
 * <p>Exposes cognitive memory tools (remember, recall, forget, etc.).</p>
 */
public final class SpectorToolRegistry {

    private SpectorToolRegistry() {} // static utility

    /**
     * Returns the list of all tool handlers registered in this server.
     *
     * @param serverVersion the server version string
     * @return unmodifiable list of tool handlers
     */
    public static List<McpToolHandler> handlers(String serverVersion) {
        return handlers(serverVersion, (SpectorMemory) null);
    }

    /**
     * Returns tool handlers including memory tools when SpectorMemory is available.
     *
     * @param serverVersion the server version string
     * @param memory        optional SpectorMemory instance
     * @return list of tool handlers
     */
    public static List<McpToolHandler> handlers(String serverVersion, SpectorMemory memory) {
        var handlers = new ArrayList<McpToolHandler>();

        if (memory != null) {
            handlers.add(new MemoryRememberTool(memory));
            handlers.add(new MemoryScratchpadTool(memory));
            handlers.add(new MemoryRecallTool(memory));
            handlers.add(new MemoryReinforceTool(memory));
            handlers.add(new MemoryForgetTool(memory));
            handlers.add(new MemoryStatusTool(memory));
            handlers.add(new MemoryIntrospectTool(memory));
            handlers.add(new MemorySuppressTool(memory));
            handlers.add(new MemoryResolveTool(memory));
            handlers.add(new MemoryReminderTool(memory));
            handlers.add(new MemoryWhyNotTool(memory));
            handlers.add(new MemoryComputeImportanceTool(memory));
            handlers.add(new MemoryInspectTool(memory));
            handlers.add(new MemoryExportTool(memory));
            handlers.add(new MemoryBrowseTool(memory));
            handlers.add(new MemorySalienceTool(memory));
        }

        return List.copyOf(handlers);
    }

    /**
     * Returns tool handlers including memory tools when SpectorMemory is available via a supplier.
     *
     * @param serverVersion  the server version string
     * @param memoryResolver per-request memory resolver for tenant isolation
     * @return list of tool handlers
     */
    public static List<McpToolHandler> handlers(String serverVersion, Supplier<SpectorMemory> memoryResolver) {
        if (memoryResolver == null) {
            return handlers(serverVersion);
        }
        var handlers = new ArrayList<McpToolHandler>();

        handlers.add(new MemoryRememberTool(memoryResolver));
        handlers.add(new MemoryScratchpadTool(memoryResolver));
        handlers.add(new MemoryRecallTool(memoryResolver));
        handlers.add(new MemoryReinforceTool(memoryResolver));
        handlers.add(new MemoryForgetTool(memoryResolver));
        handlers.add(new MemoryStatusTool(memoryResolver));
        handlers.add(new MemoryIntrospectTool(memoryResolver));
        handlers.add(new MemorySuppressTool(memoryResolver));
        handlers.add(new MemoryResolveTool(memoryResolver));
        handlers.add(new MemoryReminderTool(memoryResolver));
        handlers.add(new MemoryWhyNotTool(memoryResolver));
        handlers.add(new MemoryComputeImportanceTool(memoryResolver));
        handlers.add(new MemoryInspectTool(memoryResolver));
        handlers.add(new MemoryExportTool(memoryResolver));
        handlers.add(new MemoryBrowseTool(memoryResolver));
        handlers.add(new MemorySalienceTool(memoryResolver));

        return List.copyOf(handlers);
    }

    /**
     * Creates mode-aware tool specifications from a runtime.
     *
     * @param runtime       the Spector runtime
     * @param serverVersion the server version string
     * @return list of MCP tool specifications
     */
    public static List<McpServerFeatures.SyncToolSpecification> createAll(
            SpectorRuntime runtime, String serverVersion) {
        SpectorMemory memory = runtime.memory().orElse(null);

        var handlers = new ArrayList<McpToolHandler>();

        if (memory != null) {
            handlers.add(new MemoryRememberTool(memory));
            handlers.add(new MemoryScratchpadTool(memory));
            handlers.add(new MemoryRecallTool(memory));
            handlers.add(new MemoryReinforceTool(memory));
            handlers.add(new MemoryForgetTool(memory));
            handlers.add(new MemoryStatusTool(memory));
            handlers.add(new MemoryIntrospectTool(memory));
            handlers.add(new MemorySuppressTool(memory));
            handlers.add(new MemoryResolveTool(memory));
            handlers.add(new MemoryReminderTool(memory));
            handlers.add(new MemoryWhyNotTool(memory));
            handlers.add(new MemoryComputeImportanceTool(memory));
            handlers.add(new MemoryInspectTool(memory));
            handlers.add(new MemoryExportTool(memory));
            handlers.add(new MemoryBrowseTool(memory));
            handlers.add(new MemorySalienceTool(memory));
        }

        return handlers.stream()
                .map(handler -> handler.toToolSpecification(runtime))
                .toList();
    }

    /**
     * Creates mode-aware tool specifications with an enterprise memory resolver.
     *
     * @param runtime        the Spector runtime
     * @param serverVersion  the server version string
     * @param memoryResolver per-request memory resolver for tenant isolation
     * @return list of MCP tool specifications
     */
    public static List<McpServerFeatures.SyncToolSpecification> createAll(
            SpectorRuntime runtime, String serverVersion,
            Supplier<SpectorMemory> memoryResolver) {
        var handlers = new ArrayList<McpToolHandler>();

        if (memoryResolver != null) {
            handlers.add(new MemoryRememberTool(memoryResolver));
            handlers.add(new MemoryScratchpadTool(memoryResolver));
            handlers.add(new MemoryRecallTool(memoryResolver));
            handlers.add(new MemoryReinforceTool(memoryResolver));
            handlers.add(new MemoryForgetTool(memoryResolver));
            handlers.add(new MemoryStatusTool(memoryResolver));
            handlers.add(new MemoryIntrospectTool(memoryResolver));
            handlers.add(new MemorySuppressTool(memoryResolver));
            handlers.add(new MemoryResolveTool(memoryResolver));
            handlers.add(new MemoryReminderTool(memoryResolver));
            handlers.add(new MemoryWhyNotTool(memoryResolver));
            handlers.add(new MemoryComputeImportanceTool(memoryResolver));
            handlers.add(new MemoryInspectTool(memoryResolver));
            handlers.add(new MemoryExportTool(memoryResolver));
            handlers.add(new MemoryBrowseTool(memoryResolver));
            handlers.add(new MemorySalienceTool(memoryResolver));
        }

        return handlers.stream()
                .map(handler -> handler.toToolSpecification(runtime))
                .toList();
    }
}
