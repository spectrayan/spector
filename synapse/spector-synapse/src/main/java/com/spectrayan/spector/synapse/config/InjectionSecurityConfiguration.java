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
