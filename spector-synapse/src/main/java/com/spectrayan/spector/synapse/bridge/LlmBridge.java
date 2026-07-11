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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM bridge using LangChain4j to interface with Ollama.
 *
 * <p>Provides both synchronous and streaming chat models. The models
 * are configured from {@link SynapseProperties} and lazily initialized.</p>
 */
@Service
public class LlmBridge {

    private static final Logger log = LoggerFactory.getLogger(LlmBridge.class);

    private final SynapseProperties props;
    private final ConcurrentHashMap<String, ChatModel> chatModels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StreamingChatModel> streamingModels = new ConcurrentHashMap<>();

    public LlmBridge(SynapseProperties props) {
        this.props = props;
        log.info("[LlmBridge] Configured for Ollama at {} with default model {}",
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
     * Generate a simple chat response.
     */
    public String generate(String userMessage) {
        try {
            String response = chatModel().chat(userMessage);
            log.debug("[LlmBridge] Generated {} chars response", response.length());
            return response;
        } catch (Exception e) {
            log.error("[LlmBridge] Generation failed: {}", e.getMessage(), e);
            return "I'm currently unable to connect to the LLM. Error: " + e.getMessage();
        }
    }

    /**
     * Generate a chat response with system prompt.
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
            return "I'm currently unable to connect to the LLM. Error: " + e.getMessage();
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
