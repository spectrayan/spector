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
package com.spectrayan.spector.synapse.agent.tools;

import com.spectrayan.spector.synapse.agent.AgentTool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Current time tool — provides the current date, time, and timezone info.
 */
@Component
public class CurrentTimeTool implements AgentTool {

    @Override
    public String name() { return "current_time"; }

    @Override
    public String description() {
        return "Get the current date, time, timezone, and Unix timestamp.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "timezone", Map.of("type", "string", "description", "IANA timezone (e.g., America/New_York)", "default", "UTC")
                )
        );
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String tz = (String) arguments.getOrDefault("timezone", "UTC");
        try {
            ZoneId zoneId = ZoneId.of(tz);
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            return String.format("""
                    Current Time: %s
                    Date: %s
                    Timezone: %s
                    Unix Epoch: %d
                    ISO 8601: %s""",
                    now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (EEEE)")),
                    zoneId.getId(),
                    Instant.now().getEpochSecond(),
                    now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        } catch (Exception e) {
            return "Error: Invalid timezone '" + tz + "'. Use IANA format like 'America/New_York'.";
        }
    }
}
