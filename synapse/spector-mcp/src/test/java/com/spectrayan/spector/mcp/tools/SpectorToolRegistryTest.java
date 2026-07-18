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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.runtime.SpectorRuntime;
import com.spectrayan.spector.mcp.schema.ToolSchemaBuilder;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Unit tests for the refactored Spector MCP tool system.
 */
class SpectorToolRegistryTest {

    private static final String TEST_VERSION = "0.1.0-test";

    private static SpectorMemory memory;
    private static SpectorRuntime runtime;
    private static List<McpServerFeatures.SyncToolSpecification> specs;

    @BeforeAll
    static void setUp() {
        memory = mock(SpectorMemory.class);
        runtime = mock(SpectorRuntime.class);
        org.mockito.Mockito.when(runtime.memory()).thenReturn(java.util.Optional.of(memory));

        specs = SpectorToolRegistry.createAll(runtime, TEST_VERSION);
    }

    @Test
    void shouldRegister16Tools() {
        assertThat(specs).hasSize(16);
    }

    @Test
    void shouldHaveCorrectToolNames() {
        var names = specs.stream()
                .map(t -> t.tool().name())
                .toList();
        assertThat(names).contains(
                "memory_remember",
                "memory_scratchpad",
                "memory_recall",
                "memory_reinforce",
                "memory_forget",
                "memory_status",
                "memory_introspect",
                "memory_suppress",
                "memory_resolve",
                "memory_reminder",
                "memory_why_not",
                "memory_compute_importance",
                "memory_inspect",
                "memory_export",
                "memory_browse",
                "memory_salience"
        );
    }

    @Test
    void allToolsShouldHaveDescriptions() {
        for (var spec : specs) {
            assertThat(spec.tool().description())
                    .as("Description for tool: %s", spec.tool().name())
                    .isNotBlank();
        }
    }

    @Test
    void allToolsShouldHaveInputSchemas() {
        for (var spec : specs) {
            assertThat(spec.tool().inputSchema())
                    .as("Input schema for tool: %s", spec.tool().name())
                    .isNotNull()
                    .containsKey("type");
        }
    }

    @Test
    void schemaBuilderShouldProduceValidSchema() {
        var schema = ToolSchemaBuilder.object()
                .requiredString("name", "The name")
                .optionalInt("count", "Number of items", 10)
                .optionalBoolean("verbose", "Verbose output", false)
                .optionalEnum("format", "Output format", "json", "json", "text", "csv")
                .build();

        assertThat(schema).containsEntry("type", "object");
        assertThat(schema).containsKey("properties");
        assertThat(schema).containsKey("required");

        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKeys("name", "count", "verbose", "format");

        @SuppressWarnings("unchecked")
        var required = (List<String>) schema.get("required");
        assertThat(required).containsExactly("name");
    }

    @Test
    void emptySchemaIsValid() {
        var schema = ToolSchemaBuilder.empty();
        assertThat(schema).containsEntry("type", "object");
        assertThat(schema).containsKey("properties");
    }
}
