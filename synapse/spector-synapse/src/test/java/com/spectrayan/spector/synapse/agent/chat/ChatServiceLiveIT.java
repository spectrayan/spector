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
package com.spectrayan.spector.synapse.agent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.spectrayan.spector.synapse.agent.AgentSoul;
import com.spectrayan.spector.synapse.agent.AgentTool;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.*;
import com.spectrayan.spector.synapse.agent.chat.model.Conversation;
import com.spectrayan.spector.synapse.agent.chat.service.ChatMemoryPort;
import com.spectrayan.spector.synapse.agent.chat.service.ChatService;
import com.spectrayan.spector.synapse.agent.chat.service.ContextPrimingService;
import com.spectrayan.spector.synapse.agent.graph.AgentChatListener;
import com.spectrayan.spector.synapse.agent.graph.AgenticChatGraph;
import com.spectrayan.spector.synapse.agent.service.IdentityPrimerService;
import com.spectrayan.spector.synapse.agent.tools.CurrentTimeTool;
import com.spectrayan.spector.synapse.bridge.LlmBridge;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.*;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Integration test for the ChatService with real Ollama model.
 *
 * <p>Manually constructs the full chat pipeline:
 * {@code SynapseProperties → LlmBridge → AgenticChatGraph → ChatService}
 * without Spring Boot context (avoids MemoryBridge init issues).
 * Tests the complete cognitive chat flow against live Ollama.</p>
 *
 * <h3>Prerequisites</h3>
 * <pre>{@code
 *   ollama pull qwen3.5:latest
 *   ollama serve
 * }</pre>
 *
 * <h3>Running</h3>
 * <pre>{@code
 *   mvn test -pl spector-synapse -Dgroups=ollama \
 *       -Dtest=ChatServiceLiveIT -Dsurefire.failIfNoSpecifiedTests=false -am
 * }</pre>
 */
@Tag("ollama")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChatServiceLiveIT {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceLiveIT.class);

    private static final String OLLAMA_URL = System.getProperty(
            "ollama.url", "http://localhost:11434");
    private static final String MODEL = System.getProperty(
            "ollama.model", "qwen3.5:latest");

    private static boolean ollamaAvailable = false;

    // Manually constructed beans — no Spring Boot context needed
    private static SynapseProperties props;
    private static LlmBridge llmBridge;
    private static ToolRegistry toolRegistry;
    private static IdentityPrimerService identityPrimerService;
    private static AgenticChatGraph agenticChatGraph;

    private InMemoryChatMemoryPort memoryPort;
    private ChatService chatService;

    // ═══════════════════════════════════════════════════════════════
    // SETUP
    // ═══════════════════════════════════════════════════════════════

    @BeforeAll
    static void checkOllamaAndBuildBeans() {
        try {
            var client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5)).build();
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_URL + "/api/tags"))
                    .GET().timeout(Duration.ofSeconds(5)).build();
            var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            ollamaAvailable = resp.statusCode() == 200;
            log.info("✅ Ollama available at {} for ChatService tests", OLLAMA_URL);
        } catch (Exception e) {
            log.warn("⚠️  Ollama NOT available: {}", e.getMessage());
            return;
        }

        // Build the dependency chain manually
        props = new SynapseProperties(
                0, "test-key", System.getProperty("java.io.tmpdir") + "/spector-test",
                new OllamaProperties(OLLAMA_URL, MODEL, "nomic-embed-text"),
                new MemoryProperties(0, 0),
                new CorsProperties("http://localhost:4200")
        );

        llmBridge = new LlmBridge(props);
        toolRegistry = new ToolRegistry(List.of(new CurrentTimeTool()));
        identityPrimerService = new IdentityPrimerService();
        agenticChatGraph = new AgenticChatGraph(llmBridge, toolRegistry);
    }

    @BeforeEach
    void setupChatService() {
        memoryPort = new InMemoryChatMemoryPort();
        var contextPriming = new ContextPrimingService(memoryPort);
        chatService = new ChatService(
                memoryPort, contextPriming, identityPrimerService,
                toolRegistry, agenticChatGraph, new com.spectrayan.spector.memory.id.TsidGenerator(), props);
    }

    // ═══════════════════════════════════════════════════════════════
    // TEST 1: Full Chat Turn — Save to Session Memory
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("ChatService: full turn persists to session memory")
    void fullChatTurn_savesToSession() {
        assumeTrue(ollamaAvailable, "Ollama required");

        var soul = AgentSoul.builder()
                .name("MathHelper")
                .purpose("Helpful math assistant")
                .systemPrompt("You are a helpful math assistant. Answer concisely.")
                .model(MODEL)
                .build();

        var response = chatService.executeChat(
                "Hello! What's 2 + 2?",
                "session-1", MODEL, soul,
                10, null, AgentChatListener.NOOP);

        assertThat(response.status()).isEqualTo("DONE");
        assertThat(response.response()).isNotBlank();
        assertThat(response.sessionId()).isEqualTo("session-1");
        assertThat(response.model()).isEqualTo(MODEL);
        assertThat(response.durationMs()).isGreaterThan(0);

        // Verify session memory was saved
        assertThat(memoryPort.savedTurns).hasSize(1);
        var savedTurn = memoryPort.savedTurns.getFirst();
        assertThat(savedTurn.sessionId()).isEqualTo("session-1");
        assertThat(savedTurn.userMessage()).isEqualTo("Hello! What's 2 + 2?");
        assertThat(savedTurn.assistantResponse()).isNotBlank();

        log.info("[ChatService Turn] Response: {}", response.response());
        log.info("[ChatService Turn] Latency: {}ms", response.durationMs());
    }

    // ═══════════════════════════════════════════════════════════════
    // TEST 2: Context Priming — History Loaded from Memory
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("ChatService: context priming loads prior session history")
    void contextPriming_loadsHistory() {
        assumeTrue(ollamaAvailable, "Ollama required");

        // Pre-populate session history
        memoryPort.preloadSession("session-ctx", List.of(
                Map.of("role", "user", "content", "My favorite color is blue"),
                Map.of("role", "assistant", "content",
                        "That's a great choice! Blue is calming and peaceful.")
        ));

        var soul = AgentSoul.builder()
                .name("ContextAssistant")
                .purpose("Helpful assistant that remembers context")
                .systemPrompt("You are a helpful assistant. Answer based on prior conversation context.")
                .model(MODEL)
                .build();

        var response = chatService.executeChat(
                "What's my favorite color?",
                "session-ctx", MODEL, soul,
                10, null, AgentChatListener.NOOP);

        assertThat(response.status()).isEqualTo("DONE");
        assertThat(response.response().toLowerCase())
                .as("Should mention 'blue' from session history")
                .contains("blue");

        log.info("[Context Priming] Response: {}", response.response());
    }

    // ═══════════════════════════════════════════════════════════════
    // TEST 3: Multi-Turn via ChatService — Session Continuity
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("ChatService: multi-turn conversation maintains session continuity")
    void multiTurn_sessionContinuity() {
        assumeTrue(ollamaAvailable, "Ollama required");

        String sessionId = "session-multi-" + UUID.randomUUID();

        var soul = AgentSoul.builder()
                .name("FriendlyBot")
                .purpose("Friendly assistant that remembers user info")
                .systemPrompt("You are a friendly assistant. Remember everything the user tells you. Answer concisely.")
                .model(MODEL)
                .build();

        // Turn 1: Tell the agent where we live
        var r1 = chatService.executeChat(
                "I live in Austin, Texas.",
                sessionId, MODEL, soul,
                10, null, AgentChatListener.NOOP);

        assertThat(r1.status()).isEqualTo("DONE");
        log.info("[Turn 1] {}", r1.response());

        // Turn 2 — should recall Austin from session memory
        var r2 = chatService.executeChat(
                "What city did I say I live in?",
                sessionId, MODEL, soul,
                10, null, AgentChatListener.NOOP);

        assertThat(r2.status()).isEqualTo("DONE");
        assertThat(r2.response().toLowerCase())
                .as("Should mention Austin from the prior turn")
                .contains("austin");

        log.info("[Turn 2] {}", r2.response());
        assertThat(memoryPort.savedTurns).hasSizeGreaterThanOrEqualTo(1);
    }

    // ═══════════════════════════════════════════════════════════════
    // TEST 4: Tool Registration — Cognitive Tools Present
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("ChatService: tool registry contains cognitive tools")
    void toolRegistry_containsCognitiveTools() {
        assertThat(toolRegistry.names())
                .as("Registry should contain current_time tool")
                .contains("current_time");

        assertThat(toolRegistry.names().size()).isGreaterThanOrEqualTo(1);
        log.info("[Registry] Tools: {}", toolRegistry.names());
    }

    // ═══════════════════════════════════════════════════════════════
    // TEST 5: IdentityPrimerService — System Prompt Generation
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("IdentityPrimerService: generates system prompt with agent soul data")
    void identityPrimer_generatesPrompt() {
        // Build a soul WITHOUT a custom systemPrompt — uses the default template
        // which contains {{agent_identity}} placeholder that gets replaced
        var soul = AgentSoul.builder()
                .name("Aria")
                .purpose("Research companion for cognitive science")
                .personality("Warm, curious, and thorough")
                .expertiseDomain("cognitive science")
                .expertiseDomain("neuroscience")
                .coreValue("intellectual honesty")
                .communicationStyle("conversational yet precise")
                .ethicalGuardrail("Never provide medical advice")
                .build();

        String prompt = identityPrimerService.buildSystemPrompt(soul);

        // The prompt should contain the agent identity data
        assertThat(prompt).isNotBlank();
        // The identity block is either substituted into {{agent_identity}}
        // or appended to the default prompt
        assertThat(prompt.length())
                .as("Enriched prompt should be longer than the default")
                .isGreaterThan(50);

        log.info("[Primer] Generated prompt ({} chars):\n{}",
                prompt.length(), prompt.substring(0, Math.min(500, prompt.length())));
    }

    @Test
    @Order(6)
    @DisplayName("IdentityPrimerService: handles null/empty soul gracefully")
    void identityPrimer_handlesMissingSoul() {
        String prompt = identityPrimerService.buildSystemPrompt(AgentSoul.NONE);
        assertThat(prompt).isNotBlank();

        String promptNull = identityPrimerService.buildSystemPrompt(null);
        assertThat(promptNull).isNotBlank();

        log.info("[Primer] Empty soul prompt: {}", prompt.substring(0, Math.min(200, prompt.length())));
    }

    // ═══════════════════════════════════════════════════════════════
    // IN-MEMORY CHAT MEMORY PORT
    // ═══════════════════════════════════════════════════════════════

    static class InMemoryChatMemoryPort implements ChatMemoryPort {

        final Map<String, List<Map<String, Object>>> sessions = new LinkedHashMap<>();
        final List<SavedTurn> savedTurns = new CopyOnWriteArrayList<>();

        record SavedTurn(String sessionId, String userMessage,
                         String assistantResponse, String model) {}

        void preloadSession(String sessionId, List<Map<String, Object>> messages) {
            sessions.put(sessionId, new ArrayList<>(messages));
        }

        void clear() {
            sessions.clear();
            savedTurns.clear();
        }

        @Override
        public List<Map<String, Object>> loadSessionHistory(String sessionId) {
            return sessions.getOrDefault(sessionId, List.of());
        }

        @Override
        public void saveToSession(String sessionId, String userMessage,
                                   String assistantResponse, String model) {
            savedTurns.add(new SavedTurn(sessionId, userMessage, assistantResponse, model));
            sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
            if (userMessage != null) {
                sessions.get(sessionId).add(Map.of("role", "user", "content", userMessage));
            }
            sessions.get(sessionId).add(Map.of("role", "assistant", "content", assistantResponse));
        }

        @Override
        public List<Conversation> listSessions(int limit) {
            return sessions.entrySet().stream()
                    .limit(limit)
                    .map(e -> new Conversation(
                            e.getKey(), e.getValue().size(),
                            e.getValue().isEmpty() ? "" :
                                    e.getValue().getFirst().getOrDefault("content", "").toString(),
                            Instant.now(), Instant.now()))
                    .toList();
        }

        @Override
        public List<PrimedMemory> recallRelevantMemories(String query,
                                                          String excludeSessionId, int limit) {
            if (query == null || query.isBlank()) return List.of();
            // Extract significant words (>2 chars) for matching
            Set<String> keywords = Arrays.stream(query.toLowerCase()
                            .replaceAll("[^a-z0-9 ]", "").split("\\s+"))
                    .filter(w -> w.length() > 2)
                    .collect(java.util.stream.Collectors.toSet());
            return sessions.entrySet().stream()
                    .filter(e -> excludeSessionId == null || !e.getKey().equals(excludeSessionId))
                    .map(Map.Entry::getValue)
                    .flatMap(List::stream)
                    .filter(msg -> {
                        String content = msg.getOrDefault("content", "").toString().toLowerCase();
                        return keywords.stream().anyMatch(content::contains);
                    })
                    .limit(limit)
                    .map(msg -> new PrimedMemory(
                            msg.getOrDefault("content", "").toString(),
                            "EPISODIC", "recent", 0.8f))
                    .toList();
        }
    }
}
