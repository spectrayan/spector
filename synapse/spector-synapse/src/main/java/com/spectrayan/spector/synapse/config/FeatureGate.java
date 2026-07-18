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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Feature gate annotation for conditionally disabling REST endpoints.
 *
 * <p>When applied to a controller class or method, the {@link FeatureGateAspect}
 * intercepts the call and checks whether the referenced feature flag is enabled
 * in {@link FeatureFlags}. If the flag is {@code false}, the request is blocked
 * with an HTTP 404 response.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @RestController
 * @FeatureGate("chatEnabled")
 * public class ChatController { ... }
 * }</pre>
 *
 * @see FeatureFlags#isEnabled(String)
 * @see FeatureGateAspect
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface FeatureGate {

    /**
     * The camelCase name of the feature flag to check (e.g., "chatEnabled").
     *
     * @return the feature flag name
     */
    String value();
}
