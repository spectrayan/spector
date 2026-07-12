/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.agent.tools;

import com.spectrayan.spector.synapse.agent.AgentSoul;
import com.spectrayan.spector.synapse.agent.AgentTool;
import com.spectrayan.spector.synapse.agent.AgentTool.ToolCategory;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Agent tool that reads the current agent soul identity.
 */
@Component
public class ReadIdentityTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(ReadIdentityTool.class);

    private final CognitiveSoulService soulService;

    public ReadIdentityTool(CognitiveSoulService soulService) {
        this.soulService = soulService;
    }

    @Override public String name() { return "read_identity"; }

    @Override public String description() {
        return "Reads the current agent soul identity. Use when the user asks 'who are you?' "
                + "or before proposing identity updates.";
    }

    @Override public ToolCategory category() { return ToolCategory.MEMORY; }
    @Override public boolean isWriteTool() { return false; }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "target", Map.of("type", "string",
                                "description", "'agent' to read agent soul identity")
                ),
                "required", List.of("target")
        );
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        try {
            AgentSoul soul = soulService.getEffectiveSoul(null);
            return String.format("""
                Name: %s
                Description: %s
                Purpose: %s
                Personality: %s
                Expertise: %s
                """, 
                soul.name(), soul.description(), soul.purpose(), 
                soul.personality(), String.join(", ", soul.expertiseDomains()));
        } catch (Exception e) {
            log.warn("[ReadIdentityTool] Failed to read identity: {}", e.getMessage());
            return "Error reading agent identity: " + e.getMessage();
        }
    }
}
