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
package com.spectrayan.spector.synapse.agent.chat.service;

import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.commons.concurrent.SpectorExecutors;
import com.spectrayan.spector.commons.concurrent.ThreadPlane;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.AgentChatRequest;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.AgentChatResponse;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatConfig;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ModelsResponse;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.OllamaModel;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.SessionSummary;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.TokenUsageDto;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ToolsResponse;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.TraceEvent;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatStreamEvent;
import com.spectrayan.spector.synapse.agent.cognitive.ConversationReflector;
import com.spectrayan.spector.synapse.agent.cognitive.ConversationSummarizer;
import com.spectrayan.spector.synapse.agent.graph.AgentChatListener;
import com.spectrayan.spector.synapse.agent.graph.AgenticChatGraph;
import com.spectrayan.spector.synapse.agent.service.IdentityPrimerService;
import com.spectrayan.spector.synapse.config.SynapseProperties;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import com.spectrayan.spector.kernel.id.TsidGenerator;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-level service for executing cognitive chat turns via the agentic graph.
 *
 * <p>Orchestrates context priming, message assembly, agentic graph execution,
 * and session persistence. This is the central entry point for all chat
 * interactions in Spector Synapse.</p>
 *
 * <h3>Flow</h3>
 * <pre>
 *   1. Prime context (cross-session memory injection)
 *   2. Assemble message list (history + new message)
 *   3. Build enriched system prompt (agent soul + primed memories)
 *   4. Execute agentic graph (LLM + tool calls)
 *   5. Persist turn to session memory
 *   6. Return typed response with trace and metadata
 * </pre>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_CONTEXT_DEPTH = 10;
    private static final String DEFAULT_MODEL = "qwen3.5:latest";

    private final ChatMemoryPort chatMemoryPort;
    private final ContextPrimingService contextPrimingService;
    private final IdentityPrimerService identityPrimerService;
    private final ToolRegistry toolRegistry;
    private final AgenticChatGraph agenticChatGraph;
    private final TsidGenerator tsid;
    private final String ollamaBaseUrl;
    private final ChatTranscriptPort transcriptPort;

    /** Lazily initialized cognitive engines. */
    private final ConversationSummarizer summarizer;
    private final ConversationReflector reflector;
    private final com.spectrayan.spector.synapse.provider.usage.TokenUsageTracker tokenUsageTracker;
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "spector-sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public ChatService(ChatMemoryPort chatMemoryPort,
                       ContextPrimingService contextPrimingService,
                       IdentityPrimerService identityPrimerService,
                       ToolRegistry toolRegistry,
                       AgenticChatGraph agenticChatGraph,
                       TsidGenerator tsid,
                       SynapseProperties props) {
        this(chatMemoryPort, contextPrimingService, identityPrimerService, toolRegistry,
                agenticChatGraph, tsid, props, null, null);
    }

    public ChatService(ChatMemoryPort chatMemoryPort,
                       ContextPrimingService contextPrimingService,
                       IdentityPrimerService identityPrimerService,
                       ToolRegistry toolRegistry,
                       AgenticChatGraph agenticChatGraph,
                       TsidGenerator tsid,
                       SynapseProperties props,
                       com.spectrayan.spector.synapse.provider.usage.TokenUsageTracker tokenUsageTracker) {
        this(chatMemoryPort, contextPrimingService, identityPrimerService, toolRegistry,
                agenticChatGraph, tsid, props, tokenUsageTracker, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ChatService(ChatMemoryPort chatMemoryPort,
                       ContextPrimingService contextPrimingService,
                       IdentityPrimerService identityPrimerService,
                       ToolRegistry toolRegistry,
                       AgenticChatGraph agenticChatGraph,
                       TsidGenerator tsid,
                       SynapseProperties props,
                       @org.springframework.beans.factory.annotation.Autowired(required = false)
                       com.spectrayan.spector.synapse.provider.usage.TokenUsageTracker tokenUsageTracker,
                       @org.springframework.beans.factory.annotation.Autowired(required = false)
                       ChatTranscriptPort transcriptPort) {
        this.chatMemoryPort = Objects.requireNonNull(chatMemoryPort);
        this.contextPrimingService = Objects.requireNonNull(contextPrimingService);
        this.identityPrimerService = Objects.requireNonNull(identityPrimerService);
        this.toolRegistry = Objects.requireNonNull(toolRegistry);
        this.agenticChatGraph = Objects.requireNonNull(agenticChatGraph);
        this.tsid = Objects.requireNonNull(tsid);
        this.tokenUsageTracker = tokenUsageTracker;
        this.transcriptPort = transcriptPort;
        var genProps = props.getProvider().getGeneration();
        this.ollamaBaseUrl = genProps.baseUrl();

        // Initialize cognitive engines
        this.summarizer = new ConversationSummarizer(
                genProps.baseUrl(), genProps.model());
        this.reflector = new ConversationReflector(
                genProps.baseUrl(), genProps.model(), toolRegistry);
        log.info("[ChatService] Cognitive engines initialized: Summarizer + Reflector (transcriptPort={})",
                transcriptPort != null ? "active" : "none");
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        heartbeatScheduler.shutdownNow();
    }

    // ═══════════════════════════════════════════════════════════════
    // Core Chat
    // ═══════════════════════════════════════════════════════════════

    /**
     * Executes a full agentic chat turn and returns a typed response.
     *
     * @param message        the user's message
     * @param sessionId      the session identifier (null for new sessions)
     * @param model          the LLM model to use (null for default)
     * @param soul           the agent soul for identity priming
     * @param contextDepth   cross-session memory recall depth
     * @param messages       optional pre-assembled message list
     * @param listener       callback for real-time streaming events
     * @return typed agent chat response with trace, metadata, and session info
     */
        public AgentChatResponse executeChat(
            String message,
            String sessionId,
            String model,
            AgentSoul soul,
            int contextDepth,
            List<Map<String, Object>> messages,
            AgentChatListener listener) {

        String resolvedSessionId = sessionId != null && !sessionId.isBlank()
                ? sessionId
                : tsid.generate();

        try {
            return java.lang.ScopedValue.where(com.spectrayan.spector.commons.concurrent.MemoryScope.SESSION_ID, resolvedSessionId)
                    .call(() -> processChat(message, resolvedSessionId, model, soul, contextDepth, messages, listener));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AgentChatResponse processChat(
            String message,
            String sessionId,
            String model,
            AgentSoul soul,
            int contextDepth,
            List<Map<String, Object>> messages,
            AgentChatListener listener) {

        long startNanos = System.nanoTime();
        int depth = contextDepth > 0 ? contextDepth : DEFAULT_CONTEXT_DEPTH;
        boolean isNewSession = (sessionId == null || sessionId.isBlank());

        // ── Step 0: Load and compact session history if needed ──
        List<Map<String, Object>> history = new ArrayList<>();
        if (messages != null && !messages.isEmpty()) {
            history = new ArrayList<>(messages);
        } else if (sessionId != null && !sessionId.isBlank()) {
            history = new ArrayList<>(chatMemoryPort.loadSessionHistory(sessionId));
        }

        if (summarizer.needsCompaction(history)) {
            log.info("[ChatService] Compacting session {} history ({} messages)",
                    sessionId, history.size());
            history = summarizer.compact(history);
        }

        // Convert Map to ChatMessage
        List<ChatMessage> historyMessages = new ArrayList<>();
        for (var msg : history) {
            var parsed = parseHistoryMessage(msg);
            if (parsed != null) {
                historyMessages.add(parsed);
            }
        }

        // ── Step 1: Prime context ──
        var primedContext = contextPrimingService.prime(message, sessionId, depth);

        // ── Step 2: Build enriched system prompt ──
        String basePrompt = identityPrimerService.buildSystemPrompt(soul);
        String enrichedPrompt = basePrompt;
        if (!primedContext.contextBlock().isEmpty()) {
            enrichedPrompt = basePrompt + "\n" + primedContext.contextBlock();
        }

        // Resolve session ID
        String resolvedSessionId = sessionId != null && !sessionId.isBlank()
                ? sessionId
                : tsid.generate();

        // ── Step 3: Build agentic soul with enriched prompt ──
        var soulBuilder = AgentSoul.builder()
                .id(soul != null ? soul.id() : "default")
                .name(soul != null ? soul.name() : "Assistant")
                .description(soul != null ? soul.description() : null)
                .systemPrompt(enrichedPrompt)
                .purpose(soul != null ? soul.purpose() : null)
                .personality(soul != null ? soul.personality() : null)
                .model(model != null ? model : DEFAULT_MODEL)
                .tools(soul != null ? soul.tools() : List.of())
                .expertiseEmbedding(soul != null ? soul.expertiseEmbedding() : null)
                .purposeEmbedding(soul != null ? soul.purposeEmbedding() : null)
                .soulVersion(soul != null ? soul.soulVersion() : (short) 1)
                .createdAt(soul != null ? soul.createdAt() : java.time.Instant.now())
                .updatedAt(soul != null ? soul.updatedAt() : java.time.Instant.now());

        if (soul != null) {
            soulBuilder
                    .expertiseDomains(soul.expertiseDomains())
                    .coreValues(soul.coreValues())
                    .ethicalGuardrails(soul.ethicalGuardrails())
                    .emotionalBaseline(soul.emotionalBaseline())
                    .communicationStyle(soul.communicationStyle());
        }

        AgentSoul enrichedSoul = soulBuilder.build();

        // ── Step 4: Collect trace events ──
        List<TraceEvent> traceEvents = new CopyOnWriteArrayList<>();
        AgentChatListener wrappedListener = new AgentChatListener() {
            @Override public void onThinking(String thought) {
                traceEvents.add(new TraceEvent("thinking", thought));
                listener.onThinking(thought);
            }
            @Override public void onToolCall(String name, Map<String, Object> arguments) {
                traceEvents.add(new TraceEvent("tool_call", name));
                listener.onToolCall(name, arguments);
            }
            @Override public void onToolResult(String name, String result, boolean success) {
                traceEvents.add(new TraceEvent("tool_result", name + " -> " + result));
                listener.onToolResult(name, result, success);
            }
            @Override public void onContent(String text) {
                traceEvents.add(new TraceEvent("content", text));
                listener.onContent(text);
            }
            @Override public void onDone(String summary) {
                traceEvents.add(new TraceEvent("done", summary));
                listener.onDone(summary);
            }
            @Override public void onError(String error) {
                traceEvents.add(new TraceEvent("error", error));
                listener.onError(error);
            }
        };

        // ── Step 5: Execute agentic graph ──
        String finalResponse = agenticChatGraph.chat(enrichedSoul, historyMessages, message, wrappedListener);

        // ── Step 6: Persist the turn (skip on error) ──
        boolean hasErrorTrace = traceEvents.stream()
                .anyMatch(e -> "error".equals(e.type()));
        if (!hasErrorTrace && message != null && !message.isBlank() && !finalResponse.isEmpty()) {
            chatMemoryPort.saveToSession(resolvedSessionId, message, finalResponse,
                    model != null ? model : DEFAULT_MODEL);
        }

        // ── Step 7: Post-conversation reflection (async, skip on error) ──
        if (!hasErrorTrace && message != null && !message.isBlank() && !finalResponse.isEmpty()) {
            var conversationMessages = new ArrayList<Map<String, Object>>();
            conversationMessages.add(Map.of("role", "user", "content", message));
            conversationMessages.add(Map.of("role", "assistant", "content", finalResponse));
            reflector.reflectAsync(conversationMessages);
        }

        // ── Step 8: Calculate token usage & record telemetry ──
        long inTokens = Math.max(1, (message != null ? message.length() / 4 : 0) + (enrichedPrompt != null ? enrichedPrompt.length() / 4 : 0));
        long outTokens = Math.max(1, finalResponse.length() / 4);
        var tokenUsageDto = com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.TokenUsageDto.of(inTokens, outTokens);

        if (tokenUsageTracker != null && !hasErrorTrace) {
            tokenUsageTracker.record(com.spectrayan.spector.synapse.provider.usage.TokenUsageEvent.ofGeneration(
                    com.spectrayan.spector.synapse.provider.usage.TokenUsageCategory.CHAT,
                    "default",
                    model != null ? model : DEFAULT_MODEL,
                    soul != null ? soul.id() : null,
                    resolvedSessionId,
                    inTokens,
                    outTokens
            ));
        }

        // ── Step 9: Build typed response ──
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        String status = hasErrorTrace ? "ERROR" : "DONE";

        return new AgentChatResponse(
                finalResponse,
                resolvedSessionId,
                isNewSession,
                model != null ? model : DEFAULT_MODEL,
                status,
                elapsedMs,
                elapsedMs,
                primedContext.crossSessionMemories().size(),
                traceEvents,
                null,
                List.of(),
                tokenUsageDto
        );
    }

    /**
     * Simplified chat entry point.
     */
    public AgentChatResponse chat(String message, String sessionId,
                                   String model, AgentSoul soul) {
        return executeChat(message, sessionId, model, soul,
                DEFAULT_CONTEXT_DEPTH, null, AgentChatListener.NOOP);
    }

    // ═══════════════════════════════════════════════════════════════
    // Tools
    // ═══════════════════════════════════════════════════════════════

    /**
     * Lists available agent tools as function definitions.
     */
    public ToolsResponse listTools() {
        return new ToolsResponse(toolRegistry.toFunctionDefinitions());
    }

    // ═══════════════════════════════════════════════════════════════
    // Sessions
    // ═══════════════════════════════════════════════════════════════

    /**
     * Lists recent chat sessions.
     */
    public List<SessionSummary> listSessions(int limit) {
        return chatMemoryPort.listSessions(limit).stream()
                .map(c -> new SessionSummary(
                        c.sessionId(), c.preview(),
                        c.lastActivityAt() != null ? c.lastActivityAt().toString() : "",
                        c.messageCount()))
                .toList();
    }

    /**
     * Loads all messages for a specific session.
     */
    public List<Map<String, Object>> loadSessionMessages(String sessionId) {
        return chatMemoryPort.loadSessionHistory(sessionId);
    }

    // ═══════════════════════════════════════════════════════════════
    // Models
    // ═══════════════════════════════════════════════════════════════

    /**
     * Lists available LLM models from the Ollama API.
     */
    @SuppressWarnings("unchecked")
    public ModelsResponse listModels() {
        try {
            var client = HttpClient.newHttpClient();
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var body = mapper.readValue(resp.body(), Map.class);
                var models = (List<Map<String, Object>>) body.get("models");
                if (models != null) {
                    List<OllamaModel> result = models.stream()
                            .filter(m -> {
                                String name = String.valueOf(m.getOrDefault("name", ""));
                                return !name.contains("embed") && !name.contains("nomic");
                            })
                            .map(m -> {
                                String name = String.valueOf(m.getOrDefault("name", ""));
                                long size = m.get("size") instanceof Number n ? n.longValue() : 0;
                                String modifiedAt = String.valueOf(m.getOrDefault("modified_at", ""));
                                return new OllamaModel(name, name, size, modifiedAt,
                                        name.equals(DEFAULT_MODEL));
                            })
                            .toList();
                    return new ModelsResponse(result);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list Ollama models: {}", e.getMessage());
        }
        return new ModelsResponse(List.of(
                new OllamaModel(DEFAULT_MODEL, DEFAULT_MODEL, 0, "", true)));
    }

    /**
     * Returns chat configuration.
     */
    public ChatConfig chatConfig() {
        return new ChatConfig(DEFAULT_MODEL, 50, 10, true, "2.0-cognitive");
    }

    private dev.langchain4j.data.message.ChatMessage parseHistoryMessage(Map<String, Object> msg) {
        String role = String.valueOf(msg.getOrDefault("role", "user"));
        Object contentObj = msg.get("content");
        
        if ("system".equalsIgnoreCase(role)) {
            return SystemMessage.from(String.valueOf(contentObj != null ? contentObj : ""));
        } else if ("assistant".equalsIgnoreCase(role) || "agent".equalsIgnoreCase(role)) {
            AiMessage.Builder builder = AiMessage.builder();
            if (contentObj != null) {
                builder.text(String.valueOf(contentObj));
            }
            if (msg.containsKey("thinking")) {
                builder.thinking(String.valueOf(msg.get("thinking")));
            }
            if (msg.containsKey("attributes") && msg.get("attributes") instanceof Map<?, ?> attrs) {
                builder.attributes((Map<String, Object>) attrs);
            }
            if (msg.containsKey("toolExecutionRequests") && msg.get("toolExecutionRequests") instanceof List<?> toolRequests) {
                List<ToolExecutionRequest> requests = new java.util.ArrayList<>();
                for (Object reqObj : toolRequests) {
                    if (reqObj instanceof Map<?, ?> reqMap) {
                        requests.add(ToolExecutionRequest.builder()
                                .id(String.valueOf(reqMap.get("id")))
                                .name(String.valueOf(reqMap.get("name")))
                                .arguments(String.valueOf(reqMap.get("arguments")))
                                .build());
                    }
                }
                builder.toolExecutionRequests(requests);
            }
            return builder.build();
        } else {
            // USER or fallback
            if (contentObj instanceof List<?> list) {
                List<Content> contents = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> blockMap) {
                        String type = String.valueOf(blockMap.get("type"));
                        if ("TEXT".equalsIgnoreCase(type)) {
                            contents.add(TextContent.from(String.valueOf(blockMap.get("text"))));
                        } else if ("IMAGE".equalsIgnoreCase(type)) {
                            String url = String.valueOf(blockMap.get("url"));
                            if (blockMap.containsKey("data")) {
                                Object dataVal = blockMap.get("data");
                                byte[] data;
                                if (dataVal instanceof byte[]) {
                                    data = (byte[]) dataVal;
                                } else {
                                    String base64Str = String.valueOf(dataVal);
                                    data = java.util.Base64.getDecoder().decode(base64Str);
                                }
                                Object mimeVal = blockMap.get("mimeType");
                                String mimeType = mimeVal != null ? String.valueOf(mimeVal) : "image/png";
                                String base64 = java.util.Base64.getEncoder().encodeToString(data);
                                contents.add(ImageContent.from(base64, mimeType));
                            } else if (url != null && !url.isBlank()) {
                                contents.add(ImageContent.from(url));
                            }
                        }
                    }
                }
                return UserMessage.from(contents);
            } else {
                return UserMessage.from(String.valueOf(contentObj != null ? contentObj : ""));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Streaming Chat (SSE)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Executes an agentic chat turn with real-time SSE streaming.
     */
    public void streamChat(AgentChatRequest request, SseEmitter emitter) {
        streamChat(request, null, emitter);
    }

    /**
     * Executes an agentic chat turn with real-time SSE streaming and effective agent soul.
     */
    public void streamChat(AgentChatRequest request, AgentSoul soul, SseEmitter emitter) {
        String rawSessionId = request.resolvedSessionId();
        boolean isNewSession = (rawSessionId == null || rawSessionId.isBlank());
        String sessionId = !isNewSession ? rawSessionId : tsid.generate();
        String turnId = tsid.generate();
        String model = request.model() != null ? request.model() : (soul != null ? soul.model() : DEFAULT_MODEL);
        String message = request.message();

        // 1. Initialize operational store turn if transcriptPort present
        if (transcriptPort != null) {
            try {
                if (transcriptPort.getSession(sessionId).isEmpty()) {
                    String title = message != null && !message.isBlank()
                            ? (message.length() > 60 ? message.substring(0, 60) + "..." : message)
                            : "New Chat";
                    transcriptPort.createSession(sessionId, title);
                }
                int turnSeq = transcriptPort.countTurns(sessionId);
                transcriptPort.startTurn(turnId, sessionId, turnSeq, model, 0);
            } catch (Exception e) {
                log.warn("[ChatService] Failed to record startTurn in transcriptPort: {}", e.getMessage());
            }
        }

        // 2. Build thread-safe sink
        ChatStreamSink sink = new ChatStreamSink(emitter, sessionId, turnId, transcriptPort, heartbeatScheduler);

        // 3. IMMEDIATELY pre-flush session event (TTFT < 15ms start!)
        ChatStreamEvent.Session sessionEvent = new ChatStreamEvent.Session(
                sessionId, turnId, sink.nextSeq(), System.currentTimeMillis(), isNewSession, model);
        sink.send(sessionEvent);

        if (transcriptPort != null) {
            appendOperationalEvent(turnId, 0, "session", sessionEvent);
        }

        // 4. Dispatch long-running agentic execution to Virtual Thread
        Executor vtExecutor = SpectorExecutors.executor(ThreadPlane.VIRTUAL, "chat-stream");
        vtExecutor.execute(() -> {
            try {
                ScopedValue.where(MemoryScope.SESSION_ID, sessionId).run(() -> {
                    processStreamExecution(request, soul, sessionId, turnId, model, sink);
                });
            } catch (Exception e) {
                log.error("[ChatService] Stream execution error: {}", e.getMessage(), e);
                sink.send(new ChatStreamEvent.Error(sessionId, turnId, sink.nextSeq(),
                        System.currentTimeMillis(), "SPE-700-001", e.getMessage(), true));
                sink.triggerAbort();
            }
        });
    }

    private void processStreamExecution(
            AgentChatRequest request,
            AgentSoul soul,
            String sessionId,
            String turnId,
            String model,
            ChatStreamSink sink) {

        if (sink.isAborted()) {
            log.info("[ChatService] Early disconnect detected before stream execution for turn '{}'", turnId);
            return;
        }

        long startNanos = System.nanoTime();
        String message = request.message();
        int depth = request.contextDepth() != null && request.contextDepth() > 0
                ? request.contextDepth()
                : DEFAULT_CONTEXT_DEPTH;

        // Step 0: History compaction / loading
        List<Map<String, Object>> history = new ArrayList<>();
        if (request.messages() != null && !request.messages().isEmpty()) {
            history = new ArrayList<>(request.messages());
        } else if (sessionId != null && !sessionId.isBlank()) {
            history = new ArrayList<>(chatMemoryPort.loadSessionHistory(sessionId));
        }

        if (summarizer.needsCompaction(history)) {
            history = summarizer.compact(history);
        }

        List<ChatMessage> historyMessages = new ArrayList<>();
        for (var msg : history) {
            var parsed = parseHistoryMessage(msg);
            if (parsed != null) {
                historyMessages.add(parsed);
            }
        }

        // Step 1: Prime context
        if (sink.isAborted()) {
            log.info("[ChatService] Early disconnect detected before context priming for turn '{}'", turnId);
            return;
        }
        var primedContext = contextPrimingService.prime(message, sessionId, depth);

        // Step 2: Enriched system prompt
        String basePrompt = identityPrimerService.buildSystemPrompt(soul);
        String enrichedPrompt = basePrompt;
        if (!primedContext.contextBlock().isEmpty()) {
            enrichedPrompt = basePrompt + "\n" + primedContext.contextBlock();
        }

        // Step 3: Build enriched soul
        var soulBuilder = AgentSoul.builder()
                .id(soul != null ? soul.id() : "default")
                .name(soul != null ? soul.name() : "Assistant")
                .description(soul != null ? soul.description() : null)
                .systemPrompt(enrichedPrompt)
                .purpose(soul != null ? soul.purpose() : null)
                .personality(soul != null ? soul.personality() : null)
                .model(model)
                .tools(soul != null ? soul.tools() : List.of())
                .soulVersion(soul != null ? soul.soulVersion() : (short) 1);

        AgentSoul enrichedSoul = soulBuilder.build();

        // Step 4: Wire AgentChatListener to sink & operational store
        AtomicInteger inTokens = new AtomicInteger(0);
        AtomicInteger outTokens = new AtomicInteger(0);
        StringBuilder fullAssistantText = new StringBuilder();

        AgentChatListener listener = new AgentChatListener() {
            @Override
            public void onSession(String sId, String tId) {}

            @Override
            public void onThinking(String delta, long elapsedMs) {
                if (sink.isAborted()) return;
                int seq = sink.nextSeq();
                ChatStreamEvent.Thinking ev = new ChatStreamEvent.Thinking(
                        sessionId, turnId, seq, System.currentTimeMillis(), delta, elapsedMs);
                sink.send(ev);
                appendOperationalEvent(turnId, seq, "thinking", ev);
            }

            @Override
            public void onToken(String delta) {
                if (sink.isAborted()) return;
                fullAssistantText.append(delta);
                outTokens.incrementAndGet();
                int seq = sink.nextSeq();
                ChatStreamEvent.Token ev = new ChatStreamEvent.Token(
                        sessionId, turnId, seq, System.currentTimeMillis(), delta);
                sink.send(ev);
                appendOperationalEvent(turnId, seq, "token", ev);
            }

            @Override
            public void onToolCall(String callId, String name, Map<String, Object> arguments) {
                if (sink.isAborted()) return;
                int seq = sink.nextSeq();
                ChatStreamEvent.ToolCall ev = new ChatStreamEvent.ToolCall(
                        sessionId, turnId, seq, System.currentTimeMillis(), callId, name, arguments);
                sink.send(ev);
                appendOperationalEvent(turnId, seq, "tool_call", ev);
            }

            @Override
            public void onToolResult(String callId, String name, String status, String preview, String fullPayload, long elapsedMs) {
                if (sink.isAborted()) return;
                int seq = sink.nextSeq();
                boolean truncated = fullPayload != null && fullPayload.length() > 2048;
                ChatStreamEvent.ToolResult ev = new ChatStreamEvent.ToolResult(
                        sessionId, turnId, seq, System.currentTimeMillis(), callId, name, status, preview, truncated, elapsedMs);
                sink.send(ev);
                Map<String, Object> auditPayload = Map.of(
                        "callId", callId, "name", name, "status", status,
                        "preview", preview, "fullPayload", fullPayload != null ? fullPayload : "",
                        "elapsedMs", elapsedMs, "truncated", truncated);
                appendOperationalEvent(turnId, seq, "tool_result", auditPayload);
            }

            @Override
            public void onDone(String summary, TokenUsageDto usage) {}

            @Override
            public void onError(String code, String msg, boolean retryable) {
                if (sink.isAborted()) return;
                int seq = sink.nextSeq();
                ChatStreamEvent.Error ev = new ChatStreamEvent.Error(
                        sessionId, turnId, seq, System.currentTimeMillis(), code, msg, retryable);
                sink.send(ev);
                appendOperationalEvent(turnId, seq, "error", ev);
            }
        };

        // Step 5: Start chatStream
        AsyncGenerator.Cancellable<NodeOutput<AgentState>> generator =
                agenticChatGraph.chatStream(enrichedSoul, historyMessages, message, listener, sessionId, turnId);

        sink.setAbortAction(() -> generator.cancel(true));

        try {
            for (NodeOutput<AgentState> step : generator) {
                if (sink.isAborted()) {
                    break;
                }
            }
        } catch (Exception e) {
            if (!sink.isAborted()) {
                log.error("[ChatService] Graph execution error: {}", e.getMessage(), e);
                listener.onError("SPE-700-001", e.getMessage(), true);
                sink.triggerAbort();
                return;
            }
        }

        if (sink.isAborted()) {
            return;
        }

        // Step 6: Finalize turn
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
        inTokens.set((int) Math.max(1L, message != null ? message.length() / 4L : 0L));
        TokenUsageDto usage = new TokenUsageDto(inTokens.get(), outTokens.get(), inTokens.get() + outTokens.get());

        ChatStreamEvent.Done doneEvent = new ChatStreamEvent.Done(
                sessionId, turnId, sink.nextSeq(), System.currentTimeMillis(),
                "Completed", latencyMs, primedContext.crossSessionMemories().size(), usage);
        sink.send(doneEvent);
        appendOperationalEvent(turnId, doneEvent.seq(), "done", doneEvent);

        if (transcriptPort != null) {
            try {
                transcriptPort.updateTurnStatus(turnId, "DONE", inTokens.get(), outTokens.get(), latencyMs);
            } catch (Exception ex) {
                log.warn("[ChatService] Failed to update turn status to DONE: {}", ex.getMessage());
            }
        }

        // Persist to session memory & reflect
        String finalResp = fullAssistantText.toString();
        if (message != null && !message.isBlank() && !finalResp.isEmpty()) {
            chatMemoryPort.saveToSession(sessionId, message, finalResp, model);

            var conversationMessages = new ArrayList<Map<String, Object>>();
            conversationMessages.add(Map.of("role", "user", "content", message));
            conversationMessages.add(Map.of("role", "assistant", "content", finalResp));
            reflector.reflectAsync(conversationMessages);
        }

        sink.close();
    }

    private void appendOperationalEvent(String turnId, int seq, String type, Object payload) {
        if (transcriptPort == null) return;
        try {
            String json = payload instanceof String s ? s : MAPPER.writeValueAsString(payload);
            transcriptPort.appendEvent(tsid.generate(), turnId, seq, type, json);
        } catch (Exception e) {
            log.debug("[ChatService] Failed to persist operational event: {}", e.getMessage());
        }
    }

    /**
     * Thread-safe SSE sink managing monotonic sequence numbers, 15s keepalive heartbeats,
     * and client disconnect aborts.
     */
    public static class ChatStreamSink implements AutoCloseable {
        private final SseEmitter emitter;
        private final String sessionId;
        private final String turnId;
        private final ChatTranscriptPort transcriptPort;
        private final AtomicInteger seq = new AtomicInteger(0);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicBoolean aborted = new AtomicBoolean(false);
        private final ScheduledFuture<?> heartbeatTask;
        private volatile Runnable abortAction;

        public ChatStreamSink(
                SseEmitter emitter,
                String sessionId,
                String turnId,
                ChatTranscriptPort transcriptPort,
                ScheduledExecutorService scheduler) {
            this.emitter = emitter;
            this.sessionId = sessionId;
            this.turnId = turnId;
            this.transcriptPort = transcriptPort;

            emitter.onCompletion(this::close);
            emitter.onTimeout(this::triggerAbort);
            emitter.onError(t -> triggerAbort());

            // Schedule 15s keepalive comment :keepalive\n\n
            this.heartbeatTask = scheduler.scheduleAtFixedRate(
                    this::sendKeepalive, 15, 15, TimeUnit.SECONDS);
        }

        public void setAbortAction(Runnable abortAction) {
            this.abortAction = abortAction;
        }

        public synchronized void send(ChatStreamEvent event) {
            if (closed.get() || aborted.get()) return;
            try {
                emitter.send(SseEmitter.event()
                        .name(event.eventType())
                        .id(turnId + ":" + event.seq())
                        .data(event, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                log.warn("[ChatStreamSink] Client disconnected on write for turn '{}': {}", turnId, e.getMessage());
                triggerAbort();
            } catch (Exception e) {
                log.error("[ChatStreamSink] Failed to send event to emitter: {}", e.getMessage(), e);
            }
        }

        public synchronized void sendKeepalive() {
            if (closed.get() || aborted.get()) return;
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException e) {
                log.info("[ChatStreamSink] Client disconnected during keepalive for turn '{}'", turnId);
                triggerAbort();
            } catch (Exception e) {
                log.debug("[ChatStreamSink] Keepalive send failed: {}", e.getMessage());
            }
        }

        public synchronized void triggerAbort() {
            if (!aborted.compareAndSet(false, true)) return;

            log.info("[ChatStreamSink] Aborting turn '{}' (client disconnect)", turnId);
            if (heartbeatTask != null) {
                heartbeatTask.cancel(true);
            }

            if (abortAction != null) {
                try {
                    abortAction.run();
                } catch (Exception e) {
                    log.warn("[ChatStreamSink] Error running abort action: {}", e.getMessage());
                }
            }

            if (transcriptPort != null) {
                try {
                    transcriptPort.updateTurnStatus(turnId, "INTERRUPTED", 0, 0, 0);
                } catch (Exception e) {
                    log.error("[ChatStreamSink] Failed to update turn status to INTERRUPTED: {}", e.getMessage());
                }
            }

            close();
        }

        public int nextSeq() {
            return seq.getAndIncrement();
        }

        public boolean isAborted() {
            return aborted.get();
        }

        @Override
        public synchronized void close() {
            if (closed.compareAndSet(false, true)) {
                if (heartbeatTask != null) {
                    heartbeatTask.cancel(false);
                }
                try {
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        }
    }
}
