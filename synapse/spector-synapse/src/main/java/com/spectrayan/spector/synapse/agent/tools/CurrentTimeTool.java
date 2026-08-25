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
package com.spectrayan.spector.synapse.agent.tools;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.spectrayan.spector.mcp.tools.McpToolHandler;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Current time tool — provides the current date, time, and timezone info.
 */
@Component
public class CurrentTimeTool extends McpToolHandler {

    @Override
    public String name() { return "current_time"; }

    @Override
    public String description() {
        return "Get the current date, time, timezone, and Unix timestamp.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "timezone", Map.of("type", "string", "description", "IANA timezone (e.g., America/New_York)", "default", "UTC")
                )
        );
    }

    @Override
    public McpToolCategory category() {
        return McpToolCategory.GENERAL;
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> args) throws Exception {
        return textResult(executeInternal(args));
    }

    private String executeInternal(Map<String, Object> arguments) throws Exception {
        String tz = (String) arguments.getOrDefault("timezone", "UTC");
        try {
            ZoneId zoneId = ZoneId.of(tz);
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            return String.format("""
                    Current Time Info:
                    - ISO-8601: %s
                    - Formatted: %s
                    - Timezone: %s (offset: %s)
                    - Epoch Millis: %d
                    - Epoch Seconds: %d
                    """,
                    now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")),
                    zoneId.getId(),
                    now.getOffset().getId(),
                    Instant.now().toEpochMilli(),
                    Instant.now().getEpochSecond()
            ).trim();
        } catch (Exception e) {
            return "Error: Invalid timezone '" + tz + "'. Use IANA format like 'America/New_York' or 'Europe/London'.";
        }
    }
}
