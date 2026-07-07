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

/**
 * GENERATE node — produces the final answer from accumulated context.
 *
 * <p>Reads context from the state's {@code context} channel, constructs a
 * generation prompt, and writes the LLM's response to the {@code answer} channel.</p>
 *
 * <p>Uses the {@link LlmBridge} (Spring AI / LangChain4j) for LLM calls.</p>
 */
public final class GenerateNode implements NodeAction<CognitiveState> {

    private static final Logger log = LoggerFactory.getLogger(GenerateNode.class);

    private final LlmBridge llmBridge;

    public GenerateNode(LlmBridge llmBridge) {
        this.llmBridge = Objects.requireNonNull(llmBridge, "llmBridge");
    }

    @Override
    public Map<String, Object> apply(CognitiveState state) {
        List<String> contextEntries = state.context();
        String contextText = contextEntries.isEmpty()
                ? "(No relevant context found)"
                : String.join("\n", contextEntries);

        log.info("[GenerateNode] Generating answer from {} context entries", contextEntries.size());

        String promptTemplate = loadPromptTemplate("cognitive-generate-system");
        String prompt = promptTemplate
                .replace("{{context}}", contextText)
                .replace("{{query}}", state.originalQuery());

        String answer = llmBridge.generate(prompt);
        log.info("[GenerateNode] Generated answer ({} chars)", answer.length());

        return Map.of("answer", answer);
    }

    private String loadPromptTemplate(String name) {
        String path = "/prompts/" + name + ".txt";
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("[GenerateNode] Failed to load prompt template '{}': {}", name, e.getMessage());
        }
        // Fallback inline template
        return """
                You are a cognitive assistant. Answer the user's question using ONLY the context below.
                If the context doesn't contain enough information, say so honestly.
                
                CONTEXT:
                {{context}}
                
                QUESTION: {{query}}
                
                Provide a clear, well-structured answer:
                """;
    }
}
