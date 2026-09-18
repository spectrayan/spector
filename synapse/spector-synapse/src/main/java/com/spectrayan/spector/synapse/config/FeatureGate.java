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
