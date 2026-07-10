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
package com.spectrayan.spector.synapse.agent;

import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Agent management REST API.
 *
 * <p>CRUD operations for agent souls, tool listing, and agent execution.</p>
 */
@RestController
@RequestMapping({"/api/v1/agents", "/api/v1/agent"})
public class AgentController {

    private final CognitiveSoulService soulService;
    private final ToolRegistry toolRegistry;

    public AgentController(CognitiveSoulService soulService, ToolRegistry toolRegistry) {
        this.soulService = soulService;
        this.toolRegistry = toolRegistry;
    }

    /** Get the current active agent soul. */
    @GetMapping("/soul")
    public ResponseEntity<AgentSoul> getSoul() {
        return soulService.loadAgentSoul(null)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    // Return a default/empty soul so UI doesn't crash or fail to load
                    AgentSoul defaultSoul = AgentSoul.builder()
                            .id("default")
                            .name("Assistant")
                            .description("Default Assistant")
                            .systemPrompt("You are a helpful AI assistant.")
                            .purpose("Help users")
                            .personality("Friendly and helpful")
                            .expertiseDomains(List.of())
                            .coreValues(List.of())
                            .ethicalGuardrails(List.of())
                            .emotionalBaseline(AgentSoul.EmotionalBaseline.NEUTRAL)
                            .communicationStyle("professional")
                            .model("qwen3.5:latest")
                            .tools(List.of())
                            .build();
                    return ResponseEntity.ok(defaultSoul);
                });
    }

    /** Create/Update the active agent soul. */
    @PutMapping("/soul")
    public ResponseEntity<AgentSoul> updateSoul(@RequestBody AgentSoul soul) {
        AgentSoul updated = AgentSoul.builder()
                .id(soul.id() != null ? soul.id() : "default")
                .name(soul.name())
                .description(soul.description())
                .systemPrompt(soul.systemPrompt())
                .purpose(soul.purpose())
                .personality(soul.personality())
                .expertiseDomains(soul.expertiseDomains())
                .coreValues(soul.coreValues())
                .ethicalGuardrails(soul.ethicalGuardrails())
                .emotionalBaseline(soul.emotionalBaseline())
                .communicationStyle(soul.communicationStyle())
                .model(soul.model())
                .tools(soul.tools())
                .build();
        soulService.saveAgentSoul(updated);
        return ResponseEntity.ok(updated);
    }

    /** Partially update the active agent soul. */
    @PatchMapping("/soul")
    @SuppressWarnings("unchecked")
    public ResponseEntity<AgentSoul> patchSoul(@RequestBody Map<String, Object> updates) {
        AgentSoul current = soulService.loadAgentSoul(null).orElseGet(() -> 
            AgentSoul.builder()
                .id("default")
                .name("Assistant")
                .systemPrompt("You are a helpful AI assistant.")
                .model("qwen3.5:latest")
                .build()
        );

        var builder = AgentSoul.builder()
                .id(current.id())
                .name(updates.containsKey("name") ? (String) updates.get("name") : current.name())
                .description(updates.containsKey("description") ? (String) updates.get("description") : current.description())
                .systemPrompt(updates.containsKey("systemPrompt") ? (String) updates.get("systemPrompt") : current.systemPrompt())
                .purpose(updates.containsKey("purpose") ? (String) updates.get("purpose") : current.purpose())
                .personality(updates.containsKey("personality") ? (String) updates.get("personality") : current.personality())
                .emotionalBaseline(updates.containsKey("emotionalBaseline") ? parseEmotionalBaseline(updates.get("emotionalBaseline")) : current.emotionalBaseline())
                .communicationStyle(updates.containsKey("communicationStyle") ? (String) updates.get("communicationStyle") : current.communicationStyle())
                .model(updates.containsKey("model") ? (String) updates.get("model") : current.model());

        if (updates.containsKey("expertiseDomains")) {
            builder.expertiseDomains((List<String>) updates.get("expertiseDomains"));
        } else {
            builder.expertiseDomains(current.expertiseDomains());
        }

        if (updates.containsKey("coreValues")) {
            builder.coreValues((List<String>) updates.get("coreValues"));
        } else {
            builder.coreValues(current.coreValues());
        }

        if (updates.containsKey("ethicalGuardrails")) {
            builder.ethicalGuardrails((List<String>) updates.get("ethicalGuardrails"));
        } else {
            builder.ethicalGuardrails(current.ethicalGuardrails());
        }

        if (updates.containsKey("tools")) {
            builder.tools((List<String>) updates.get("tools"));
        } else {
            builder.tools(current.tools());
        }

        AgentSoul updated = builder.build();
        soulService.saveAgentSoul(updated);
        return ResponseEntity.ok(updated);
    }

    /** Reset the active agent soul. */
    @DeleteMapping("/soul")
    public ResponseEntity<Void> resetSoul() {
        soulService.saveAgentSoul(AgentSoul.builder()
                .id("default")
                .name("Assistant")
                .systemPrompt("You are a helpful AI assistant.")
                .model("qwen3.5:latest")
                .build());
        return ResponseEntity.noContent().build();
    }

    /** List the active agent soul. */
    @GetMapping
    public ResponseEntity<List<AgentSoul>> listAgents() {
        // Since we are moving to cognitive memory, we focus on the primary soul for now.
        return ResponseEntity.ok(soulService.loadAgentSoul(null).map(List::of).orElse(List.of()));
    }

    /** Get an agent soul by ID. */
    @GetMapping("/{id}")
    public ResponseEntity<AgentSoul> getAgent(@PathVariable String id) {
        return soulService.loadAgentSoul(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Create/Update an agent soul. */
    @PostMapping
    public ResponseEntity<AgentSoul> createAgent(@RequestBody AgentSoul soul) {
        soulService.saveAgentSoul(soul);
        return ResponseEntity.status(HttpStatus.CREATED).body(soul);
    }

    /** Update an existing agent soul. */
    @PutMapping("/{id}")
    public ResponseEntity<AgentSoul> updateAgent(@PathVariable String id, @RequestBody AgentSoul soul) {
        AgentSoul updated = AgentSoul.builder()
                .id(id)
                .name(soul.name())
                .description(soul.description())
                .systemPrompt(soul.systemPrompt())
                .purpose(soul.purpose())
                .personality(soul.personality())
                .expertiseDomains(soul.expertiseDomains())
                .coreValues(soul.coreValues())
                .ethicalGuardrails(soul.ethicalGuardrails())
                .emotionalBaseline(soul.emotionalBaseline())
                .communicationStyle(soul.communicationStyle())
                .model(soul.model())
                .tools(soul.tools())
                .build();
        soulService.saveAgentSoul(updated);
        return ResponseEntity.ok(updated);
    }

    /** Delete an agent soul. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAgent(@PathVariable String id) {
        // In cognitive memory, we just let it fade or "forget" it.
        // For now, no-op or implement forget in service if needed.
        return ResponseEntity.noContent().build();
    }

    /** List all available tools. */
    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> listTools() {
        var toolDetails = toolRegistry.toFunctionDefinitions();
        return ResponseEntity.ok(Map.of(
                "count", toolDetails.size(),
                "tools", toolDetails
        ));
    }

    private static AgentSoul.EmotionalBaseline parseEmotionalBaseline(Object obj) {
        if (obj == null) {
            return AgentSoul.EmotionalBaseline.NEUTRAL;
        }
        if (obj instanceof AgentSoul.EmotionalBaseline eb) {
            return eb;
        }
        if (obj instanceof Map<?, ?> map) {
            Number val = (Number) map.get("defaultValence");
            Number ar = (Number) map.get("defaultArousal");
            byte valence = val != null ? val.byteValue() : 0;
            byte arousal = ar != null ? ar.byteValue() : (byte) 128;
            return new AgentSoul.EmotionalBaseline(valence, arousal);
        }
        if (obj instanceof String s) {
            return switch (s.toLowerCase()) {
                case "warm" -> AgentSoul.EmotionalBaseline.WARM;
                case "energetic" -> AgentSoul.EmotionalBaseline.ENERGETIC;
                default -> AgentSoul.EmotionalBaseline.NEUTRAL;
            };
        }
        return AgentSoul.EmotionalBaseline.NEUTRAL;
    }
}
