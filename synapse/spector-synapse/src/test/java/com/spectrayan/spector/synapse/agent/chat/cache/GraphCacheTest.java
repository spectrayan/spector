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
package com.spectrayan.spector.synapse.agent.chat.cache;

import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("GraphCache Compilation Caching & Fingerprint Invalidation Tests")
class GraphCacheTest {

    private GraphCache graphCache;
    private ToolRegistry toolRegistry;
    private AgentSoul soulV1;
    private AgentSoul soulV2;

    @BeforeEach
    void setUp() {
        graphCache = new GraphCache();
        toolRegistry = new ToolRegistry(List.of());

        soulV1 = AgentSoul.builder()
                .id("test-agent")
                .name("Test Agent")
                .model("qwen3.5:latest")
                .soulVersion((short) 1)
                .build();

        soulV2 = AgentSoul.builder()
                .id("test-agent")
                .name("Test Agent")
                .model("qwen3.5:latest")
                .soulVersion((short) 2)
                .build();
    }

    @Test
    @DisplayName("Should cache CompiledGraph on miss and return cached instance on hit")
    @SuppressWarnings("unchecked")
    void testCacheHitAndMiss() {
        CompiledGraph<AgentState> graphInstance = mock(CompiledGraph.class);
        AtomicInteger compileCount = new AtomicInteger(0);

        CompiledGraph<AgentState> first = graphCache.getOrCompile(soulV1, toolRegistry, () -> {
            compileCount.incrementAndGet();
            return graphInstance;
        });

        assertThat(first).isSameAs(graphInstance);
        assertThat(compileCount.get()).isEqualTo(1);
        assertThat(graphCache.missCount()).isEqualTo(1);
        assertThat(graphCache.hitCount()).isEqualTo(0);

        // Second call should hit the cache
        CompiledGraph<AgentState> second = graphCache.getOrCompile(soulV1, toolRegistry, () -> {
            compileCount.incrementAndGet();
            return mock(CompiledGraph.class);
        });

        assertThat(second).isSameAs(graphInstance);
        assertThat(compileCount.get()).isEqualTo(1); // Compiler not called again
        assertThat(graphCache.hitCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should invalidate cache when soul version changes")
    @SuppressWarnings("unchecked")
    void testInvalidationOnSoulVersionChange() {
        CompiledGraph<AgentState> graph1 = mock(CompiledGraph.class);
        CompiledGraph<AgentState> graph2 = mock(CompiledGraph.class);

        graphCache.getOrCompile(soulV1, toolRegistry, () -> graph1);
        CompiledGraph<AgentState> second = graphCache.getOrCompile(soulV2, toolRegistry, () -> graph2);

        assertThat(second).isSameAs(graph2);
        assertThat(graphCache.missCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should invalidate cache when tool catalog fingerprint changes")
    @SuppressWarnings("unchecked")
    void testInvalidationOnToolRegistryFingerprintChange() {
        CompiledGraph<AgentState> graphBefore = mock(CompiledGraph.class);
        CompiledGraph<AgentState> graphAfter = mock(CompiledGraph.class);

        String fpBefore = toolRegistry.fingerprint();
        assertThat(fpBefore).isNotNull();

        graphCache.getOrCompile(soulV1, toolRegistry, () -> graphBefore);

        // Register a new tool dynamically
        McpToolHandler dynamicTool = new McpToolHandler() {
            @Override public String name() { return "weather_search"; }
            @Override public String description() { return "Search weather data"; }
            @Override public McpToolCategory category() { return McpToolCategory.GENERAL; }
            @Override public Map<String, Object> inputSchema() {
                return Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string")));
            }
            @Override public McpSchema.CallToolResult execute(Map<String, Object> arguments) {
                return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("Sunny")), false, null, null);
            }
        };

        toolRegistry.register(dynamicTool);
        String fpAfter = toolRegistry.fingerprint();
        assertThat(fpAfter).isNotEqualTo(fpBefore);

        // Subsequent getOrCompile must detect new fingerprint and recompile
        CompiledGraph<AgentState> result = graphCache.getOrCompile(soulV1, toolRegistry, () -> graphAfter);
        assertThat(result).isSameAs(graphAfter);
        assertThat(graphCache.missCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should invalidate entries by soul ID")
    @SuppressWarnings("unchecked")
    void testInvalidateSoul() {
        CompiledGraph<AgentState> graph = mock(CompiledGraph.class);
        graphCache.getOrCompile(soulV1, toolRegistry, () -> graph);
        assertThat(graphCache.size()).isEqualTo(1);

        graphCache.invalidateSoul("test-agent");
        assertThat(graphCache.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should clear all entries on invalidateAll")
    @SuppressWarnings("unchecked")
    void testInvalidateAll() {
        CompiledGraph<AgentState> graph1 = mock(CompiledGraph.class);
        CompiledGraph<AgentState> graph2 = mock(CompiledGraph.class);

        graphCache.getOrCompile(soulV1, toolRegistry, () -> graph1);
        graphCache.getOrCompile(soulV2, toolRegistry, () -> graph2);
        assertThat(graphCache.size()).isEqualTo(2);

        graphCache.invalidateAll();
        assertThat(graphCache.size()).isEqualTo(0);
    }
}
