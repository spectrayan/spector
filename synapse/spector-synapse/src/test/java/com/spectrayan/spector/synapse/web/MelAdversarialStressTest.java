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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.RememberContext;
import com.spectrayan.spector.memory.model.ScoreBreakdown;
import com.spectrayan.spector.mel.MelEvaluator;
import com.spectrayan.spector.mel.MelParser;
import com.spectrayan.spector.mel.MelResult;
import com.spectrayan.spector.mel.MelResult.MelDiagnostic;
import com.spectrayan.spector.mel.MelStatement;
import com.spectrayan.spector.mel.MelStatement.PayloadType;
import com.spectrayan.spector.mel.MelStatement.RecallStmt;
import com.spectrayan.spector.mel.MelStatement.RememberStmt;
import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceAccessDeniedException;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceNotFoundException;
import com.spectrayan.spector.synapse.config.GlobalExceptionHandler;
import com.spectrayan.spector.synapse.memory.MemoryBinding;
import com.spectrayan.spector.synapse.memory.MemoryRequestBinder;
import com.spectrayan.spector.synapse.memory.RequestMemoryContext;
import com.spectrayan.spector.synapse.web.MelController.MelExecuteRequest;
import com.spectrayan.spector.synapse.web.MelController.MelValidateRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Adversarial Stress Testing: MelEvaluator & MelController")
class MelAdversarialStressTest {

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
                "tenant-adversarial",
                List.of(),
                "acc-adversarial",
                "ns-" + namespace,
                namespace,
                role,
                Set.of(),
                "sess-adversarial",
                List.of(),
                null,
                null,
                null
        );
        return new MemoryBinding(memory, "acc-adversarial", "ns-" + namespace, namespace, () -> {}, context);
    }

    // ═══════════════════════════════════════════════════════════════
    //  CHALLENGE 1: Unexpected Provenance Sources
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Challenge 1: Adversarial Provenance Sources")
    class ProvenanceSourceStressTests {

        @Test
        @DisplayName("mapSource never throws IllegalArgumentException across extreme inputs")
        void testMapSourceNeverThrows() {
            String[] testInputs = {
                    null, "", "   ", "\t\n\r",
                    "EXPERIENCED", "experienced", "eXpErIeNcEd", "  experienced  ",
                    "OBSERVED", "observed", "Observed",
                    "DISTILLED", "distilled", "DiStIlLeD",
                    "CONSOLIDATED", "consolidated", "Consolidated",
                    "REFLECTED", "reflected", "Reflected",
                    "SIMULATED", "simulated", "SiMuLaTeD",
                    "DREAMED", "dreamed", "Dreamed",
                    "USER_STATED", "user_stated", "User_Stated",
                    "PROCEDURAL", "procedural", "Procedural",
                    "REHEARSED", "rehearsed", "Rehearsed",
                    "TRANSFERRED", "transferred", "Transferred",
                    "INFERRED", "inferred", "Inferred",
                    "THOUGHT_EXPERIMENT", "thought_experiment", "Thought_Experiment",
                    "LANGEVIN_DISCOVERY", "langevin_discovery", "Langevin_Discovery",
                    "UNKNOWN_SOURCE_XYZ", "ALIEN_TELEPATHY", "quantum_teleportation",
                    "12345", "!@#$%^&*()_+=-~`", "\u0000\u0001\uFFFF",
                    "A".repeat(5000)
            };

            for (String input : testInputs) {
                assertThatCode(() -> {
                    MemorySource source = MelEvaluator.mapSource(input);
                    assertThat(source).isNotNull();
                }).as("mapSource failed on input: '%s'", input).doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("Unknown, null, and empty sources strictly default to OBSERVED")
        void testUnknownSourcesDefaultToObserved() {
            assertThat(MelEvaluator.mapSource(null)).isEqualTo(MemorySource.OBSERVED);
            assertThat(MelEvaluator.mapSource("")).isEqualTo(MemorySource.OBSERVED);
            assertThat(MelEvaluator.mapSource("   ")).isEqualTo(MemorySource.OBSERVED);
            assertThat(MelEvaluator.mapSource("NON_EXISTENT_SOURCE_12345")).isEqualTo(MemorySource.OBSERVED);
            assertThat(MelEvaluator.mapSource("random_junk_value")).isEqualTo(MemorySource.OBSERVED);
            assertThat(MelEvaluator.mapSource("!@#$%^&*()")).isEqualTo(MemorySource.OBSERVED);
        }

        @Test
        @DisplayName("All case variations of aliases map cleanly to their intended MemorySource")
        void testCaseVariationsOfAliases() {
            assertThat(MelEvaluator.mapSource("experienced")).isEqualTo(MemorySource.OBSERVED);
            assertThat(MelEvaluator.mapSource("ExPeRiEnCeD")).isEqualTo(MemorySource.OBSERVED);
            assertThat(MelEvaluator.mapSource("distilled")).isEqualTo(MemorySource.REFLECTED);
            assertThat(MelEvaluator.mapSource("DiStIlLeD")).isEqualTo(MemorySource.REFLECTED);
            assertThat(MelEvaluator.mapSource("consolidated")).isEqualTo(MemorySource.REFLECTED);
            assertThat(MelEvaluator.mapSource("CONSOLIDATED")).isEqualTo(MemorySource.REFLECTED);
            assertThat(MelEvaluator.mapSource("simulated")).isEqualTo(MemorySource.DREAMED);
            assertThat(MelEvaluator.mapSource("SiMuLaTeD")).isEqualTo(MemorySource.DREAMED);
            assertThat(MelEvaluator.mapSource("rehearsed")).isEqualTo(MemorySource.TRANSFERRED);
            assertThat(MelEvaluator.mapSource("ReHeArSeD")).isEqualTo(MemorySource.TRANSFERRED);
        }

        @Test
        @DisplayName("evalRemember evaluates RememberStmt with unexpected source without throwing")
        void testEvalRememberWithUnexpectedSource() {
            MelEvaluator evaluator = new MelEvaluator(memory);

            String[] sources = {"UNKNOWN_XYZ", "eXpErIeNcEd", "distilled", null, "", "   ", "!@#$"};
            for (String src : sources) {
                reset(memory);
                RememberStmt stmt = new RememberStmt(
                        PayloadType.TEXT,
                        "Stress test content",
                        "EPISODIC",
                        Optional.of("stress-id"),
                        List.of("tag1"),
                        OptionalDouble.of(0.8),
                        OptionalDouble.of(0.2),
                        OptionalDouble.of(0.5),
                        Optional.ofNullable(src),
                        Optional.of(false),
                        Optional.of(false)
                );

                MelResult result = evaluator.evaluate(stmt);
                assertThat(result.isOk()).isTrue();
                assertThat(result.statementType()).isEqualTo("REMEMBER");

                ArgumentCaptor<MemorySource> sourceCaptor = ArgumentCaptor.forClass(MemorySource.class);
                verify(memory).remember(
                        eq("stress-id"),
                        eq("Stress test content"),
                        eq(MemoryType.EPISODIC),
                        sourceCaptor.capture(),
                        any(RememberContext.class),
                        any(String[].class)
                );

                assertThat(sourceCaptor.getValue()).isNotNull();
                if ("distilled".equals(src)) {
                    assertThat(sourceCaptor.getValue()).isEqualTo(MemorySource.REFLECTED);
                } else {
                    assertThat(sourceCaptor.getValue()).isEqualTo(MemorySource.OBSERVED);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CHALLENGE 2: Multi-Statement Scripts, Comments, Semicolons & Diagnostics
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Challenge 2: Comments, Semicolons in Strings & Diagnostics")
    class MultiStatementScriptStressTests {

        @Test
        @DisplayName("Script with single-line comments, block comments, and semicolons in strings parses correctly")
        void testComplexCommentsAndSemicolonsInStrings() {
            String script = """
                    -- Leading single-line comment
                    REMEMBER TEXT 'Statement 1; has ; semicolons; in string' INTO EPISODIC ID 's1' TAGS ['tag;1', 'tag2'];
                    /* Multi-line block comment
                       with code inside that must be ignored:
                       REMEMBER TEXT 'fake' INTO WORKING;
                    */
                    RECALL 'Search cue; with semicolon; and -- not a comment' TOP 5;
                    -- Trailing comment without newline""";

            List<MelStatement> statements = MelParser.parseScript(script);
            assertThat(statements).hasSize(2);

            assertThat(statements.get(0)).isInstanceOf(RememberStmt.class);
            RememberStmt rem = (RememberStmt) statements.get(0);
            assertThat(rem.payloadValue()).isEqualTo("Statement 1; has ; semicolons; in string");
            assertThat(rem.id()).contains("s1");
            assertThat(rem.tags()).containsExactly("tag;1", "tag2");

            assertThat(statements.get(1)).isInstanceOf(RecallStmt.class);
            RecallStmt rec = (RecallStmt) statements.get(1);
            assertThat(rec.query()).contains("Search cue; with semicolon; and -- not a comment");
            assertThat(rec.topK()).isEqualTo(5);
        }

        @Test
        @DisplayName("Syntax error in statement N reports exact line and column coordinates")
        void testSyntaxErrorInStatementNReportsAccurateCoordinates() {
            String script = """
                    REMEMBER TEXT 'Alpha fact' INTO SEMANTIC ID 'fact-alpha';
                    /* Valid recall statement with semicolons inside string */
                    RECALL 'database; connection; failure' TOP 10;
                    -- Now statement 3 contains a syntax error:
                    FORGET ;
                    """;

            // Statement 1: line 1
            // Statement 2: lines 2-3
            // Statement 3: line 5 (FORGET ;) -> missing IDs
            MelParser.MelParseException parseException = null;
            try {
                MelParser.parseScript(script);
            } catch (MelParser.MelParseException e) {
                parseException = e;
            }

            assertThat(parseException).isNotNull();
            assertThat(parseException.line()).isEqualTo(5);
            assertThat(parseException.column()).isEqualTo(8); // position of ';' token on line 5
            assertThat(parseException.getMessage()).contains("MEL parse error at 5:8");

            // Verify MelEvaluator.evaluateScript catches this and returns MelDiagnostic with matching line & col
            MelEvaluator evaluator = new MelEvaluator(memory);
            List<MelResult> evalResults = evaluator.evaluateScript(script);
            assertThat(evalResults).hasSize(1);
            MelResult errResult = evalResults.get(0);
            assertThat(errResult.isOk()).isFalse();
            assertThat(errResult.status()).isEqualTo(MelResult.Status.ERROR);
            assertThat(errResult.diagnostics()).hasSize(1);
            MelResult.MelDiagnostic diag = errResult.diagnostics().get(0);
            assertThat(diag.line()).isEqualTo(5);
            assertThat(diag.column()).isEqualTo(8);
            assertThat(diag.severity()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("Validate endpoint returns line and column diagnostics on statement N failure")
        void testValidateStatementNFailure() throws Exception {
            String script = """
                    REMEMBER TEXT 'Valid 1' INTO EPISODIC ID 'v-1';
                    RECALL 'Valid 2' TOP 3;
                    REMEMBER TEXT 'Missing INTO clause' EPISODIC;
                    """;

            MelValidateRequest req = new MelValidateRequest(script);

            mockMvc.perform(post("/api/v1/mel/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$.statement_count").value(0))
                    .andExpect(jsonPath("$.diagnostics", hasSize(1)))
                    .andExpect(jsonPath("$.diagnostics[0].line").value(3))
                    .andExpect(jsonPath("$.diagnostics[0].column").isNumber())
                    .andExpect(jsonPath("$.diagnostics[0].severity").value("ERROR"));
        }

        @Test
        @DisplayName("Execute endpoint returns 200 OK with ERROR status and diagnostics on syntax error in statement N")
        void testExecuteStatementNFailure() throws Exception {
            String script = """
                    REMEMBER TEXT 'Valid 1' INTO EPISODIC ID 'v-1';
                    FORGET ;
                    """;

            MelExecuteRequest req = new MelExecuteRequest(script, "test-ns");

            mockMvc.perform(post("/api/v1/mel/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ERROR"))
                    .andExpect(jsonPath("$.diagnostics", hasSize(1)))
                    .andExpect(jsonPath("$.diagnostics[0].line").value(2))
                    .andExpect(jsonPath("$.diagnostics[0].column").value(8))
                    .andExpect(jsonPath("$.namespace").value("test-ns"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CHALLENGE 3: RBAC Permissions & Namespace Bound Isolation
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Challenge 3: RBAC Permissions & Namespace Isolation")
    class RbacAndNamespaceStressTests {

        @Test
        @DisplayName("READER role can execute RECALL, EXPLAIN RECALL, and INTROSPECT")
        void testReaderCanExecuteReadOperations() throws Exception {
            MemoryBinding binding = createBinding(GrantRole.READER, "read-only-ns");
            when(binder.bind(any(), eq(Optional.of("read-only-ns")))).thenReturn(binding);
            doNothing().when(binder).unbind(binding);

            when(memory.totalMemories()).thenReturn(10);
            when(memory.recall(eq("cue"), any(RecallOptions.class))).thenReturn(List.of());

            // 1. RECALL
            mockMvc.perform(post("/api/v1/mel/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new MelExecuteRequest("RECALL 'cue' TOP 5;", "read-only-ns"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"))
                    .andExpect(jsonPath("$.statement_type").value("RECALL"));

            // 2. EXPLAIN RECALL
            mockMvc.perform(post("/api/v1/mel/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new MelExecuteRequest("EXPLAIN RECALL 'cue' TOP 5;", "read-only-ns"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"))
                    .andExpect(jsonPath("$.statement_type").value("EXPLAIN_RECALL"));

            // 3. INTROSPECT
            mockMvc.perform(post("/api/v1/mel/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new MelExecuteRequest("INTROSPECT;", "read-only-ns"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"))
                    .andExpect(jsonPath("$.statement_type").value("INTROSPECT"));

            verify(binder, times(3)).unbind(binding);
        }

        @Test
        @DisplayName("READER role is strictly rejected on REMEMBER, CONSOLIDATE, and FORGET")
        void testReaderDeniedOnMutatingStatements() throws Exception {
            MemoryBinding binding = createBinding(GrantRole.READER, "locked-ns");
            when(binder.bind(any(), eq(Optional.of("locked-ns")))).thenReturn(binding);
            doNothing().when(binder).unbind(binding);

            String[] mutatingScripts = {
                    "REMEMBER TEXT 'Forbidden write' INTO EPISODIC;",
                    "CONSOLIDATE 'e-1' INTO SEMANTIC;",
                    "FORGET 'e-1' TOMBSTONE;",
                    "FORGET 'e-1' WEAKEN;",
                    "FORGET 'e-1' SUPPRESS;"
            };

            for (String script : mutatingScripts) {
                mockMvc.perform(post("/api/v1/mel/execute")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new MelExecuteRequest(script, "locked-ns"))))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.error").value("SPE-003-001"))
                        .andExpect(jsonPath("$.message", containsString("Write permission (GrantRole.WRITER) required")));
            }

            // Verify no mutations ever reached the memory engine
            verify(memory, never()).remember(any(String.class), any(String.class), any(MemoryType.class), any(MemorySource.class), any(RememberContext.class), any(String[].class));
            verify(memory, never()).consolidate();
            verify(memory, never()).forget(any());
            verify(memory, never()).suppress(any(), any());
            verify(memory, never()).reinforce(any(), any(byte.class));
            verify(binder, times(mutatingScripts.length)).unbind(binding);
        }

        @Test
        @DisplayName("Hybrid script containing read + mutate is rejected in its entirety for READER")
        void testHybridScriptRejectedCompletelyForReader() throws Exception {
            MemoryBinding binding = createBinding(GrantRole.READER, "hybrid-ns");
            when(binder.bind(any(), eq(Optional.of("hybrid-ns")))).thenReturn(binding);
            doNothing().when(binder).unbind(binding);

            String hybridScript = """
                    RECALL 'initial read' TOP 5;
                    REMEMBER TEXT 'injected exploit memory' INTO EPISODIC ID 'exploit-1';
                    RECALL 'second read' TOP 5;
                    """;

            mockMvc.perform(post("/api/v1/mel/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new MelExecuteRequest(hybridScript, "hybrid-ns"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("SPE-003-001"));

            // Verify zero execution: neither recall nor remember was called
            verify(memory, never()).recall(any(String.class), any(RecallOptions.class));
            verify(memory, never()).remember(any(String.class), any(String.class), any(MemoryType.class), any(MemorySource.class), any(RememberContext.class), any(String[].class));
            verify(binder).unbind(binding);
        }

        @Test
        @DisplayName("Caller cannot escape namespace bounds: unauthorized namespace throws 403 or 404")
        void testCallerCannotEscapeNamespaceBounds() throws Exception {
            // Case 1: Unauthorized namespace (Access Denied)
            when(binder.bind(any(), eq(Optional.of("foreign-tenant-ns"))))
                    .thenThrow(new NamespaceAccessDeniedException("foreign-tenant-ns", "acc-adversarial"));

            mockMvc.perform(post("/api/v1/mel/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new MelExecuteRequest("RECALL 'cue' TOP 5;", "foreign-tenant-ns"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("SPE-800-002"));

            // Case 2: Non-existent namespace
            when(binder.bind(any(), eq(Optional.of("non-existent-ns"))))
                    .thenThrow(new NamespaceNotFoundException("non-existent-ns"));

            mockMvc.perform(post("/api/v1/mel/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new MelExecuteRequest("RECALL 'cue' TOP 5;", "non-existent-ns"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SPE-800-001"));

            // Case 3: Wildcard escape attempt ('*' or commas)
            when(binder.bind(any(), eq(Optional.of("*"))))
                    .thenThrow(new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Wildcard '*' or multi-namespace selection is not permitted"));

            mockMvc.perform(post("/api/v1/mel/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new MelExecuteRequest("RECALL 'cue' TOP 5;", "*"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SPE-100-013"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CHALLENGE 4: Concurrency & Virtual Thread Stress
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Challenge 4: Concurrency & Virtual Thread Lock Safety")
    class ConcurrencyStressTests {

        @Test
        @DisplayName("Concurrent execution requests across virtual threads do not deadlock or leak bindings")
        void testConcurrentVirtualThreads() throws Exception {
            int threadCount = 20;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            MemoryBinding binding = createBinding(GrantRole.WRITER, "concurrent-ns");
            when(binder.bind(any(), eq(Optional.of("concurrent-ns")))).thenReturn(binding);
            doNothing().when(binder).unbind(binding);
            when(memory.totalMemories()).thenReturn(100);
            when(memory.recall(any(String.class), any(RecallOptions.class))).thenReturn(List.of());

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        String script = "RECALL 'thread-" + idx + "' TOP 5;";
                        mockMvc.perform(post("/api/v1/mel/execute")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(new MelExecuteRequest(script, "concurrent-ns"))))
                                .andExpect(status().isOk());
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);

            assertThat(completed).isTrue();
            assertThat(successCount.get()).isEqualTo(threadCount);
            verify(binder, times(threadCount)).unbind(binding);
            executor.shutdown();
        }
    }
}
