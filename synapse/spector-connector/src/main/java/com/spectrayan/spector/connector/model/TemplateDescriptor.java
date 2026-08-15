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
package com.spectrayan.spector.connector.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Describes an integration template that users can browse and instantiate.
 *
 * <p>Templates can be:</p>
 * <ul>
 *   <li><b>Built-in</b> ({@code builtIn = true}): Shipped with the library,
 *       defined as native Camel RouteTemplates in Java DSL.</li>
 *   <li><b>Custom</b> ({@code builtIn = false}): Created by admins at runtime,
 *       carrying Camel YAML DSL loaded dynamically.</li>
 * </ul>
 *
 * @param templateId       unique template identifier (matches Camel routeTemplate ID for built-ins)
 * @param displayName      display name shown in the UI
 * @param description      human-readable description
 * @param icon             icon identifier (e.g., "slack", "s3", "database")
 * @param category         grouping category (e.g., "Data", "Messaging", "Triggers")
 * @param connectorType    connector type label (see {@link ConnectorType})
 * @param parameters       parameter definitions — describes what the user must configure
 * @param requiresCredential whether this template needs credentials
 * @param builtIn          whether this is a system built-in (not editable)
 * @param routeYaml        Camel YAML DSL for custom templates (null for built-ins)
 * @param createdAt        when this template was created
 */
public record TemplateDescriptor(
        String templateId,
        String displayName,
        String description,
        String icon,
        String category,
        String connectorType,
        List<ParameterDefinition> parameters,
        boolean requiresCredential,
        boolean builtIn,
        String routeYaml,
        Instant createdAt
) {
    public TemplateDescriptor {
        Objects.requireNonNull(templateId, "templateId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        if (parameters == null) parameters = List.of();
        if (createdAt == null) createdAt = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Describes a single parameter that a user must provide
     * when instantiating this template.
     *
     * @param name         parameter key (used in Camel template placeholders)
     * @param displayName  display name shown in the UI
     * @param description  optional help text
     * @param type         value type: "string", "number", "boolean", "secret", "cron", "url"
     * @param required     whether the parameter is required
     * @param defaultValue default value (null if none)
     * @param placeholder  placeholder hint for UI input fields
     */
    public record ParameterDefinition(
            String name,
            String displayName,
            String description,
            String type,
            boolean required,
            String defaultValue,
            String placeholder
    ) {
        public ParameterDefinition {
            Objects.requireNonNull(name, "parameter name must not be null");
            if (type == null) type = "string";
        }

        public static ParamBuilder builder() {
            return new ParamBuilder();
        }

        public static final class ParamBuilder {
            private String name;
            private String displayName;
            private String description;
            private String type = "string";
            private boolean required = true;
            private String defaultValue;
            private String placeholder;

            public ParamBuilder name(String name) { this.name = name; return this; }
            public ParamBuilder displayName(String displayName) { this.displayName = displayName; return this; }
            public ParamBuilder description(String description) { this.description = description; return this; }
            public ParamBuilder type(String type) { this.type = type; return this; }
            public ParamBuilder required(boolean required) { this.required = required; return this; }
            public ParamBuilder defaultValue(String defaultValue) { this.defaultValue = defaultValue; return this; }
            public ParamBuilder placeholder(String placeholder) { this.placeholder = placeholder; return this; }

            public ParameterDefinition build() {
                return new ParameterDefinition(name, displayName, description, type, required, defaultValue, placeholder);
            }
        }
    }

    public static final class Builder {
        private String templateId;
        private String displayName;
        private String description;
        private String icon;
        private String category;
        private String connectorType;
        private List<ParameterDefinition> parameters = List.of();
        private boolean requiresCredential = false;
        private boolean builtIn = false;
        private String routeYaml;

        public Builder templateId(String templateId) { this.templateId = templateId; return this; }
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder icon(String icon) { this.icon = icon; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder connectorType(String connectorType) { this.connectorType = connectorType; return this; }
        public Builder parameters(List<ParameterDefinition> parameters) { this.parameters = parameters; return this; }
        public Builder requiresCredential(boolean requiresCredential) { this.requiresCredential = requiresCredential; return this; }
        public Builder builtIn(boolean builtIn) { this.builtIn = builtIn; return this; }
        public Builder routeYaml(String routeYaml) { this.routeYaml = routeYaml; return this; }

        public TemplateDescriptor build() {
            return new TemplateDescriptor(templateId, displayName, description, icon, category,
                    connectorType, parameters, requiresCredential, builtIn, routeYaml, Instant.now());
        }
    }
}
