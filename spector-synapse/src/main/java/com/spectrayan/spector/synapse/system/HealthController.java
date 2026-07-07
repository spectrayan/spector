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

import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.bridge.LlmBridge;
import com.spectrayan.spector.synapse.bridge.MemoryBridge;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check and diagnostics controller.
 */
@RestController
@RequestMapping("/api/v1/system")
public class HealthController {

    private final MemoryBridge memoryBridge;
    private final LlmBridge llmBridge;
    private final ToolRegistry toolRegistry;
    private final SynapseProperties props;
    private final Instant startTime = Instant.now();

    public HealthController(MemoryBridge memoryBridge, LlmBridge llmBridge,
                            ToolRegistry toolRegistry, SynapseProperties props) {
        this.memoryBridge = memoryBridge;
        this.llmBridge = llmBridge;
        this.toolRegistry = toolRegistry;
        this.props = props;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("uptime", Duration.between(startTime, Instant.now()).toString());
        result.put("timestamp", Instant.now().toString());

        // Component health
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("memory", Map.of(
                "status", memoryBridge.isAvailable() ? "UP" : "DEGRADED",
                "engine", memoryBridge.isAvailable() ? "SpectorMemory" : "stub"
        ));
        components.put("llm", Map.of(
                "status", "CONFIGURED",
                "model", llmBridge.modelName(),
                "baseUrl", llmBridge.baseUrl()
        ));
        components.put("tools", Map.of(
                "status", "UP",
                "count", toolRegistry.all().size()
        ));
        result.put("components", components);

        return result;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        var runtime = Runtime.getRuntime();
        var mx = ManagementFactory.getRuntimeMXBean();
        return Map.of(
                "jvm", Map.of(
                        "version", System.getProperty("java.version"),
                        "vendor", System.getProperty("java.vendor"),
                        "uptime", Duration.ofMillis(mx.getUptime()).toString(),
                        "heap", Map.of(
                                "used", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) + " MB",
                                "max", runtime.maxMemory() / (1024 * 1024) + " MB",
                                "total", runtime.totalMemory() / (1024 * 1024) + " MB"
                        ),
                        "processors", runtime.availableProcessors()
                ),
                "synapse", Map.of(
                        "tools", toolRegistry.all().size(),
                        "memoryEngine", memoryBridge.isAvailable() ? "active" : "stub",
                        "llmModel", llmBridge.modelName()
                )
        );
    }
}
