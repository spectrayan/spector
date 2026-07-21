/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.agent.cognitive;

import com.spectrayan.spector.synapse.agent.ToolRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Post-conversation reflection engine that analyzes conversation history
 * and automatically extracts user facts and knowledge artifacts.
 *
 * <h3>Biological Analog</h3>
 * <p>Mirrors hippocampal replay during rest — the brain replays experiences
 * to consolidate them into long-term memory. The reflector replays the
 * conversation to extract and store important information.</p>
 *
 * <h3>Execution</h3>
 * <p>Runs asynchronously after the main response is sent to the user.
 * Uses a lightweight LLM prompt to extract structured data, then
 * invokes the appropriate tools (memory_remember) to persist knowledge.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Thread-safe. Runs on virtual threads.</p>
 */
public final class ConversationReflector {

    private static final Logger log = LoggerFactory.getLogger(ConversationReflector.class);
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static final Pattern FACT_PATTERN =
            Pattern.compile("FACT: field=(\\S+) value=(.+?) action=(\\S+)");
    private static final Pattern KNOWLEDGE_PATTERN =
            Pattern.compile("KNOWLEDGE: text=(.+?) importance=(\\d+)");

    private final String ollamaBaseUrl;
    private final String reflectionModel;
    private final ToolRegistry toolRegistry;
    private final String reflectionPromptTemplate;
    private final HttpClient httpClient;

    /**
     * @param ollamaBaseUrl   Ollama API base URL
     * @param reflectionModel model to use for reflection (can be smaller/faster than main model)
     * @param toolRegistry    registry containing memory_remember tool
     */
    public ConversationReflector(String ollamaBaseUrl,
                                  String reflectionModel,
                                  ToolRegistry toolRegistry) {
        this.ollamaBaseUrl = Objects.requireNonNull(ollamaBaseUrl, "ollamaBaseUrl");
        this.reflectionModel = Objects.requireNonNull(reflectionModel, "reflectionModel");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.reflectionPromptTemplate = loadPromptTemplate();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Asynchronously reflects on a conversation to extract and store important information.
     *
     * @param conversationMessages the messages from the conversation (role + content maps)
     */
    public void reflectAsync(List<Map<String, Object>> conversationMessages) {
        if (conversationMessages == null || conversationMessages.size() < 2) {
            return; // Need at least a user message + assistant response
        }

        Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
            try {
                reflect(conversationMessages);
            } catch (Exception e) {
                log.warn("[Reflector] Async reflection failed: {}", e.getMessage());
            }
        });
    }

    /**
     * Synchronous reflection — extracts and applies updates.
     */
    void reflect(List<Map<String, Object>> conversationMessages) {
        String conversationText = formatConversation(conversationMessages);
        String prompt = reflectionPromptTemplate.replace("{{conversation}}", conversationText);

        try {
            String extraction = callLlm(prompt);
            if (extraction == null || extraction.isBlank()) {
                log.debug("[Reflector] No extraction from reflection");
                return;
            }

            List<Map<String, String>> facts = extractFacts(extraction);
            List<Map<String, Object>> knowledge = extractKnowledge(extraction);

            log.info("[Reflector] Extracted {} facts, {} knowledge items",
                    facts.size(), knowledge.size());

            // Store knowledge artifacts via memory_remember tool
            var rememberTool = toolRegistry.get("memory_remember");
            if (rememberTool.isPresent()) {
                for (var item : knowledge) {
                    try {
                        io.modelcontextprotocol.spec.McpSchema.CallToolResult toolResult = rememberTool.get().execute(null, item);
                        StringBuilder sb = new StringBuilder();
                        if (toolResult != null && toolResult.content() != null) {
                            for (var contentEntry : toolResult.content()) {
                                if (contentEntry instanceof io.modelcontextprotocol.spec.McpSchema.TextContent tc) {
                                    sb.append(tc.text());
                                }
                            }
                        }
                        String result = sb.toString();
                        log.debug("[Reflector] Knowledge stored: {}", result);
                    } catch (Exception e) {
                        log.debug("[Reflector] Knowledge storage failed: {}", e.getMessage());
                    }
                }
            }

            // Store user facts as memories
            if (!facts.isEmpty()) {
                var factTool = rememberTool.orElse(null);
                if (factTool != null) {
                    for (var fact : facts) {
                        try {
                            Map<String, Object> args = new HashMap<>(fact);
                            args.put("text", "User fact: " + fact.get("field")
                                    + " = " + fact.get("value"));
                            factTool.execute(null, args);
                        } catch (Exception e) {
                            log.debug("[Reflector] Fact storage failed: {}", e.getMessage());
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.warn("[Reflector] Reflection failed: {}", e.getMessage());
        }
    }

    // ── LLM Call ─────────────────────────────────────────────────────

    private String callLlm(String prompt) throws IOException, InterruptedException {
        var requestBody = Map.of(
                "model", reflectionModel,
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
            log.warn("[Reflector] LLM returned HTTP {}", response.statusCode());
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> body = MAPPER.readValue(response.body(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) body.get("message");
        return message != null ? (String) message.get("content") : null;
    }

    // ── Extraction ───────────────────────────────────────────────────

    private static List<Map<String, String>> extractFacts(String text) {
        List<Map<String, String>> facts = new ArrayList<>();
        Matcher m = FACT_PATTERN.matcher(text);
        while (m.find()) {
            facts.add(Map.of("field", m.group(1), "value", m.group(2).trim(), "action", m.group(3)));
        }
        return facts;
    }

    private static List<Map<String, Object>> extractKnowledge(String text) {
        List<Map<String, Object>> knowledge = new ArrayList<>();
        Matcher m = KNOWLEDGE_PATTERN.matcher(text);
        while (m.find()) {
            var item = new HashMap<String, Object>();
            item.put("text", m.group(1).trim());
            item.put("importance", m.group(2));
            knowledge.add(item);
        }
        return knowledge;
    }

    // ── Formatting ───────────────────────────────────────────────────

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

    private static String loadPromptTemplate() {
        try (InputStream is = ConversationReflector.class.getResourceAsStream(
                "/prompts/reflection-extraction.txt")) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LoggerFactory.getLogger(ConversationReflector.class)
                    .warn("Failed to load reflection prompt: {}", e.getMessage());
        }
        return "Analyze the conversation and extract user facts and knowledge.\n{{conversation}}";
    }
}
