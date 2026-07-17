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
package com.spectrayan.spector.provider.ollama;

import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.*;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * Text generation provider backed by a local Ollama server, utilizing LangChain4j.
 */
public class OllamaLlmProvider implements LlmProvider {

    private final String model;
    private final String baseUrl;
    private final Duration timeout;
    private final OllamaChatModel delegate;
    private final Semaphore llmGate = new Semaphore(1, true);

    public OllamaLlmProvider(String model, String baseUrl, Duration timeout) {
        this.model = Objects.requireNonNull(model, "model");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.delegate = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(model)
                .timeout(timeout)
                .build();
    }

    public static OllamaLlmProvider create(String model) {
        return new OllamaLlmProvider(model, "http://localhost:11434", Duration.ofSeconds(60));
    }

    public static OllamaLlmProvider create(String model, String baseUrl) {
        return new OllamaLlmProvider(model, baseUrl, Duration.ofSeconds(60));
    }

    public static OllamaLlmProvider createDefault() {
        return new OllamaLlmProvider("qwen3:0.6b", "http://localhost:11434", Duration.ofSeconds(60));
    }

    @Override
    public LlmResponse generate(LlmRequest request, GenerationOptions options) {
        Objects.requireNonNull(request, "request must not be null");
        var actualOptions = options != null ? options : GenerationOptions.DEFAULT;

        try {
            llmGate.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationException("Interrupted waiting for LLM gate", e);
        }

        try {
            // Build parameters from GenerationOptions
            var paramsBuilder = ChatRequestParameters.builder();
            paramsBuilder.temperature((double) actualOptions.temperature());

            if (actualOptions.maxTokens() > 0) {
                paramsBuilder.maxOutputTokens(actualOptions.maxTokens());
            }
            paramsBuilder.topP((double) actualOptions.topP());

            if (actualOptions.stopSequences() != null && actualOptions.stopSequences().length > 0) {
                paramsBuilder.stopSequences(List.of(actualOptions.stopSequences()));
            }

            if (request.responseJsonSchema() != null && !request.responseJsonSchema().isBlank()) {
                paramsBuilder.responseFormat(ResponseFormat.JSON);
            }

            // Map Spector messages to LangChain4j messages
            List<dev.langchain4j.data.message.ChatMessage> langChainMessages = request.messages().stream()
                    .map(this::mapMessage)
                    .filter(Objects::nonNull)
                    .toList();

            var chatRequest = ChatRequest.builder()
                    .messages(langChainMessages)
                    .parameters(paramsBuilder.build())
                    .build();

            var response = delegate.chat(chatRequest);

            int inputTokens = 0;
            int outputTokens = 0;
            if (response.tokenUsage() != null) {
                inputTokens = response.tokenUsage().inputTokenCount() != null ? response.tokenUsage().inputTokenCount() : 0;
                outputTokens = response.tokenUsage().outputTokenCount() != null ? response.tokenUsage().outputTokenCount() : 0;
            }

            return new LlmResponse(response.aiMessage().text(), inputTokens, outputTokens, model);
        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("ConnectException") || e.getMessage().contains("UnknownHostException") || e.getMessage().contains("Connection refused"))) {
                throw new GenerationException("Ollama server unavailable at " + baseUrl, e);
            }
            throw new GenerationException("Ollama generation failed: " + e.getMessage(), e);
        } finally {
            llmGate.release();
        }
    }

    private dev.langchain4j.data.message.ChatMessage mapMessage(ChatMessage message) {
        switch (message.role()) {
            case SYSTEM:
                return dev.langchain4j.data.message.SystemMessage.from(message.text());
            case AI:
                return dev.langchain4j.data.message.AiMessage.from(message.text());
            case TOOL:
                return dev.langchain4j.data.message.UserMessage.from(message.text());
            case USER:
            default:
                List<dev.langchain4j.data.message.Content> contents = message.content().stream()
                        .map(this::mapContentBlock)
                        .filter(Objects::nonNull)
                        .toList();
                return dev.langchain4j.data.message.UserMessage.from(contents);
        }
    }

    private dev.langchain4j.data.message.Content mapContentBlock(ContentBlock block) {
        if (block instanceof TextContent txt) {
            return dev.langchain4j.data.message.TextContent.from(txt.text());
        } else if (block instanceof ImageContent img) {
            if (img.hasData()) {
                return dev.langchain4j.data.message.ImageContent.from(
                        java.util.Base64.getEncoder().encodeToString(img.data()),
                        img.mimeType()
                );
            } else if (img.hasUrl()) {
                return dev.langchain4j.data.message.ImageContent.from(img.url());
            }
        }
        return null;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public boolean isAvailable() {
        try {
            delegate.chat("ping");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
