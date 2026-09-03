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

/**
 * Enhanced analyzer with Porter stemming support.
 *
 * <p>Pipeline: tokenize → lowercase → stop word removal → stemming.</p>
 */
public class StemmingAnalyzer implements Analyzer {

    @Override
    public List<String> analyze(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> rawTokens = new ArrayList<>();
        StandardAnalyzer.tokenize(text, rawTokens);

        List<String> stemmed = new ArrayList<>(rawTokens.size());
        for (String token : rawTokens) {
            stemmed.add(stem(token));
        }
        return stemmed;
    }

    /**
     * Simplified Porter stemmer — handles the most common English suffixes.
     * For production, replace with a full Porter/Snowball implementation.
     */
    static String stem(String word) {
        if (word.length() <= 3) return word;

        // Step 1: plurals and past tenses
        if (word.endsWith("sses")) return word.substring(0, word.length() - 2);
        if (word.endsWith("ies")) return word.substring(0, word.length() - 2);
        if (word.endsWith("ied")) return word.substring(0, word.length() - 2);

        // Step 2: longer suffixes (check BEFORE short ones like -ss, -s)
        if (word.endsWith("edness") && word.length() > 8) return dedupConsonant(word.substring(0, word.length() - 6));
        if (word.endsWith("ingly") && word.length() > 7) return dedupConsonant(word.substring(0, word.length() - 5));
        if (word.endsWith("edly") && word.length() > 6) return dedupConsonant(word.substring(0, word.length() - 4));
        if (word.endsWith("ness") && word.length() > 5) return word.substring(0, word.length() - 4);
        if (word.endsWith("ment") && word.length() > 5) return word.substring(0, word.length() - 4);
        if (word.endsWith("tion") && word.length() > 5) return word.substring(0, word.length() - 4);
        if (word.endsWith("able") && word.length() > 5) return word.substring(0, word.length() - 4);
        if (word.endsWith("ible") && word.length() > 5) return word.substring(0, word.length() - 4);
        if (word.endsWith("ing") && word.length() > 5) return stem(dedupConsonant(word.substring(0, word.length() - 3)));
        if (word.endsWith("ful") && word.length() > 4) return word.substring(0, word.length() - 3);
        if (word.endsWith("ous") && word.length() > 4) return word.substring(0, word.length() - 3);
        if (word.endsWith("ive") && word.length() > 4) return word.substring(0, word.length() - 3);
        if (word.endsWith("ly") && word.length() > 4) return word.substring(0, word.length() - 2);
        if (word.endsWith("ed") && word.length() > 4) return stem(dedupConsonant(word.substring(0, word.length() - 2)));
        if (word.endsWith("er") && word.length() > 4) return dedupConsonant(word.substring(0, word.length() - 2));

        // Step 3: simple plural (after checking longer suffixes)
        if (word.endsWith("ss")) return word;
        if (word.endsWith("s") && word.length() > 3) return word.substring(0, word.length() - 1);

        return word;
    }

    /**
     * Removes trailing duplicate consonants (e.g., "runn" → "run", "stopp" → "stop").
     */
    private static String dedupConsonant(String stem) {
        int len = stem.length();
        if (len >= 2) {
            char last = stem.charAt(len - 1);
            char prev = stem.charAt(len - 2);
            if (last == prev && !isVowel(last)) {
                return stem.substring(0, len - 1);
            }
        }
        return stem;
    }

    private static boolean isVowel(char c) {
        return "aeiou".indexOf(c) >= 0;
    }
}
