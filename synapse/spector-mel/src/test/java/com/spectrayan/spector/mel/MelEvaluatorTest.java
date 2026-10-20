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
package com.spectrayan.spector.mel;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.RememberContext;
import com.spectrayan.spector.memory.model.ScoreBreakdown;
import com.spectrayan.spector.mel.MelResult.FunnelTelemetry;
import com.spectrayan.spector.mel.MelStatement.ExplainRecallStmt;
import com.spectrayan.spector.mel.MelStatement.PayloadType;
import com.spectrayan.spector.mel.MelStatement.RecallStmt;
import com.spectrayan.spector.mel.MelStatement.RememberStmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MelEvaluator Unit Tests")
class MelEvaluatorTest {

    private SpectorMemory memory;
    private MelEvaluator evaluator;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        evaluator = new MelEvaluator(memory);
    }

    @Nested
    @DisplayName("Source Mapping")
    class SourceMappingTests {

        @Test
        @DisplayName("mapSource correctly maps standard and alias sources without throwing")
        void testMapSourceMappings() {
            assertThat(MelEvaluator.mapSource("EXPERIENCED")).isEqualTo(MemorySource.OBSERVED);
            assertThat(MelEvaluator.mapSource("OBSERVED")).isEqualTo(MemorySource.OBSERVED);
            assertThat(MelEvaluator.mapSource("DISTILLED")).isEqualTo(MemorySource.REFLECTED);
            assertThat(MelEvaluator.mapSource("CONSOLIDATED")).isEqualTo(MemorySource.REFLECTED);
            assertThat(MelEvaluator.mapSource("REFLECTED")).isEqualTo(MemorySource.REFLECTED);
            assertThat(MelEvaluator.mapSource("SIMULATED")).isEqualTo(MemorySource.DREAMED);
            assertThat(MelEvaluator.mapSource("DREAMED")).isEqualTo(MemorySource.DREAMED);
            assertThat(MelEvaluator.mapSource("USER_STATED")).isEqualTo(MemorySource.USER_STATED);
            assertThat(MelEvaluator.mapSource("PROCEDURAL")).isEqualTo(MemorySource.PROCEDURAL);
            assertThat(MelEvaluator.mapSource("REHEARSED")).isEqualTo(MemorySource.TRANSFERRED);
            assertThat(MelEvaluator.mapSource("TRANSFERRED")).isEqualTo(MemorySource.TRANSFERRED);
            assertThat(MelEvaluator.mapSource("INFERRED")).isEqualTo(MemorySource.INFERRED);
            assertThat(MelEvaluator.mapSource(null)).isEqualTo(MemorySource.OBSERVED);
            assertThat(MelEvaluator.mapSource("")).isEqualTo(MemorySource.OBSERVED);
            assertThat(MelEvaluator.mapSource("NON_EXISTENT_SOURCE_XYZ")).isEqualTo(MemorySource.OBSERVED);
        }
    }

    @Nested
    @DisplayName("REMEMBER Evaluation")
    class RememberTests {

        @Test
        @DisplayName("evalRemember honors all fields including id, tags, importance, valence, arousal, pin, unresolved")
        void testRememberHonorsAllFields() {
            RememberStmt stmt = new RememberStmt(
                    PayloadType.TEXT,
                    "Redis pool exhausted during spike",
                    "EPISODIC",
                    Optional.of("ep-redis-01"),
                    List.of("infra", "redis"),
                    OptionalDouble.of(0.85),
                    OptionalDouble.of(-0.4),
                    OptionalDouble.of(0.75),
                    Optional.of("EXPERIENCED"),
                    Optional.of(true),
                    Optional.of(true)
            );

            MelResult result = evaluator.evaluate(stmt);

            assertThat(result.isOk()).isTrue();
            assertThat(result.statementType()).isEqualTo("REMEMBER");
            assertThat(result.affectedIds()).containsExactly("ep-redis-01");
            assertThat(result.output()).contains("ep-redis-01", "EPISODIC", "infra, redis");

            ArgumentCaptor<RememberContext> contextCaptor = ArgumentCaptor.forClass(RememberContext.class);
            ArgumentCaptor<String[]> tagsCaptor = ArgumentCaptor.forClass(String[].class);

            verify(memory).remember(
                    eq("ep-redis-01"),
                    eq("Redis pool exhausted during spike"),
                    eq(MemoryType.EPISODIC),
                    eq(MemorySource.OBSERVED),
                    contextCaptor.capture(),
                    tagsCaptor.capture()
            );

            RememberContext ctx = contextCaptor.getValue();
            assertThat(ctx.metadata()).containsEntry("pinned", "true");
            assertThat(ctx.metadata()).containsEntry("unresolved", "true");
            assertThat(ctx.hints()).isNotNull();
            assertThat(ctx.hints().valence()).isNotEqualTo((byte) 0);
            assertThat(tagsCaptor.getValue()).containsExactly("infra", "redis");
        }

        @Test
        @DisplayName("evalRemember with auto-generated ID returns generated ID")
        void testRememberAutoId() {
            RememberStmt stmt = new RememberStmt(
                    PayloadType.TEXT,
                    "Automatic test observation",
                    "SEMANTIC",
                    Optional.empty(),
                    List.of("test"),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    Optional.of("OBSERVED"),
                    Optional.empty(),
                    Optional.empty()
            );

            when(memory.remember(any(String.class), any(MemoryType.class), any(MemorySource.class),
                    any(RememberContext.class), any(String[].class)))
                    .thenReturn("auto-tsid-123");

            MelResult result = evaluator.evaluate(stmt);

            assertThat(result.isOk()).isTrue();
            assertThat(result.statementType()).isEqualTo("REMEMBER");
            assertThat(result.affectedIds()).containsExactly("auto-tsid-123");
            assertThat(result.output()).contains("auto-tsid-123");
        }
    }

    @Nested
    @DisplayName("RECALL & EXPLAIN RECALL Evaluation")
    class RecallTests {

        @Test
        @DisplayName("evalRecall populates 7-signal score breakdown, 6-phase funnel, and duration")
        void testRecallPopulatesRichTelemetry() {
            RecallStmt stmt = new RecallStmt(
                    Optional.of("database timeout"),
                    List.of("infra"),
                    OptionalDouble.of(-0.5),
                    OptionalDouble.of(0.8),
                    Optional.of("2026-01-01T00:00:00Z"),
                    Optional.of("2026-12-31T23:59:59Z"),
                    OptionalDouble.of(0.4),
                    List.of("EPISODIC", "SEMANTIC"),
                    5,
                    Optional.of(false),
                    Optional.empty()
            );

            ScoreBreakdown breakdown = new ScoreBreakdown(
                    0.92f, 0.85f, 1.2f, 1.0f, 1.15f, 1.05f, 0.94f
            );

            CognitiveResult hit = new CognitiveResult(
                    "mem-001",
                    "Database connection timeout under load",
                    0.94f,
                    0.85f,
                    1.5f,
                    3,
                    (byte) -30,
                    MemoryType.EPISODIC,
                    MemorySource.OBSERVED,
                    new String[]{"infra", "db"},
                    0.88f,
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

            when(memory.totalMemories()).thenReturn(100);
            when(memory.recall(eq("database timeout"), any(RecallOptions.class)))
                    .thenReturn(List.of(hit));

            MelResult result = evaluator.evaluate(stmt);

            assertThat(result.isOk()).isTrue();
            assertThat(result.statementType()).isEqualTo("RECALL");
            assertThat(result.affectedIds()).containsExactly("mem-001");
            assertThat(result.durationMicros()).isGreaterThan(0L);
            assertThat(result.candidates()).hasSize(1);

            MelResult.MelCandidateItem item = result.candidates().get(0);
            assertThat(item.id()).isEqualTo("mem-001");
            assertThat(item.tier()).isEqualTo("EPISODIC");
            assertThat(item.finalScore()).isEqualTo(0.94f);

            MelResult.CandidateBreakdown cb = item.scores();
            assertThat(cb.vectorSim()).isEqualTo(0.92f);
            assertThat(cb.recencyDecay()).isEqualTo(0.88f);
            assertThat(cb.cognitiveMass()).isEqualTo(0.85f);
            assertThat(cb.valenceBoost()).isEqualTo(1.05f);
            assertThat(cb.hebbianCoAct()).isEqualTo(1.15f);
            assertThat(cb.bm25Lexical()).isGreaterThan(0f);
            assertThat(cb.tagMatchRatio()).isEqualTo(1.0f);

            FunnelTelemetry funnel = result.funnel();
            assertThat(funnel.totalScanned()).isGreaterThanOrEqualTo(funnel.phase1Alive());
            assertThat(funnel.phase1Alive()).isGreaterThanOrEqualTo(funnel.phase2TagSurvivors());
            assertThat(funnel.phase2TagSurvivors()).isGreaterThanOrEqualTo(funnel.phase3ValenceSurvivors());
            assertThat(funnel.phase3ValenceSurvivors()).isGreaterThanOrEqualTo(funnel.phase4MassSurvivors());
            assertThat(funnel.phase4MassSurvivors()).isGreaterThanOrEqualTo(funnel.phase5SimdEvaluated());
            assertThat(funnel.phase5SimdEvaluated()).isGreaterThanOrEqualTo(funnel.phase6FinalReturned());
            assertThat(funnel.phase6FinalReturned()).isEqualTo(1);

            assertThat(funnel.toPhases()).hasSize(6);
            assertThat(result.explainRows()).hasSize(1);
        }

        @Test
        @DisplayName("evalExplainRecall returns EXPLAIN_RECALL statementType and formatted trace")
        void testExplainRecall() {
            RecallStmt inner = new RecallStmt(
                    Optional.of("redis error"),
                    List.of(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    OptionalDouble.empty(),
                    List.of(),
                    5,
                    Optional.empty(),
                    Optional.empty()
            );
            ExplainRecallStmt stmt = new ExplainRecallStmt(inner);

            when(memory.totalMemories()).thenReturn(50);
            when(memory.recall(eq("redis error"), any(RecallOptions.class)))
                    .thenReturn(List.of());

            MelResult result = evaluator.evaluate(stmt);

            assertThat(result.isOk()).isTrue();
            assertThat(result.statementType()).isEqualTo("EXPLAIN_RECALL");
            assertThat(result.output()).contains("EXPLAIN RECALL 'redis error' TOP 5");
            assertThat(result.candidates()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Script Execution & Diagnostics")
    class ScriptTests {

        @Test
        @DisplayName("evaluateScript evaluates multi-statement script sequentially")
        void testEvaluateScriptSequentially() {
            String script = """
                    REMEMBER TEXT 'First fact' INTO SEMANTIC ID 'fact-1';
                    REMEMBER TEXT 'Second fact' INTO EPISODIC ID 'fact-2';
                    FORGET 'fact-1' TOMBSTONE;
                    """;

            List<MelResult> results = MelEvaluator.evaluateScript(script, memory);

            assertThat(results).hasSize(3);
            assertThat(results.get(0).statementType()).isEqualTo("REMEMBER");
            assertThat(results.get(0).affectedIds()).containsExactly("fact-1");
            assertThat(results.get(1).statementType()).isEqualTo("REMEMBER");
            assertThat(results.get(1).affectedIds()).containsExactly("fact-2");
            assertThat(results.get(2).statementType()).isEqualTo("FORGET");
            assertThat(results.get(2).affectedIds()).containsExactly("fact-1");
        }

        @Test
        @DisplayName("evaluateScript returns diagnostic on parse error")
        void testEvaluateScriptSyntaxErrorDiagnostics() {
            String brokenScript = "REMEMBER INTO SEMANTIC;"; // missing payload

            List<MelResult> results = evaluator.evaluateScript(brokenScript);

            assertThat(results).hasSize(1);
            MelResult errorResult = results.get(0);
            assertThat(errorResult.isOk()).isFalse();
            assertThat(errorResult.diagnostics()).hasSize(1);

            MelResult.MelDiagnostic diag = errorResult.diagnostics().get(0);
            assertThat(diag.line()).isGreaterThanOrEqualTo(1);
            assertThat(diag.column()).isGreaterThanOrEqualTo(1);
            assertThat(diag.severity()).isEqualTo("ERROR");
            assertThat(diag.message()).isNotBlank();
        }
    }
}
