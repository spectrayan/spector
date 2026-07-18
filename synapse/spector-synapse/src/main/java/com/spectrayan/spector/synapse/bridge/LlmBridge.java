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
import com.spectrayan.spector.synapse.config.SynapseProperties;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.spectrayan.spector.provider.ProviderRegistry;
import com.spectrayan.spector.provider.langchain4j.LangChain4jGenerationAdapter;
import com.spectrayan.spector.provider.ollama.OllamaLlmProvider;

import java.time.Duration;
import java.util.List;
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
    private final ConcurrentHashMap<String, ChatModel> chatModels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StreamingChatModel> streamingModels = new ConcurrentHashMap<>();

    public LlmBridge(SynapseProperties props, ProviderRegistry providerRegistry) {
        this.props = props;
        this.providerRegistry = providerRegistry;
        log.info("[LlmBridge] Configured with ProviderRegistry and Ollama fallback at {} with default model {}",
                props.ollama().baseUrl(), props.ollama().model());
    }

    /**
     * Get the default synchronous chat model.
     */
    public ChatModel chatModel() {
        return chatModel(props.ollama().model());
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
        String resolvedModel = (modelName == null || modelName.isBlank()) ? props.ollama().model() : modelName;
        return chatModels.computeIfAbsent(resolvedModel, name -> {
            var model = OllamaChatModel.builder()
                    .baseUrl(props.ollama().baseUrl())
                    .modelName(name)
                    .timeout(Duration.ofSeconds(120))
                    .temperature(0.7)
                    .build();
            log.info("[LlmBridge] Initialized ChatModel for model: {}", name);
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
            var model = OllamaChatModel.builder()
                    .baseUrl(props.ollama().baseUrl())
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
        return streamingModel(props.ollama().model());
    }

    /**
     * Get the streaming chat model for a specific model name (lazy-initialized and cached).
     */
    public StreamingChatModel streamingModel(String modelName) {
        String resolvedModel = (modelName == null || modelName.isBlank()) ? props.ollama().model() : modelName;
        return streamingModels.computeIfAbsent(resolvedModel, name -> {
            var model = OllamaStreamingChatModel.builder()
                    .baseUrl(props.ollama().baseUrl())
                    .modelName(name)
                    .timeout(Duration.ofSeconds(120))
                    .temperature(0.7)
                    .build();
            log.info("[LlmBridge] Initialized StreamingChatModel for model: {}", name);
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

    /** Get the configured model name. */
    public String modelName() {
        return props.ollama().model();
    }

    /** Get the configured base URL. */
    public String baseUrl() {
        return props.ollama().baseUrl();
    }
}
