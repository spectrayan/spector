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
package com.spectrayan.spector.mcp;

import com.spectrayan.spector.mcp.spec.McpToolSpec;
import com.spectrayan.spector.mcp.spec.McpToolSpecLoader;
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.mcp.tools.SpectorToolRegistry;
import com.spectrayan.spector.memory.SpectorMemory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Validation test suite for declarative MCP tool definitions (ADR-001).
 * Ensures all registered tools map 1:1 to compliant JSON schemas on the classpath.
 */
@DisplayName("MCP Declarative Tool Schema Validation (ADR-001)")
class McpToolSchemaValidationTest {

    private static final Set<String> VALID_SCHEMA_TYPES = Set.of(
            "string", "number", "integer", "boolean", "array", "object"
    );

    static Stream<McpToolHandler> allRegisteredTools() {
        SpectorMemory mockMemory = mock(SpectorMemory.class);
        return SpectorToolRegistry.handlers("0.1.0-alpha", mockMemory).stream();
    }

    @ParameterizedTest(name = "Tool: {0}")
    @MethodSource("allRegisteredTools")
    @DisplayName("Every registered MCP tool must have a valid declarative JSON specification")
    void toolSpec_isValidAndCompliant(McpToolHandler handler) {
        String toolName = handler.name();
        assertThat(toolName).isNotBlank();

        McpToolSpec spec = McpToolSpecLoader.load(toolName);
        assertThat(spec).isNotNull();
        assertThat(spec.name()).isEqualTo(toolName);
        assertThat(spec.description()).isNotBlank();
        assertThat(spec.category()).isNotBlank();
        assertThat(spec.scopes()).isNotEmpty();

        // Validate handler delegating to spec
        assertThat(handler.description()).isEqualTo(spec.description());
        assertThat(handler.inputSchema()).isEqualTo(spec.inputSchema());
        assertThat(handler.requiredScopes()).isEqualTo(spec.scopes());

        // Validate JSON Schema structure
        Map<String, Object> inputSchema = spec.inputSchema();
        assertThat(inputSchema).containsKey("type");
        assertThat(inputSchema.get("type")).isEqualTo("object");

        if (inputSchema.containsKey("properties")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
            assertThat(properties).isNotNull();

            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String propName = entry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
                assertThat(propDef)
                        .as("Property '%s' in tool '%s' must declare a 'type'", propName, toolName)
                        .containsKey("type");

                String type = (String) propDef.get("type");
                assertThat(VALID_SCHEMA_TYPES)
                        .as("Property '%s' in tool '%s' must have valid JSON schema type", propName, toolName)
                        .contains(type);

                assertThat(propDef)
                        .as("Property '%s' in tool '%s' must have a description", propName, toolName)
                        .containsKey("description");
            }

            if (inputSchema.containsKey("required")) {
                @SuppressWarnings("unchecked")
                List<String> required = (List<String>) inputSchema.get("required");
                for (String req : required) {
                    assertThat(properties)
                            .as("Required parameter '%s' in tool '%s' must be defined in properties", req, toolName)
                            .containsKey(req);
                }
            }
        }
    }

    @Test
    @DisplayName("Spec loader caches specifications on repeated calls")
    void loader_cachesSpecs() {
        McpToolSpec spec1 = McpToolSpecLoader.load("memory_remember");
        McpToolSpec spec2 = McpToolSpecLoader.load("memory_remember");
        assertThat(spec1).isSameAs(spec2);
    }

    @Test
    @DisplayName("Spec loader throws IllegalArgumentException on missing spec")
    void loader_throwsOnMissing() {
        assertThatThrownBy(() -> McpToolSpecLoader.load("non_existent_tool_xyz"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MCP tool specification not found for 'non_existent_tool_xyz'");
    }
}
