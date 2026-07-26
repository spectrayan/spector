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
package com.spectrayan.spector.hdc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for character n-gram extraction.
 */
public final class NgramEncoder {

    private NgramEncoder() {
        // Utility class
    }

    /**
     * Extracts character n-grams from the given text.
     * @param text the input text
     * @param ngramSize the size of n-grams to extract
     * @return a list of n-grams
     */
    public static List<String> encode(String text, int ngramSize) {
        if (text == null) {
            return Collections.emptyList();
        }
        
        String cleanText = text.trim().toLowerCase();
        if (cleanText.isEmpty()) {
            return Collections.emptyList();
        }
        
        if (ngramSize <= 0) {
            throw new IllegalArgumentException("n-gram size must be positive");
        }
        
        if (cleanText.length() < ngramSize) {
            return Collections.singletonList(cleanText);
        }
        
        List<String> ngrams = new ArrayList<>(cleanText.length() - ngramSize + 1);
        for (int i = 0; i <= cleanText.length() - ngramSize; i++) {
            ngrams.add(cleanText.substring(i, i + ngramSize));
        }
        
        return ngrams;
    }

    /**
     * Extracts character n-grams from the given text using default n=3.
     * @param text the input text
     * @return a list of 3-grams
     */
    public static List<String> encode(String text) {
        return encode(text, 3);
    }
}
