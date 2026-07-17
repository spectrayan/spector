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

import com.spectrayan.spector.embed.GenerationOptions;
import com.spectrayan.spector.embed.TextGenerationProvider;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.data.message.UserMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Adapts a LangChain4j {@link ChatModel} to Spector's {@link TextGenerationProvider} SPI.
 *
 * <p>Spector uses text generation for sleep consolidation (ReflectDaemon),
 * entity extraction, and tag extraction. This adapter bridges LangChain4j's
 * chat model API to these use cases.</p>
 *
 * <p><strong>Per-request options:</strong> Unlike the enterprise version, this adapter
 * properly forwards {@link GenerationOptions} (temperature, maxTokens, topP, stopSequences)
 * to LangChain4j's {@link ChatRequestParameters} on each request, allowing runtime
 * override of model defaults.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Thread-safe if the underlying {@link ChatModel} is thread-safe
 * (all LangChain4j models are documented as thread-safe).</p>
 */
public class LangChain4jGenerationAdapter implements TextGenerationProvider {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jGenerationAdapter.class);

    private final ChatModel delegate;
    private final String modelName;

    /**
     * Creates an adapter wrapping a LangChain4j chat model.
     *
     * @param delegate  the LangChain4j chat model
     * @param modelName model identifier for logging
     */
    public LangChain4jGenerationAdapter(ChatModel delegate, String modelName) {
        this.delegate = Objects.requireNonNull(delegate, "ChatModel must not be null");
        this.modelName = Objects.requireNonNull(modelName, "modelName must not be null");
    }

    @Override
    public String generate(String prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        return delegate.chat(prompt);
    }

    @Override
    public String generate(String prompt, GenerationOptions options) {
        Objects.requireNonNull(prompt, "prompt must not be null");

        if (options == null) {
            return generate(prompt);
        }

        // Build per-request parameters from GenerationOptions
        var paramsBuilder = ChatRequestParameters.builder();
        paramsBuilder.temperature(options.temperature());

        if (options.maxTokens() > 0) {
            paramsBuilder.maxOutputTokens(options.maxTokens());
        }
        paramsBuilder.topP(options.topP());

        if (options.stopSequences() != null && options.stopSequences().length > 0) {
            paramsBuilder.stopSequences(List.of(options.stopSequences()));
        }

        var chatRequest = ChatRequest.builder()
                .messages(List.of(UserMessage.from(prompt)))
                .parameters(paramsBuilder.build())
                .build();

        var response = delegate.chat(chatRequest);
        return response.aiMessage().text();
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

    /** Returns the underlying LangChain4j model. */
    public ChatModel delegate() {
        return delegate;
    }
}
