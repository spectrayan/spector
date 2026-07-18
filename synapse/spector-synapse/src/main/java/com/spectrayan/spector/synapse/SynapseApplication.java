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
package com.spectrayan.spector.synapse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.spectrayan.spector.synapse.config.FeatureFlags;
import com.spectrayan.spector.synapse.config.SynapseProperties;

/**
 * Spector Synapse — the central nervous system.
 *
 * <p>This is the Spring Boot 4 entry point that unifies all Spector subsystems:
 * the core memory engine, LLM providers, autonomous agent orchestration,
 * data connectors, channel adapters, and the Cortex UI — all served through
 * a single Armeria port.</p>
 *
 * <h3>Biological Naming</h3>
 * <ul>
 *   <li><b>Spector Memory</b> — Hippocampus (storage, recall, learning)</li>
 *   <li><b>Cortex UI</b> — Visual Cortex (perception, interface)</li>
 *   <li><b>Synapse</b> — Synaptic Network (agent orchestration, signal routing)</li>
 * </ul>
 *
 * @see com.spectrayan.spector.synapse.config.WebConfig
 * @see com.spectrayan.spector.synapse.config.SynapseProperties
 * @see com.spectrayan.spector.synapse.config.FeatureFlags
 */
@SpringBootApplication
@EnableConfigurationProperties({SynapseProperties.class, FeatureFlags.class})
public class SynapseApplication {

    private static final Logger log = LoggerFactory.getLogger(SynapseApplication.class);

    /**
     * Bootstrap the Spector Synapse server.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        log.info("⚡ Starting Spector Synapse — the central nervous system");
        SpringApplication.run(SynapseApplication.class, args);
        log.info("✅ Spector Synapse is online");
    }
}
