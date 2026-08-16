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
package com.spectrayan.spector.synapse.provider.usage;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST API controller for querying and managing token usage telemetry.
 */
@RestController
@RequestMapping("/api/v1/usage")
public class TokenUsageController {

    private final TokenUsageTracker tracker;

    public TokenUsageController(TokenUsageTracker tracker) {
        this.tracker = tracker;
    }

    /**
     * Retrieve aggregated summary of token usage across all dimensions.
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(tracker.getSummary());
    }

    /**
     * Retrieve token statistics for a specific user ID.
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<TokenUsageStats> getUserStats(@PathVariable String userId) {
        return ResponseEntity.ok(tracker.getUserStats(userId));
    }

    /**
     * Retrieve token statistics for a specific model name.
     */
    @GetMapping("/models/{modelName}")
    public ResponseEntity<TokenUsageStats> getModelStats(@PathVariable String modelName) {
        return ResponseEntity.ok(tracker.getModelStats(modelName));
    }

    /**
     * Retrieve token statistics for a specific session ID.
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<TokenUsageStats> getSessionStats(@PathVariable String sessionId) {
        return ResponseEntity.ok(tracker.getSessionStats(sessionId));
    }

    /**
     * Retrieve token statistics for an operational category.
     */
    @GetMapping("/categories/{category}")
    public ResponseEntity<TokenUsageStats> getCategoryStats(@PathVariable String category) {
        try {
            TokenUsageCategory cat = TokenUsageCategory.valueOf(category.toUpperCase());
            return ResponseEntity.ok(tracker.getCategoryStats(cat));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Reset all token usage telemetry and evict the Spring Cache.
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset() {
        tracker.reset();
        return ResponseEntity.ok(Map.of("status", "reset", "message", "Token usage cache cleared"));
    }
}
