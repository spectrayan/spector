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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.agent.AgentSoul;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Deep conversation integration test with LLM-as-Judge validation.
 *
 * <p>Runs multi-turn (10+) conversations from JSON scripts in test resources,
 * evaluating each agent response using a separate LLM judge call via
 * {@link LlmBridge}. Tests the agent's ability to handle:</p>
 * <ul>
 *   <li>Terse replies ("yes", "nah", "the second one")</li>
 *   <li>Contextual follow-ups across many turns</li>
 *   <li>Ordinal references ("the second one sounds good")</li>
 *   <li>Iterative refinement ("keep it simpler")</li>
 *   <li>Dual-intent messages ("thanks! btw call me Sam")</li>
 * </ul>
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
 *       -Dtest=DeepConversationLiveIT -Dsurefire.failIfNoSpecifiedTests=false -am
 * }</pre>
 */
@Tag("ollama")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeepConversationLiveIT {

    private static final Logger log = LoggerFactory.getLogger(DeepConversationLiveIT.class);
    // Use Jackson 2 ObjectMapper (com.fasterxml.jackson) which is on the Spring Boot classpath
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String OLLAMA_URL = System.getProperty(
            "ollama.url", "http://localhost:11434");
    private static final String MODEL = System.getProperty(
            "ollama.model", "qwen3.5:latest");

    private static boolean ollamaAvailable = false;

    // Manually constructed beans
    private static SynapseProperties props;
    private static LlmBridge llmBridge;
    private static ToolRegistry toolRegistry;
    private static IdentityPrimerService identityPrimerService;
    private static AgenticChatGraph agenticChatGraph;

    private InMemoryChatMemoryPort memoryPort;
    private ChatService chatService;

    // ═══════════════════════════════════════════════════════════════
    //  SETUP
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
            log.info("✅ Ollama available at {}", OLLAMA_URL);

            if (ollamaAvailable) {
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
        } catch (Exception e) {
            log.warn("⚠️  Ollama NOT available: {}", e.getMessage());
        }
    }

    @BeforeEach
    void setupChatService() {
        memoryPort = new InMemoryChatMemoryPort();
        var contextPriming = new ContextPrimingService(memoryPort);
        chatService = new ChatService(
                memoryPort, contextPriming, identityPrimerService,
                toolRegistry, agenticChatGraph, props);
    }

    // ═══════════════════════════════════════════════════════════════
    //  TEST 1: Full Onboarding Conversation (12 turns, LLM-judged)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("Deep conversation: 12-turn onboarding with terse replies + LLM judge")
    void onboardingFlow_deepConversation() throws Exception {
        assumeTrue(ollamaAvailable, "Ollama required");

        // ── Load conversation script from JSON ──
        JsonNode script = loadConversationScript("conversations/onboarding-flow.json");
        String systemPrompt = script.path("systemPrompt").asText();
        JsonNode turns = script.path("turns");

        assertThat(turns.size())
                .as("Conversation should have 10+ turns")
                .isGreaterThanOrEqualTo(10);

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("  DEEP CONVERSATION TEST: {} ({} turns)",
                script.path("scenario").asText(), turns.size());
        log.info("═══════════════════════════════════════════════════════════════");

        // ── Build an agent soul from the script's system prompt ──
        var soul = AgentSoul.builder()
                .name("OnboardingCompanion")
                .purpose("Cognitive AI companion for onboarding conversations")
                .systemPrompt(systemPrompt)
                .model(MODEL)
                .tool("current_time")
                .build();

        String sessionId = "deep-conv-" + UUID.randomUUID();

        // ── State tracking ──
        List<String> conversationLog = new ArrayList<>();
        List<Boolean> verdicts = new ArrayList<>();
        List<String> allToolsCalled = new ArrayList<>();
        int turnsPassed = 0;

        // ── Execute each turn ──
        for (int i = 0; i < turns.size(); i++) {
            JsonNode turn = turns.get(i);
            String userMessage = turn.path("user").asText();
            JsonNode judgeSpec = turn.path("judge");
            String criteria = judgeSpec.path("criteria").asText();

            log.info("\n──── Turn {} ────", i + 1);
            log.info("[USER] {}", userMessage);

            // Track tools called in this turn
            List<String> turnToolsCalled = new CopyOnWriteArrayList<>();
            var listener = new AgentChatListener() {
                @Override public void onThinking(String thought) {}
                @Override public void onContent(String content) {}
                @Override
                public void onToolCall(String toolName, Map<String, Object> args) {
                    turnToolsCalled.add(toolName);
                    log.info("[TOOL] {} → {}", toolName, args);
                }
                @Override
                public void onToolResult(String toolName, String result, boolean success) {
                    log.debug("[TOOL RESULT] {} success={}", toolName, success);
                }
                @Override public void onDone(String finalResponse) {}
                @Override public void onError(String error) { log.error("[ERROR] {}", error); }
            };

            // Execute the chat turn
            var response = chatService.executeChat(
                    userMessage, sessionId, MODEL,
                    soul, 20, null, listener);

            String agentResponse = response.response() != null
                    ? response.response() : "";

            log.info("[AGENT] {}", agentResponse.length() > 300
                    ? agentResponse.substring(0, 300) + "..." : agentResponse);
            if (!turnToolsCalled.isEmpty()) {
                log.info("[TOOLS CALLED] {}", turnToolsCalled);
            }

            // Build conversation log for context
            conversationLog.add("User: " + userMessage);
            conversationLog.add("Agent: " + truncate(agentResponse, 150));
            allToolsCalled.addAll(turnToolsCalled);

            // ── LLM Judge evaluation using LlmBridge ──
            int contextStart = Math.max(0, conversationLog.size() - 6);
            String recentContext = String.join(" → ",
                    conversationLog.subList(contextStart, conversationLog.size()));

            String judgePrompt = String.format("""
                    You are an objective AI response evaluator.
                    Evaluate the agent's response against the criteria below.
                    Reply ONLY with "PASS" or "FAIL" on the first line, then a brief reason.

                    Turn %d of a multi-turn conversation.
                    User said: "%s"
                    Agent responded: "%s"
                    Recent context: %s

                    Criteria: %s
                    """, i + 1, userMessage, truncate(agentResponse, 300),
                    recentContext, criteria);

            String judgeResult = llmBridge.generate(
                    "You are a strict but fair evaluator. Reply with PASS or FAIL on the first line.",
                    judgePrompt);

            boolean passed = judgeResult != null
                    && judgeResult.trim().toUpperCase().startsWith("PASS");
            verdicts.add(passed);

            String status = passed ? "✅ PASS" : "❌ FAIL";
            log.info("[JUDGE] {} — {}", status,
                    truncate(judgeResult, 120));

            if (passed) turnsPassed++;
        }

        // ═══════════════════════════════════════════════════════════════
        //  FINAL REPORT
        // ═══════════════════════════════════════════════════════════════

        log.info("\n╔══════════════════════════════════════════════╗");
        log.info("║   LLM JUDGE EVALUATION REPORT               ║");
        log.info("╠══════════════════════════════════════════════╣");
        log.info("║  Passed: {} / {}                            ║",
                turnsPassed, turns.size());
        log.info("╚══════════════════════════════════════════════╝");

        for (int i = 0; i < verdicts.size(); i++) {
            log.info("[Turn {}] {}", i + 1, verdicts.get(i) ? "✅" : "❌");
        }

        // At least 60% of turns must pass (small models are less consistent)
        assertThat(turnsPassed)
                .as("At least 60%% of turns should pass LLM judge evaluation")
                .isGreaterThanOrEqualTo((int) Math.ceil(turns.size() * 0.6));

        // Memory should have recorded the turns
        assertThat(memoryPort.savedTurns)
                .as("Memory should have recorded conversation turns")
                .hasSizeGreaterThanOrEqualTo(turns.size() / 2);

        log.info("\n✅ DEEP CONVERSATION TEST PASSED: {}/{} turns pass, {} tool calls",
                turnsPassed, turns.size(), allToolsCalled.size());
    }

    // ═══════════════════════════════════════════════════════════════
    //  TEST 2: Personality Continuity — Agent remembers across turns
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("Deep conversation: personality continuity and fact retention")
    void personalityContinuity_factRetention() throws Exception {
        assumeTrue(ollamaAvailable, "Ollama required");

        String sessionId = "fact-retention-" + UUID.randomUUID();

        var soul = AgentSoul.builder()
                .name("WarmCompanion")
                .purpose("Warm, curious AI companion that remembers everything")
                .personality("Warm, curious, empathetic")
                .systemPrompt("You are a warm, curious AI companion. Remember everything "
                        + "the user tells you and use it naturally in later responses. "
                        + "Answer concisely.")
                .model(MODEL)
                .build();

        List<String> conversationLog = new ArrayList<>();
        List<Boolean> verdicts = new ArrayList<>();

        // ── Seed facts across turns ──
        String[][] factTurns = {
                {"My dog's name is Biscuit",
                 "Agent should acknowledge the dog's name naturally and show interest"},
                {"He's a golden retriever, about 3 years old",
                 "Agent must connect this to Biscuit from the prior turn, not treat as new topic"},
                {"yeah he loves the park",
                 "Agent must interpret 'yeah' as confirming something about Biscuit and relate 'the park' to the dog"},
                {"I also have two cats",
                 "Agent should note the cats AND still remember Biscuit the golden retriever"},
                {"Whiskers and Shadow",
                 "Agent must understand these are the cats' names (from the 'two cats' turn). Must not confuse with dog"},
                {"What pets did I tell you about?",
                 "Agent MUST recall: Biscuit (golden retriever, 3yo), Whiskers, Shadow (cats). All 3 pets."},
        };

        for (int i = 0; i < factTurns.length; i++) {
            String userMsg = factTurns[i][0];
            String criteria = factTurns[i][1];

            log.info("\n──── Fact Turn {} ────", i + 1);
            log.info("[USER] {}", userMsg);

            var response = chatService.executeChat(
                    userMsg, sessionId, MODEL, soul,
                    20, null, AgentChatListener.NOOP);

            String agentResponse = response.response() != null ? response.response() : "";
            log.info("[AGENT] {}", agentResponse.length() > 300
                    ? agentResponse.substring(0, 300) + "..." : agentResponse);

            conversationLog.add("User: " + userMsg);
            conversationLog.add("Agent: " + truncate(agentResponse, 150));

            // LLM Judge via LlmBridge
            int ctxStart = Math.max(0, conversationLog.size() - 6);
            String recentCtx = String.join(" → ",
                    conversationLog.subList(ctxStart, conversationLog.size()));

            String judgePrompt = String.format("""
                    You are an objective AI response evaluator.
                    Reply ONLY with "PASS" or "FAIL" on the first line, then a brief reason.

                    Turn %d of a pet conversation. User said: "%s"
                    Agent responded: "%s"
                    Recent context: %s

                    Criteria: %s
                    """, i + 1, userMsg, truncate(agentResponse, 300),
                    recentCtx, criteria);

            String judgeResult = llmBridge.generate(
                    "You are a strict but fair evaluator. Reply with PASS or FAIL on the first line.",
                    judgePrompt);

            boolean passed = judgeResult != null
                    && judgeResult.trim().toUpperCase().startsWith("PASS");
            verdicts.add(passed);

            log.info("[JUDGE] {} — {}", passed ? "✅" : "❌",
                    truncate(judgeResult, 120));
        }

        // The final recall turn is the most critical
        assertThat(verdicts.getLast())
                .as("Final recall turn must pass — agent should remember all 3 pets")
                .isTrue();

        long passed = verdicts.stream().filter(v -> v).count();
        assertThat(passed)
                .as("At least 3/6 turns should pass")
                .isGreaterThanOrEqualTo(3);
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private JsonNode loadConversationScript(String resourcePath) throws IOException {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(resourcePath)) {
            assertThat(is)
                    .as("Conversation script not found: " + resourcePath)
                    .isNotNull();
            return MAPPER.readTree(is);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  IN-MEMORY CHAT MEMORY PORT
    // ═══════════════════════════════════════════════════════════════

    static class InMemoryChatMemoryPort implements ChatMemoryPort {

        final Map<String, List<Map<String, Object>>> sessions = new LinkedHashMap<>();
        final List<SavedTurn> savedTurns = new CopyOnWriteArrayList<>();

        record SavedTurn(String sessionId, String userMessage,
                         String assistantResponse, String model) {}

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
            Set<String> keywords = Arrays.stream(query.toLowerCase()
                            .replaceAll("[^a-z0-9 ]", "").split("\\s+"))
                    .filter(w -> w.length() > 2)
                    .collect(java.util.stream.Collectors.toSet());
            return sessions.values().stream()
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
