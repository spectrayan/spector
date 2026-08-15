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
import java.util.Map;

/**
 * Configuration for a data source connector route.
 *
 * @param id              unique route identifier
 * @param name            human-readable name (e.g., "Production S3 Docs")
 * @param templateId      which template to use (e.g., "s3-poll", "file-watch")
 * @param connectorType   connector type (see {@link ConnectorType})
 * @param tenantId        tenant isolation key
 * @param source          explicit source endpoint URI (overrides template, nullable)
 * @param schedule        cron schedule (nullable — null means event-driven)
 * @param properties      template-specific parameters (bucket name, path, etc.)
 * @param credentialRef   credential reference for secret resolution (nullable)
 * @param routeYaml       raw Camel YAML DSL for custom routes (nullable)
 * @param status          lifecycle status
 * @param enabled         whether this route should be started
 * @param createdAt       when this config was created
 */
public record RouteConfig(
        String id,
        String name,
        String templateId,
        String connectorType,
        String tenantId,
        String source,
        String schedule,
        Map<String, String> properties,
        String credentialRef,
        String routeYaml,
        RouteStatus status,
        boolean enabled,
        Instant createdAt
) {
    public RouteConfig {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Route id must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Route name must not be blank");
        if (templateId == null || templateId.isBlank()) throw new IllegalArgumentException("templateId must not be blank");
        if (tenantId == null || tenantId.isBlank()) tenantId = "default";
        if (properties == null) properties = Map.of();
        if (status == null) status = RouteStatus.DRAFT;
        if (createdAt == null) createdAt = Instant.now();
    }

    /** Creates a builder pre-filled with required fields. */
    public static Builder builder(String id, String name, String templateId) {
        return new Builder(id, name, templateId);
    }

    public static final class Builder {
        private final String id;
        private final String name;
        private final String templateId;
        private String connectorType;
        private String tenantId = "default";
        private String source;
        private String schedule;
        private Map<String, String> properties = Map.of();
        private String credentialRef;
        private String routeYaml;
        private RouteStatus status = RouteStatus.DRAFT;
        private boolean enabled = true;

        private Builder(String id, String name, String templateId) {
            this.id = id;
            this.name = name;
            this.templateId = templateId;
        }

        public Builder connectorType(String connectorType) { this.connectorType = connectorType; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder schedule(String schedule) { this.schedule = schedule; return this; }
        public Builder properties(Map<String, String> properties) { this.properties = properties; return this; }
        public Builder credentialRef(String credentialRef) { this.credentialRef = credentialRef; return this; }
        public Builder routeYaml(String routeYaml) { this.routeYaml = routeYaml; return this; }
        public Builder status(RouteStatus status) { this.status = status; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }

        public RouteConfig build() {
            return new RouteConfig(id, name, templateId, connectorType, tenantId, source, schedule,
                    properties, credentialRef, routeYaml, status, enabled, Instant.now());
        }
    }
}
