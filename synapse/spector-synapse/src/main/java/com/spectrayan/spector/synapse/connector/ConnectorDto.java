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
package com.spectrayan.spector.synapse.connector;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Connector data transfer objects.
 */
public final class ConnectorDto {

    private ConnectorDto() {}

    /** Supported connector types. */
    public enum ConnectorType {
        FILE_SYSTEM, GIT, CONFLUENCE, JIRA, SLACK, GOOGLE_DRIVE,
        NOTION, DATABASE, S3, WEB_CRAWLER, REST_API, CUSTOM
    }

    /** Connector template descriptor — loaded from YAML configs. */
    public record TemplateDescriptor(
            String id,
            String name,
            String description,
            ConnectorType type,
            String icon,
            Map<String, FieldDescriptor> configFields,
            String cronExpression
    ) {}

    /** Field descriptor for template configuration. */
    public record FieldDescriptor(
            String name,
            String label,
            String type,        // "string", "password", "number", "boolean", "select"
            boolean required,
            String defaultValue,
            String description
    ) {}

    /** Route configuration — an instance of a connector template. */
    public record RouteConfig(
            String id,
            String templateId,
            String name,
            RouteStatus status,
            Map<String, String> config,
            String cronExpression,
            Instant createdAt,
            Instant lastRunAt
    ) {}

    /** Route lifecycle status. */
    public enum RouteStatus {
        CREATED, RUNNING, PAUSED, STOPPED, ERROR
    }

    /** Connection test result. */
    public record ConnectionTestResult(
            boolean success,
            String message,
            Duration latency,
            Instant testedAt
    ) {
        public static ConnectionTestResult success(Duration latency) {
            return new ConnectionTestResult(true, "Connection successful", latency, Instant.now());
        }

        public static ConnectionTestResult failure(String message, Duration latency) {
            return new ConnectionTestResult(false, message, latency, Instant.now());
        }
    }

    /** Route execution record. */
    public record ExecutionRecord(
            String routeId,
            Instant startTime,
            Instant endTime,
            int documentsProcessed,
            int errors,
            String status
    ) {}
}
