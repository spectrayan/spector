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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Pre-built prompt templates for different LLM test validation types.
 *
 * <p>Each template instructs the LLM to respond in structured JSON format
 * for reliable automated parsing.</p>
 *
 * <h3>Supported Validation Types</h3>
 * <ul>
 *   <li><b>Relevance</b>: Are these results semantically relevant to the query?</li>
 *   <li><b>Ranking</b>: Are higher-scored results more relevant than lower-scored ones?</li>
 *   <li><b>Coverage</b>: Does the result set cover the expected topics?</li>
 * </ul>
 */
public final class JudgePromptTemplates {

    private JudgePromptTemplates() {}

    private static final int MAX_RESULT_CHARS = 200;
    private static final int MAX_RESULTS_IN_PROMPT = 10;

    /**
     * Builds a relevance judgment prompt.
     *
     * @param query       the recall query
     * @param resultTexts the texts of the recall results
     * @param criteria    human-readable description of what makes results relevant
     * @return formatted prompt
     */
    public static String relevancePrompt(String query, List<String> resultTexts, String criteria) {
        String formattedResults = formatResults(resultTexts);

        return """
                You are a test validation judge. Your job is to determine whether a set of \
                memory recall results is relevant to a given query.

                QUERY: "%s"

                RELEVANCE CRITERIA: %s

                RESULTS:
                %s

                INSTRUCTIONS:
                - Evaluate whether the results are semantically relevant to the query.
                - Consider partial relevance (a result about "databases" is partially relevant to "PostgreSQL connection pools").
                - If at least 30%% of results are relevant to the query topic, judge as relevant.
                - Be lenient — real-world semantic search often returns tangentially related content.

                Respond ONLY with this exact JSON format, no other text:
                {"relevant": true, "confidence": 0.85, "reasoning": "Brief explanation"}
                """.formatted(query, criteria, formattedResults);
    }

    /**
     * Builds a ranking quality judgment prompt.
     *
     * @param query         the recall query
     * @param rankedResults the texts of results in score-descending order
     * @return formatted prompt
     */
    public static String rankingPrompt(String query, List<String> rankedResults) {
        String formattedResults = formatNumberedResults(rankedResults);

        return """
                You are a test validation judge. Your job is to evaluate whether the ranking \
                order of memory recall results makes sense for a given query.

                QUERY: "%s"

                RESULTS (ordered by score, highest first):
                %s

                INSTRUCTIONS:
                - Check if higher-ranked results are generally more relevant than lower-ranked ones.
                - Minor ranking imperfections are acceptable (e.g., #2 slightly more relevant than #1).
                - Judge as relevant if the overall ranking trend is reasonable.

                Respond ONLY with this exact JSON format, no other text:
                {"relevant": true, "confidence": 0.85, "reasoning": "Brief explanation"}
                """.formatted(query, formattedResults);
    }

    /**
     * Builds a topic coverage judgment prompt.
     *
     * @param query          the recall query
     * @param resultTexts    the texts of the recall results
     * @param expectedTopics topics that should be covered
     * @return formatted prompt
     */
    public static String coveragePrompt(String query, List<String> resultTexts,
                                         List<String> expectedTopics) {
        String formattedResults = formatResults(resultTexts);
        String topics = String.join(", ", expectedTopics);

        return """
                You are a test validation judge. Your job is to evaluate whether a set of \
                memory recall results covers the expected topics.

                QUERY: "%s"

                EXPECTED TOPICS: %s

                RESULTS:
                %s

                INSTRUCTIONS:
                - Check if the result set covers at least some of the expected topics.
                - A topic is "covered" if any result is semantically related to it.
                - If at least 50%% of expected topics are covered, judge as relevant.

                Respond ONLY with this exact JSON format, no other text:
                {"relevant": true, "confidence": 0.85, "reasoning": "Brief explanation of which topics were/weren't covered"}
                """.formatted(query, topics, formattedResults);
    }

    // ─────────────── Formatting helpers ───────────────

    private static String formatResults(List<String> texts) {
        return texts.stream()
                .limit(MAX_RESULTS_IN_PROMPT)
                .map(t -> "- " + truncate(t, MAX_RESULT_CHARS))
                .collect(Collectors.joining("\n"));
    }

    private static String formatNumberedResults(List<String> texts) {
        return IntStream.range(0, Math.min(texts.size(), MAX_RESULTS_IN_PROMPT))
                .mapToObj(i -> String.format("#%d: %s", i + 1, truncate(texts.get(i), MAX_RESULT_CHARS)))
                .collect(Collectors.joining("\n"));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "<null>";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}
