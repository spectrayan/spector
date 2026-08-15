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

import com.spectrayan.spector.commons.template.TemplateEngine;
import com.spectrayan.spector.memory.model.AgentSoul;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

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
    private final TemplateEngine templateEngine;

    @Autowired
    public IdentityPrimerService() {
        this(TemplateEngine.getDefault());
    }

    public IdentityPrimerService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine != null ? templateEngine : TemplateEngine.getDefault();
    }

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
        Map<String, Object> model = new HashMap<>();
        boolean hasIdentity = soul != null && !AgentSoul.NONE.equals(soul) && hasAnyIdentity(soul);
        model.put("hasIdentity", hasIdentity);
        model.put("soul", soul != null ? soul : Map.of());
        model.put("agent_name", soul != null && soul.name() != null ? soul.name() : "Assistant");
        model.put("primed_memories", ""); // Memories injected by ChatService

        // Also build agent block for legacy {{agent_identity}} placeholder support
        String agentBlock = buildAgentBlock(soul);
        model.put("agent_identity", agentBlock);

        if (soul != null && soul.systemPrompt() != null && !soul.systemPrompt().isBlank()) {
            return templateEngine.renderInline(soul.systemPrompt(), model);
        }

        return templateEngine.render("prompts/companion-system", model);
    }

    private boolean hasAnyIdentity(AgentSoul soul) {
        return soul != null && (
                (soul.name() != null && !soul.name().isBlank())
                || (soul.description() != null && !soul.description().isBlank())
                || (soul.purpose() != null && !soul.purpose().isBlank())
                || (soul.personality() != null && !soul.personality().isBlank())
                || !soul.expertiseDomains().isEmpty()
                || !soul.coreValues().isEmpty()
                || (soul.communicationStyle() != null && !soul.communicationStyle().isBlank())
                || !soul.ethicalGuardrails().isEmpty()
                || !soul.tools().isEmpty()
        );
    }

    /**
     * Builds the agent identity block from the soul's cognitive fields using Handlebars template.
     */
    public String buildAgentBlock(AgentSoul soul) {
        if (!hasAnyIdentity(soul)) {
            return "";
        }
        String block = templateEngine.render("prompts/agent-identity", Map.of(
                "hasIdentity", true,
                "soul", soul
        ));
        return "\n" + block.trim() + "\n";
    }
}
