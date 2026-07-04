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

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent management REST API.
 *
 * <p>CRUD operations for agent souls, tool listing, and agent execution.</p>
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final AgentSoulRepository soulRepo;
    private final ToolRegistry toolRegistry;

    public AgentController(AgentSoulRepository soulRepo, ToolRegistry toolRegistry) {
        this.soulRepo = soulRepo;
        this.toolRegistry = toolRegistry;
    }

    /** List all agent souls. */
    @GetMapping
    public ResponseEntity<List<AgentSoul>> listAgents() {
        return ResponseEntity.ok(soulRepo.findAll());
    }

    /** Get an agent soul by ID. */
    @GetMapping("/{id}")
    public ResponseEntity<AgentSoul> getAgent(@PathVariable String id) {
        return soulRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Create a new agent soul. */
    @PostMapping
    public ResponseEntity<AgentSoul> createAgent(@RequestBody AgentSoul soul) {
        AgentSoul saved = soulRepo.save(soul);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /** Update an existing agent soul. */
    @PutMapping("/{id}")
    public ResponseEntity<AgentSoul> updateAgent(@PathVariable String id, @RequestBody AgentSoul soul) {
        if (soulRepo.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AgentSoul updated = new AgentSoul(id, soul.name(), soul.description(),
                soul.systemPrompt(), soul.personality(), soul.model(), soul.tools(),
                soul.createdAt(), soul.updatedAt());
        return ResponseEntity.ok(soulRepo.save(updated));
    }

    /** Delete an agent soul. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAgent(@PathVariable String id) {
        return soulRepo.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** List all available tools. */
    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> listTools() {
        Set<String> names = toolRegistry.names();
        List<Map<String, Object>> toolDetails = names.stream()
                .map(name -> toolRegistry.get(name).map(tool -> Map.<String, Object>of(
                        "name", tool.name(),
                        "description", tool.description(),
                        "parameters", tool.parameterSchema()
                )).orElse(Map.of()))
                .toList();
        return ResponseEntity.ok(Map.of(
                "count", names.size(),
                "tools", toolDetails
        ));
    }
}
