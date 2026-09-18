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
package com.spectrayan.spector.synapse.security.toolaccess;

import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.agent.graph.CognitiveState;
import com.spectrayan.spector.synapse.agent.graph.nodes.ToolExecutionNode;
import com.spectrayan.spector.synapse.security.toolaccess.ToolAccessPolicy.AgentRule;
import com.spectrayan.spector.synapse.security.toolaccess.ToolAccessPolicy.DefaultMode;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;

import io.modelcontextprotocol.spec.McpSchema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tool access enforcement (list + execute)")
class ToolAccessEnforcementTest {

    private ToolRegistry registry;
    private ToolAccessPolicy policy;

    @BeforeEach
    void setUp() {
        McpToolHandler web = stub("web_search", false);
        McpToolHandler memory = stub("memory_recall", false);
        McpToolHandler calc = stub("calculator", false);
        McpToolHandler shell = stub("shell_execution", true);

        policy = ToolAccessPolicy.of(
                DefaultMode.DENY_ALL,
                Map.of("researcher", new AgentRule(List.of("web_search", "memory_recall"), List.of())),
                true);
        registry = new ToolRegistry(List.of(web, memory, calc, shell), policy);
    }

    @Test
    @DisplayName("resolveToolSpecs returns only policy allowlist of 2 tools")
    void listOnlyAllowlistedTools() {
        AgentSoul soul = AgentSoul.builder()
                .id("researcher")
                .name("Researcher")
                .tools(List.of())
                .build();

        List<ToolSpecification> specs = registry.resolveToolSpecs(soul);
        assertThat(specs).extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("web_search", "memory_recall");
    }

    @Test
    @DisplayName("resolveToolSpecs intersects policy with soul.tools hint")
    void listIntersectsSoulTools() {
        AgentSoul soul = AgentSoul.builder()
                .id("researcher")
                .name("Researcher")
                .tools(List.of("web_search", "calculator"))
                .build();

        List<ToolSpecification> specs = registry.resolveToolSpecs(soul);
        assertThat(specs).extracting(ToolSpecification::name)
                .containsExactly("web_search");
    }

    @Test
    @DisplayName("executeTool denies tools outside allowlist with SPE-500-013")
    void executeDeniedTool() {
        ToolExecutionRequest req = ToolExecutionRequest.builder()
                .id("1")
                .name("shell_execution")
                .arguments("{}")
                .build();

        String result = registry.executeTool(req, "researcher", List.of());
        assertThat(result).contains("SPE-500-013").contains("Permission denied");
    }

    @Test
    @DisplayName("executeTool allows tools on the allowlist")
    void executeAllowedTool() {
        ToolExecutionRequest req = ToolExecutionRequest.builder()
                .id("1")
                .name("web_search")
                .arguments("{\"q\":\"x\"}")
                .build();

        String result = registry.executeTool(req, "researcher", List.of());
        assertThat(result).isEqualTo("ok:web_search");
    }

    @Test
    @DisplayName("ToolExecutionNode returns permission error for denied tool")
    void toolExecutionNodeDenies() {
        ToolExecutionNode node = new ToolExecutionNode(registry, null, null, null, List.of());
        CognitiveState state = new CognitiveState(Map.of(
                "tool_calls", List.of("shell_execution({})"),
                "acting_soul_id", "researcher"
        ));

        Map<String, Object> out = node.apply(state);
        @SuppressWarnings("unchecked")
        List<String> results = (List<String>) out.get("tool_results");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).contains("SPE-500-013").contains("Permission denied");
    }

    @Test
    @DisplayName("no policy entry with deny-all default yields empty tool list")
    void denyAllDefaultWhenNoEntry() {
        AgentSoul soul = AgentSoul.builder()
                .id("unknown-agent")
                .name("Unknown")
                .tools(List.of())
                .build();
        assertThat(registry.resolveToolSpecs(soul)).isEmpty();
    }

    private static McpToolHandler stub(String name, boolean write) {
        return new McpToolHandler() {
            @Override public String name() { return name; }
            @Override public String description() { return name; }
            @Override public Map<String, Object> inputSchema() { return Map.of(); }
            @Override public McpToolCategory category() { return McpToolCategory.GENERAL; }
            @Override public boolean isWriteTool() { return write; }
            @Override public McpSchema.CallToolResult execute(Map<String, Object> args) {
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("ok:" + name)), false, null, null);
            }
        };
    }
}
