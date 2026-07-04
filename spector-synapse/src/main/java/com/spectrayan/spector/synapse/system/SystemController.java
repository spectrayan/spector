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
package com.spectrayan.spector.synapse.system;

import com.spectrayan.spector.synapse.config.SynapseProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * System status API — provides health and configuration information.
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final Instant startTime = Instant.now();
    private final SynapseProperties props;

    public SystemController(SynapseProperties props) {
        this.props = props;
    }

    /**
     * Get system status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Duration uptime = Duration.between(startTime, Instant.now());
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "version", "1.0.0-SNAPSHOT",
                "uptime", formatDuration(uptime),
                "uptimeSeconds", uptime.toSeconds(),
                "port", props.port(),
                "ollamaUrl", props.ollama().baseUrl(),
                "ollamaModel", props.ollama().model()
        ));
    }

    /**
     * Get configuration (non-sensitive).
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        return ResponseEntity.ok(Map.of(
                "port", props.port(),
                "dataDir", props.dataDir(),
                "ollama", Map.of(
                        "baseUrl", props.ollama().baseUrl(),
                        "model", props.ollama().model(),
                        "embedModel", props.ollama().embedModel()
                ),
                "cors", Map.of(
                        "allowedOrigins", props.cors().allowedOrigins()
                )
        ));
    }

    private String formatDuration(Duration d) {
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();
        return String.format("%dh %dm %ds", hours, minutes, seconds);
    }
}
