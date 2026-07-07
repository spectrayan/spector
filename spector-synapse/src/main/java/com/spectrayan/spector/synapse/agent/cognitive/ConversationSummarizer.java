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
package com.spectrayan.spector.synapse.agent.cognitive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Context window management via conversation summarization.
 *
 * <h3>Biological Analog</h3>
 * <p>Mirrors cortical memory consolidation — as episodic memories age,
 * the brain compresses them into gist representations. This summarizer
 * compresses older conversation turns into a condensed summary, freeing
 * the context window for recent exchanges.</p>
 *
 * <h3>Strategy</h3>
 * <p>When conversation history exceeds a configurable token threshold:</p>
 * <ol>
 *   <li>Keep the most recent {@code N} messages verbatim</li>
 *   <li>Summarize all older messages into a single condensed block</li>
 *   <li>Return the combined context (summary + recent) within budget</li>
 * </ol>
 *
 * <h3>Thread Safety</h3>
 * <p>Thread-safe. Stateless per invocation.</p>
 */
public final class ConversationSummarizer {

    private static final Logger log = LoggerFactory.getLogger(ConversationSummarizer.class);
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /** Default number of recent messages to keep verbatim. */
    private static final int DEFAULT_KEEP_RECENT = 6;

    /** Default estimated max tokens before summarization triggers. */
    private static final int DEFAULT_MAX_TOKENS = 4096;

    /** Rough estimate: 1 token ≈ 4 characters for English text. */
    private static final int CHARS_PER_TOKEN = 4;

    private final String ollamaBaseUrl;
    private final String model;
    private final int keepRecent;
    private final int maxTokens;
    private final HttpClient httpClient;

    /**
     * @param ollamaBaseUrl Ollama API base URL
     * @param model         LLM model for summarization (can be smaller/faster than main model)
     */
    public ConversationSummarizer(String ollamaBaseUrl, String model) {
        this(ollamaBaseUrl, model, DEFAULT_KEEP_RECENT, DEFAULT_MAX_TOKENS);
    }

    /**
     * @param ollamaBaseUrl Ollama API base URL
     * @param model         LLM model for summarization
     * @param keepRecent    number of recent messages to keep verbatim
     * @param maxTokens     estimated max tokens before summarization triggers
     */
    public ConversationSummarizer(String ollamaBaseUrl, String model,
                                   int keepRecent, int maxTokens) {
        this.ollamaBaseUrl = Objects.requireNonNull(ollamaBaseUrl, "ollamaBaseUrl");
        this.model = Objects.requireNonNull(model, "model");
        this.keepRecent = keepRecent;
        this.maxTokens = maxTokens;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Compacts conversation history if it exceeds the token budget.
     *
     * <p>If the conversation fits within the budget, returns it unchanged.
     * Otherwise, summarizes older messages and prepends the summary as a
     * system message before the recent verbatim messages.</p>
     *
     * @param messages the full conversation history (role + content maps)
     * @return compacted message list within budget
     */
    public List<Map<String, Object>> compact(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        int estimatedTokens = estimateTokens(messages);
        if (estimatedTokens <= maxTokens || messages.size() <= keepRecent) {
            log.debug("[Summarizer] Within budget ({} est. tokens, {} msgs) — no compaction needed",
                    estimatedTokens, messages.size());
            return messages;
        }

        log.info("[Summarizer] Compacting {} messages ({} est. tokens, budget: {})",
                messages.size(), estimatedTokens, maxTokens);

        // Split: older messages to summarize, recent to keep verbatim
        int splitPoint = messages.size() - keepRecent;
        List<Map<String, Object>> olderMessages = messages.subList(0, splitPoint);
        List<Map<String, Object>> recentMessages = messages.subList(splitPoint, messages.size());

        try {
            String summary = summarize(olderMessages);
            if (summary == null || summary.isBlank()) {
                log.warn("[Summarizer] Summarization returned empty — keeping original");
                return messages;
            }

            // Build compacted list: summary system message + recent verbatim
            List<Map<String, Object>> compacted = new ArrayList<>(keepRecent + 1);
            compacted.add(Map.of(
                    "role", "system",
                    "content", "## Previous Conversation Summary\n\n" + summary
                            + "\n\n---\n*The above summarizes " + olderMessages.size()
                            + " earlier messages. The conversation continues below.*"
            ));
            compacted.addAll(recentMessages);

            log.info("[Summarizer] Compacted {} → {} messages (summary: {} chars)",
                    messages.size(), compacted.size(), summary.length());
            return compacted;

        } catch (Exception e) {
            log.warn("[Summarizer] Summarization failed: {} — keeping original", e.getMessage());
            return messages;
        }
    }

    /**
     * Checks whether the conversation needs compaction.
     *
     * @param messages the conversation history
     * @return true if the estimated tokens exceed the budget
     */
    public boolean needsCompaction(List<Map<String, Object>> messages) {
        return messages != null
                && messages.size() > keepRecent
                && estimateTokens(messages) > maxTokens;
    }

    // ── Summarization ────────────────────────────────────────────────

    private String summarize(List<Map<String, Object>> messages)
            throws IOException, InterruptedException {
        String conversationText = formatConversation(messages);

        String prompt = """
                Summarize the following conversation concisely. Focus on:
                1. Key facts shared by the user (name, preferences, decisions)
                2. Important topics discussed and conclusions reached
                3. Any action items or commitments made
                4. The overall tone and direction of the conversation
                
                Keep the summary factual and under 300 words. Use bullet points.
                Do NOT include greetings, pleasantries, or filler.
                
                CONVERSATION:
                """ + conversationText;

        var requestBody = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "stream", false);

        String jsonBody = MAPPER.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaBaseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofMinutes(2))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("[Summarizer] LLM returned HTTP {}", response.statusCode());
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> body = MAPPER.readValue(response.body(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) body.get("message");
        return message != null ? (String) message.get("content") : null;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static int estimateTokens(List<Map<String, Object>> messages) {
        int totalChars = 0;
        for (var msg : messages) {
            String content = String.valueOf(msg.getOrDefault("content", ""));
            totalChars += content.length();
        }
        return totalChars / CHARS_PER_TOKEN;
    }

    private static String formatConversation(List<Map<String, Object>> messages) {
        var sb = new StringBuilder();
        for (var msg : messages) {
            String role = String.valueOf(msg.getOrDefault("role", "unknown"));
            String content = String.valueOf(msg.getOrDefault("content", ""));
            if ("system".equals(role)) continue;
            if ("tool".equals(role)) continue;
            sb.append(role.toUpperCase()).append(": ").append(content).append("\n\n");
        }
        return sb.toString();
    }
}
