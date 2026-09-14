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
package com.spectrayan.spector.synapse.config;

import com.spectrayan.spector.synapse.security.config.SecurityProperties;
import com.spectrayan.spector.synapse.security.toolaccess.ToolAccessPolicy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Synapse {@link ToolAccessPolicy} from {@code spector.security.tool-access.*}
 * and classpath {@code security/tool-access.yml} (ADR-0035).
 */
@Configuration
public class ToolAccessSecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ToolAccessPolicy toolAccessPolicy(SynapseProperties properties) {
        SecurityProperties security = properties.getSecurity() != null
                ? properties.getSecurity()
                : new SecurityProperties();
        return new ToolAccessPolicy(security.getToolAccess());
    }
}
