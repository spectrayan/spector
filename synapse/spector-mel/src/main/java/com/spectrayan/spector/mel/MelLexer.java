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

import com.spectrayan.spector.mel.MelToken.Token;
import com.spectrayan.spector.mel.MelToken.Type;

/**
 * Lexer (tokenizer) for the Memory Engine Language.
 *
 * <p>Converts a MEL source string into a list of {@link Token}s.
 * Keywords are case-insensitive. Strings use single quotes with
 * {@code ''} as an embedded quote. Comments are {@code --} to
 * end-of-line or {@code /* ... * /} blocks.</p>
 */
public final class MelLexer {

    private final String source;
    private int pos;
    private int line = 1;
    private int col = 1;

    public MelLexer(String source) {
        this.source = source;
    }

    /**
     * Tokenizes the entire source string.
     *
     * @return list of tokens, always ending with {@link Type#EOF}
     */
    public List<Token> tokenize() {
        var tokens = new ArrayList<Token>();
        Token tok;
        do {
            tok = next();
            tokens.add(tok);
        } while (tok.type() != Type.EOF);
        return List.copyOf(tokens);
    }

    /**
     * Returns the next token from the source.
     */
    public Token next() {
        skipWhitespaceAndComments();

        if (pos >= source.length()) {
            return token(Type.EOF, "", line, col);
        }

        int startLine = line;
        int startCol = col;
        char c = peek();

        // Single-character punctuation
        return switch (c) {
            case ';' -> { advance(); yield token(Type.SEMICOLON, ";", startLine, startCol); }
            case ',' -> { advance(); yield token(Type.COMMA, ",", startLine, startCol); }
            case '[' -> { advance(); yield token(Type.LBRACKET, "[", startLine, startCol); }
            case ']' -> { advance(); yield token(Type.RBRACKET, "]", startLine, startCol); }
            case '>' -> {
                advance();
                if (pos < source.length() && peek() == '=') {
                    advance();
                    yield token(Type.GTE, ">=", startLine, startCol);
                }
                yield token(Type.ERROR, ">", startLine, startCol);
            }
            default -> {
                if (c == '\'') {
                    yield readString(startLine, startCol);
                }
                if (c == '+' || c == '-') {
                    // Look ahead: if next char is digit, parse as number
                    if (pos + 1 < source.length() && Character.isDigit(source.charAt(pos + 1))) {
                        yield readNumber(startLine, startCol);
                    }
                }
                if (Character.isDigit(c)) {
                    yield readNumber(startLine, startCol);
                }
                if (Character.isLetter(c) || c == '_') {
                    yield readIdentifierOrKeyword(startLine, startCol);
                }
                advance();
                yield token(Type.ERROR, String.valueOf(c), startLine, startCol);
            }
        };
    }

    private Token readString(int startLine, int startCol) {
        advance(); // consume opening quote
        var sb = new StringBuilder();
        while (pos < source.length()) {
            char c = peek();
            if (c == '\'') {
                advance();
                // Check for escaped quote ''
                if (pos < source.length() && peek() == '\'') {
                    sb.append('\'');
                    advance();
                } else {
                    return token(Type.STRING, sb.toString(), startLine, startCol);
                }
            } else {
                sb.append(c);
                if (c == '\n') { line++; col = 1; } else { col++; }
                pos++;
            }
        }
        // Unterminated string
        return token(Type.ERROR, "Unterminated string: '" + sb, startLine, startCol);
    }

    private Token readNumber(int startLine, int startCol) {
        var sb = new StringBuilder();
        if (peek() == '+' || peek() == '-') {
            sb.append(peek());
            advance();
        }
        boolean hasDot = false;
        while (pos < source.length()) {
            char c = peek();
            if (Character.isDigit(c)) {
                sb.append(c);
                advance();
            } else if (c == '.' && !hasDot) {
                hasDot = true;
                sb.append(c);
                advance();
            } else {
                break;
            }
        }
        return token(hasDot ? Type.NUMBER : Type.INTEGER, sb.toString(), startLine, startCol);
    }

    private Token readIdentifierOrKeyword(int startLine, int startCol) {
        var sb = new StringBuilder();
        while (pos < source.length()) {
            char c = peek();
            if (Character.isLetterOrDigit(c) || c == '_') {
                sb.append(c);
                advance();
            } else {
                break;
            }
        }
        String word = sb.toString();
        Type kw = Type.keyword(word);
        if (kw != null) {
            return token(kw, word, startLine, startCol);
        }
        return token(Type.IDENTIFIER, word, startLine, startCol);
    }

    private void skipWhitespaceAndComments() {
        while (pos < source.length()) {
            char c = peek();
            if (Character.isWhitespace(c)) {
                if (c == '\n') { line++; col = 1; } else { col++; }
                pos++;
            } else if (c == '-' && pos + 1 < source.length() && source.charAt(pos + 1) == '-') {
                // Line comment: -- to end of line
                while (pos < source.length() && source.charAt(pos) != '\n') {
                    pos++;
                }
            } else if (c == '/' && pos + 1 < source.length() && source.charAt(pos + 1) == '*') {
                // Block comment: /* ... */
                pos += 2; col += 2;
                while (pos + 1 < source.length()) {
                    if (source.charAt(pos) == '*' && source.charAt(pos + 1) == '/') {
                        pos += 2; col += 2;
                        break;
                    }
                    if (source.charAt(pos) == '\n') { line++; col = 1; } else { col++; }
                    pos++;
                }
            } else {
                break;
            }
        }
    }

    private char peek() {
        return source.charAt(pos);
    }

    private void advance() {
        pos++;
        col++;
    }

    private static Token token(Type type, String value, int line, int col) {
        return new Token(type, value, line, col);
    }
}
