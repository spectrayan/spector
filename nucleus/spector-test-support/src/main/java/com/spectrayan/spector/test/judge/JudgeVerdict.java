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
package com.spectrayan.spector.test.judge;

/**
 * Structured verdict from the LLM test judge.
 *
 * <p>Represents the LLM's assessment of whether a set of recall results
 * is relevant, well-ranked, or covers expected topics.</p>
 *
 * @param relevant    whether the result set is judged relevant to the query
 * @param confidence  confidence score from 0.0 (uncertain) to 1.0 (highly confident)
 * @param reasoning   the LLM's explanation for its verdict
 * @param query       the original query that was evaluated
 * @param resultCount number of results that were evaluated
 * @param latencyMs   time taken for the LLM judgment in milliseconds
 */
public record JudgeVerdict(
        boolean relevant,
        float confidence,
        String reasoning,
        String query,
        int resultCount,
        long latencyMs
) {

    /**
     * Returns a verdict indicating the judge was not available.
     */
    public static JudgeVerdict unavailable(String query) {
        return new JudgeVerdict(true, 0f,
                "LLM judge unavailable — skipping validation", query, 0, 0);
    }

    /**
     * Returns a verdict for a parse failure (LLM returned non-JSON).
     */
    public static JudgeVerdict parseFailure(String query, String rawResponse, long latencyMs) {
        return new JudgeVerdict(true, 0f,
                "Failed to parse LLM verdict: " + truncate(rawResponse, 200),
                query, 0, latencyMs);
    }

    /**
     * Whether this verdict passed (relevant or high confidence).
     */
    public boolean passed() {
        return relevant;
    }

    /**
     * Whether this verdict passed with confidence above the given threshold.
     */
    public boolean passedWithConfidence(float threshold) {
        return relevant && confidence >= threshold;
    }

    @Override
    public String toString() {
        return String.format("JudgeVerdict[%s, confidence=%.2f, results=%d, latency=%dms] %s",
                relevant ? "RELEVANT" : "NOT_RELEVANT",
                confidence, resultCount, latencyMs, reasoning);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "<null>";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
