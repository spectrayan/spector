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

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.mel.MelToken.Token;
import com.spectrayan.spector.mel.MelToken.Type;

/**
 * Unit tests for the MEL lexer.
 */
@DisplayName("MelLexer")
class MelLexerTest {

    @Nested
    @DisplayName("Keywords")
    class Keywords {

        @Test
        void shouldTokenizeRequiredKeywords() {
            var tokens = new MelLexer("REMEMBER RECALL CONSOLIDATE FORGET").tokenize();
            assertThat(tokens).extracting(Token::type)
                    .containsExactly(Type.REMEMBER, Type.RECALL, Type.CONSOLIDATE, Type.FORGET, Type.EOF);
        }

        @Test
        void shouldBeCaseInsensitive() {
            var tokens = new MelLexer("remember Recall CONSOLIDATE forGET").tokenize();
            assertThat(tokens).extracting(Token::type)
                    .containsExactly(Type.REMEMBER, Type.RECALL, Type.CONSOLIDATE, Type.FORGET, Type.EOF);
        }

        @Test
        void shouldTokenizeTierKeywords() {
            var tokens = new MelLexer("WORKING EPISODIC SEMANTIC PROCEDURAL").tokenize();
            assertThat(tokens).extracting(Token::type)
                    .containsExactly(Type.WORKING, Type.EPISODIC, Type.SEMANTIC, Type.PROCEDURAL, Type.EOF);
        }

        @Test
        void shouldTokenizeDiagnosticKeywords() {
            var tokens = new MelLexer("EXPLAIN INTROSPECT").tokenize();
            assertThat(tokens).extracting(Token::type)
                    .containsExactly(Type.EXPLAIN, Type.INTROSPECT, Type.EOF);
        }
    }

    @Nested
    @DisplayName("Strings")
    class Strings {

        @Test
        void shouldTokenizeSingleQuotedString() {
            var tokens = new MelLexer("'hello world'").tokenize();
            assertThat(tokens).hasSize(2); // STRING + EOF
            assertThat(tokens.get(0).type()).isEqualTo(Type.STRING);
            assertThat(tokens.get(0).value()).isEqualTo("hello world");
        }

        @Test
        void shouldHandleEscapedQuotes() {
            var tokens = new MelLexer("'it''s a test'").tokenize();
            assertThat(tokens.get(0).value()).isEqualTo("it's a test");
        }

        @Test
        void shouldHandleEmptyString() {
            var tokens = new MelLexer("''").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(Type.STRING);
            assertThat(tokens.get(0).value()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Numbers")
    class Numbers {

        @Test
        void shouldTokenizeInteger() {
            var tokens = new MelLexer("42").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(Type.INTEGER);
            assertThat(tokens.get(0).value()).isEqualTo("42");
        }

        @Test
        void shouldTokenizeDecimal() {
            var tokens = new MelLexer("3.14").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(Type.NUMBER);
            assertThat(tokens.get(0).value()).isEqualTo("3.14");
        }

        @Test
        void shouldTokenizeNegativeNumber() {
            var tokens = new MelLexer("-0.5").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(Type.NUMBER);
            assertThat(tokens.get(0).value()).isEqualTo("-0.5");
        }

        @Test
        void shouldTokenizePositiveNumber() {
            var tokens = new MelLexer("+7").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(Type.INTEGER);
            assertThat(tokens.get(0).value()).isEqualTo("+7");
        }
    }

    @Nested
    @DisplayName("Punctuation")
    class Punctuation {

        @Test
        void shouldTokenizeSemicolon() {
            var tokens = new MelLexer(";").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(Type.SEMICOLON);
        }

        @Test
        void shouldTokenizeComma() {
            var tokens = new MelLexer(",").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(Type.COMMA);
        }

        @Test
        void shouldTokenizeBrackets() {
            var tokens = new MelLexer("[ ]").tokenize();
            assertThat(tokens).extracting(Token::type)
                    .containsExactly(Type.LBRACKET, Type.RBRACKET, Type.EOF);
        }

        @Test
        void shouldTokenizeGte() {
            var tokens = new MelLexer(">=").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(Type.GTE);
        }
    }

    @Nested
    @DisplayName("Comments")
    class Comments {

        @Test
        void shouldSkipLineComments() {
            var tokens = new MelLexer("RECALL -- this is a comment\n'query'").tokenize();
            assertThat(tokens).extracting(Token::type)
                    .containsExactly(Type.RECALL, Type.STRING, Type.EOF);
        }

        @Test
        void shouldSkipBlockComments() {
            var tokens = new MelLexer("RECALL /* block comment */ 'query'").tokenize();
            assertThat(tokens).extracting(Token::type)
                    .containsExactly(Type.RECALL, Type.STRING, Type.EOF);
        }
    }

    @Nested
    @DisplayName("Complete Statements")
    class CompleteStatements {

        @Test
        void shouldTokenizeFullRememberStatement() {
            var tokens = new MelLexer(
                    "REMEMBER TEXT 'hello' INTO SEMANTIC TAGS ['tag1', 'tag2'] IMPORTANCE 0.8;")
                    .tokenize();

            assertThat(tokens).extracting(Token::type)
                    .containsExactly(
                            Type.REMEMBER, Type.TEXT, Type.STRING, Type.INTO, Type.SEMANTIC,
                            Type.TAGS, Type.LBRACKET, Type.STRING, Type.COMMA, Type.STRING,
                            Type.RBRACKET, Type.IMPORTANCE, Type.NUMBER, Type.SEMICOLON, Type.EOF
                    );
        }

        @Test
        void shouldTokenizeRecallWithClauses() {
            var tokens = new MelLexer("RECALL 'test' TOP 5 IMPORTANCE >= 0.3;").tokenize();
            assertThat(tokens).extracting(Token::type)
                    .containsExactly(
                            Type.RECALL, Type.STRING, Type.TOP, Type.INTEGER,
                            Type.IMPORTANCE, Type.GTE, Type.NUMBER, Type.SEMICOLON, Type.EOF
                    );
        }
    }

    @Test
    @DisplayName("Should track line and column numbers")
    void shouldTrackPosition() {
        var tokens = new MelLexer("RECALL\n'query'").tokenize();
        assertThat(tokens.get(0).line()).isEqualTo(1);
        assertThat(tokens.get(1).line()).isEqualTo(2);
    }
}
