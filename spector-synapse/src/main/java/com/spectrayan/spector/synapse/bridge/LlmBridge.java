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
    private volatile ChatModel chatModel;
    private volatile StreamingChatModel streamingModel;

    public LlmBridge(SynapseProperties props) {
        this.props = props;
        log.info("[LlmBridge] Configured for Ollama at {} with model {}",
                props.ollama().baseUrl(), props.ollama().model());
    }

    /**
     * Get the synchronous chat model (lazy-initialized).
     */
    public ChatModel chatModel() {
        if (chatModel == null) {
            synchronized (this) {
                if (chatModel == null) {
                    chatModel = OllamaChatModel.builder()
                            .baseUrl(props.ollama().baseUrl())
                            .modelName(props.ollama().model())
                            .timeout(Duration.ofSeconds(120))
                            .temperature(0.7)
                            .build();
                    log.info("[LlmBridge] Initialized ChatModel: {}", props.ollama().model());
                }
            }
        }
        return chatModel;
    }

    /**
     * Get the streaming chat model (lazy-initialized).
     */
    public StreamingChatModel streamingModel() {
        if (streamingModel == null) {
            synchronized (this) {
                if (streamingModel == null) {
                    streamingModel = OllamaStreamingChatModel.builder()
                            .baseUrl(props.ollama().baseUrl())
                            .modelName(props.ollama().model())
                            .timeout(Duration.ofSeconds(120))
                            .temperature(0.7)
                            .build();
                    log.info("[LlmBridge] Initialized StreamingChatModel: {}", props.ollama().model());
                }
            }
        }
        return streamingModel;
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
