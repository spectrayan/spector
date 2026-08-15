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
package com.spectrayan.spector.connector.template;

import com.spectrayan.spector.connector.model.RouteConfig;
import com.spectrayan.spector.connector.model.TemplateDescriptor;
import com.spectrayan.spector.connector.model.TemplateDescriptor.ParameterDefinition;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generic route config validator driven entirely by
 * {@link TemplateDescriptor} metadata.
 *
 * <p>No per-connector classes needed — validation rules are derived
 * from the YAML template descriptor:</p>
 * <ul>
 *   <li>{@code required: true} → parameter must be present and non-blank</li>
 *   <li>{@code type: url} → must be a valid {@code http://} or {@code https://} URL</li>
 *   <li>{@code type: number} → must be a valid number</li>
 *   <li>{@code requiresCredential: true} → {@code credentialRef} must be set</li>
 * </ul>
 */
public final class TemplateDescriptorValidator {

    private TemplateDescriptorValidator() {}

    /**
     * Validate a route config against its template descriptor.
     *
     * @param config     the route configuration to validate
     * @param descriptor the template descriptor with parameter metadata
     * @return list of validation errors (empty = valid)
     */
    public static List<String> validate(RouteConfig config, TemplateDescriptor descriptor) {
        List<String> errors = new ArrayList<>();
        Map<String, String> props = config.properties();

        // Validate each parameter from the descriptor
        for (ParameterDefinition param : descriptor.parameters()) {
            String value = props.get(param.name());
            boolean hasValue = value != null && !value.isBlank();

            // Required check
            if (param.required() && !hasValue) {
                errors.add("Required parameter '" + param.displayName()
                        + "' (" + param.name() + ") is missing");
                continue; // Skip type validation if missing
            }

            // Type-specific validation (only if value is present)
            if (hasValue && param.type() != null) {
                switch (param.type()) {
                    case "url" -> validateUrl(value, param, errors);
                    case "number" -> validateNumber(value, param, errors);
                    default -> { /* string, boolean, secret — no extra validation */ }
                }
            }
        }

        // Credential check
        if (descriptor.requiresCredential()) {
            if (config.credentialRef() == null || config.credentialRef().isBlank()) {
                errors.add("This connector requires a credential reference (credentialRef)");
            }
        }

        return errors;
    }

    private static void validateUrl(String value, ParameterDefinition param, List<String> errors) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                errors.add("'" + param.displayName() + "' must be an http:// or https:// URL");
            }
        } catch (IllegalArgumentException e) {
            errors.add("'" + param.displayName() + "' is not a valid URL: " + value);
        }
    }

    private static void validateNumber(String value, ParameterDefinition param, List<String> errors) {
        try {
            Long.parseLong(value);
        } catch (NumberFormatException e) {
            errors.add("'" + param.displayName() + "' must be a number, got: " + value);
        }
    }
}
