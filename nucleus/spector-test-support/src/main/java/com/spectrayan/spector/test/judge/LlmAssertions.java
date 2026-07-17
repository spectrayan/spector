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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluent assertion API for LLM-based test validation.
 *
 * <p>Provides a clean way to add LLM judge assertions to existing tests.
 * Uses a text extractor function so it works with any result type (module-agnostic).</p>
 *
 * <h3>Usage with CognitiveResult</h3>
 * <pre>{@code
 *   LlmAssertions.assertRecall(judge, query, results, CognitiveResult::text)
 *       .isRelevantTo("Results should contain database-related memories");
 * }</pre>
 *
 * <h3>Usage with raw strings</h3>
 * <pre>{@code
 *   LlmAssertions.assertTexts(judge, query, textList)
 *       .warnIfIrrelevant("Expected search results about Kafka");
 * }</pre>
 */
public final class LlmAssertions {

    private static final Logger log = LoggerFactory.getLogger(LlmAssertions.class);

    private LlmAssertions() {}

    /**
     * Creates a fluent assertion for recall results using a text extractor.
     *
     * @param judge          the LLM test judge
     * @param query          the query that produced the results
     * @param results        the recall results
     * @param textExtractor  function to extract text from each result
     * @param <T>            result type
     * @return fluent assertion chain
     */
    public static <T> LlmRecallAssert<T> assertRecall(LlmTestJudge judge, String query,
                                                        List<T> results,
                                                        Function<T, String> textExtractor) {
        return new LlmRecallAssert<>(judge, query, results, textExtractor);
    }

    /**
     * Creates a fluent assertion for raw text lists.
     *
     * @param judge  the LLM test judge
     * @param query  the query that produced the results
     * @param texts  the result texts
     * @return fluent assertion chain
     */
    public static LlmRecallAssert<String> assertTexts(LlmTestJudge judge, String query,
                                                       List<String> texts) {
        return new LlmRecallAssert<>(judge, query, texts, Function.identity());
    }

    /**
     * Fluent assertion chain for LLM-judged recall results.
     *
     * @param <T> the result type
     */
    public static class LlmRecallAssert<T> {

        private final LlmTestJudge judge;
        private final String query;
        private final List<T> results;
        private final Function<T, String> textExtractor;
        private final List<String> resultTexts;

        LlmRecallAssert(LlmTestJudge judge, String query, List<T> results,
                         Function<T, String> textExtractor) {
            this.judge = judge;
            this.query = query;
            this.results = results;
            this.textExtractor = textExtractor;
            this.resultTexts = results.stream().map(textExtractor).toList();
        }

        /**
         * Hard-fails the test if the LLM judges the results as not relevant.
         *
         * @param criteria description of what makes results relevant
         * @return this for chaining
         */
        public LlmRecallAssert<T> isRelevantTo(String criteria) {
            JudgeVerdict verdict = judge.judgeRelevance(query, resultTexts, criteria);
            assertThat(verdict.passed())
                    .as("LLM Judge [%s]: %s — %s",
                            judge.llm().modelName(), criteria, verdict.reasoning())
                    .isTrue();
            return this;
        }

        /**
         * Logs a warning if the LLM judges the results as not relevant,
         * but does NOT fail the test.
         *
         * @param criteria description of what makes results relevant
         * @return this for chaining
         */
        public LlmRecallAssert<T> warnIfIrrelevant(String criteria) {
            JudgeVerdict verdict = judge.judgeRelevance(query, resultTexts, criteria);
            if (!verdict.passed()) {
                log.warn("⚠ LLM Judge [{}]: NOT_RELEVANT (confidence={}) — {}",
                        judge.llm().modelName(),
                        String.format("%.2f", verdict.confidence()),
                        verdict.reasoning());
            }
            return this;
        }

        /**
         * Judges whether the ranking order is reasonable.
         *
         * @return this for chaining
         */
        public LlmRecallAssert<T> hasGoodRanking() {
            JudgeVerdict verdict = judge.judgeRanking(query, resultTexts);
            if (!verdict.passed()) {
                log.warn("⚠ LLM Judge [{}]: Ranking quality low (confidence={}) — {}",
                        judge.llm().modelName(),
                        String.format("%.2f", verdict.confidence()),
                        verdict.reasoning());
            }
            return this;
        }

        /**
         * Judges whether results cover the expected topics.
         *
         * @param topics expected topics
         * @return this for chaining
         */
        public LlmRecallAssert<T> coversTopics(String... topics) {
            JudgeVerdict verdict = judge.judgeCoverage(query, resultTexts, List.of(topics));
            if (!verdict.passed()) {
                log.warn("⚠ LLM Judge [{}]: Missing topic coverage (confidence={}) — {}",
                        judge.llm().modelName(),
                        String.format("%.2f", verdict.confidence()),
                        verdict.reasoning());
            }
            return this;
        }

        /**
         * Returns the verdict without asserting — for custom handling.
         *
         * @param criteria relevance criteria
         * @return the verdict
         */
        public JudgeVerdict verdict(String criteria) {
            return judge.judgeRelevance(query, resultTexts, criteria);
        }
    }
}
