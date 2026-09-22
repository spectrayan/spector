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
import java.util.Map;
import java.util.Set;

/**
 * Token types and token record for the MEL lexer.
 *
 * <p>Tokens represent the lexical units of the Memory Engine Language.
 * Keywords are case-insensitive; this enum captures them in uppercase.</p>
 */
public final class MelToken {

    private MelToken() {} // namespace only

    /**
     * Token types produced by {@link MelLexer}.
     */
    public enum Type {
        // ── Keywords (required statements) ──
        REMEMBER, RECALL, CONSOLIDATE, FORGET,
        // ── Keywords (diagnostic) ──
        EXPLAIN, INTROSPECT,
        // ── Keywords (clauses) ──
        TEXT, BYTES, RULE, INTO, ID, TAGS, IMPORTANCE, VALENCE, AROUSAL,
        SOURCE, PIN, UNRESOLVED, CONTAIN, BETWEEN, AND, TIME, TIERS, TOP,
        ALLOW, SIMULATED, CONTEXT, WEAKEN, SUPPRESS, TOMBSTONE,
        AS, REMEMBERER, TIER,
        // ── Keywords (tier names) ──
        WORKING, EPISODIC, SEMANTIC, PROCEDURAL,
        // ── Keywords (source literals) ──
        EXPERIENCED, DISTILLED,
        // ── Keywords (boolean) ──
        TRUE, FALSE,
        // ── Literals ──
        STRING, NUMBER, INTEGER, IDENTIFIER,
        // ── Punctuation ──
        SEMICOLON, COMMA, LBRACKET, RBRACKET, GTE,
        // ── Control ──
        EOF, ERROR;

        private static final Map<String, Type> KEYWORDS;
        static {
            var kw = new java.util.HashMap<String, Type>();
            for (Type t : values()) {
                if (t.ordinal() <= TIER.ordinal()) {
                    kw.put(t.name(), t);
                }
            }
            // tier names and source literals are also keywords
            kw.put("WORKING", WORKING);
            kw.put("EPISODIC", EPISODIC);
            kw.put("SEMANTIC", SEMANTIC);
            kw.put("PROCEDURAL", PROCEDURAL);
            kw.put("EXPERIENCED", EXPERIENCED);
            kw.put("DISTILLED", DISTILLED);
            kw.put("TRUE", TRUE);
            kw.put("FALSE", FALSE);
            KEYWORDS = Map.copyOf(kw);
        }

        /**
         * Looks up a keyword by name (case-insensitive).
         * @return the keyword Type, or null if not a keyword
         */
        public static Type keyword(String name) {
            return KEYWORDS.get(name.toUpperCase());
        }
    }

    /**
     * A single lexical token.
     *
     * @param type   the token type
     * @param value  the raw text of the token
     * @param line   1-based line number
     * @param column 1-based column number
     */
    public record Token(Type type, String value, int line, int column) {

        @Override
        public String toString() {
            return type + "(" + value + ") at " + line + ":" + column;
        }
    }
}
