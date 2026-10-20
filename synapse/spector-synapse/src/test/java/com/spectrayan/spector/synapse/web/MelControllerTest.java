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
package com.spectrayan.spector.synapse.web;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.RememberContext;
import com.spectrayan.spector.memory.model.ScoreBreakdown;
import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.config.GlobalExceptionHandler;
import com.spectrayan.spector.synapse.memory.MemoryBinding;
import com.spectrayan.spector.synapse.memory.MemoryRequestBinder;
import com.spectrayan.spector.synapse.memory.RequestMemoryContext;
import com.spectrayan.spector.synapse.web.MelController.MelExecuteRequest;
import com.spectrayan.spector.synapse.web.MelController.MelValidateRequest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("MelController REST API Tests")
class MelControllerTest {

    private MockMvc mockMvc;
    private MemoryRequestBinder binder;
    private SpectorMemory memory;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        binder = mock(MemoryRequestBinder.class);
        memory = mock(SpectorMemory.class);
        objectMapper = new ObjectMapper();

        MelController controller = new MelController(binder);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private MemoryBinding createBinding(GrantRole role, String namespace) {
        RequestMemoryContext context = new RequestMemoryContext(
                "tenant-test",
                List.of(),
                "acc-test",
                "ns-" + namespace,
                namespace,
                role,
                Set.of(),
                "sess-test",
                List.of(),
                null,
                null,
                null
        );
        return new MemoryBinding(memory, "acc-test", "ns-" + namespace, namespace, () -> {}, context);
    }

    // ═══════════════════════════════════════════════════════════════
    //  POST /api/v1/mel/execute Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Execute Endpoint (/api/v1/mel/execute)")
    class ExecuteEndpointTests {

        @Test
        @DisplayName("execute with blank script returns 400 Bad Request")
        void testExecuteBlankScriptReturnsBadRequest() throws Exception {
            MelExecuteRequest req = new MelExecuteRequest("   ", "default");

            mockMvc.perform(post("/api/v1/mel/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SPE-100-007"));
        }

        @Test
        @DisplayName("execute with null request body returns 400 Bad Request")
        void testExecuteNullRequestBodyReturnsBadRequest() throws Exception {
            mockMvc.perform(post("/api/v1/mel/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SPE-100-007"));
        }

        @Test
        @DisplayName("execute with syntax error returns 200 OK with ERROR status and diagnostics")
        void testExecuteSyntaxErrorReturnsOkWithErrorStatus() throws Exception {
            MelExecuteRequest req = new MelExecuteRequest("INVALID_KEYWORD foo;", "default");

            mockMvc.perform(post("/api/v1/mel/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ERROR"))
                    .andExpect(jsonPath("$.diagnostics", hasSize(1)))
                    .andExpect(jsonPath("$.diagnostics[0].severity").value("ERROR"))
                    .andExpect(jsonPath("$.diagnostics[0].line").value(1));
        }

        @Test
        @DisplayName("execute read-only RECALL with READER role succeeds")
        void testExecuteReadOnlyWithReaderRoleSucceeds() throws Exception {
            MemoryBinding binding = createBinding(GrantRole.READER, "eng-team");
            when(binder.bind(any(), eq(Optional.of("eng-team")))).thenReturn(binding);
            doNothing().when(binder).unbind(binding);

            ScoreBreakdown breakdown = new ScoreBreakdown(0.9f, 0.8f, 1.1f, 1.0f, 1.2f, 1.0f, 0.95f);
            CognitiveResult hit = new CognitiveResult(
                    "engram-42",
                    "Kafka broker failover logic",
                    0.92f,
                    0.88f,
                    1.4f,
                    2,
                    (byte) -10,
                    MemoryType.EPISODIC,
                    MemorySource.OBSERVED,
                    new String[]{"kafka", "infra"},
                    0.85f,
                    0.90f,
                    CognitiveResult.RetrievalMode.STANDARD,
                    breakdown,
                    null,
                    null,
                    null,
                    (byte) 0,
                    1770000000000L,
                    false
            );

            when(memory.totalMemories()).thenReturn(50);
            when(memory.recall(eq("kafka failover"), any(RecallOptions.class))).thenReturn(List.of(hit));

            MelExecuteRequest req = new MelExecuteRequest("RECALL 'kafka failover' TOP 5;", "eng-team");

            mockMvc.perform(post("/api/v1/mel/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"))
                    .andExpect(jsonPath("$.statement_type").value("RECALL"))
                    .andExpect(jsonPath("$.namespace").value("eng-team"))
                    .andExpect(jsonPath("$.affected_ids[0]").value("engram-42"))
                    .andExpect(jsonPath("$.candidates", hasSize(1)))
                    .andExpect(jsonPath("$.candidates[0].id").value("engram-42"))
                    .andExpect(jsonPath("$.candidates[0].tier").value("EPISODIC"))
                    .andExpect(jsonPath("$.funnel.totalScanned").value(50))
                    .andExpect(jsonPath("$.funnel.phase6FinalReturned").value(1))
                    .andExpect(jsonPath("$.funnel_phases", hasSize(6)))
                    .andExpect(jsonPath("$.statements", hasSize(1)))
                    .andExpect(jsonPath("$.statements[0].statement_type").value("RECALL"));

            verify(binder).unbind(binding);
        }

        @Test
        @DisplayName("execute EXPLAIN RECALL with READER role succeeds and populates explain rows")
        void testExecuteExplainRecallWithReaderRoleSucceeds() throws Exception {
            MemoryBinding binding = createBinding(GrantRole.READER, "qa-ns");
            when(binder.bind(any(), eq(Optional.of("qa-ns")))).thenReturn(binding);
            doNothing().when(binder).unbind(binding);

            ScoreBreakdown breakdown = new ScoreBreakdown(0.95f, 0.9f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f);
            CognitiveResult hit = new CognitiveResult(
                    "eng-explain-1",
                    "Simulated memory trace",
                    0.96f,
                    0.90f,
                    1.2f,
                    1,
                    (byte) 20,
                    MemoryType.SEMANTIC,
                    MemorySource.REFLECTED,
                    new String[]{"theory"},
                    0.92f,
                    0.95f,
                    CognitiveResult.RetrievalMode.STANDARD,
                    breakdown,
                    null,
                    null,
                    null,
                    (byte) 0,
                    1770000000000L,
                    false
            );

            when(memory.totalMemories()).thenReturn(20);
            when(memory.recall(eq("test cue"), any(RecallOptions.class))).thenReturn(List.of(hit));

            MelExecuteRequest req = new MelExecuteRequest("EXPLAIN RECALL 'test cue' TOP 3;", "qa-ns");

            mockMvc.perform(post("/api/v1/mel/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"))
                    .andExpect(jsonPath("$.statement_type").value("EXPLAIN_RECALL"))
                    .andExpect(jsonPath("$.explain_rows", hasSize(1)))
                    .andExpect(jsonPath("$.explain_rows[0].memoryId").value("eng-explain-1"))
                    .andExpect(jsonPath("$.explain_rows[0].tier").value("SEMANTIC"));

            verify(binder).unbind(binding);
        }

        @Test
        @DisplayName("execute mutating REMEMBER with READER role throws 403 Forbidden")
        void testExecuteMutatingWithReaderRoleThrowsForbidden() throws Exception {
            MemoryBinding binding = createBinding(GrantRole.READER, "sandbox");
            when(binder.bind(any(), eq(Optional.of("sandbox")))).thenReturn(binding);
            doNothing().when(binder).unbind(binding);

            MelExecuteRequest req = new MelExecuteRequest(
                    "REMEMBER TEXT 'New insight' INTO EPISODIC;",
                    "sandbox"
            );

            mockMvc.perform(post("/api/v1/mel/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("SPE-003-001"))
                    .andExpect(jsonPath("$.message", containsString("Write permission (GrantRole.WRITER) required")));

            verify(binder).unbind(binding);
        }

        @Test
        @DisplayName("execute mutating CONSOLIDATE with READER role throws 403 Forbidden")
        void testExecuteConsolidateWithReaderRoleThrowsForbidden() throws Exception {
            MemoryBinding binding = createBinding(GrantRole.READER, "sandbox");
            when(binder.bind(any(), eq(Optional.of("sandbox")))).thenReturn(binding);
            doNothing().when(binder).unbind(binding);

            MelExecuteRequest req = new MelExecuteRequest(
                    "CONSOLIDATE 'ep-1' INTO SEMANTIC;",
                    "sandbox"
            );

            mockMvc.perform(post("/api/v1/mel/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("SPE-003-001"));

            verify(binder).unbind(binding);
        }

        @Test
        @DisplayName("execute mutating FORGET with READER role throws 403 Forbidden")
        void testExecuteForgetWithReaderRoleThrowsForbidden() throws Exception {
            MemoryBinding binding = createBinding(GrantRole.READER, "sandbox");
            when(binder.bind(any(), eq(Optional.of("sandbox")))).thenReturn(binding);
            doNothing().when(binder).unbind(binding);

            MelExecuteRequest req = new MelExecuteRequest(
                    "FORGET 'ep-1' TOMBSTONE;",
                    "sandbox"
            );

            mockMvc.perform(post("/api/v1/mel/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("SPE-003-001"));

            verify(binder).unbind(binding);
        }

        @Test
        @DisplayName("execute mutating REMEMBER with WRITER role succeeds")
        void testExecuteMutatingWithWriterRoleSucceeds() throws Exception {
            MemoryBinding binding = createBinding(GrantRole.WRITER, "prod-ns");
            when(binder.bind(any(), eq(Optional.of("prod-ns")))).thenReturn(binding);
            doNothing().when(binder).unbind(binding);

            doNothing().when(memory).remember(eq("m-99"), eq("Zero downtime deploy"), eq(MemoryType.EPISODIC),
                    eq(MemorySource.OBSERVED), any(RememberContext.class), any(String[].class));

            MelExecuteRequest req = new MelExecuteRequest(
                    "REMEMBER TEXT 'Zero downtime deploy' INTO EPISODIC ID 'm-99' TAGS ['deploy', 'prod'] IMPORTANCE 0.95 SOURCE EXPERIENCED;",
                    "prod-ns"
            );

            mockMvc.perform(post("/api/v1/mel/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"))
                    .andExpect(jsonPath("$.statement_type").value("REMEMBER"))
                    .andExpect(jsonPath("$.affected_ids[0]").value("m-99"))
                    .andExpect(jsonPath("$.output", containsString("m-99")));

            verify(binder).unbind(binding);
        }

        @Test
        @DisplayName("execute multi-statement script with WRITER role succeeds and concatenates results")
        void testExecuteMultiStatementScriptSucceeds() throws Exception {
            MemoryBinding binding = createBinding(GrantRole.WRITER, "multi-ns");
            when(binder.bind(any(), eq(Optional.of("multi-ns")))).thenReturn(binding);
            doNothing().when(binder).unbind(binding);

            doNothing().when(memory).remember(eq("f-1"), eq("Fact 1"), eq(MemoryType.SEMANTIC),
                    eq(MemorySource.OBSERVED), any(RememberContext.class), any(String[].class));
            when(memory.totalMemories()).thenReturn(10);
            when(memory.recall(eq("Fact 1"), any(RecallOptions.class))).thenReturn(List.of());

            String script = "REMEMBER TEXT 'Fact 1' INTO SEMANTIC ID 'f-1';\nRECALL 'Fact 1' TOP 3;";
            MelExecuteRequest req = new MelExecuteRequest(script, "multi-ns");

            mockMvc.perform(post("/api/v1/mel/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"))
                    .andExpect(jsonPath("$.statement_type").value("SCRIPT"))
                    .andExpect(jsonPath("$.statements", hasSize(2)))
                    .andExpect(jsonPath("$.statements[0].statement_type").value("REMEMBER"))
                    .andExpect(jsonPath("$.statements[1].statement_type").value("RECALL"))
                    .andExpect(jsonPath("$.affected_ids[0]").value("f-1"));

            verify(binder).unbind(binding);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  POST /api/v1/mel/validate Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Validate Endpoint (/api/v1/mel/validate)")
    class ValidateEndpointTests {

        @Test
        @DisplayName("validate with blank script returns valid response with 0 statements")
        void testValidateBlankScriptReturnsValid() throws Exception {
            MelValidateRequest req = new MelValidateRequest("   ");

            mockMvc.perform(post("/api/v1/mel/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.statement_count").value(0))
                    .andExpect(jsonPath("$.statements", empty()))
                    .andExpect(jsonPath("$.diagnostics", empty()));
        }

        @Test
        @DisplayName("validate valid multi-statement script returns statement summaries")
        void testValidateValidScriptReturnsSummaries() throws Exception {
            String script = "REMEMBER TEXT 'Test memory' INTO EPISODIC ID 'e-1';\nRECALL 'Test memory' TOP 5;";
            MelValidateRequest req = new MelValidateRequest(script);

            mockMvc.perform(post("/api/v1/mel/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.statement_count").value(2))
                    .andExpect(jsonPath("$.statements", hasSize(2)))
                    .andExpect(jsonPath("$.statements[0].type").value("REMEMBER"))
                    .andExpect(jsonPath("$.statements[1].type").value("RECALL"))
                    .andExpect(jsonPath("$.diagnostics", empty()));
        }

        @Test
        @DisplayName("validate invalid script returns valid=false and line/column diagnostics")
        void testValidateInvalidScriptReturnsDiagnostics() throws Exception {
            MelValidateRequest req = new MelValidateRequest("FORGET ;");

            mockMvc.perform(post("/api/v1/mel/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$.statement_count").value(0))
                    .andExpect(jsonPath("$.diagnostics", hasSize(1)))
                    .andExpect(jsonPath("$.diagnostics[0].severity").value("ERROR"))
                    .andExpect(jsonPath("$.diagnostics[0].line").value(1))
                    .andExpect(jsonPath("$.diagnostics[0].column").isNumber());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /api/v1/mel/schema Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Schema Endpoint (/api/v1/mel/schema)")
    class SchemaEndpointTests {

        @Test
        @DisplayName("schema returns complete language keywords, tiers, sources, and snippets")
        void testSchemaReturnsStandardLibrary() throws Exception {
            mockMvc.perform(get("/api/v1/mel/schema"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.keywords", hasItem("REMEMBER")))
                    .andExpect(jsonPath("$.keywords", hasItem("RECALL")))
                    .andExpect(jsonPath("$.keywords", hasItem("EXPLAIN")))
                    .andExpect(jsonPath("$.keywords", hasItem("CONSOLIDATE")))
                    .andExpect(jsonPath("$.keywords", hasItem("FORGET")))
                    .andExpect(jsonPath("$.keywords", hasItem("INTROSPECT")))
                    .andExpect(jsonPath("$.tiers", hasItem("WORKING")))
                    .andExpect(jsonPath("$.tiers", hasItem("EPISODIC")))
                    .andExpect(jsonPath("$.tiers", hasItem("SEMANTIC")))
                    .andExpect(jsonPath("$.tiers", hasItem("PROCEDURAL")))
                    .andExpect(jsonPath("$.sources", hasItem("EXPERIENCED")))
                    .andExpect(jsonPath("$.sources", hasItem("OBSERVED")))
                    .andExpect(jsonPath("$.clause_templates", notNullValue()))
                    .andExpect(jsonPath("$.snippets", notNullValue()))
                    .andExpect(jsonPath("$.snippet_library", notNullValue()));
        }
    }
}
