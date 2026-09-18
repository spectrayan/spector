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
package com.spectrayan.spector.memory.cortex.consolidation;

import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detector that checks if two semantically similar memories contradict each other.
 */
public final class ContradictionDetector {

    private static final Logger log = LoggerFactory.getLogger(ContradictionDetector.class);

    private final LlmProvider textGenerator;

    public ContradictionDetector(LlmProvider textGenerator) {
        this.textGenerator = textGenerator;
    }

    /**
     * Determines whether two memory texts contradict each other.
     *
     * @param textA first memory text
     * @param textB second memory text
     * @return true if the statements assert opposing facts or mutually exclusive preferences
     */
    public boolean areContradictory(String textA, String textB) {
        if (textGenerator == null) {
            // Standalone mode: cannot verify contradictions via LLM, default to false (safe merge fallback)
            return false;
        }

        try {
            String prompt = String.format(
                    "Analyze these two statements and determine if they contradict each other " +
                    "(i.e., they assert opposing facts or mutually exclusive preferences). " +
                    "Reply with exactly 'YES' if they contradict, or 'NO' if they are complementary or non-contradictory.\n\n" +
                    "Statement 1: \"%s\"\n" +
                    "Statement 2: \"%s\"\n\n" +
                    "Contradicts:", textA, textB);

            String response = textGenerator.generate(prompt, GenerationOptions.CONCISE);
            if (response != null) {
                String cleaned = response.trim().toUpperCase();
                if (cleaned.contains("YES")) {
                    log.debug("ContradictionDetector: contradiction detected between: [\"{}\"] and [\"{}\"]", textA, textB);
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("ContradictionDetector: LLM call failed, assuming no contradiction: {}", e.getMessage());
        }

        return false;
    }
}
