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
