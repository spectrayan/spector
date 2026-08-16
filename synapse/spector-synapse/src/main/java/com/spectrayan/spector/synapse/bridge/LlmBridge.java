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
package com.spectrayan.spector.synapse.bridge;

import com.spectrayan.spector.synapse.agent.graph.spec.LlmSpec;
import com.spectrayan.spector.synapse.bridge.structured.JsonSchemaGenerator;
import com.spectrayan.spector.synapse.bridge.structured.StructuredOutputException;
import com.spectrayan.spector.synapse.bridge.structured.StructuredOutputParser;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.spectrayan.spector.provider.ProviderRegistry;
import com.spectrayan.spector.provider.langchain4j.LangChain4jGenerationAdapter;
import com.spectrayan.spector.provider.ollama.OllamaLlmProvider;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM bridge using LangChain4j to interface with Ollama.
 *
 * <p>Provides both synchronous and streaming chat models. The models
 * are configured from {@link SynapseProperties} and lazily initialized.</p>
 *
 * <p>Supports per-request model configuration via {@link LlmSpec} for
 * dynamic graph nodes that need custom model/temperature settings.</p>
 */
@Service
public class LlmBridge {

    private static final Logger log = LoggerFactory.getLogger(LlmBridge.class);

    private final SynapseProperties props;
    private final ProviderRegistry providerRegistry;
    private final com.spectrayan.spector.synapse.config.service.ConfigResolutionService configResolutionService;
    private final com.spectrayan.spector.synapse.provider.usage.TokenUsageTracker tokenUsageTracker;
    private final ConcurrentHashMap<String, ChatModel> chatModels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StreamingChatModel> streamingModels = new ConcurrentHashMap<>();

    public LlmBridge(SynapseProperties props, ProviderRegistry providerRegistry) {
        this(props, providerRegistry, null, null);
    }

    public LlmBridge(SynapseProperties props, ProviderRegistry providerRegistry,
                     com.spectrayan.spector.synapse.config.service.ConfigResolutionService configResolutionService) {
        this(props, providerRegistry, configResolutionService, null);
    }

    @Autowired
    public LlmBridge(SynapseProperties props, ProviderRegistry providerRegistry,
                     com.spectrayan.spector.synapse.config.service.ConfigResolutionService configResolutionService,
                     @Autowired(required = false) com.spectrayan.spector.synapse.provider.usage.TokenUsageTracker tokenUsageTracker) {
        this.props = props;
        this.providerRegistry = providerRegistry;
        this.configResolutionService = configResolutionService;
        this.tokenUsageTracker = tokenUsageTracker;
        log.info("[LlmBridge] Configured with ProviderRegistry, ConfigResolutionService, TokenUsageTracker, and Ollama fallback");
    }

    /**
     * Get the default synchronous chat model.
     */
    /**
     * Get the default synchronous chat model.
     */
    public ChatModel chatModel() {
        if (configResolutionService == null) {
            return chatModel(props.getProvider().getGeneration().model());
        }
        Map<String, Object> llmConfig = configResolutionService.resolve("default", "default", com.spectrayan.spector.synapse.config.model.ConfigCategory.LLM_PROVIDER);
        String activeModel = (String) llmConfig.getOrDefault("model", props.getProvider().getGeneration().model());
        return chatModel(activeModel);
    }

    /**
     * Get the synchronous chat model for a specific model name (lazy-initialized and cached).
     */
    public ChatModel chatModel(String modelName) {
        if (providerRegistry != null) {
            var activeLlmOpt = providerRegistry.activeGeneration();
            if (activeLlmOpt.isPresent()) {
                var activeLlm = activeLlmOpt.get();
                if (modelName == null || modelName.isBlank() || modelName.equals(activeLlm.modelName())) {
                    if (activeLlm instanceof LangChain4jGenerationAdapter adapter) {
                        return adapter.delegate();
                    } else if (activeLlm instanceof OllamaLlmProvider ollamaLlm) {
                        return ollamaLlm.delegate();
                    }
                }
            }
        }
        if (configResolutionService == null) {
            String resolvedModel = (modelName == null || modelName.isBlank()) ? props.getProvider().getGeneration().model() : modelName;
            return chatModels.computeIfAbsent(resolvedModel, name -> {
                var model = OllamaChatModel.builder()
                        .baseUrl(props.getProvider().getGeneration().baseUrl())
                        .modelName(name)
                        .timeout(Duration.ofSeconds(120))
                        .temperature(0.7)
                        .build();
                log.info("[LlmBridge] Initialized ChatModel for model: {}", name);
                return model;
            });
        }
        Map<String, Object> llmConfig = configResolutionService.resolve("default", "default", com.spectrayan.spector.synapse.config.model.ConfigCategory.LLM_PROVIDER);
        double temp = ((Number) llmConfig.getOrDefault("temperature", 0.7)).doubleValue();
        String resolvedModel = (modelName == null || modelName.isBlank()) ? (String) llmConfig.getOrDefault("model", props.getProvider().getGeneration().model()) : modelName;
        String cacheKey = resolvedModel + ":" + temp;
        return chatModels.computeIfAbsent(cacheKey, name -> {
            var model = OllamaChatModel.builder()
                    .baseUrl((String) llmConfig.getOrDefault("base-url", props.getProvider().getGeneration().baseUrl()))
                    .modelName(resolvedModel)
                    .timeout(Duration.ofSeconds(120))
                    .temperature(temp)
                    .build();
            log.info("[LlmBridge] Initialized ChatModel for model: {} with temperature {}", resolvedModel, temp);
            return model;
        });
    }

    /**
     * Get the synchronous chat model for a specific {@link LlmSpec} configuration.
     *
     * <p>Uses a composite cache key {@code (model:temperature:maxTokens)} so that
     * the same model with different temperature settings produces separate instances.</p>
     *
     * @param spec the LLM configuration (model, temperature, maxTokens)
     * @return cached or newly built ChatModel
     */
    public ChatModel chatModel(LlmSpec spec) {
        if (spec == null) {
            return chatModel();
        }
        if (providerRegistry != null) {
            var activeLlmOpt = providerRegistry.activeGeneration();
            if (activeLlmOpt.isPresent()) {
                var activeLlm = activeLlmOpt.get();
                if (spec.model() == null || spec.model().isBlank() || spec.model().equals(activeLlm.modelName())) {
                    if (activeLlm instanceof LangChain4jGenerationAdapter adapter) {
                        return adapter.delegate();
                    } else if (activeLlm instanceof OllamaLlmProvider ollamaLlm) {
                        return ollamaLlm.delegate();
                    }
                }
            }
        }
        String cacheKey = spec.provider() + ":" + spec.model() + ":"
                + spec.temperature() + ":" + spec.maxTokens();
        return chatModels.computeIfAbsent(cacheKey, key -> {
            String baseUrl = props.getProvider().getGeneration().baseUrl();
            if (configResolutionService != null) {
                Map<String, Object> llmConfig = configResolutionService.resolve("default", "default", com.spectrayan.spector.synapse.config.model.ConfigCategory.LLM_PROVIDER);
                baseUrl = (String) llmConfig.getOrDefault("base-url", props.getProvider().getGeneration().baseUrl());
            }
            var model = OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(spec.model())
                    .timeout(Duration.ofSeconds(120))
                    .temperature(spec.temperature())
                    .build();
            log.info("[LlmBridge] Initialized ChatModel from LlmSpec: model={}, temp={}, maxTokens={}",
                    spec.model(), spec.temperature(), spec.maxTokens());
            return model;
        });
    }

    /**
     * Get the default streaming chat model.
     */
    public StreamingChatModel streamingModel() {
        if (configResolutionService == null) {
            return streamingModel(props.getProvider().getGeneration().model());
        }
        Map<String, Object> llmConfig = configResolutionService.resolve("default", "default", com.spectrayan.spector.synapse.config.model.ConfigCategory.LLM_PROVIDER);
        String activeModel = (String) llmConfig.getOrDefault("model", props.getProvider().getGeneration().model());
        return streamingModel(activeModel);
    }

    /**
     * Get the streaming chat model for a specific model name (lazy-initialized and cached).
     */
    public StreamingChatModel streamingModel(String modelName) {
        if (configResolutionService == null) {
            String resolvedModel = (modelName == null || modelName.isBlank()) ? props.getProvider().getGeneration().model() : modelName;
            return streamingModels.computeIfAbsent(resolvedModel, name -> {
                var model = OllamaStreamingChatModel.builder()
                        .baseUrl(props.getProvider().getGeneration().baseUrl())
                        .modelName(name)
                        .timeout(Duration.ofSeconds(120))
                        .temperature(0.7)
                        .build();
                log.info("[LlmBridge] Initialized StreamingChatModel for model: {}", name);
                return model;
            });
        }
        Map<String, Object> llmConfig = configResolutionService.resolve("default", "default", com.spectrayan.spector.synapse.config.model.ConfigCategory.LLM_PROVIDER);
        double temp = ((Number) llmConfig.getOrDefault("temperature", 0.7)).doubleValue();
        String resolvedModel = (modelName == null || modelName.isBlank()) ? (String) llmConfig.getOrDefault("model", props.getProvider().getGeneration().model()) : modelName;
        String cacheKey = resolvedModel + ":" + temp;
        return streamingModels.computeIfAbsent(cacheKey, name -> {
            var model = OllamaStreamingChatModel.builder()
                    .baseUrl((String) llmConfig.getOrDefault("base-url", props.getProvider().getGeneration().baseUrl()))
                    .modelName(resolvedModel)
                    .timeout(Duration.ofSeconds(120))
                    .temperature(temp)
                    .build();
            log.info("[LlmBridge] Initialized StreamingChatModel for model: {} with temperature {}", resolvedModel, temp);
            return model;
        });
    }

    /**
     * Generate a simple chat response using the default model.
     *
     * @param userMessage the user's message
     * @return the generated response text
     * @throws LlmBridgeException if generation fails
     */
    public String generate(String userMessage) {
        try {
            String response = chatModel().chat(userMessage);
            log.debug("[LlmBridge] Generated {} chars response", response.length());
            return response;
        } catch (Exception e) {
            log.error("[LlmBridge] Generation failed: {}", e.getMessage(), e);
            throw new LlmBridgeException("LLM generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a chat response with system prompt using the default model.
     *
     * @param systemPrompt the system-level instructions
     * @param userMessage  the user's message
     * @return the generated response text
     * @throws LlmBridgeException if generation fails
     */
    public String generate(String systemPrompt, String userMessage) {
        try {
            ChatResponse response = chatModel().chat(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userMessage)
            );
            String text = response.aiMessage().text();
            log.debug("[LlmBridge] Generated {} chars with system prompt", text.length());

            if (tokenUsageTracker != null) {
                int inTokens = (response.tokenUsage() != null && response.tokenUsage().inputTokenCount() != null)
                        ? response.tokenUsage().inputTokenCount()
                        : ((systemPrompt != null ? systemPrompt.length() : 0) + (userMessage != null ? userMessage.length() : 0)) / 4;
                int outTokens = (response.tokenUsage() != null && response.tokenUsage().outputTokenCount() != null)
                        ? response.tokenUsage().outputTokenCount()
                        : text.length() / 4;
                tokenUsageTracker.record(com.spectrayan.spector.synapse.provider.usage.TokenUsageEvent.ofGeneration(
                        com.spectrayan.spector.synapse.provider.usage.TokenUsageCategory.SYSTEM,
                        "default",
                        modelName(),
                        null,
                        null,
                        Math.max(1, inTokens),
                        Math.max(1, outTokens)
                ));
            }

            return text;
        } catch (Exception e) {
            log.error("[LlmBridge] Generation with system prompt failed: {}", e.getMessage(), e);
            throw new LlmBridgeException("LLM generation with system prompt failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a chat response using a specific {@link LlmSpec} configuration.
     *
     * <p>This enables dynamic graph nodes to use per-agent model settings
     * (different model, temperature, max tokens) as defined in the flow spec.</p>
     *
     * @param userMessage the user's message
     * @param spec        the LLM configuration to use
     * @return the generated response text
     * @throws LlmBridgeException if generation fails
     */
    public String generate(String userMessage, LlmSpec spec) {
        try {
            ChatModel model = chatModel(spec);
            String response = model.chat(userMessage);
            log.debug("[LlmBridge] Generated {} chars using spec (model={}, temp={})",
                    response.length(), spec.model(), spec.temperature());
            return response;
        } catch (Exception e) {
            log.error("[LlmBridge] Generation with LlmSpec failed (model={}): {}",
                    spec.model(), e.getMessage(), e);
            throw new LlmBridgeException("LLM generation failed for model '"
                    + spec.model() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Generate structured JSON output conforming to a JSON schema.
     *
     * @param userMessage the prompt or payload to process
     * @param jsonSchema  the expected JSON schema string
     * @return validated JSON string
     */
    public String generateStructured(String userMessage, String jsonSchema) {
        return generateStructured(null, userMessage, jsonSchema, 2);
    }

    public String generateStructured(String userMessage, String jsonSchema, int maxRetries) {
        return generateStructured(null, userMessage, jsonSchema, maxRetries);
    }

    /**
     * Generate structured JSON output with a system prompt and JSON schema.
     *
     * @param systemPrompt system instructions
     * @param userMessage  the prompt or payload to process
     * @param jsonSchema   the expected JSON schema string
     * @return validated JSON string
     */
    public String generateStructured(String systemPrompt, String userMessage, String jsonSchema) {
        return generateStructured(systemPrompt, userMessage, jsonSchema, 2);
    }

    /**
     * Generate structured JSON output conforming to a target Java class/record schema.
     *
     * @param userMessage the prompt or payload to process
     * @param targetClass target Java class or record
     * @param <T>         target type
     * @return strongly-typed instance of T
     */
    public <T> T generateStructured(String userMessage, Class<T> targetClass) {
        return generateStructured(null, userMessage, targetClass, 2);
    }

    public <T> T generateStructured(String userMessage, Class<T> targetClass, int maxRetries) {
        return generateStructured(null, userMessage, targetClass, maxRetries);
    }

    /**
     * Generate structured JSON output with a system prompt conforming to a target Java class/record schema.
     *
     * @param systemPrompt system instructions
     * @param userMessage  the prompt or payload to process
     * @param targetClass  target Java class or record
     * @param <T>          target type
     * @return strongly-typed instance of T
     */
    public <T> T generateStructured(String systemPrompt, String userMessage, Class<T> targetClass) {
        return generateStructured(systemPrompt, userMessage, targetClass, 2);
    }

    /**
     * Generate structured JSON output with retry mechanism and return typed object.
     *
     * @param systemPrompt system instructions (optional)
     * @param userMessage  the prompt or payload
     * @param targetClass  target Java class or record
     * @param maxRetries   maximum self-healing retries on validation failure
     * @param <T>          target type
     * @return strongly-typed instance of T
     */
    public <T> T generateStructured(String systemPrompt, String userMessage, Class<T> targetClass, int maxRetries) {
        String schema = JsonSchemaGenerator.generateSchemaJson(targetClass, true);
        String rawJson = generateStructured(systemPrompt, userMessage, schema, maxRetries);
        return StructuredOutputParser.parseObject(rawJson, targetClass);
    }

    /**
     * Generate structured JSON string with self-healing feedback retry loop.
     *
     * @param systemPrompt system instructions (optional)
     * @param userMessage  the prompt or payload
     * @param jsonSchema   the expected JSON schema string
     * @param maxRetries   maximum self-healing retries on validation failure
     * @return validated JSON string
     */
    public String generateStructured(String systemPrompt, String userMessage, String jsonSchema, int maxRetries) {
        String effectiveSystemPrompt = buildStructuredSystemPrompt(systemPrompt, jsonSchema);
        String currentUserPrompt = userMessage;

        Exception lastException = null;
        for (int attempt = 0; attempt <= Math.max(0, maxRetries); attempt++) {
            try {
                ChatRequestParameters params = ChatRequestParameters.builder()
                        .responseFormat(ResponseFormat.JSON)
                        .temperature(0.1)
                        .build();

                ChatRequest chatRequest = ChatRequest.builder()
                        .messages(
                                effectiveSystemPrompt != null && !effectiveSystemPrompt.isBlank()
                                        ? List.of(SystemMessage.from(effectiveSystemPrompt), UserMessage.from(currentUserPrompt))
                                        : List.of(UserMessage.from(currentUserPrompt))
                        )
                        .parameters(params)
                        .build();

                ChatResponse response = chatModel().chat(chatRequest);
                String text = response.aiMessage() != null && response.aiMessage().text() != null
                        ? response.aiMessage().text() : "";

                if (tokenUsageTracker != null) {
                    int inTokens = (response.tokenUsage() != null && response.tokenUsage().inputTokenCount() != null)
                            ? response.tokenUsage().inputTokenCount()
                            : ((effectiveSystemPrompt != null ? effectiveSystemPrompt.length() : 0) + currentUserPrompt.length()) / 4;
                    int outTokens = (response.tokenUsage() != null && response.tokenUsage().outputTokenCount() != null)
                            ? response.tokenUsage().outputTokenCount()
                            : text.length() / 4;
                    tokenUsageTracker.record(com.spectrayan.spector.synapse.provider.usage.TokenUsageEvent.ofGeneration(
                            com.spectrayan.spector.synapse.provider.usage.TokenUsageCategory.SYSTEM,
                            "default",
                            modelName(),
                            null,
                            null,
                            Math.max(1, inTokens),
                            Math.max(1, outTokens)
                    ));
                }

                StructuredOutputParser.parseJsonNode(text);
                return StructuredOutputParser.extractJsonString(text);
            } catch (Exception e) {
                lastException = e;
                log.warn("[LlmBridge] Structured output parsing failed (attempt {} of {}): {}",
                        attempt + 1, maxRetries + 1, e.getMessage());

                if (attempt < maxRetries) {
                    currentUserPrompt = userMessage + "\n\n"
                            + "[CORRECTION REQUIRED]: Your previous response could not be parsed as valid JSON: "
                            + e.getMessage() + "\n"
                            + "Please provide strictly valid JSON conforming to the schema.";
                }
            }
        }

        throw new StructuredOutputException(
                "Structured generation failed after " + (maxRetries + 1) + " attempts: "
                        + (lastException != null ? lastException.getMessage() : "Unknown error"),
                null, jsonSchema, lastException);
    }

    private String buildStructuredSystemPrompt(String systemPrompt, String jsonSchema) {
        StringBuilder sb = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            sb.append(systemPrompt).append("\n\n");
        }
        sb.append("You MUST respond ONLY with valid JSON conforming to the following JSON schema:\n");
        if (jsonSchema != null && !jsonSchema.isBlank()) {
            sb.append(jsonSchema).append("\n");
        }
        sb.append("Do NOT wrap in explanatory markdown or conversational preambles.");
        return sb.toString();
    }

    /** Get the configured model name. */
    public String modelName() {
        return props.getProvider().getGeneration().model();
    }

    /** Get the configured base URL. */
    public String baseUrl() {
        return props.getProvider().getGeneration().baseUrl();
    }
}
