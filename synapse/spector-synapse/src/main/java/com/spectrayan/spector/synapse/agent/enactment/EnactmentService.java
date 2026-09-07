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
package com.spectrayan.spector.synapse.agent.enactment;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.aisme.enactment.EnactmentConfig;
import com.spectrayan.spector.memory.aisme.enactment.EnactmentEngine;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.enactment.EnactMode;
import com.spectrayan.spector.memory.model.enactment.Enactment;
import com.spectrayan.spector.memory.model.enactment.SituationFrame;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
import com.spectrayan.spector.synapse.memory.MemoryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the full AISME Persona Enactment lifecycle (ADR-0032).
 */
@Service
public class EnactmentService {

    private static final Logger log = LoggerFactory.getLogger(EnactmentService.class);

    private final CognitiveSoulService soulService;
    private final MemoryRegistry memoryRegistry;
    private final EnactmentConfig enactmentConfig;

    public EnactmentService(CognitiveSoulService soulService, MemoryRegistry memoryRegistry) {
        this(soulService, memoryRegistry, EnactmentConfig.defaultConfig());
    }

    @Autowired
    public EnactmentService(
            CognitiveSoulService soulService,
            MemoryRegistry memoryRegistry,
            @Autowired(required = false) EnactmentConfig enactmentConfig) {
        this.soulService = soulService;
        this.memoryRegistry = memoryRegistry;
        this.enactmentConfig = (enactmentConfig != null) ? enactmentConfig : EnactmentConfig.defaultConfig();
    }

    public EnactmentConfig enactmentConfig() {
        return enactmentConfig;
    }

    /**
     * Executes a complete persona enactment cycle with default configuration.
     *
     * @param situation the problem and situational context
     * @param namespace the memory namespace (nullable; falls back to default)
     * @param actingSoulId the ID of the persona to enact
     * @param mode the enactment mode (REACT, DECIDE, SIMULATE, REPLAY)
     * @return complete, structured Enactment
     */
    public Enactment enact(SituationFrame situation, String namespace, String actingSoulId, EnactMode mode) {
        return enact(situation, namespace, actingSoulId, mode, this.enactmentConfig);
    }

    /**
     * Executes a complete persona enactment cycle with explicit EnactmentConfig override.
     *
     * @param situation the problem and situational context
     * @param namespace the memory namespace (nullable; falls back to default)
     * @param actingSoulId the ID of the persona to enact
     * @param mode the enactment mode (REACT, DECIDE, SIMULATE, REPLAY)
     * @param config the enactment configuration override
     * @return complete, structured Enactment
     */
    public Enactment enact(SituationFrame situation, String namespace, String actingSoulId, EnactMode mode, EnactmentConfig config) {
        if (situation == null) {
            situation = SituationFrame.of("");
        }
        if (mode == null) {
            mode = EnactMode.REACT;
        }
        String effectiveNamespace = (namespace != null && !namespace.isBlank()) ? namespace : "default";
        String targetSoulId = (actingSoulId != null && !actingSoulId.isBlank()) ? actingSoulId : "default";

        // 1. Resolve Memory and Acting Soul
        SpectorMemory memory = null;
        try {
            memory = memoryRegistry.resolveFor(effectiveNamespace);
        } catch (Exception e) {
            log.warn("Could not resolve SpectorMemory for namespace {}: {}", effectiveNamespace, e.getMessage());
        }

        AgentSoul soul = soulService.getEffectiveSoul(targetSoulId);
        if (soul == null) {
            soul = CognitiveSoulService.DEFAULT_FALLBACK_SOUL;
        }

        EnactmentConfig effectiveConfig = (config != null) ? config : this.enactmentConfig;
        return EnactmentEngine.enact(memory, soul, situation, mode, effectiveConfig);
    }
}
