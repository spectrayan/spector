/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.pathway.express.persona;

import com.spectrayan.spector.memory.model.StylometricFeatures;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StylometricAnalyzer {

    public static StylometricFeatures analyze(String text) {
        if (text == null || text.trim().isEmpty()) {
            return StylometricFeatures.NEUTRAL;
        }

        // Split into sentences
        String[] sentences = text.split("(?<=[.!?])\\s+");
        if (sentences.length == 0) {
            return StylometricFeatures.NEUTRAL;
        }

        float totalLength = 0;
        Set<String> uniqueWords = new HashSet<>();
        int wordCount = 0;
        
        int clauses = 1;
        int commas = 0;
        int dashes = 0;
        int ellipses = 0;
        int exclamations = 0;
        int questions = 0;
        
        float[] lengths = new float[sentences.length];
        
        for (int i = 0; i < sentences.length; i++) {
            String s = sentences[i];
            
            // Punctuation counts
            for (char c : s.toCharArray()) {
                if (c == ',') commas++;
                else if (c == '-') dashes++;
                else if (c == '!') exclamations++;
                else if (c == '?') questions++;
            }
            ellipses += (s.length() - s.replace("...", "").length()) / 3;
            clauses += (s.length() - s.replace(";", "").length()); // rough approximation
            clauses += (s.length() - s.replace(":", "").length());
            
            String[] words = s.split("\\W+");
            lengths[i] = words.length;
            totalLength += words.length;
            wordCount += words.length;
            
            for (String w : words) {
                if (!w.isEmpty()) {
                    uniqueWords.add(w.toLowerCase());
                }
            }
        }
        
        float meanSentenceLength = totalLength / sentences.length;
        float variance = 0;
        for (float l : lengths) {
            variance += (l - meanSentenceLength) * (l - meanSentenceLength);
        }
        variance /= sentences.length;
        
        float typeTokenRatio = wordCount == 0 ? 0 : (float) uniqueWords.size() / wordCount;
        
        float commaRate = wordCount == 0 ? 0 : (float) commas / wordCount;
        float dashRate = wordCount == 0 ? 0 : (float) dashes / wordCount;
        float ellipsisRate = wordCount == 0 ? 0 : (float) ellipses / wordCount;
        float exclamationRate = wordCount == 0 ? 0 : (float) exclamations / wordCount;
        float questionRate = wordCount == 0 ? 0 : (float) questions / wordCount;
        float clauseComplexity = sentences.length == 0 ? 0 : (float) (clauses + sentences.length) / sentences.length;
        
        // Formality score based on contractions and word length
        int contractions = 0;
        Matcher m = Pattern.compile("\\b(n't|'ve|'re|'ll|'m|'d|'s)\\b").matcher(text.toLowerCase());
        while (m.find()) {
            contractions++;
        }
        
        float totalWordLength = 0;
        for (String w : uniqueWords) {
            totalWordLength += w.length();
        }
        float avgWordLength = uniqueWords.isEmpty() ? 0 : totalWordLength / uniqueWords.size();
        
        float formalityScore = 0.5f;
        if (wordCount > 0) {
            float contractionRate = (float) contractions / wordCount;
            // Higher avg word length -> more formal, more contractions -> less formal
            formalityScore = Math.clamp(0.5f + (avgWordLength - 4.5f) * 0.1f - contractionRate * 2.0f, 0.0f, 1.0f);
        }
        
        return new StylometricFeatures(
                meanSentenceLength, variance, typeTokenRatio, clauseComplexity,
                commaRate, dashRate, ellipsisRate, exclamationRate, questionRate, formalityScore
        );
    }
}
