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
package com.spectrayan.spector.synapse.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for all available agent tools.
 *
 * <p>Tools are auto-discovered via Spring component scan. Any bean implementing
 * {@link AgentTool} is automatically registered at startup.</p>
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final ConcurrentHashMap<String, AgentTool> tools = new ConcurrentHashMap<>();

    /**
     * Auto-register all AgentTool beans found by Spring.
     */
    public ToolRegistry(List<AgentTool> toolBeans) {
        for (AgentTool tool : toolBeans) {
            tools.put(tool.name(), tool);
            log.info("[ToolRegistry] Registered tool: {} — {}", tool.name(), tool.description());
        }
        log.info("[ToolRegistry] {} tools registered", tools.size());
    }

    /** Get a tool by name. */
    public Optional<AgentTool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /** Get all registered tool names. */
    public Set<String> names() {
        return Collections.unmodifiableSet(tools.keySet());
    }

    /** Get all registered tools. */
    public Map<String, AgentTool> all() {
        return Collections.unmodifiableMap(tools);
    }

    /** Register a tool dynamically. */
    public void register(AgentTool tool) {
        tools.put(tool.name(), tool);
        log.info("[ToolRegistry] Dynamically registered tool: {}", tool.name());
    }

    /** Check if a tool is registered. */
    public boolean has(String name) {
        return tools.containsKey(name);
    }

    /** Get tools matching a list of names (for agent soul filtering). */
    public List<AgentTool> forNames(List<String> names) {
        return names.stream()
                .map(tools::get)
                .filter(t -> t != null)
                .toList();
    }
}
