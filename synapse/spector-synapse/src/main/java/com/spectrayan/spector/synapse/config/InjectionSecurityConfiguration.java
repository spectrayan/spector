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
import com.spectrayan.spector.synapse.security.injection.ClassifierInjectionDetector;
import com.spectrayan.spector.synapse.security.injection.InjectionInterceptor;
import com.spectrayan.spector.synapse.security.injection.PatternInjectionDetector;
import com.spectrayan.spector.synapse.security.injection.PromptShield;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires prompt-injection detection beans ({@link PromptShield},
 * {@link InjectionInterceptor}) from {@code spector.security.injection.*}.
 */
@Configuration
public class InjectionSecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PatternInjectionDetector patternInjectionDetector() {
        return new PatternInjectionDetector();
    }

    @Bean
    @ConditionalOnMissingBean
    public ClassifierInjectionDetector classifierInjectionDetector() {
        return new ClassifierInjectionDetector();
    }

    @Bean
    @ConditionalOnMissingBean
    public PromptShield promptShield(SynapseProperties properties,
                                     PatternInjectionDetector patternInjectionDetector,
                                     ClassifierInjectionDetector classifierInjectionDetector) {
        SecurityProperties security = properties.getSecurity() != null
                ? properties.getSecurity()
                : new SecurityProperties();
        return new PromptShield(security.getInjection(), patternInjectionDetector, classifierInjectionDetector);
    }

    @Bean
    @ConditionalOnMissingBean
    public InjectionInterceptor injectionInterceptor(PromptShield promptShield) {
        return new InjectionInterceptor(promptShield);
    }
}
