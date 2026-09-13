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
import com.spectrayan.spector.synapse.security.pii.PiiDetector;
import com.spectrayan.spector.synapse.security.pii.PiiInterceptor;
import com.spectrayan.spector.synapse.security.pii.PiiRedactor;
import com.spectrayan.spector.synapse.security.pii.PiiRehydrator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires PII detection/redaction beans ({@link PiiInterceptor}) from
 * {@code spector.security.pii.*}.
 */
@Configuration
public class PiiSecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PiiDetector piiDetector() {
        return new PiiDetector();
    }

    @Bean
    @ConditionalOnMissingBean
    public PiiRedactor piiRedactor(PiiDetector piiDetector) {
        return new PiiRedactor(piiDetector);
    }

    @Bean
    @ConditionalOnMissingBean
    public PiiRehydrator piiRehydrator() {
        return new PiiRehydrator();
    }

    @Bean
    @ConditionalOnMissingBean
    public PiiInterceptor piiInterceptor(SynapseProperties properties,
                                         PiiRedactor piiRedactor,
                                         PiiRehydrator piiRehydrator) {
        SecurityProperties security = properties.getSecurity() != null
                ? properties.getSecurity()
                : new SecurityProperties();
        return new PiiInterceptor(security.getPii(), piiRedactor, piiRehydrator);
    }
}
