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
package com.spectrayan.spector.synapse.agent.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.agent.chat.cache.GraphCache;
import com.spectrayan.spector.synapse.agent.chat.stream.TokenSplitter;
import com.spectrayan.spector.synapse.bridge.LlmBridge;
import com.spectrayan.spector.synapse.security.injection.InjectionInterceptor;
import com.spectrayan.spector.synapse.security.injection.PromptInjectionException;
import com.spectrayan.spector.synapse.security.pii.PiiInterceptor;
import com.spectrayan.spector.synapse.security.pii.PiiRedactionResult;
import com.spectrayan.spector.synapse.security.pii.PiiRedactionSession;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.NodeActionWithConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * Agentic chat graph — a LangGraph4j {@link StateGraph} implementing the ReAct
 * (Reasoning + Acting) pattern with sub-500ms TTFT streaming and tool interleaving
 * (Issue #263, ADR-0084).
 *
 * <h3>Graph Flow</h3>
 * <pre>
 *   START → agent → shouldUseTool? → tools → agent (loop)
 *                                  → END   (no tools needed)
 * </pre>
 */
@Component
public class AgenticChatGraph {

    private static final Logger log = LoggerFactory.getLogger(AgenticChatGraph.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MESSAGES_KEY = "messages";
    private static final String AGENT_NODE = "agent";
    private static final String TOOLS_NODE = "tools";

    private final LlmBridge llmBridge;
    private final ToolRegistry toolRegistry;
    private final InjectionInterceptor injectionInterceptor;
    private final PiiInterceptor piiInterceptor;
    private final GraphCache graphCache;
    private final BaseCheckpointSaver checkpointSaver;

    public AgenticChatGraph(LlmBridge llmBridge, ToolRegistry toolRegistry) {
        this(llmBridge, toolRegistry, null, null, null, null);
    }

    public AgenticChatGraph(LlmBridge llmBridge,
                            ToolRegistry toolRegistry,
                            InjectionInterceptor injectionInterceptor) {
        this(llmBridge, toolRegistry, injectionInterceptor, null, null, null);
    }

    public AgenticChatGraph(LlmBridge llmBridge,
                            ToolRegistry toolRegistry,
                            InjectionInterceptor injectionInterceptor,
                            PiiInterceptor piiInterceptor) {
        this(llmBridge, toolRegistry, injectionInterceptor, piiInterceptor, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AgenticChatGraph(LlmBridge llmBridge,
                            ToolRegistry toolRegistry,
                            @org.springframework.beans.factory.annotation.Autowired(required = false)
                            InjectionInterceptor injectionInterceptor,
                            @org.springframework.beans.factory.annotation.Autowired(required = false)
                            PiiInterceptor piiInterceptor,
                            @org.springframework.beans.factory.annotation.Autowired(required = false)
                            GraphCache graphCache,
                            @org.springframework.beans.factory.annotation.Autowired(required = false)
                            BaseCheckpointSaver checkpointSaver) {
        this.llmBridge = Objects.requireNonNull(llmBridge, "LlmBridge must not be null");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "ToolRegistry must not be null");
        this.injectionInterceptor = injectionInterceptor;
        this.piiInterceptor = piiInterceptor;
        this.graphCache = graphCache != null ? graphCache : new GraphCache();
        this.checkpointSaver = checkpointSaver;
    }

    /**
     * Builds and compiles the agentic chat graph for a given agent soul, utilizing
     * {@link GraphCache} to prevent re-compilation overhead.
     *
     * @param soul the agent identity providing system prompt and tool configuration
     * @return a compiled, ready-to-execute graph
     */
    public CompiledGraph<AgentState> compile(AgentSoul soul) {
        return compile(soul, this.checkpointSaver);
    }

    /**
     * Builds and compiles the agentic chat graph with a specific checkpoint saver.
     */
    public CompiledGraph<AgentState> compile(AgentSoul soul, BaseCheckpointSaver customCheckpointSaver) {
        if (graphCache != null) {
            return graphCache.getOrCompile(soul, toolRegistry, () -> doCompile(soul, customCheckpointSaver));
        }
        return doCompile(soul, customCheckpointSaver);
    }

    private CompiledGraph<AgentState> doCompile(AgentSoul soul, BaseCheckpointSaver saver) {
        try {
            Map<String, Channel<?>> channels = Map.of(
                    MESSAGES_KEY, Channels.<ChatMessage>appender(ArrayList::new)
            );

            List<ToolSpecification> toolSpecs = resolveToolSpecs(soul);
            String systemPrompt = soul.systemPrompt() != null
                    ? soul.systemPrompt()
                    : "You are a helpful AI assistant with access to tools.";

            String modelName = soul.model();
            String agentId = soul.id();
            List<String> soulToolsHint = soul.tools() != null ? soul.tools() : List.of();

            NodeActionWithConfig<AgentState> agentAction = (state, config) ->
                    agentNodeStreaming(state, config, systemPrompt, toolSpecs, modelName);

            NodeActionWithConfig<AgentState> toolAction = (state, config) ->
                    toolNodeInterleaved(state, config, agentId, soulToolsHint);

            var stateSerializer = new LC4jStateSerializer<>(AgentState::new);
            stateSerializer.mapper().register(dev.langchain4j.data.message.ImageContent.class,
                    new com.spectrayan.spector.synapse.agent.graph.serializer.SafeImageContentSerializer());

            CompileConfig.Builder compileConfigBuilder = CompileConfig.builder();
            if (saver != null) {
                compileConfigBuilder.checkpointSaver(saver);
            }
            CompileConfig compileConfig = compileConfigBuilder.build();

            CompiledGraph<AgentState> compiled = new StateGraph<>(channels, stateSerializer)
                    .addNode(AGENT_NODE, AsyncNodeActionWithConfig.node_async(agentAction))
                    .addNode(TOOLS_NODE, AsyncNodeActionWithConfig.node_async(toolAction))
                    .addEdge(START, AGENT_NODE)
                    .addConditionalEdges(AGENT_NODE,
                            AsyncEdgeAction.edge_async(this::shouldUseTool),
                            Map.of("tools", TOOLS_NODE, "end", END))
                    .addEdge(TOOLS_NODE, AGENT_NODE)
                    .compile(compileConfig);

            log.info("[AgenticChatGraph] Compiled graph for soul '{}' with {} tools (checkpointSaver={})",
                    soul.name(), toolSpecs.size(), saver != null ? "active" : "none");
            return compiled;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to compile agentic graph for soul '" + soul.name() + "'", e);
        }
    }

    /**
     * Executes a streaming agentic chat turn and returns an interruptible stream generator.
     */
    public AsyncGenerator.Cancellable<NodeOutput<AgentState>> chatStream(
            AgentSoul soul,
            List<ChatMessage> history,
            String message,
            AgentChatListener listener,
            String sessionId,
            String turnId) {

        String safeMessage = message;
        if (injectionInterceptor != null) {
            try {
                safeMessage = injectionInterceptor.interceptUserInput(message);
            } catch (PromptInjectionException pie) {
                log.warn("[AgenticChatGraph] Blocked prompt injection: {}", pie.getMessage());
                listener.onError("SPE-400-001", pie.getMessage(), false);
                throw pie;
            }
        }

        if (piiInterceptor != null && piiInterceptor.isActive()) {
            PiiRedactionResult redacted = piiInterceptor.redact(safeMessage);
            safeMessage = redacted.redactedText();
        }

        List<ChatMessage> initialMessages = new ArrayList<>(history);
        initialMessages.add(UserMessage.from(safeMessage));

        Map<String, Object> input = Map.of(
                MESSAGES_KEY, initialMessages
        );

        CompiledGraph<AgentState> compiled = compile(soul, checkpointSaver);
        RunnableConfig config = RunnableConfig.builder()
                .threadId(sessionId)
                .addMetadata("listener", listener)
                .addMetadata("sessionId", sessionId)
                .addMetadata("turnId", turnId)
                .build();

        return compiled.stream(input, config);
    }

    /**
     * Executes a single user message through the agentic graph.
     */
    public String chat(AgentSoul soul, String message) {
        return chat(soul, message, AgentChatListener.NOOP);
    }

    /**
     * Executes a single user message with a streaming listener.
     */
    public String chat(AgentSoul soul, String message, AgentChatListener listener) {
        return chat(soul, List.of(), message, listener);
    }

    /**
     * Executes a user message through the agentic graph with conversation history.
     */
    public String chat(AgentSoul soul, List<ChatMessage> history, String message) {
        return chat(soul, history, message, AgentChatListener.NOOP);
    }

    /**
     * Executes a user message through the agentic graph with conversation history and streaming.
     */
    public String chat(AgentSoul soul, List<ChatMessage> history, String message, AgentChatListener listener) {
        CompiledGraph<AgentState> compiled = compile(soul, checkpointSaver);

        try {
            listener.onThinking("Processing message...");

            // Prompt-injection shield
            String safeMessage = message;
            if (injectionInterceptor != null) {
                try {
                    safeMessage = injectionInterceptor.interceptUserInput(message);
                } catch (PromptInjectionException pie) {
                    log.warn("[AgenticChatGraph] Blocked prompt injection: {}", pie.getMessage());
                    listener.onError(pie.getMessage());
                    return "Your message was blocked by the prompt injection shield. "
                            + "Please rephrase without attempts to override system instructions.";
                }
            }

            // PII redaction
            PiiRedactionSession piiSession = null;
            if (piiInterceptor != null && piiInterceptor.isActive()) {
                PiiRedactionResult redacted = piiInterceptor.redact(safeMessage);
                safeMessage = redacted.redactedText();
                piiSession = redacted.session();
                piiInterceptor.beginSession(piiSession);
            }

            try {
                List<ChatMessage> initialMessages = new ArrayList<>(history);
                initialMessages.add(UserMessage.from(safeMessage));

                Map<String, Object> input = Map.of(
                        MESSAGES_KEY, initialMessages
                );

                RunnableConfig config = RunnableConfig.builder()
                        .threadId("turn-" + System.currentTimeMillis())
                        .addMetadata("listener", listener)
                        .build();

                var result = compiled.invoke(input, config);
                @SuppressWarnings("unchecked")
                List<ChatMessage> messages = result.map(state ->
                        state.<List<ChatMessage>>value(MESSAGES_KEY)
                                .orElse(List.of()))
                        .orElse(List.of());

                String response = messages.reversed().stream()
                        .filter(m -> m instanceof AiMessage)
                        .map(m -> ((AiMessage) m).text())
                        .filter(t -> t != null && !t.isBlank())
                        .findFirst()
                        .orElse("I couldn't generate a response.");

                if (piiInterceptor != null && piiSession != null) {
                    response = piiInterceptor.rehydrate(response, piiSession);
                }

                listener.onDone("Completed");
                return response;
            } finally {
                if (piiInterceptor != null) {
                    piiInterceptor.endSession();
                }
            }

        } catch (Exception e) {
            log.error("[AgenticChatGraph] Chat execution failed: {}", e.getMessage(), e);
            String rootCause = extractRootCause(e);
            String userMessage;
            if (rootCause.contains("not found") || rootCause.contains("ModelNotFound")) {
                userMessage = "The configured LLM model is not available. " +
                        "Please check your Ollama installation and model name. " +
                        "Detail: " + rootCause;
            } else if (rootCause.contains("Connection refused") || rootCause.contains("ConnectException")) {
                userMessage = "Cannot connect to Ollama. Please ensure Ollama is running at the configured URL.";
            } else {
                userMessage = "I'm sorry, I encountered an issue processing your request. Please try again.";
            }

            listener.onError(rootCause);
            return userMessage;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Graph Node Implementations
    // ═══════════════════════════════════════════════════════════════

    /**
     * Streaming Agent node — streams tokens through TokenSplitter, isolating reasoning
     * and pausing visible tokens on tool requests.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> agentNodeStreaming(
            AgentState state,
            RunnableConfig config,
            String systemPrompt,
            List<ToolSpecification> toolSpecs,
            String modelName) {

        AgentChatListener listener = (AgentChatListener) config.metadata("listener")
                .orElse(AgentChatListener.NOOP);

        List<ChatMessage> messages = state.<List<ChatMessage>>value(MESSAGES_KEY)
                .orElse(List.of());

        List<ChatMessage> fullMessages = new ArrayList<>();
        fullMessages.add(SystemMessage.from(systemPrompt));
        fullMessages.addAll(messages);

        log.debug("[AgenticChatGraph] Agent node streaming — {} messages, {} tool specs, model {}",
                messages.size(), toolSpecs.size(), modelName);

        StreamingChatModel streamingModel = null;
        try {
            streamingModel = llmBridge.streamingModel(modelName);
        } catch (Exception e) {
            log.debug("[AgenticChatGraph] Could not acquire streamingModel, falling back to chatModel: {}", e.getMessage());
        }

        ChatResponse response;
        if (streamingModel != null) {
            TokenSplitter splitter = new TokenSplitter(listener);
            CompletableFuture<ChatResponse> future = new CompletableFuture<>();

            StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    splitter.onTextChunk(partialResponse);
                }

                @Override
                public void onPartialThinking(PartialThinking partialThinking) {
                    if (partialThinking != null && partialThinking.text() != null) {
                        splitter.onNativeThinking(partialThinking.text());
                    }
                }

                @Override
                public void onPartialToolCall(PartialToolCall partialToolCall) {
                    splitter.onToolCall();
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    splitter.flush();
                    future.complete(completeResponse);
                }

                @Override
                public void onError(Throwable error) {
                    splitter.flush();
                    future.completeExceptionally(error);
                }
            };

            var chatRequestBuilder = dev.langchain4j.model.chat.request.ChatRequest.builder()
                    .messages(fullMessages);
            if (!toolSpecs.isEmpty()) {
                chatRequestBuilder.toolSpecifications(toolSpecs);
            }

            streamingModel.chat(chatRequestBuilder.build(), handler);

            try {
                response = future.join();
            } catch (Exception e) {
                String causeMessage = extractRootCause(e);
                log.error("[AgenticChatGraph] Streaming model failure: {}", causeMessage);
                listener.onError("SPE-700-001", causeMessage, true);
                throw new RuntimeException("Model streaming failed: " + causeMessage, e);
            }
        } else {
            // Non-streaming fallback (e.g. for mock tests)
            var chatModel = llmBridge.chatModel(modelName);
            if (!toolSpecs.isEmpty()) {
                response = chatModel.chat(
                        dev.langchain4j.model.chat.request.ChatRequest.builder()
                                .messages(fullMessages)
                                .toolSpecifications(toolSpecs)
                                .build()
                );
            } else {
                response = chatModel.chat(fullMessages);
            }
            if (response != null && response.aiMessage() != null && response.aiMessage().text() != null) {
                listener.onToken(response.aiMessage().text());
            }
        }

        AiMessage aiMessage = response.aiMessage();
        log.debug("[AgenticChatGraph] LLM response — text={}, toolCalls={}",
                aiMessage.text() != null ? aiMessage.text().length() + " chars" : "null",
                aiMessage.hasToolExecutionRequests() ? aiMessage.toolExecutionRequests().size() : 0);

        return Map.of(MESSAGES_KEY, List.of((ChatMessage) aiMessage));
    }

    /**
     * Tool node — executes pending tool calls, emits tool_call and tool_result events
     * (2 KiB preview on wire, full payload in state/JDBC), and pauses visible tokens.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toolNodeInterleaved(
            AgentState state,
            RunnableConfig config,
            String agentId,
            List<String> soulToolsHint) {

        AgentChatListener listener = (AgentChatListener) config.metadata("listener")
                .orElse(AgentChatListener.NOOP);

        List<ChatMessage> messages = state.<List<ChatMessage>>value(MESSAGES_KEY)
                .orElse(List.of());

        AiMessage lastAi = messages.reversed().stream()
                .filter(m -> m instanceof AiMessage ai && ai.hasToolExecutionRequests())
                .map(m -> (AiMessage) m)
                .findFirst()
                .orElse(null);

        if (lastAi == null || !lastAi.hasToolExecutionRequests()) {
            log.warn("[AgenticChatGraph] Tool node called but no tool requests found");
            return Map.of();
        }

        List<ChatMessage> toolResults = new ArrayList<>();
        for (ToolExecutionRequest req : lastAi.toolExecutionRequests()) {
            String callId = req.id() != null && !req.id().isBlank()
                    ? req.id()
                    : "call_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);

            Map<String, Object> arguments = parseArguments(req.arguments());

            // 1. Fire onToolCall callback (pauses visible tokens in UI)
            listener.onToolCall(callId, req.name(), arguments);

            long startMs = System.currentTimeMillis();
            String result;
            String status = "success";

            try {
                // 2. Execute via ToolRegistry with security interceptors
                result = toolRegistry.executeTool(req, agentId, soulToolsHint);
                if (injectionInterceptor != null) {
                    result = injectionInterceptor.interceptToolOutput(result);
                }
                if (piiInterceptor != null) {
                    result = piiInterceptor.redactUsingActiveSession(result);
                }
            } catch (Exception ex) {
                log.error("[AgenticChatGraph] Tool '{}' failed", req.name(), ex);
                status = "failure";
                result = "Error executing tool '" + req.name() + "': " + ex.getMessage();
            }

            long elapsedMs = System.currentTimeMillis() - startMs;

            // 3. 2 KiB preview truncation on the wire
            String safeResult = result != null ? result : "";
            boolean truncated = safeResult.length() > 2048;
            String preview = truncated ? safeResult.substring(0, 2048) : safeResult;

            // 4. Fire onToolResult (preview for wire, full payload for JDBC persistence)
            listener.onToolResult(callId, req.name(), status, preview, safeResult, elapsedMs);

            // 5. Append ToolExecutionResultMessage to graph state
            toolResults.add(ToolExecutionResultMessage.from(req, safeResult));
            log.debug("[AgenticChatGraph] Tool '{}' [{}] finished in {}ms ({} chars)",
                    req.name(), status, elapsedMs, safeResult.length());
        }

        return Map.of(MESSAGES_KEY, toolResults);
    }

    /**
     * Conditional edge — routes between tools and end based on tool calls.
     */
    @SuppressWarnings("unchecked")
    private String shouldUseTool(AgentState state) {
        List<ChatMessage> messages = state.<List<ChatMessage>>value(MESSAGES_KEY)
                .orElse(List.of());

        if (!messages.isEmpty()) {
            ChatMessage last = messages.getLast();
            if (last instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
                return "tools";
            }
        }
        return "end";
    }

    private List<ToolSpecification> resolveToolSpecs(AgentSoul soul) {
        return toolRegistry.resolveToolSpecs(soul);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(argumentsJson, Map.class);
        } catch (Exception e) {
            log.debug("[AgenticChatGraph] Could not parse tool arguments JSON: {}", e.getMessage());
            return Map.of("raw", argumentsJson);
        }
    }

    private static String extractRootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        return message;
    }
}
