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
package com.spectrayan.spector.synapse.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.events.EmbeddingProjectionTelemetry.ProjectedPoint;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.ScoreBreakdown;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Architectural and integration contract test guaranteeing that Spector's
 * observability, system, metrics, and admin-reachable endpoints remain 100% content-free
 * (ADR-0083, Issue #1051).
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(roles = "admin")
@DisplayName("Content-Free Observability & Admin Contract Tests (ADR-0083, Issue #1051)")
class ContentFreeObservabilityContractTest {

    private static final String CANARY_MEMORY_TEXT = "CANARY_TOP_SECRET_COGNITIVE_ENGRAM_DO_NOT_LEAK_XYZ123";
    private static final String CANARY_TAG = "canary-classified-tag-456";
    private static final String CANARY_RECALL_TEXT = "CANARY_TOP_SECRET_RECALL_TEXT_DO_NOT_LEAK_789";

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private SpectorMemory memory;

    @MockitoBean
    private SpectorMemoryAdmin memoryAdmin;

    @MockitoBean
    private MemoryIndex memoryIndex;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        when(memory.namespaceId()).thenReturn("default");
        when(memory.totalMemories()).thenReturn(5);
        when(memory.memoryCount(any())).thenReturn(1);
        when(memory.admin()).thenReturn(memoryAdmin);
        when(memoryAdmin.index()).thenReturn(memoryIndex);
        when(memoryIndex.size()).thenReturn(5);
        when(memoryIndex.locationMap()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());

        byte[] canaryVector = new byte[128];
        Arrays.fill(canaryVector, (byte) 42);

        var canaryRecord = new CognitiveRecord(
                "mem-canary-001",
                CANARY_MEMORY_TEXT,
                MemoryType.SEMANTIC,
                MemorySource.USER_STATED,
                new String[]{CANARY_TAG},
                System.currentTimeMillis(),
                0L,
                0.95f,
                4.5f,
                2,
                1,
                (short) 0,
                (byte) 1,
                (byte) 0,
                1.0f,
                (byte) 0,
                (byte) 0,
                canaryVector,
                0,
                0L,
                Map.of("confidential", "true"),
                false
        );

        when(memoryAdmin.listAll()).thenReturn(new ArrayList<>(List.of(canaryRecord)));

        ScoreBreakdown breakdown = new ScoreBreakdown(0.92f, 0.85f, 1.2f, 0.0f, 0.1f, 0.05f, 0.88f);
        var canaryRecallResult = new CognitiveResult(
                "mem-canary-001",
                CANARY_RECALL_TEXT,
                0.88f,
                0.95f,
                0.1f,
                2,
                (byte) 1,
                MemoryType.SEMANTIC,
                MemorySource.USER_STATED,
                new String[]{CANARY_TAG},
                0.9f,
                0.9f,
                CognitiveResult.RetrievalMode.STANDARD,
                breakdown
        );
        when(memory.recall(any(String.class), any(com.spectrayan.spector.memory.model.RecallOptions.class)))
                .thenReturn(List.of(canaryRecallResult));
    }

    @Nested
    @DisplayName("ArchUnit Structural Bytecode Invariants")
    class ArchUnitRules {

        private final JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.spectrayan.spector.synapse.observability");

        @Test
        @DisplayName("Observability DTOs must never declare content fields (text, vector, tags, payload, metadata)")
        void observabilityDtosMustNotDeclareContentFields() {
            Set<String> bannedFieldNames = Set.of(
                    "text", "vector", "quantizedVector", "payload",
                    "tags", "metadata", "rawSecret", "secret", "ciphertext"
            );

            for (var clazz : classes) {
                if (clazz.getPackageName().contains(".dto")) {
                    for (JavaField field : clazz.getFields()) {
                        assertThat(bannedFieldNames)
                                .as("Class %s must not declare content field '%s'", clazz.getName(), field.getName())
                                .doesNotContain(field.getName().toLowerCase());
                    }
                }
            }
        }

        @Test
        @DisplayName("ObservabilityController endpoints must not return CognitiveRecord or MemoryTableRow")
        void endpointsMustNotReturnContentDomainTypes() {
            Set<String> bannedReturnTypes = Set.of(
                    "com.spectrayan.spector.memory.model.CognitiveRecord",
                    "com.spectrayan.spector.memory.model.CognitiveResult",
                    "com.spectrayan.spector.synapse.memory.MemoryDto$MemoryTableRow",
                    "com.spectrayan.spector.synapse.memory.MemoryDto$StoreResponse",
                    "com.spectrayan.spector.synapse.memory.MemoryDto$RecallResult"
            );

            var controllerClass = classes.get(ObservabilityController.class);
            for (JavaMethod method : controllerClass.getMethods()) {
                if (method.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC)) {
                    String returnTypeName = method.getReturnType().getName();
                    for (String banned : bannedReturnTypes) {
                        assertThat(returnTypeName)
                                .as("Endpoint %s in ObservabilityController must not return %s", method.getName(), banned)
                                .doesNotContain(banned);
                    }
                }
            }
        }

        @Test
        @DisplayName("ProjectedPoint must not declare label, text, tags, or vector fields")
        void projectedPointMustNotDeclareContentFields() {
            Set<String> bannedFields = Set.of("label", "text", "tags", "vector", "metadata");
            for (java.lang.reflect.Field field : ProjectedPoint.class.getDeclaredFields()) {
                assertThat(bannedFields)
                        .as("ProjectedPoint must not declare field '%s'", field.getName())
                        .doesNotContain(field.getName().toLowerCase());
            }
        }
    }

    @Nested
    @DisplayName("Endpoint Content-Free Contract Verification")
    class EndpointContractVerification {

        @Test
        @DisplayName("GET /api/v1/observability/timeline — does not leak memory text, tags, or metadata")
        void timelineDoesNotLeakContent() throws Exception {
            MvcResult result = mvc.perform(get("/api/v1/observability/timeline"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.events", notNullValue()))
                    .andExpect(jsonPath("$.totalEvents", is(1)))
                    .andExpect(jsonPath("$.events[0].memoryId", is("mem-canary-001")))
                    .andExpect(jsonPath("$.events[0].tier", is("SEMANTIC")))
                    .andExpect(jsonPath("$.events[0].importance", is(4.5)))
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody)
                    .as("Timeline payload must not contain canary memory text")
                    .doesNotContain(CANARY_MEMORY_TEXT)
                    .as("Timeline payload must not contain canary tags")
                    .doesNotContain(CANARY_TAG)
                    .as("Timeline payload must not declare metadata object")
                    .doesNotContain("\"metadata\"")
                    .as("Timeline payload must not declare text field")
                    .doesNotContain("\"text\"");

            JsonNode root = mapper.readTree(responseBody);
            JsonNode event = root.get("events").get(0);
            assertThat(event.has("metadata")).isFalse();
            assertThat(event.has("text")).isFalse();
            assertThat(event.has("tags")).isFalse();
            assertThat(event.has("vector")).isFalse();
        }

        @Test
        @DisplayName("POST /api/v1/observability/traced-recall — returns algorithmic scores without memory text")
        void tracedRecallDoesNotLeakText() throws Exception {
            Map<String, Object> request = Map.of(
                    "query", "canary query",
                    "topK", 5
            );

            MvcResult result = mvc.perform(post("/api/v1/observability/traced-recall")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results", notNullValue()))
                    .andExpect(jsonPath("$.totalResults", is(1)))
                    .andExpect(jsonPath("$.results[0].id", is("mem-canary-001")))
                    .andExpect(jsonPath("$.results[0].score", closeTo(0.88, 0.001)))
                    .andExpect(jsonPath("$.results[0].breakdown", notNullValue()))
                    .andExpect(jsonPath("$.results[0].breakdown.similarity", closeTo(0.92, 0.001)))
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody)
                    .as("Traced recall must not contain memory text")
                    .doesNotContain(CANARY_RECALL_TEXT)
                    .as("Traced recall must not contain 'text' property in result items")
                    .doesNotContain("\"text\":");

            JsonNode root = mapper.readTree(responseBody);
            JsonNode item = root.get("results").get(0);
            assertThat(item.has("text")).isFalse();
            assertThat(item.has("vector")).isFalse();
            assertThat(item.has("tags")).isFalse();
        }

        @Test
        @DisplayName("GET /api/v1/observability/stats — returns only numerical aggregations")
        void statsAreContentFree() throws Exception {
            MvcResult result = mvc.perform(get("/api/v1/observability/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalMemories", is(5)))
                    .andExpect(jsonPath("$.indexedMemories", is(5)))
                    .andExpect(jsonPath("$.tierDistribution.SEMANTIC", is(1)))
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody)
                    .doesNotContain(CANARY_MEMORY_TEXT)
                    .doesNotContain("\"text\"")
                    .doesNotContain("\"metadata\"");
        }

        @Test
        @DisplayName("GET /api/v1/observability/metrics/live — returns only telemetry rates")
        void liveMetricsAreContentFree() throws Exception {
            MvcResult result = mvc.perform(get("/api/v1/observability/metrics/live"))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody)
                    .doesNotContain(CANARY_MEMORY_TEXT)
                    .doesNotContain("\"text\"");
        }

        @Test
        @DisplayName("GET /api/v1/observability/age-distribution — returns only histogram buckets")
        void ageDistributionIsContentFree() throws Exception {
            MvcResult result = mvc.perform(get("/api/v1/observability/age-distribution"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.buckets", notNullValue()))
                    .andExpect(jsonPath("$.totalMemories", is(1)))
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody)
                    .doesNotContain(CANARY_MEMORY_TEXT)
                    .doesNotContain("\"text\"");
        }

        @Test
        @DisplayName("GET /api/v1/memory/vector-space/projection — points do not contain label with memory text")
        void vectorSpaceProjectionPointsDoNotLeakText() throws Exception {
            MvcResult result = mvc.perform(get("/api/v1/memory/vector-space/projection"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.points", notNullValue()))
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody)
                    .as("Projection payload must not contain canary memory text")
                    .doesNotContain(CANARY_MEMORY_TEXT);

            JsonNode root = mapper.readTree(responseBody);
            JsonNode points = root.get("points");
            if (points != null && points.isArray() && !points.isEmpty()) {
                JsonNode firstPoint = points.get(0);
                assertThat(firstPoint.has("label")).isFalse();
                assertThat(firstPoint.has("text")).isFalse();
                assertThat(firstPoint.has("id")).isTrue();
                assertThat(firstPoint.has("x")).isTrue();
                assertThat(firstPoint.has("y")).isTrue();
                assertThat(firstPoint.has("z")).isTrue();
            }
        }
    }
}
