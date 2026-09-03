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
package com.spectrayan.spector.index;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Standard text analyzer: lowercase → Unicode-aware tokenize → stop word removal.
 *
 * <p>Splits on non-alphanumeric boundaries, lowercases all tokens, and removes
 * common English stop words. Tokens shorter than 2 characters are discarded.</p>
 */
public class StandardAnalyzer implements Analyzer {

    private static final int MIN_TOKEN_LENGTH = 2;

    /** Common English stop words. */
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "but", "by",
            "for", "if", "in", "into", "is", "it", "its", "no", "not",
            "of", "on", "or", "such", "that", "the", "their", "then",
            "there", "these", "they", "this", "to", "was", "will", "with",
            "do", "does", "did", "have", "has", "had", "what"
    );

    @Override
    public List<String> analyze(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        tokenize(text, tokens);
        return tokens;
    }

    /**
     * High-throughput single-pass tokenizer: scans alphanumeric tokens, lowercases in-place,
     * filters stop words, and discards tokens shorter than {@value #MIN_TOKEN_LENGTH} chars.
     * Zero regex compilation, zero lowercased full-string allocations.
     *
     * @param text   input string
     * @param tokens destination list for extracted tokens
     */
    public static void tokenize(String text, List<String> tokens) {
        final int len = text.length();
        int tokenStart = -1;

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (isAlphaNumeric(c)) {
                if (tokenStart == -1) {
                    tokenStart = i;
                }
            } else if (tokenStart != -1) {
                emitToken(text, tokenStart, i, tokens);
                tokenStart = -1;
            }
        }

        if (tokenStart != -1) {
            emitToken(text, tokenStart, len, tokens);
        }
    }

    private static void emitToken(String text, int start, int end, List<String> tokens) {
        final int tokenLen = end - start;
        if (tokenLen < MIN_TOKEN_LENGTH) {
            return;
        }

        // Fast scan to check if any uppercase characters exist
        boolean hasUpper = false;
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c > 127 && Character.isUpperCase(c))) {
                hasUpper = true;
                break;
            }
        }

        String token;
        if (!hasUpper) {
            token = text.substring(start, end);
        } else {
            char[] chars = new char[tokenLen];
            for (int i = 0; i < tokenLen; i++) {
                char c = text.charAt(start + i);
                if (c >= 'A' && c <= 'Z') {
                    chars[i] = (char) (c + 32);
                } else if (c > 127) {
                    chars[i] = Character.toLowerCase(c);
                } else {
                    chars[i] = c;
                }
            }
            token = new String(chars);
        }

        if (!STOP_WORDS.contains(token)) {
            tokens.add(token);
        }
    }

    private static boolean isAlphaNumeric(char c) {
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
            return true;
        }
        return c > 127 && Character.isLetterOrDigit(c);
    }
}
