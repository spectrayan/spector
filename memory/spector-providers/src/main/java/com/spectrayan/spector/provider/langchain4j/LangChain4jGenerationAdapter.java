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
package com.spectrayan.spector.provider.langchain4j;

import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.*;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Adapts a LangChain4j {@link ChatModel} to Spector's {@link LlmProvider} SPI.
 */
public class LangChain4jGenerationAdapter implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jGenerationAdapter.class);

    private final ChatModel delegate;
    private final String modelName;

    public LangChain4jGenerationAdapter(ChatModel delegate, String modelName) {
        this.delegate = Objects.requireNonNull(delegate, "ChatModel must not be null");
        this.modelName = Objects.requireNonNull(modelName, "modelName must not be null");
    }

    @Override
    public LlmResponse generate(LlmRequest request, GenerationOptions options) {
        Objects.requireNonNull(request, "request must not be null");
        var actualOptions = options != null ? options : GenerationOptions.DEFAULT;

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

        // Support structured outputs
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

        return new LlmResponse(response.aiMessage().text(), inputTokens, outputTokens, modelName);
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
        return modelName;
    }

    @Override
    public boolean isAvailable() {
        try {
            delegate.chat("ping");
            return true;
        } catch (Exception e) {
            log.debug("LangChain4j model availability check failed: {}", e.getMessage());
            return false;
        }
    }

    public ChatModel delegate() {
        return delegate;
    }
}
