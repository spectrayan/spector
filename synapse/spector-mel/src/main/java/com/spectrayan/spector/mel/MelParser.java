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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import com.spectrayan.spector.mel.MelStatement.*;
import com.spectrayan.spector.mel.MelToken.Token;
import com.spectrayan.spector.mel.MelToken.Type;

/**
 * Recursive-descent parser for the Memory Engine Language.
 *
 * <p>Consumes a token stream from {@link MelLexer} and produces
 * {@link MelStatement} AST nodes. Phase 1 supports 6 productions:
 * {@code REMEMBER}, {@code RECALL}, {@code CONSOLIDATE}, {@code FORGET},
 * {@code EXPLAIN RECALL}, and {@code INTROSPECT}.</p>
 *
 * <p>Optional statements (REHEARSE, ASSOCIATE, etc.) produce a clear
 * error message indicating they are not yet supported.</p>
 */
public final class MelParser {

    private static final Set<String> UNSUPPORTED_KEYWORDS = Set.of(
            "REHEARSE", "RECONSOLIDATE", "ASSOCIATE", "INHIBIT",
            "REINFORCE", "PROJECT", "SIMULATE", "DREAM", "COMMIT"
    );

    private final List<Token> tokens;
    private int pos;

    public MelParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * Convenience: lex and parse a single statement.
     */
    public static MelStatement parse(String source) {
        var lexer = new MelLexer(source);
        var tokens = lexer.tokenize();
        var parser = new MelParser(tokens);
        return parser.parseStatement();
    }

    /**
     * Convenience: lex and parse a full script (multiple statements).
     */
    public static List<MelStatement> parseScript(String source) {
        var lexer = new MelLexer(source);
        var tokens = lexer.tokenize();
        var parser = new MelParser(tokens);
        return parser.parseAllStatements();
    }

    /**
     * Parses all statements in the token stream.
     */
    public List<MelStatement> parseAllStatements() {
        var stmts = new ArrayList<MelStatement>();
        while (peek().type() != Type.EOF) {
            stmts.add(parseStatement());
            if (peek().type() == Type.SEMICOLON) {
                advance(); // consume optional ;
            }
        }
        return List.copyOf(stmts);
    }

    /**
     * Parses a single statement.
     */
    public MelStatement parseStatement() {
        Token tok = peek();
        return switch (tok.type()) {
            case REMEMBER -> parseRemember();
            case RECALL -> parseRecall();
            case CONSOLIDATE -> parseConsolidate();
            case FORGET -> parseForget();
            case EXPLAIN -> parseExplain();
            case INTROSPECT -> parseIntrospect();
            case IDENTIFIER -> {
                if (UNSUPPORTED_KEYWORDS.contains(tok.value().toUpperCase())) {
                    yield error("Statement '" + tok.value().toUpperCase()
                            + "' is not yet supported in MEL Phase 1. "
                            + "Supported: REMEMBER, RECALL, CONSOLIDATE, FORGET, EXPLAIN RECALL, INTROSPECT.");
                }
                yield error("Unknown statement: '" + tok.value() + "'");
            }
            default -> error("Expected a MEL statement, got: " + tok);
        };
    }

    // ═══════════════════════════════════════════════════════════════
    //  REMEMBER
    // ═══════════════════════════════════════════════════════════════

    private RememberStmt parseRemember() {
        expect(Type.REMEMBER);

        // payload
        PayloadType pType = parsePayloadType();
        String pValue = expectString();

        // INTO tier
        expect(Type.INTO);
        String tier = parseTier();

        // Optional clauses
        Optional<String> id = Optional.empty();
        List<String> tags = List.of();
        OptionalDouble importance = OptionalDouble.empty();
        OptionalDouble valence = OptionalDouble.empty();
        OptionalDouble arousal = OptionalDouble.empty();
        Optional<String> source = Optional.empty();
        Optional<Boolean> pin = Optional.empty();
        Optional<Boolean> unresolved = Optional.empty();

        while (isRememberClause(peek().type())) {
            switch (peek().type()) {
                case ID -> { advance(); id = Optional.of(parseId()); }
                case TAGS -> { advance(); tags = parseTagList(); }
                case IMPORTANCE -> { advance(); importance = OptionalDouble.of(expectNumber()); }
                case VALENCE -> { advance(); valence = OptionalDouble.of(expectNumber()); }
                case AROUSAL -> { advance(); arousal = OptionalDouble.of(expectNumber()); }
                case SOURCE -> { advance(); source = Optional.of(parseSourceLit()); }
                case PIN -> { advance(); pin = Optional.of(parseBool()); }
                case UNRESOLVED -> { advance(); unresolved = Optional.of(parseBool()); }
                default -> { break; }
            }
        }

        return new RememberStmt(pType, pValue, tier, id, tags,
                importance, valence, arousal, source, pin, unresolved);
    }

    private boolean isRememberClause(Type type) {
        return type == Type.ID || type == Type.TAGS || type == Type.IMPORTANCE
                || type == Type.VALENCE || type == Type.AROUSAL || type == Type.SOURCE
                || type == Type.PIN || type == Type.UNRESOLVED;
    }

    // ═══════════════════════════════════════════════════════════════
    //  RECALL
    // ═══════════════════════════════════════════════════════════════

    RecallStmt parseRecall() {
        expect(Type.RECALL);

        // Optional query string
        Optional<String> query = Optional.empty();
        if (peek().type() == Type.STRING) {
            query = Optional.of(advance().value());
        }

        // Clauses
        List<String> filterTags = List.of();
        OptionalDouble minValence = OptionalDouble.empty();
        OptionalDouble maxValence = OptionalDouble.empty();
        Optional<String> timeFrom = Optional.empty();
        Optional<String> timeTo = Optional.empty();
        OptionalDouble minImportance = OptionalDouble.empty();
        List<String> tiers = List.of();
        int topK = 10; // default
        Optional<Boolean> allowSimulated = Optional.empty();
        Optional<String> context = Optional.empty();

        while (isRecallClause(peek().type())) {
            switch (peek().type()) {
                case TAGS -> {
                    advance(); expect(Type.CONTAIN);
                    filterTags = parseTagList();
                }
                case VALENCE -> {
                    advance(); expect(Type.BETWEEN);
                    minValence = OptionalDouble.of(expectNumber());
                    expect(Type.AND);
                    maxValence = OptionalDouble.of(expectNumber());
                }
                case TIME -> {
                    advance(); expect(Type.BETWEEN);
                    timeFrom = Optional.of(expectString());
                    expect(Type.AND);
                    timeTo = Optional.of(expectString());
                }
                case IMPORTANCE -> {
                    advance(); expect(Type.GTE);
                    minImportance = OptionalDouble.of(expectNumber());
                }
                case TIERS -> {
                    advance();
                    tiers = parseTierList();
                }
                case TOP -> {
                    advance();
                    topK = expectInteger();
                }
                case ALLOW -> {
                    advance(); expect(Type.SIMULATED);
                    allowSimulated = Optional.of(parseBool());
                }
                case CONTEXT -> {
                    advance();
                    context = Optional.of(expectString());
                }
                default -> { break; }
            }
        }

        return new RecallStmt(query, filterTags, minValence, maxValence,
                timeFrom, timeTo, minImportance, tiers, topK,
                allowSimulated, context);
    }

    private boolean isRecallClause(Type type) {
        return type == Type.TAGS || type == Type.VALENCE || type == Type.TIME
                || type == Type.IMPORTANCE || type == Type.TIERS || type == Type.TOP
                || type == Type.ALLOW || type == Type.CONTEXT;
    }

    // ═══════════════════════════════════════════════════════════════
    //  CONSOLIDATE
    // ═══════════════════════════════════════════════════════════════

    private ConsolidateStmt parseConsolidate() {
        expect(Type.CONSOLIDATE);
        List<String> ids = parseIdList();
        expect(Type.INTO);

        String targetTier = peek().value().toUpperCase();
        if (peek().type() != Type.SEMANTIC && peek().type() != Type.PROCEDURAL) {
            throw parseError("Expected SEMANTIC or PROCEDURAL after INTO, got: " + peek());
        }
        advance();

        Optional<PayloadType> asType = Optional.empty();
        Optional<String> asValue = Optional.empty();
        if (peek().type() == Type.AS) {
            advance();
            asType = Optional.of(parsePayloadType());
            asValue = Optional.of(expectString());
        }

        return new ConsolidateStmt(ids, targetTier, asType, asValue);
    }

    // ═══════════════════════════════════════════════════════════════
    //  FORGET
    // ═══════════════════════════════════════════════════════════════

    private ForgetStmt parseForget() {
        expect(Type.FORGET);
        List<String> ids = parseIdList();

        ForgetMode mode = switch (peek().type()) {
            case WEAKEN -> { advance(); yield ForgetMode.WEAKEN; }
            case SUPPRESS -> { advance(); yield ForgetMode.SUPPRESS; }
            case TOMBSTONE -> { advance(); yield ForgetMode.TOMBSTONE; }
            default -> throw parseError("Expected WEAKEN, SUPPRESS, or TOMBSTONE after FORGET ids, got: " + peek());
        };

        return new ForgetStmt(ids, mode);
    }

    // ═══════════════════════════════════════════════════════════════
    //  EXPLAIN RECALL
    // ═══════════════════════════════════════════════════════════════

    private ExplainRecallStmt parseExplain() {
        expect(Type.EXPLAIN);
        if (peek().type() != Type.RECALL) {
            throw parseError("Expected RECALL after EXPLAIN, got: " + peek());
        }
        RecallStmt inner = parseRecall();
        return new ExplainRecallStmt(inner);
    }

    // ═══════════════════════════════════════════════════════════════
    //  INTROSPECT
    // ═══════════════════════════════════════════════════════════════

    private IntrospectStmt parseIntrospect() {
        expect(Type.INTROSPECT);

        Optional<String> remembererId = Optional.empty();
        Optional<String> tier = Optional.empty();

        if (peek().type() == Type.REMEMBERER) {
            advance();
            remembererId = Optional.of(parseId());
        }
        if (peek().type() == Type.TIER) {
            advance();
            tier = Optional.of(parseTier());
        }

        return new IntrospectStmt(remembererId, tier);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Terminal Parsers
    // ═══════════════════════════════════════════════════════════════

    private PayloadType parsePayloadType() {
        return switch (peek().type()) {
            case TEXT -> { advance(); yield PayloadType.TEXT; }
            case BYTES -> { advance(); yield PayloadType.BYTES; }
            case RULE -> { advance(); yield PayloadType.RULE; }
            default -> throw parseError("Expected TEXT, BYTES, or RULE, got: " + peek());
        };
    }

    private String parseTier() {
        return switch (peek().type()) {
            case WORKING, EPISODIC, SEMANTIC, PROCEDURAL -> advance().value().toUpperCase();
            default -> throw parseError("Expected tier (WORKING, EPISODIC, SEMANTIC, PROCEDURAL), got: " + peek());
        };
    }

    private List<String> parseTierList() {
        var tiers = new ArrayList<String>();
        tiers.add(parseTier());
        while (peek().type() == Type.COMMA) {
            advance();
            tiers.add(parseTier());
        }
        return List.copyOf(tiers);
    }

    private String parseSourceLit() {
        return switch (peek().type()) {
            case EXPERIENCED, DISTILLED, SIMULATED -> advance().value().toUpperCase();
            default -> throw parseError("Expected source (EXPERIENCED, DISTILLED, SIMULATED, REHEARSED), got: " + peek());
        };
    }

    private List<String> parseTagList() {
        expect(Type.LBRACKET);
        var tags = new ArrayList<String>();
        tags.add(expectString());
        while (peek().type() == Type.COMMA) {
            advance();
            tags.add(expectString());
        }
        expect(Type.RBRACKET);
        return List.copyOf(tags);
    }

    private List<String> parseIdList() {
        var ids = new ArrayList<String>();
        ids.add(parseId());
        while (peek().type() == Type.COMMA) {
            advance();
            ids.add(parseId());
        }
        return List.copyOf(ids);
    }

    private String parseId() {
        Token tok = peek();
        if (tok.type() == Type.IDENTIFIER || tok.type() == Type.STRING) {
            advance();
            return tok.value();
        }
        throw parseError("Expected identifier or string, got: " + tok);
    }

    private boolean parseBool() {
        return switch (peek().type()) {
            case TRUE -> { advance(); yield true; }
            case FALSE -> { advance(); yield false; }
            default -> throw parseError("Expected TRUE or FALSE, got: " + peek());
        };
    }

    // ═══════════════════════════════════════════════════════════════
    //  Token Helpers
    // ═══════════════════════════════════════════════════════════════

    private Token peek() {
        return pos < tokens.size() ? tokens.get(pos) : new Token(Type.EOF, "", 0, 0);
    }

    private Token advance() {
        return tokens.get(pos++);
    }

    private void expect(Type type) {
        Token tok = peek();
        if (tok.type() != type) {
            throw parseError("Expected " + type + ", got: " + tok);
        }
        advance();
    }

    private String expectString() {
        Token tok = peek();
        if (tok.type() != Type.STRING) {
            throw parseError("Expected string literal, got: " + tok);
        }
        advance();
        return tok.value();
    }

    private double expectNumber() {
        Token tok = peek();
        if (tok.type() == Type.NUMBER || tok.type() == Type.INTEGER) {
            advance();
            return Double.parseDouble(tok.value());
        }
        throw parseError("Expected number, got: " + tok);
    }

    private int expectInteger() {
        Token tok = peek();
        if (tok.type() == Type.INTEGER) {
            advance();
            return Integer.parseInt(tok.value());
        }
        throw parseError("Expected integer, got: " + tok);
    }

    private MelParseException parseError(String message) {
        Token tok = peek();
        return new MelParseException(message, tok.line(), tok.column());
    }

    private MelStatement error(String message) {
        throw parseError(message);
    }

    /**
     * Parse error with source location information.
     */
    public static final class MelParseException extends RuntimeException {
        private final int line;
        private final int column;

        public MelParseException(String message, int line, int column) {
            super("MEL parse error at " + line + ":" + column + " — " + message);
            this.line = line;
            this.column = column;
        }

        public int line() { return line; }
        public int column() { return column; }
    }
}
