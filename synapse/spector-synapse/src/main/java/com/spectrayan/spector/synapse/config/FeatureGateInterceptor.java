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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring MVC interceptor that enforces feature gates on REST controllers.
 *
 * <p>Intercepts incoming requests and checks whether the target handler class
 * or method is annotated with {@link FeatureGate}. When the referenced feature
 * flag is {@code false}, the interceptor short-circuits the request and returns
 * an HTTP 404 response with a descriptive JSON error body.</p>
 *
 * <h3>Response Format (when blocked)</h3>
 * <pre>{@code
 * {
 *   "error": "Feature 'chatEnabled' is not enabled",
 *   "feature": "chatEnabled",
 *   "enableHint": "Set spector.features.chat-enabled=true or ensure Ollama is running"
 * }
 * }</pre>
 *
 * <p>This approach avoids the need for AspectJ / spring-boot-starter-aop
 * and works with the standard Spring MVC interceptor chain.</p>
 *
 * @see FeatureGate
 * @see FeatureFlags
 */
@Component
public class FeatureGateInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FeatureGateInterceptor.class);

    private final FeatureFlags featureFlags;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the interceptor with the injected feature flags and mapper.
     *
     * @param featureFlags the bound feature flag configuration
     * @param objectMapper the Jackson mapper for JSON serialization
     */
    public FeatureGateInterceptor(FeatureFlags featureFlags, ObjectMapper objectMapper) {
        this.featureFlags = featureFlags;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // Check method-level annotation first, then class-level
        FeatureGate gate = handlerMethod.getMethodAnnotation(FeatureGate.class);
        if (gate == null) {
            gate = handlerMethod.getBeanType().getAnnotation(FeatureGate.class);
        }

        if (gate == null) {
            return true; // No feature gate — allow through
        }

        String featureName = gate.value();

        if (featureFlags.isEnabled(featureName)) {
            return true; // Feature is enabled — allow through
        }

        // Feature is disabled — block with 404
        log.debug("Feature gate '{}' blocked request to {} {}",
                featureName, request.getMethod(), request.getRequestURI());

        String kebabName = toKebabCase(featureName);
        var errorBody = Map.of(
                "error", "Feature '" + featureName + "' is not enabled",
                "feature", featureName,
                "enableHint", "Set spector.features." + kebabName + "=true or ensure Ollama is running"
        );

        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(), errorBody);
        return false;
    }

    /**
     * Converts a camelCase feature name to kebab-case for the property hint.
     *
     * @param camelCase the camelCase name (e.g., "chatEnabled")
     * @return the kebab-case equivalent (e.g., "chat-enabled")
     */
    private static String toKebabCase(String camelCase) {
        var sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('-');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
