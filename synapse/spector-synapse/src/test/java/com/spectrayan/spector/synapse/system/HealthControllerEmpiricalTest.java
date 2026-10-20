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

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.bridge.LlmBridge;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.memory.MemoryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Empirical Verification: HealthController Spector & Synapse Metrics")
class HealthControllerEmpiricalTest {

    private MemoryService memoryService;
    private LlmBridge llmBridge;
    private ToolRegistry toolRegistry;
    private SynapseProperties properties;
    private HealthController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        memoryService = mock(MemoryService.class);
        llmBridge = mock(LlmBridge.class);
        toolRegistry = mock(ToolRegistry.class);
        properties = mock(SynapseProperties.class);

        when(llmBridge.modelName()).thenReturn("qwen2.5:7b-instruct-q4_K_M");
        when(llmBridge.baseUrl()).thenReturn("http://localhost:11434");
        when(toolRegistry.all()).thenReturn(Map.of("search", mock(com.spectrayan.spector.mcp.tools.McpToolHandler.class)));
        when(memoryService.isEngineAvailable()).thenReturn(true);

        controller = new HealthController(memoryService, llmBridge, toolRegistry, properties);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Metrics returns spector key as primary and synapse key as backward-compatible alias")
    @SuppressWarnings("unchecked")
    void metricsReturnsSpectorAndSynapseKeys() {
        Map<String, Object> metrics = controller.metrics();

        assertThat(metrics).containsKey("spector");
        assertThat(metrics).containsKey("synapse");
        assertThat(metrics).containsKey("jvm");

        Map<String, Object> spectorMetrics = (Map<String, Object>) metrics.get("spector");
        Map<String, Object> synapseMetrics = (Map<String, Object>) metrics.get("synapse");

        assertThat(spectorMetrics).isNotNull();
        assertThat(synapseMetrics).isNotNull();

        assertThat(spectorMetrics).containsEntry("tools", 1);
        assertThat(spectorMetrics).containsEntry("memoryEngine", "active");
        assertThat(spectorMetrics).containsEntry("llmModel", "qwen2.5:7b-instruct-q4_K_M");

        // Strict parity between spector and synapse payloads
        assertThat(synapseMetrics).isEqualTo(spectorMetrics);
    }

    @Test
    @DisplayName("Metrics reports memoryEngine as stub when engine is unavailable")
    @SuppressWarnings("unchecked")
    void metricsReportsStubWhenEngineUnavailable() {
        when(memoryService.isEngineAvailable()).thenReturn(false);

        Map<String, Object> metrics = controller.metrics();
        Map<String, Object> spectorMetrics = (Map<String, Object>) metrics.get("spector");

        assertThat(spectorMetrics).containsEntry("memoryEngine", "stub");
        assertThat((Map<String, Object>) metrics.get("synapse")).containsEntry("memoryEngine", "stub");
    }

    @Test
    @DisplayName("MockMvc: GET /api/v1/system/metrics returns JSON payload with spector and synapse fields")
    void mockMvcMetricsEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/system/metrics")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spector").isMap())
                .andExpect(jsonPath("$.spector.tools").value(1))
                .andExpect(jsonPath("$.spector.memoryEngine").value("active"))
                .andExpect(jsonPath("$.spector.llmModel").value("qwen2.5:7b-instruct-q4_K_M"))
                .andExpect(jsonPath("$.synapse").isMap())
                .andExpect(jsonPath("$.synapse.tools").value(1))
                .andExpect(jsonPath("$.synapse.memoryEngine").value("active"))
                .andExpect(jsonPath("$.synapse.llmModel").value("qwen2.5:7b-instruct-q4_K_M"))
                .andExpect(jsonPath("$.jvm.version").isString());
    }

    @Test
    @DisplayName("MockMvc: GET /api/v1/system/health returns UP status and SpectorMemory component")
    void mockMvcHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/system/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.memory.status").value("UP"))
                .andExpect(jsonPath("$.components.memory.engine").value("SpectorMemory"))
                .andExpect(jsonPath("$.components.llm.model").value("qwen2.5:7b-instruct-q4_K_M"));
    }
}
