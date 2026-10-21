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

import java.util.Locale;

/**
 * Detector that checks if two semantically similar memories contradict each other.
 *
 * <p>The detector fails closed: when no LLM provider is configured, when the provider
 * throws, or when its reply is empty or unparseable, the verdict is
 * {@link Verdict#UNKNOWN}. Callers must only merge a pair on {@link Verdict#COMPATIBLE},
 * so an outage can never silently fuse (and tombstone) contradictory memories (#1146).</p>
 */
public final class ContradictionDetector {

    private static final Logger log = LoggerFactory.getLogger(ContradictionDetector.class);

    /**
     * Outcome of a contradiction check.
     */
    public enum Verdict {
        /** The statements assert opposing facts or mutually exclusive preferences. */
        CONTRADICTORY,
        /** The LLM confirmed the statements are complementary or non-contradictory. */
        COMPATIBLE,
        /** No verdict could be obtained (no provider, provider error, or unparseable reply). */
        UNKNOWN
    }

    private final LlmProvider textGenerator;

    public ContradictionDetector(LlmProvider textGenerator) {
        this.textGenerator = textGenerator;
    }

    /**
     * Returns whether a contradiction verdict can be obtained at all.
     *
     * @return {@code true} when an LLM provider is configured
     */
    public boolean canVerify() {
        return textGenerator != null;
    }

    /**
     * Classifies whether two memory texts contradict each other.
     *
     * @param textA first memory text
     * @param textB second memory text
     * @return {@link Verdict#CONTRADICTORY}, {@link Verdict#COMPATIBLE}, or
     *         {@link Verdict#UNKNOWN} when no verdict is available
     */
    public Verdict verdict(String textA, String textB) {
        if (textGenerator == null) {
            log.debug("ContradictionDetector: no LLM provider configured, verdict UNKNOWN");
            return Verdict.UNKNOWN;
        }

        String response;
        try {
            String prompt = String.format(
                    "Analyze these two statements and determine if they contradict each other " +
                    "(i.e., they assert opposing facts or mutually exclusive preferences). " +
                    "Reply with exactly 'YES' if they contradict, or 'NO' if they are complementary or non-contradictory.\n\n" +
                    "Statement 1: \"%s\"\n" +
                    "Statement 2: \"%s\"\n\n" +
                    "Contradicts:", textA, textB);

            response = textGenerator.generate(prompt, GenerationOptions.CONCISE);
        } catch (Exception e) {
            log.warn("ContradictionDetector: LLM call failed, verdict UNKNOWN (pair will not be merged): {}",
                    e.getMessage());
            return Verdict.UNKNOWN;
        }

        Verdict verdict = parse(response);
        if (verdict == Verdict.CONTRADICTORY) {
            log.debug("ContradictionDetector: contradiction detected between: [\"{}\"] and [\"{}\"]", textA, textB);
        } else if (verdict == Verdict.UNKNOWN) {
            log.warn("ContradictionDetector: unparseable LLM reply, verdict UNKNOWN (pair will not be merged)");
        }
        return verdict;
    }

    /**
     * Determines whether two memory texts are known to contradict each other.
     *
     * <p>Note that {@code false} does not mean the pair is safe to merge; use
     * {@link #verdict(String, String)} and merge only on {@link Verdict#COMPATIBLE}.</p>
     *
     * @param textA first memory text
     * @param textB second memory text
     * @return true only if the LLM confirmed a contradiction
     */
    public boolean areContradictory(String textA, String textB) {
        return verdict(textA, textB) == Verdict.CONTRADICTORY;
    }

    /**
     * Parses a YES/NO reply. The first word decides; anything else is UNKNOWN.
     */
    static Verdict parse(String response) {
        if (response == null) {
            return Verdict.UNKNOWN;
        }
        String cleaned = response.strip().toUpperCase(Locale.ROOT);
        int end = 0;
        while (end < cleaned.length() && Character.isLetter(cleaned.charAt(end))) {
            end++;
        }
        String firstWord = cleaned.substring(0, end);
        return switch (firstWord) {
            case "YES" -> Verdict.CONTRADICTORY;
            case "NO" -> Verdict.COMPATIBLE;
            default -> Verdict.UNKNOWN;
        };
    }
}
