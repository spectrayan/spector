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
package com.spectrayan.spector.synapse.provider;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * Provider management REST API.
 *
 * <p>Endpoints for listing, activating, and checking health of LLM providers.</p>
 */
@RestController
@RequestMapping("/api/v1/providers")
public class ProviderController {

    private final ProviderRegistry registry;

    public ProviderController(ProviderRegistry registry) {
        this.registry = registry;
    }

    /** List all registered providers. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        return ResponseEntity.ok(Map.of(
                "embedding", Map.of(
                        "providers", registry.embeddingProviderNames(),
                        "active", registry.activeEmbeddingName().orElse("none")
                ),
                "generation", Map.of(
                        "providers", registry.generationProviderNames(),
                        "active", registry.activeGenerationName().orElse("none")
                )
        ));
    }

    /** Activate an embedding provider. */
    @PostMapping("/embedding/{name}/activate")
    public ResponseEntity<Map<String, String>> activateEmbedding(@PathVariable String name) {
        registry.activateEmbedding(name);
        return ResponseEntity.ok(Map.of(
                "status", "activated",
                "provider", name,
                "type", "embedding"
        ));
    }

    /** Activate a generation provider. */
    @PostMapping("/generation/{name}/activate")
    public ResponseEntity<Map<String, String>> activateGeneration(@PathVariable String name) {
        registry.activateGeneration(name);
        return ResponseEntity.ok(Map.of(
                "status", "activated",
                "provider", name,
                "type", "generation"
        ));
    }

    /** Get health for all providers. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, ProviderHealth>> health() {
        return ResponseEntity.ok(registry.allHealth());
    }

    /** Get health for a specific embedding provider. */
    @GetMapping("/embedding/{name}/health")
    public ResponseEntity<ProviderHealth> embeddingHealth(@PathVariable String name) {
        return ResponseEntity.ok(registry.checkEmbeddingHealth(name));
    }

    /** Get health for a specific generation provider. */
    @GetMapping("/generation/{name}/health")
    public ResponseEntity<ProviderHealth> generationHealth(@PathVariable String name) {
        return ResponseEntity.ok(registry.checkGenerationHealth(name));
    }
}
