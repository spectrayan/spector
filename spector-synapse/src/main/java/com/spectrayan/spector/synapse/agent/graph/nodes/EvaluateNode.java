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
package com.spectrayan.spector.synapse.agent.graph.nodes;

import com.spectrayan.spector.synapse.agent.graph.CognitiveState;
import com.spectrayan.spector.synapse.bridge.LlmBridge;

import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EVALUATE node — asks the LLM to assess retrieved context sufficiency.
 *
 * <p>Sets the {@code decision} channel to one of:</p>
 * <ul>
 *   <li>{@code GENERATE} — context is sufficient, proceed to answer generation</li>
 *   <li>{@code REQUERY} — context is insufficient, refine query and retrieve again</li>
 * </ul>
 *
 * <p>Uses the {@link LlmBridge} (Spring AI / LangChain4j) for LLM calls instead of
 * the enterprise {@code TextGenerationProvider}.</p>
 */
public final class EvaluateNode implements NodeAction<CognitiveState> {

    private static final Logger log = LoggerFactory.getLogger(EvaluateNode.class);

    private static final Pattern REQUERY_PATTERN = Pattern.compile(
            "DECISION:\\s*REQUERY.*?REFINED_QUERY:\\s*(.+?)(?:\\nREASON:|$)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern GENERATE_PATTERN = Pattern.compile(
            "DECISION:\\s*GENERATE",
            Pattern.CASE_INSENSITIVE);

    private final LlmBridge llmBridge;

    public EvaluateNode(LlmBridge llmBridge) {
        this.llmBridge = Objects.requireNonNull(llmBridge, "llmBridge");
    }

    @Override
    public Map<String, Object> apply(CognitiveState state) {
        List<String> contextEntries = state.context();
        String contextText = contextEntries.isEmpty()
                ? "(No results retrieved)"
                : String.join("\n", contextEntries);

        log.info("[EvaluateNode] Evaluating {} context entries", contextEntries.size());

        // Load prompt template and substitute placeholders
        String promptTemplate = loadPromptTemplate("agentic-rag-evaluate");
        String prompt = promptTemplate
                .replace("{{context}}", contextText)
                .replace("{{query}}", state.originalQuery());

        String response = llmBridge.generate(prompt);
        log.debug("[EvaluateNode] LLM response: {}", truncate(response, 200));

        // Parse decision
        Matcher requeryMatcher = REQUERY_PATTERN.matcher(response);
        if (requeryMatcher.find()) {
            String refinedQuery = requeryMatcher.group(1).trim();
            log.info("[EvaluateNode] REQUERY → '{}'", refinedQuery);
            return Map.of(
                    "decision", "REQUERY",
                    "query", refinedQuery
            );
        }

        if (GENERATE_PATTERN.matcher(response).find()) {
            log.info("[EvaluateNode] GENERATE");
            return Map.of("decision", "GENERATE");
        }

        // Fallback: treat as GENERATE
        log.warn("[EvaluateNode] Could not parse decision, defaulting to GENERATE");
        return Map.of("decision", "GENERATE");
    }

    private String loadPromptTemplate(String name) {
        String path = "/prompts/" + name + ".txt";
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("[EvaluateNode] Failed to load prompt template '{}': {}", name, e.getMessage());
        }
        // Fallback inline template
        return """
                You are evaluating whether the retrieved context is sufficient to answer the query.
                
                CONTEXT:
                {{context}}
                
                QUERY: {{query}}
                
                Respond with EXACTLY one of:
                DECISION: GENERATE
                REASON: [why context is sufficient]
                
                OR:
                DECISION: REQUERY
                REFINED_QUERY: [a better search query]
                REASON: [why context is insufficient]
                """;
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
