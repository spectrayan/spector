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
package com.spectrayan.spector.synapse.agent.graph.coordinator;

import com.spectrayan.spector.synapse.agent.AgentSoul;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Capability-based Agent Selector. Matches sub-tasks to the best available child agent.
 */
@Component
public class AgentSelector {

    private static final Logger log = LoggerFactory.getLogger(AgentSelector.class);

    private final CognitiveSoulService soulService;

    public AgentSelector(CognitiveSoulService soulService) {
        this.soulService = soulService;
    }

    /**
     * Resolves an agent by ID, or dynamically selects the best agent matching the task.
     *
     * @param agentId            optional specific agent ID requested
     * @param subtaskDescription sub-task description
     * @return resolved agent soul
     */
    public Optional<AgentSoul> getAgentByIdOrSelection(String agentId, String subtaskDescription) {
        if (agentId != null && !agentId.isBlank()) {
            Optional<AgentSoul> soul = soulService.loadAgentSoul(agentId);
            if (soul.isPresent()) {
                log.info("[AgentSelector] Resolved agent by ID: {}", agentId);
                return soul;
            }
        }
        return selectBestAgent(subtaskDescription);
    }

    /**
     * Selects the best agent based on keywords matching the subtask description.
     *
     * @param subtaskDescription sub-task description
     * @return matched agent soul
     */
    public Optional<AgentSoul> selectBestAgent(String subtaskDescription) {
        List<AgentSoul> agents = soulService.listAllAgents();
        if (agents.isEmpty()) {
            log.warn("[AgentSelector] No agent souls found in cognitive memory");
            return Optional.empty();
        }

        AgentSoul bestAgent = null;
        int bestScore = -1;

        String query = subtaskDescription != null ? subtaskDescription.toLowerCase() : "";
        for (AgentSoul agent : agents) {
            int score = 0;

            // Name match (highest weight)
            if (agent.name() != null && query.contains(agent.name().toLowerCase())) {
                score += 10;
            }

            // Description match
            if (agent.description() != null && query.contains(agent.description().toLowerCase())) {
                score += 5;
            }

            // Purpose match
            if (agent.purpose() != null && query.contains(agent.purpose().toLowerCase())) {
                score += 5;
            }

            // Expertise domains match
            if (agent.expertiseDomains() != null) {
                for (String domain : agent.expertiseDomains()) {
                    if (query.contains(domain.toLowerCase())) {
                        score += 3;
                    }
                }
            }

            // Tools match
            if (agent.tools() != null) {
                for (String tool : agent.tools()) {
                    if (query.contains(tool.toLowerCase())) {
                        score += 2;
                    }
                }
            }

            log.debug("[AgentSelector] Agent '{}' matching score: {}", agent.name(), score);

            if (score > bestScore) {
                bestScore = score;
                bestAgent = agent;
            }
        }

        if (bestAgent != null) {
            log.info("[AgentSelector] Selected agent '{}' (score={}) for subtask: '{}'",
                    bestAgent.name(), bestScore, subtaskDescription);
        }

        return Optional.ofNullable(bestAgent);
    }
}
