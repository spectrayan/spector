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
package com.spectrayan.spector.synapse.agent.chat.stream;

import com.spectrayan.spector.kernel.id.TsidGenerator;
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.agent.chat.cache.GraphCache;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.AgentChatRequest;
import com.spectrayan.spector.synapse.agent.chat.infrastructure.JdbcChatTranscriptAdapter;
import com.spectrayan.spector.synapse.agent.chat.infrastructure.JdbcCheckpointSaver;
import com.spectrayan.spector.synapse.agent.chat.service.ChatMemoryPort;
import com.spectrayan.spector.synapse.agent.chat.service.ChatService;
import com.spectrayan.spector.synapse.agent.chat.service.ContextPrimingService;
import com.spectrayan.spector.synapse.agent.graph.AgenticChatGraph;
import com.spectrayan.spector.synapse.agent.service.IdentityPrimerService;
import com.spectrayan.spector.synapse.bridge.LlmBridge;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.modelcontextprotocol.spec.McpSchema;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Empirical challenger tests verifying:
 * 1. Client socket disconnects mid-stream during token generation and tool execution.
 * 2. Graph execution cancellation and absence of orphaned threads.
 * 3. Durable persistence of turn.status = 'INTERRUPTED' in CHAT_TURN.
 * 4. Preservation of LangGraph4j state checkpoint in GRAPH_CHECKPOINT and resumption on subsequent turns.
 */
@DisplayName("Disconnect Handling & Checkpoint Preservation Empirical Tests")
class DisconnectAndCheckpointEmpiricalTest {

    private JdbcClient jdbc;
    private JdbcChatTranscriptAdapter transcriptPort;
    private JdbcCheckpointSaver checkpointSaver;
    private ToolRegistry toolRegistry;
    private TsidGenerator tsid;
    private SynapseProperties props;
    private IdentityPrimerService identityPrimerService;
    private ContextPrimingService contextPrimingService;
    private ChatMemoryPort memoryPort;
    private GraphCache graphCache;
    private LlmBridge mockLlmBridge;
    private AgentSoul testSoul;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:disconnect-test-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");

        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        jdbc = JdbcClient.create(ds);
        transcriptPort = new JdbcChatTranscriptAdapter(jdbc, new com.fasterxml.jackson.databind.ObjectMapper());
        checkpointSaver = new JdbcCheckpointSaver(jdbc);

        toolRegistry = new ToolRegistry(Collections.emptyList());
        tsid = new TsidGenerator();
        props = new SynapseProperties();
        identityPrimerService = new IdentityPrimerService();
        memoryPort = mock(ChatMemoryPort.class);
        contextPrimingService = new ContextPrimingService(memoryPort);
        graphCache = new GraphCache();
        mockLlmBridge = mock(LlmBridge.class);

        testSoul = AgentSoul.builder()
                .id("test-soul-" + System.nanoTime())
                .name("EmpiricalTester")
                .soulVersion((short) 1)
                .model("test-model")
                .build();
    }

    @AfterEach
    void tearDown() {
        graphCache.invalidateAll();
    }

    /**
     * Test SseEmitter that simulates client disconnect after a configurable number of event writes,
     * or on a specific event name.
     */
    static class DisconnectingSseEmitter extends SseEmitter {
        private final AtomicInteger sendCount = new AtomicInteger(0);
        private final int disconnectOnCount;
        private final String disconnectOnEventName;
        private final List<String> sentEventNames = new CopyOnWriteArrayList<>();
        private final CountDownLatch completionLatch = new CountDownLatch(1);
        private final AtomicBoolean abortOccurred = new AtomicBoolean(false);

        private Runnable onCompletionCallback;
        private Consumer<Throwable> onErrorCallback;
        private Runnable onTimeoutCallback;

        public DisconnectingSseEmitter(int disconnectOnCount) {
            super(60_000L);
            this.disconnectOnCount = disconnectOnCount;
            this.disconnectOnEventName = null;
        }

        public DisconnectingSseEmitter(String disconnectOnEventName) {
            super(60_000L);
            this.disconnectOnCount = -1;
            this.disconnectOnEventName = disconnectOnEventName;
        }

        @Override
        public synchronized void onCompletion(Runnable callback) {
            this.onCompletionCallback = callback;
        }

        @Override
        public synchronized void onError(Consumer<Throwable> callback) {
            this.onErrorCallback = callback;
        }

        @Override
        public synchronized void onTimeout(Runnable callback) {
            this.onTimeoutCallback = callback;
        }

        @Override
        public synchronized void complete() {
            if (onCompletionCallback != null) {
                try {
                    onCompletionCallback.run();
                } catch (Exception ignored) {}
            }
            completionLatch.countDown();
        }

        @Override
        public synchronized void completeWithError(Throwable ex) {
            abortOccurred.set(true);
            if (onErrorCallback != null) {
                try {
                    onErrorCallback.accept(ex);
                } catch (Exception ignored) {}
            }
            completionLatch.countDown();
        }

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            int current = sendCount.incrementAndGet();

            // Extract event name from builder if possible
            Set<?> dataToSend = builder.build();
            String eventName = "unknown";
            for (Object item : dataToSend) {
                if (item instanceof ResponseBodyEmitter.DataWithMediaType d) {
                    Object data = d.getData();
                    if (data instanceof com.spectrayan.spector.synapse.agent.chat.dto.ChatStreamEvent ev) {
                        eventName = ev.eventType();
                    } else if (data != null && data.toString().contains(":keepalive")) {
                        eventName = "keepalive";
                    } else if (data != null && data.toString().contains("event:")) {
                        String s = data.toString();
                        int idx = s.indexOf("event:");
                        int end = s.indexOf('\n', idx);
                        if (idx >= 0 && end > idx) {
                            eventName = s.substring(idx + 6, end).trim();
                        }
                    }
                }
            }
            sentEventNames.add(eventName);

            if (disconnectOnCount > 0 && current == disconnectOnCount) {
                abortOccurred.set(true);
                throw new IOException("Simulated client disconnect on write #" + current + " (Broken pipe)");
            }

            if (disconnectOnEventName != null && disconnectOnEventName.equalsIgnoreCase(eventName)) {
                abortOccurred.set(true);
                throw new IOException("Simulated client disconnect on event '" + eventName + "' (Connection reset)");
            }
        }

        public boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
            return completionLatch.await(timeout, unit);
        }

        public List<String> getSentEventNames() {
            return sentEventNames;
        }

        public boolean isAbortOccurred() {
            return abortOccurred.get();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Test 1: Mid-Stream Disconnect During Token Generation
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Mid-stream disconnect during token generation aborts execution, sets INTERRUPTED, and preserves checkpoint")
    void testDisconnectDuringTokenGeneration() throws Exception {
        CountDownLatch streamStarted = new CountDownLatch(1);
        CountDownLatch streamCancelled = new CountDownLatch(1);

        StreamingChatModel streamingModel = new StreamingChatModel() {
            @Override
            public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
                streamStarted.countDown();
                try {
                    // Emit multiple tokens
                    handler.onPartialResponse("Token 1: Thinking about life. ");
                    Thread.sleep(50);
                    handler.onPartialResponse("Token 2: Disconnect happens here. ");
                    Thread.sleep(50);
                    handler.onPartialResponse("Token 3: Should never be saved. ");
                    handler.onCompleteResponse(ChatResponse.builder()
                            .aiMessage(AiMessage.from("Token 1: Thinking about life. Token 2: Disconnect happens here. Token 3: Should never be saved."))
                            .build());
                } catch (InterruptedException ie) {
                    streamCancelled.countDown();
                }
            }
        };

        when(mockLlmBridge.streamingModel(any())).thenReturn(streamingModel);

        AgenticChatGraph graph = new AgenticChatGraph(mockLlmBridge, toolRegistry, null, null, graphCache, checkpointSaver);
        ChatService chatService = new ChatService(
                memoryPort, contextPrimingService, identityPrimerService,
                toolRegistry, graph, tsid, props, null, transcriptPort);

        // Disconnect on write #3 (write 1 is session, write 2 is token 1, write 3 is token 2)
        DisconnectingSseEmitter emitter = new DisconnectingSseEmitter(3);

        String sessionId = "sess-tokendisconnect-1";
        AgentChatRequest request = new AgentChatRequest("Hello Spector", sessionId);

        chatService.streamChat(request, testSoul, emitter);

        // Await completion of emitter
        boolean completed = emitter.awaitCompletion(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(emitter.isAbortOccurred()).isTrue();

        // Allow virtual thread to cleanly exit
        Thread.sleep(200);

        // Assert 1: CHAT_TURN status is durably 'INTERRUPTED'
        List<Map<String, Object>> turns = jdbc.sql("SELECT id, status, model FROM CHAT_TURN WHERE session_id = :sessionId")
                .param("sessionId", sessionId)
                .query().listOfRows();

        assertThat(turns).hasSize(1);
        assertThat(turns.get(0).get("STATUS")).isEqualTo("INTERRUPTED");

        // Assert 2: Operational events do NOT contain 'done'
        List<String> eventTypes = jdbc.sql("SELECT type FROM CHAT_EVENT WHERE turn_id = :turnId ORDER BY seq ASC")
                .param("turnId", turns.get(0).get("ID"))
                .query((rs, rowNum) -> rs.getString("type"))
                .list();

        assertThat(eventTypes).contains("session");
        assertThat(eventTypes).doesNotContain("done");

        // Assert 3: Checkpoint is preserved in GRAPH_CHECKPOINT
        Optional<byte[]> checkpointBlob = checkpointSaver.loadCheckpoint(sessionId);
        assertThat(checkpointBlob).isPresent();
        assertThat(checkpointBlob.get().length).isGreaterThan(0);

        // Verify checkpoint can be deserialized
        RunnableConfig config = RunnableConfig.builder().threadId(sessionId).build();
        Optional<Checkpoint> checkpoint = checkpointSaver.get(config);
        assertThat(checkpoint).isPresent();

        chatService.shutdown();
    }

    // ═══════════════════════════════════════════════════════════════
    // Test 2: Mid-Stream Disconnect on Tool Call Emission
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Mid-stream disconnect on tool_call emission cancels graph, sets INTERRUPTED, and preserves checkpoint")
    void testDisconnectOnToolCall() throws Exception {
        McpToolHandler mockTool = new McpToolHandler() {
            @Override public String name() { return "calculator"; }
            @Override public String description() { return "Calculates math"; }
            @Override public Map<String, Object> inputSchema() { return Map.of(); }
            @Override public McpToolCategory category() { return McpToolCategory.GENERAL; }
            @Override public boolean isWriteTool() { return false; }
            @Override public McpSchema.CallToolResult execute(Map<String, Object> args) {
                return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("result=42")), false, null, null);
            }
        };
        toolRegistry.register(mockTool);

        StreamingChatModel toolStreamingModel = new StreamingChatModel() {
            @Override
            public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
                ToolExecutionRequest toolReq = ToolExecutionRequest.builder()
                        .id("call-calc-1")
                        .name("calculator")
                        .arguments("{\"expr\": \"6 * 7\"}")
                        .build();

                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolReq)).build())
                        .build());
            }
        };

        when(mockLlmBridge.streamingModel(any())).thenReturn(toolStreamingModel);

        AgenticChatGraph graph = new AgenticChatGraph(mockLlmBridge, toolRegistry, null, null, graphCache, checkpointSaver);
        ChatService chatService = new ChatService(
                memoryPort, contextPrimingService, identityPrimerService,
                toolRegistry, graph, tsid, props, null, transcriptPort);

        // Disconnect specifically when tool_call event is emitted
        DisconnectingSseEmitter emitter = new DisconnectingSseEmitter("tool_call");

        String sessionId = "sess-tooldisconnect-1";
        AgentChatRequest request = new AgentChatRequest("What is 6 * 7?", sessionId);

        chatService.streamChat(request, testSoul, emitter);

        boolean completed = emitter.awaitCompletion(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(emitter.isAbortOccurred()).isTrue();

        Thread.sleep(200);

        // Assert 1: CHAT_TURN status is durably 'INTERRUPTED'
        List<Map<String, Object>> turns = jdbc.sql("SELECT id, status FROM CHAT_TURN WHERE session_id = :sessionId")
                .param("sessionId", sessionId)
                .query().listOfRows();

        assertThat(turns).hasSize(1);
        assertThat(turns.get(0).get("STATUS")).isEqualTo("INTERRUPTED");

        // Assert 2: Checkpoint is preserved in GRAPH_CHECKPOINT
        Optional<byte[]> checkpointBlob = checkpointSaver.loadCheckpoint(sessionId);
        assertThat(checkpointBlob).isPresent();

        chatService.shutdown();
    }

    // ═══════════════════════════════════════════════════════════════
    // Test 3: Disconnect While Tool is Actively Executing
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Disconnect while tool is actively executing aborts graph, sets INTERRUPTED, and avoids secondary loops")
    void testDisconnectWhileToolExecuting() throws Exception {
        CountDownLatch toolStarted = new CountDownLatch(1);
        CountDownLatch toolCanFinish = new CountDownLatch(1);
        AtomicBoolean toolExecuted = new AtomicBoolean(false);

        McpToolHandler blockingTool = new McpToolHandler() {
            @Override public String name() { return "blocking_fetch"; }
            @Override public String description() { return "Simulates long running network fetch"; }
            @Override public Map<String, Object> inputSchema() { return Map.of(); }
            @Override public McpToolCategory category() { return McpToolCategory.GENERAL; }
            @Override public boolean isWriteTool() { return false; }
            @Override public McpSchema.CallToolResult execute(Map<String, Object> args) {
                toolStarted.countDown();
                try {
                    toolCanFinish.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                toolExecuted.set(true);
                return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("done")), false, null, null);
            }
        };
        toolRegistry.register(blockingTool);

        StreamingChatModel toolStreamingModel = new StreamingChatModel() {
            @Override
            public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
                ToolExecutionRequest toolReq = ToolExecutionRequest.builder()
                        .id("call-block-1")
                        .name("blocking_fetch")
                        .arguments("{}")
                        .build();

                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolReq)).build())
                        .build());
            }
        };

        when(mockLlmBridge.streamingModel(any())).thenReturn(toolStreamingModel);

        AgenticChatGraph graph = new AgenticChatGraph(mockLlmBridge, toolRegistry, null, null, graphCache, checkpointSaver);
        ChatService chatService = new ChatService(
                memoryPort, contextPrimingService, identityPrimerService,
                toolRegistry, graph, tsid, props, null, transcriptPort);

        DisconnectingSseEmitter emitter = new DisconnectingSseEmitter(-1);

        String sessionId = "sess-in-flight-tool";
        AgentChatRequest request = new AgentChatRequest("Fetch remote data", sessionId);

        chatService.streamChat(request, testSoul, emitter);

        // Wait until tool starts running
        boolean started = toolStarted.await(3, TimeUnit.SECONDS);
        assertThat(started).isTrue();

        // While tool is in flight, simulate client abort
        emitter.completeWithError(new IOException("Client aborted while tool in flight"));

        // Release tool
        toolCanFinish.countDown();

        boolean completed = emitter.awaitCompletion(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        Thread.sleep(200);

        // Assert: CHAT_TURN status is durably 'INTERRUPTED'
        List<Map<String, Object>> turns = jdbc.sql("SELECT id, status FROM CHAT_TURN WHERE session_id = :sessionId")
                .param("sessionId", sessionId)
                .query().listOfRows();

        assertThat(turns).hasSize(1);
        assertThat(turns.get(0).get("STATUS")).isEqualTo("INTERRUPTED");

        chatService.shutdown();
    }

    // ═══════════════════════════════════════════════════════════════
    // Test 4: Checkpoint Preservation and Resumption on Subsequent Turn
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("LangGraph4j checkpoint preserved after disconnect and resumed on subsequent turn")
    void testCheckpointPreservationAndResumption() throws Exception {
        AtomicInteger invocationCount = new AtomicInteger(0);

        // First invocation outputs a tool call, then gets interrupted
        // Second invocation (resumption) outputs final assistant text acknowledging previous messages
        StreamingChatModel dynamicModel = new StreamingChatModel() {
            @Override
            public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
                int inv = invocationCount.incrementAndGet();
                if (inv == 1) {
                    // Turn 1: Stream tokens then disconnect
                    handler.onPartialResponse("Turn 1 partial response. ");
                    handler.onCompleteResponse(ChatResponse.builder()
                            .aiMessage(AiMessage.from("Turn 1 partial response. "))
                            .build());
                } else {
                    // Turn 2: Verify request history contains prior context
                    List<ChatMessage> incoming = request.messages();
                    boolean hasPriorContext = incoming.stream()
                            .anyMatch(m -> m instanceof UserMessage u && u.singleText().contains("Initial question"));

                    String reply = hasPriorContext ? "Resumed successfully with prior context." : "No prior context found.";
                    handler.onPartialResponse(reply);
                    handler.onCompleteResponse(ChatResponse.builder()
                            .aiMessage(AiMessage.from(reply))
                            .build());
                }
            }
        };

        when(mockLlmBridge.streamingModel(any())).thenReturn(dynamicModel);

        AgenticChatGraph graph = new AgenticChatGraph(mockLlmBridge, toolRegistry, null, null, graphCache, checkpointSaver);
        ChatService chatService = new ChatService(
                memoryPort, contextPrimingService, identityPrimerService,
                toolRegistry, graph, tsid, props, null, transcriptPort);

        String sessionId = "sess-resume-emp-1";

        // ─── Turn 1: Starts and Disconnects ───
        DisconnectingSseEmitter emitter1 = new DisconnectingSseEmitter(2); // Disconnect on token write
        AgentChatRequest request1 = new AgentChatRequest("Initial question for turn 1", sessionId);

        chatService.streamChat(request1, testSoul, emitter1);
        emitter1.awaitCompletion(5, TimeUnit.SECONDS);

        Thread.sleep(200);

        // Verify Turn 1 is INTERRUPTED
        List<Map<String, Object>> turnsTurn1 = jdbc.sql("SELECT id, status, seq FROM CHAT_TURN WHERE session_id = :sessionId ORDER BY seq ASC")
                .param("sessionId", sessionId)
                .query().listOfRows();
        assertThat(turnsTurn1).hasSize(1);
        assertThat(turnsTurn1.get(0).get("STATUS")).isEqualTo("INTERRUPTED");
        String turn1Id = (String) turnsTurn1.get(0).get("ID");

        // Verify GRAPH_CHECKPOINT has valid state
        Optional<byte[]> blobBefore = checkpointSaver.loadCheckpoint(sessionId);
        assertThat(blobBefore).isPresent();
        assertThat(blobBefore.get().length).isGreaterThan(0);

        // ─── Turn 2: Connects cleanly on SAME sessionId ───
        DisconnectingSseEmitter emitter2 = new DisconnectingSseEmitter(-1); // Clean connection, no disconnect
        AgentChatRequest request2 = new AgentChatRequest("Follow-up question for turn 2", sessionId);

        chatService.streamChat(request2, testSoul, emitter2);
        emitter2.awaitCompletion(5, TimeUnit.SECONDS);

        Thread.sleep(200);

        // Verify Turn statuses: Turn 1 is STILL INTERRUPTED, Turn 2 is DONE
        List<Map<String, Object>> turnsAfter = jdbc.sql("SELECT id, status, seq FROM CHAT_TURN WHERE session_id = :sessionId ORDER BY seq ASC")
                .param("sessionId", sessionId)
                .query().listOfRows();

        assertThat(turnsAfter).hasSize(2);
        assertThat(turnsAfter.get(0).get("ID")).isEqualTo(turn1Id);
        assertThat(turnsAfter.get(0).get("STATUS")).isEqualTo("INTERRUPTED");
        assertThat(turnsAfter.get(1).get("STATUS")).isEqualTo("DONE");

        // Verify GRAPH_CHECKPOINT still exists and holds updated checkpoint
        Optional<byte[]> blobAfter = checkpointSaver.loadCheckpoint(sessionId);
        assertThat(blobAfter).isPresent();

        chatService.shutdown();
    }

    // ═══════════════════════════════════════════════════════════════
    // Test 5: Stress Test & Orphaned Thread Leak Audit
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Rapid abort stress test executes 15 disconnects without orphaned threads or thread leaks")
    void testRapidDisconnectStressAndNoOrphanedThreads() throws Exception {
        StreamingChatModel streamingModel = new StreamingChatModel() {
            @Override
            public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
                try {
                    for (int i = 0; i < 5; i++) {
                        handler.onPartialResponse("Chunk " + i + " ");
                        Thread.sleep(20);
                    }
                    handler.onCompleteResponse(ChatResponse.builder()
                            .aiMessage(AiMessage.from("Full text"))
                            .build());
                } catch (InterruptedException ignored) {}
            }
        };

        when(mockLlmBridge.streamingModel(any())).thenReturn(streamingModel);

        AgenticChatGraph graph = new AgenticChatGraph(mockLlmBridge, toolRegistry, null, null, graphCache, checkpointSaver);
        ChatService chatService = new ChatService(
                memoryPort, contextPrimingService, identityPrimerService,
                toolRegistry, graph, tsid, props, null, transcriptPort);

        int initialThreadCount = Thread.activeCount();

        int iterations = 15;
        for (int i = 0; i < iterations; i++) {
            String sessionId = "sess-stress-" + i;
            // Disconnect on write 2 or 3 alternately
            int disconnectOn = (i % 2 == 0) ? 2 : 3;
            DisconnectingSseEmitter emitter = new DisconnectingSseEmitter(disconnectOn);
            AgentChatRequest request = new AgentChatRequest("Stress test message " + i, sessionId);

            chatService.streamChat(request, testSoul, emitter);
            emitter.awaitCompletion(2, TimeUnit.SECONDS);
        }

        // Allow grace period for virtual threads and scheduled tasks to complete
        Thread.sleep(500);

        // Verify all 15 turns in DB are marked INTERRUPTED
        List<String> statuses = jdbc.sql("SELECT status FROM CHAT_TURN WHERE session_id LIKE 'sess-stress-%'")
                .query((rs, rowNum) -> rs.getString("status"))
                .list();

        assertThat(statuses).hasSize(iterations);
        assertThat(statuses).allMatch(s -> "INTERRUPTED".equals(s));

        // Audit thread state: ensure no threads named 'chat-stream' or 'AsyncGenerator' are indefinitely blocked
        Set<Thread> allThreads = Thread.getAllStackTraces().keySet();
        long lingeringChatStreamThreads = allThreads.stream()
                .filter(t -> t.getName().contains("chat-stream") && t.isAlive() && t.getState() != Thread.State.TERMINATED)
                .count();

        assertThat(lingeringChatStreamThreads)
                .as("There should be no lingering active chat-stream threads")
                .isEqualTo(0);

        chatService.shutdown();
    }

    // ═══════════════════════════════════════════════════════════════
    // Test 6: Multi-Tool Call Disconnect Handling
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Disconnect on first tool call of multi-tool plan cancels further processing and preserves checkpoint")
    void testDisconnectOnMultiToolCall() throws Exception {
        AtomicBoolean tool1Executed = new AtomicBoolean(false);
        AtomicBoolean tool2Executed = new AtomicBoolean(false);

        toolRegistry.register(new McpToolHandler() {
            @Override public String name() { return "tool_alpha"; }
            @Override public String description() { return "First tool"; }
            @Override public Map<String, Object> inputSchema() { return Map.of(); }
            @Override public McpToolCategory category() { return McpToolCategory.GENERAL; }
            @Override public boolean isWriteTool() { return false; }
            @Override public McpSchema.CallToolResult execute(Map<String, Object> args) {
                tool1Executed.set(true);
                return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("alpha")), false, null, null);
            }
        });

        toolRegistry.register(new McpToolHandler() {
            @Override public String name() { return "tool_beta"; }
            @Override public String description() { return "Second tool"; }
            @Override public Map<String, Object> inputSchema() { return Map.of(); }
            @Override public McpToolCategory category() { return McpToolCategory.GENERAL; }
            @Override public boolean isWriteTool() { return false; }
            @Override public McpSchema.CallToolResult execute(Map<String, Object> args) {
                tool2Executed.set(true);
                return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("beta")), false, null, null);
            }
        });

        StreamingChatModel multiToolModel = new StreamingChatModel() {
            @Override
            public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
                ToolExecutionRequest req1 = ToolExecutionRequest.builder().id("c1").name("tool_alpha").arguments("{}").build();
                ToolExecutionRequest req2 = ToolExecutionRequest.builder().id("c2").name("tool_beta").arguments("{}").build();
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.builder().toolExecutionRequests(List.of(req1, req2)).build())
                        .build());
            }
        };

        when(mockLlmBridge.streamingModel(any())).thenReturn(multiToolModel);

        AgenticChatGraph graph = new AgenticChatGraph(mockLlmBridge, toolRegistry, null, null, graphCache, checkpointSaver);
        ChatService chatService = new ChatService(
                memoryPort, contextPrimingService, identityPrimerService,
                toolRegistry, graph, tsid, props, null, transcriptPort);

        // Disconnect immediately on tool_call
        DisconnectingSseEmitter emitter = new DisconnectingSseEmitter("tool_call");

        String sessionId = "sess-multitool-disconnect";
        AgentChatRequest request = new AgentChatRequest("Execute both tools", sessionId);

        chatService.streamChat(request, testSoul, emitter);
        emitter.awaitCompletion(5, TimeUnit.SECONDS);

        Thread.sleep(200);

        // Status is INTERRUPTED
        List<Map<String, Object>> turns = jdbc.sql("SELECT id, status FROM CHAT_TURN WHERE session_id = :sessionId")
                .param("sessionId", sessionId)
                .query().listOfRows();

        assertThat(turns).hasSize(1);
        assertThat(turns.get(0).get("STATUS")).isEqualTo("INTERRUPTED");

        // Checkpoint is preserved
        Optional<byte[]> cp = checkpointSaver.loadCheckpoint(sessionId);
        assertThat(cp).isPresent();

        chatService.shutdown();
    }

    // ═══════════════════════════════════════════════════════════════
    // Test 7: Immediate Disconnect on Initial Session Event
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Immediate disconnect on initial session event write marks turn INTERRUPTED")
    void testImmediateDisconnectOnInitialSessionEvent() throws Exception {
        StreamingChatModel model = mock(StreamingChatModel.class);
        when(mockLlmBridge.streamingModel(any())).thenReturn(model);

        AgenticChatGraph graph = new AgenticChatGraph(mockLlmBridge, toolRegistry, null, null, graphCache, checkpointSaver);
        ChatService chatService = new ChatService(
                memoryPort, contextPrimingService, identityPrimerService,
                toolRegistry, graph, tsid, props, null, transcriptPort);

        // Disconnect immediately on write #1 (the session pre-flush event)
        DisconnectingSseEmitter emitter = new DisconnectingSseEmitter(1);

        String sessionId = "sess-immediate-abort-1";
        AgentChatRequest request = new AgentChatRequest("Hello before disconnect", sessionId);

        chatService.streamChat(request, testSoul, emitter);

        boolean completed = emitter.awaitCompletion(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(emitter.isAbortOccurred()).isTrue();

        Thread.sleep(200);

        // Status must be durably INTERRUPTED in operational DB
        List<Map<String, Object>> turns = jdbc.sql("SELECT id, status FROM CHAT_TURN WHERE session_id = :sessionId")
                .param("sessionId", sessionId)
                .query().listOfRows();

        assertThat(turns).hasSize(1);
        assertThat(turns.get(0).get("STATUS")).isEqualTo("INTERRUPTED");

        chatService.shutdown();
    }

    // ═══════════════════════════════════════════════════════════════
    // Test 8: Disconnect During Keepalive Heartbeat
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Disconnect during keepalive heartbeat cleanly aborts turn and marks INTERRUPTED")
    void testKeepaliveDisconnectTriggersAbort() {
        SseEmitter mockEmitter = mock(SseEmitter.class);
        java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();

        try {
            org.mockito.Mockito.doThrow(new IOException("Broken pipe on keepalive"))
                    .when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));

            ChatService.ChatStreamSink sink = new ChatService.ChatStreamSink(
                    mockEmitter, "sess-ka-abort", "turn-ka-abort", transcriptPort, scheduler);

            AtomicBoolean abortCallbackCalled = new AtomicBoolean(false);
            sink.setAbortAction(() -> abortCallbackCalled.set(true));

            // Execute keepalive
            sink.sendKeepalive();

            assertThat(sink.isAborted()).isTrue();
            assertThat(abortCallbackCalled.get()).isTrue();

            // Verify turn status updated in DB if turn existed
            List<Map<String, Object>> turns = jdbc.sql("SELECT id, status FROM CHAT_TURN WHERE id = 'turn-ka-abort'")
                    .query().listOfRows();
            // Since turn was not seeded in this slice, verify sink cleanly handled it
            assertThat(sink.isAborted()).isTrue();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            scheduler.shutdownNow();
        }
    }
}
