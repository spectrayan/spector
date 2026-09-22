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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.mel.MelStatement.*;

/**
 * Unit tests for the MEL recursive-descent parser.
 */
@DisplayName("MelParser")
class MelParserTest {

    @Nested
    @DisplayName("REMEMBER")
    class Remember {

        @Test
        void shouldParseMinimalRemember() {
            var stmt = (RememberStmt) MelParser.parse(
                    "REMEMBER TEXT 'hello world' INTO SEMANTIC;");

            assertThat(stmt.payloadType()).isEqualTo(PayloadType.TEXT);
            assertThat(stmt.payloadValue()).isEqualTo("hello world");
            assertThat(stmt.tier()).isEqualTo("SEMANTIC");
            assertThat(stmt.id()).isEmpty();
            assertThat(stmt.tags()).isEmpty();
        }

        @Test
        void shouldParseRememberWithAllClauses() {
            var stmt = (RememberStmt) MelParser.parse("""
                    REMEMBER TEXT 'learn Java' INTO PROCEDURAL
                    TAGS ['java', 'learning']
                    IMPORTANCE 0.9
                    VALENCE 5
                    SOURCE EXPERIENCED;""");

            assertThat(stmt.payloadType()).isEqualTo(PayloadType.TEXT);
            assertThat(stmt.payloadValue()).isEqualTo("learn Java");
            assertThat(stmt.tier()).isEqualTo("PROCEDURAL");
            assertThat(stmt.tags()).containsExactly("java", "learning");
            assertThat(stmt.importance()).hasValue(0.9);
            assertThat(stmt.valence()).hasValue(5.0);
            assertThat(stmt.source()).hasValue("EXPERIENCED");
        }

        @Test
        void shouldParseRememberIntoEpisodic() {
            var stmt = (RememberStmt) MelParser.parse(
                    "REMEMBER TEXT 'met with team' INTO EPISODIC;");
            assertThat(stmt.tier()).isEqualTo("EPISODIC");
        }

        @Test
        void shouldParseRememberWithId() {
            var stmt = (RememberStmt) MelParser.parse(
                    "REMEMBER TEXT 'hello' INTO SEMANTIC ID 'custom-id-1';");
            assertThat(stmt.id()).hasValue("custom-id-1");
        }
    }

    @Nested
    @DisplayName("RECALL")
    class Recall {

        @Test
        void shouldParseMinimalRecall() {
            var stmt = (RecallStmt) MelParser.parse("RECALL 'query';");
            assertThat(stmt.query()).hasValue("query");
            assertThat(stmt.topK()).isEqualTo(10); // default
        }

        @Test
        void shouldParseRecallWithTop() {
            var stmt = (RecallStmt) MelParser.parse("RECALL 'query' TOP 5;");
            assertThat(stmt.topK()).isEqualTo(5);
        }

        @Test
        void shouldParseRecallWithTagFilter() {
            var stmt = (RecallStmt) MelParser.parse(
                    "RECALL 'query' TAGS CONTAIN ['java', 'architecture'];");
            assertThat(stmt.filterTags()).containsExactly("java", "architecture");
        }

        @Test
        void shouldParseRecallWithMultipleClauses() {
            var stmt = (RecallStmt) MelParser.parse("""
                    RECALL 'debugging tips'
                    TOP 3
                    IMPORTANCE >= 0.5
                    TIERS SEMANTIC, PROCEDURAL;""");

            assertThat(stmt.query()).hasValue("debugging tips");
            assertThat(stmt.topK()).isEqualTo(3);
            assertThat(stmt.minImportance()).hasValue(0.5);
            assertThat(stmt.tiers()).containsExactly("SEMANTIC", "PROCEDURAL");
        }

        @Test
        void shouldParseRecallWithValenceRange() {
            var stmt = (RecallStmt) MelParser.parse(
                    "RECALL 'errors' VALENCE BETWEEN -128 AND -10;");
            assertThat(stmt.minValence()).hasValue(-128.0);
            assertThat(stmt.maxValence()).hasValue(-10.0);
        }
    }

    @Nested
    @DisplayName("CONSOLIDATE")
    class Consolidate {

        @Test
        void shouldParseConsolidate() {
            var stmt = (ConsolidateStmt) MelParser.parse(
                    "CONSOLIDATE id1, id2, id3 INTO SEMANTIC;");
            assertThat(stmt.ids()).containsExactly("id1", "id2", "id3");
            assertThat(stmt.targetTier()).isEqualTo("SEMANTIC");
            assertThat(stmt.asPayloadType()).isEmpty();
        }

        @Test
        void shouldParseConsolidateWithAsPayload() {
            var stmt = (ConsolidateStmt) MelParser.parse(
                    "CONSOLIDATE id1, id2 INTO PROCEDURAL AS TEXT 'summarized knowledge';");
            assertThat(stmt.targetTier()).isEqualTo("PROCEDURAL");
            assertThat(stmt.asPayloadType()).hasValue(PayloadType.TEXT);
            assertThat(stmt.asPayloadValue()).hasValue("summarized knowledge");
        }

        @Test
        void shouldParseConsolidateWithStringIds() {
            var stmt = (ConsolidateStmt) MelParser.parse(
                    "CONSOLIDATE 'trace-001', 'trace-002' INTO SEMANTIC;");
            assertThat(stmt.ids()).containsExactly("trace-001", "trace-002");
        }
    }

    @Nested
    @DisplayName("FORGET")
    class Forget {

        @Test
        void shouldParseTombstone() {
            var stmt = (ForgetStmt) MelParser.parse("FORGET id1 TOMBSTONE;");
            assertThat(stmt.ids()).containsExactly("id1");
            assertThat(stmt.mode()).isEqualTo(ForgetMode.TOMBSTONE);
        }

        @Test
        void shouldParseSuppress() {
            var stmt = (ForgetStmt) MelParser.parse("FORGET id1, id2 SUPPRESS;");
            assertThat(stmt.ids()).containsExactly("id1", "id2");
            assertThat(stmt.mode()).isEqualTo(ForgetMode.SUPPRESS);
        }

        @Test
        void shouldParseWeaken() {
            var stmt = (ForgetStmt) MelParser.parse("FORGET id1 WEAKEN;");
            assertThat(stmt.mode()).isEqualTo(ForgetMode.WEAKEN);
        }
    }

    @Nested
    @DisplayName("EXPLAIN RECALL")
    class ExplainRecall {

        @Test
        void shouldParseExplainRecall() {
            var stmt = (ExplainRecallStmt) MelParser.parse(
                    "EXPLAIN RECALL 'query' TOP 5;");
            assertThat(stmt.inner().query()).hasValue("query");
            assertThat(stmt.inner().topK()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("INTROSPECT")
    class Introspect {

        @Test
        void shouldParseMinimalIntrospect() {
            var stmt = (IntrospectStmt) MelParser.parse("INTROSPECT;");
            assertThat(stmt.remembererId()).isEmpty();
            assertThat(stmt.tier()).isEmpty();
        }

        @Test
        void shouldParseIntrospectWithTier() {
            var stmt = (IntrospectStmt) MelParser.parse("INTROSPECT TIER SEMANTIC;");
            assertThat(stmt.tier()).hasValue("SEMANTIC");
        }
    }

    @Nested
    @DisplayName("Script parsing")
    class ScriptParsing {

        @Test
        void shouldParseMultipleStatements() {
            var stmts = MelParser.parseScript("""
                    REMEMBER TEXT 'fact 1' INTO SEMANTIC;
                    RECALL 'query' TOP 3;
                    INTROSPECT;""");
            assertThat(stmts).hasSize(3);
            assertThat(stmts.get(0)).isInstanceOf(RememberStmt.class);
            assertThat(stmts.get(1)).isInstanceOf(RecallStmt.class);
            assertThat(stmts.get(2)).isInstanceOf(IntrospectStmt.class);
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        void shouldRejectUnsupportedStatements() {
            assertThatThrownBy(() -> MelParser.parse("REHEARSE id1;"))
                    .isInstanceOf(MelParser.MelParseException.class)
                    .hasMessageContaining("not yet supported in MEL Phase 1");
        }

        @Test
        void shouldReportMissingTier() {
            assertThatThrownBy(() -> MelParser.parse("REMEMBER TEXT 'hello' INTO;"))
                    .isInstanceOf(MelParser.MelParseException.class)
                    .hasMessageContaining("Expected tier");
        }

        @Test
        void shouldReportMissingForgetMode() {
            assertThatThrownBy(() -> MelParser.parse("FORGET id1;"))
                    .isInstanceOf(MelParser.MelParseException.class)
                    .hasMessageContaining("Expected WEAKEN, SUPPRESS, or TOMBSTONE");
        }
    }

    @Nested
    @DisplayName("Comments in statements")
    class CommentsInStatements {

        @Test
        void shouldIgnoreLineComments() {
            var stmt = (RecallStmt) MelParser.parse("""
                    -- This is a recall query
                    RECALL 'hello' TOP 5;""");
            assertThat(stmt.query()).hasValue("hello");
        }

        @Test
        void shouldIgnoreBlockComments() {
            var stmt = (RememberStmt) MelParser.parse("""
                    REMEMBER TEXT /* inline */ 'data' INTO SEMANTIC;""");
            assertThat(stmt.payloadValue()).isEqualTo("data");
        }
    }
}
