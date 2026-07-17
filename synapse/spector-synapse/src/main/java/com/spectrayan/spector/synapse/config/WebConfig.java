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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures CORS for Spector Synapse using Spring MVC.
 *
 * <p>Replaces the Armeria-based CORS configuration while Armeria is disabled
 * due to the Jackson 2/3 classpath conflict with Spring Boot 4.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private final SynapseProperties props;

    public WebConfig(SynapseProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String origins = props.cors().allowedOrigins();
        String[] originArray = origins.split(",");
        for (int i = 0; i < originArray.length; i++) {
            originArray[i] = originArray[i].trim();
        }

        registry.addMapping("/**")
                .allowedOriginPatterns(originArray)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "X-API-Key")
                .exposedHeaders("Content-Type")
                .allowCredentials(true)
                .maxAge(3600);

        log.info("[CORS] Enabled for origins: {}", origins);
    }
}
