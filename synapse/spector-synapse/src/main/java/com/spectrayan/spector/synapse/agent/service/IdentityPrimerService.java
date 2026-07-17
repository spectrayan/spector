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
package com.spectrayan.spector.synapse.agent.service;

import com.spectrayan.spector.synapse.agent.AgentSoul;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Service for enriching system prompts with agent identity.
 *
 * <p>Builds the full system prompt by combining the base template with
 * agent identity blocks derived from the {@link AgentSoul}. When memories
 * are primed by the {@link com.spectrayan.spector.synapse.agent.chat.service.ContextPrimingService},
 * they are appended to the prompt produced here.</p>
 */
@Service
public class IdentityPrimerService {

    private static final Logger log = LoggerFactory.getLogger(IdentityPrimerService.class);
    private static final String DEFAULT_PROMPT =
            "You are a cognitive assistant powered by the Spector Engine. "
            + "You have access to tools for memory recall, file operations, and web search. "
            + "Use tools when needed to provide accurate, helpful responses.";

    /**
     * Builds the full system prompt from an agent soul.
     *
     * <p>If the soul has a custom system prompt, it's used as-is with identity
     * enrichment. Otherwise, a default template is loaded from the classpath.</p>
     *
     * @param soul the agent's identity
     * @return fully enriched system prompt
     */
    public String buildSystemPrompt(AgentSoul soul) {
        if (soul == null) {
            return loadTemplate();
        }

        // Use the soul's system prompt or the default template
        String basePrompt = soul.systemPrompt() != null && !soul.systemPrompt().isBlank()
                ? soul.systemPrompt()
                : loadTemplate();

        String agentBlock = buildAgentBlock(soul);

        return basePrompt
                .replace("{{agent_identity}}", agentBlock)
                .replace("{{agent_name}}", soul.name() != null ? soul.name() : "Assistant")
                .replace("{{primed_memories}}", ""); // Memories injected by ChatService
    }

    /**
     * Builds the agent identity block from the soul's cognitive fields.
     */
    private String buildAgentBlock(AgentSoul soul) {
        var sb = new StringBuilder();

        if (soul.name() != null && !soul.name().isBlank()) {
            sb.append("\n== AGENT IDENTITY ==\n");
            sb.append("Name: ").append(soul.name()).append('\n');
        }

        if (soul.description() != null && !soul.description().isBlank()) {
            sb.append("Description: ").append(soul.description()).append('\n');
        }

        if (soul.purpose() != null && !soul.purpose().isBlank()) {
            sb.append("Purpose: ").append(soul.purpose()).append('\n');
        }

        if (soul.personality() != null && !soul.personality().isBlank()) {
            sb.append("Personality: ").append(soul.personality()).append('\n');
        }

        if (!soul.expertiseDomains().isEmpty()) {
            sb.append("Expertise: ").append(String.join(", ", soul.expertiseDomains())).append('\n');
        }

        if (!soul.coreValues().isEmpty()) {
            sb.append("Core Values: ").append(String.join(", ", soul.coreValues())).append('\n');
        }

        if (soul.communicationStyle() != null && !soul.communicationStyle().isBlank()) {
            sb.append("Communication Style: ").append(soul.communicationStyle()).append('\n');
        }

        if (!soul.ethicalGuardrails().isEmpty()) {
            sb.append("Ethical Guardrails: ").append(String.join("; ", soul.ethicalGuardrails())).append('\n');
        }

        // Available tools
        if (!soul.tools().isEmpty()) {
            sb.append("Available Tools: ").append(String.join(", ", soul.tools())).append('\n');
        }

        return sb.toString();
    }

    /**
     * Loads the system prompt template from the classpath.
     */
    private String loadTemplate() {
        try (InputStream is = getClass().getResourceAsStream("/prompts/companion-system.txt")) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("Failed to load prompt template: {}", e.getMessage());
        }
        return DEFAULT_PROMPT;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
