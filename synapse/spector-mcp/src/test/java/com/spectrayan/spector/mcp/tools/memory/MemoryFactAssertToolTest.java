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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.memory.SpectorMemory;

import io.modelcontextprotocol.spec.McpSchema;

class MemoryFactAssertToolTest {

    private SpectorMemory memory;
    private MemoryFactAssertTool tool;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        tool = new MemoryFactAssertTool(memory);
    }

    @Test
    void testExecuteMemory() throws Exception {
        when(memory.assertFact(eq("Sky"), eq("color"), eq("Blue"), anyLong(), anyLong(), anyFloat(), anyBoolean()))
                .thenReturn(42);

        Map<String, Object> args = Map.of(
                "subject", "Sky",
                "predicate", "color",
                "object", "Blue"
        );

        McpSchema.CallToolResult result = tool.executeMemory(memory, args);

        assertTrue(((io.modelcontextprotocol.spec.McpSchema.TextContent) result.content().get(0)).text().contains("42"));
        verify(memory).assertFact(eq("Sky"), eq("color"), eq("Blue"), anyLong(), anyLong(), anyFloat(), anyBoolean());
    }
}
