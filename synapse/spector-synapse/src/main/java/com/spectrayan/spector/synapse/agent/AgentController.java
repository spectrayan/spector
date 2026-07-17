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
import com.spectrayan.spector.synapse.agent.graph.AgenticChatGraph;
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
    private final AgenticChatGraph agenticChatGraph;

    public AgentController(CognitiveSoulService soulService,
                           ToolRegistry toolRegistry,
                           AgenticChatGraph agenticChatGraph) {
        this.soulService = soulService;
        this.toolRegistry = toolRegistry;
        this.agenticChatGraph = agenticChatGraph;
    }

    /** Get the current active agent soul. */
    @GetMapping("/soul")
    public ResponseEntity<AgentSoul> getSoul() {
        return ResponseEntity.ok(soulService.getActiveSoul());
    }

    /** Get the current active agent card manifest. */
    @GetMapping("/soul/card")
    public ResponseEntity<AgentCard> getSoulCard() {
        return ResponseEntity.ok(AgentCard.from(soulService.getActiveSoul()));
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
    public ResponseEntity<AgentSoul> patchSoul(@RequestBody Map<String, Object> updates) {
        return ResponseEntity.ok(soulService.patchAgentSoul(updates));
    }

    /** Reset the active agent soul. */
    @DeleteMapping("/soul")
    public ResponseEntity<Void> resetSoul() {
        soulService.resetAgentSoul();
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

    /** Get an agent card manifest by ID. */
    @GetMapping("/{id}/card")
    public ResponseEntity<AgentCard> getAgentCard(@PathVariable String id) {
        return soulService.loadAgentSoul(id)
                .map(AgentCard::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Invoke an agent by ID to chat. */
    @PostMapping("/{id}/invoke")
    public ResponseEntity<Map<String, String>> invokeAgent(
            @PathVariable String id,
            @RequestBody Map<String, String> payload) {
        String prompt = payload.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return soulService.loadAgentSoul(id)
                .map(soul -> {
                    String reply = agenticChatGraph.chat(soul, prompt);
                    return ResponseEntity.ok(Map.of("response", reply));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
