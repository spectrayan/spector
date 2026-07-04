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
package com.spectrayan.spector.synapse.agent.tools;

import com.spectrayan.spector.synapse.agent.AgentTool;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreResponse;
import com.spectrayan.spector.synapse.memory.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Memory remember tool — agents can store new memories in the Spector engine.
 */
@Component
public class MemoryRememberTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(MemoryRememberTool.class);
    private final MemoryService memoryService;

    public MemoryRememberTool(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public String name() { return "memory_remember"; }

    @Override
    public String description() {
        return "Store a new memory in the cognitive memory engine for future recall.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "text", Map.of("type", "string", "description", "Text content to memorize"),
                        "tags", Map.of("type", "array", "description", "Optional tags",
                                "items", Map.of("type", "string")),
                        "importance", Map.of("type", "number", "description", "Importance weight 0.0-1.0")
                ),
                "required", List.of("text")
        );
    }

    @Override
    public boolean isWriteTool() {
        return true;
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.MEMORY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(Map<String, Object> arguments) {
        String text = (String) arguments.get("text");
        List<String> tags = arguments.containsKey("tags") ? (List<String>) arguments.get("tags") : List.of();
        Double importance = arguments.containsKey("importance")
                ? ((Number) arguments.get("importance")).doubleValue() : null;

        StoreResponse response = memoryService.store(new StoreRequest(text, tags, importance, Map.of()));
        return "Memory stored (id=" + response.id() + ", tier=" + response.tier() + "): " + response.message();
    }
}
