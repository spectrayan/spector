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

import com.spectrayan.spector.embed.GenerationOptions;
import com.spectrayan.spector.embed.TextGenerationProvider;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.data.message.UserMessage;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * Text generation provider backed by a local Ollama server, utilizing LangChain4j.
 */
public class OllamaLlmProvider implements TextGenerationProvider {

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
    public String generate(String prompt) {
        return generate(prompt, GenerationOptions.DEFAULT);
    }

    @Override
    public String generate(String prompt, GenerationOptions options) {
        if (prompt == null || prompt.isBlank()) {
            throw new GenerationException("prompt must not be null or blank");
        }

        try {
            llmGate.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationException("Interrupted waiting for LLM gate", e);
        }

        try {
            if (options == null) {
                return delegate.chat(prompt);
            }

            var paramsBuilder = ChatRequestParameters.builder();
            paramsBuilder.temperature((double) options.temperature());
            if (options.maxTokens() > 0) {
                paramsBuilder.maxOutputTokens(options.maxTokens());
            }
            paramsBuilder.topP((double) options.topP());
            if (options.stopSequences() != null && options.stopSequences().length > 0) {
                paramsBuilder.stopSequences(List.of(options.stopSequences()));
            }

            var chatRequest = ChatRequest.builder()
                    .messages(List.of(UserMessage.from(prompt)))
                    .parameters(paramsBuilder.build())
                    .build();

            var response = delegate.chat(chatRequest);
            return response.aiMessage().text();
        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("ConnectException") || e.getMessage().contains("UnknownHostException") || e.getMessage().contains("Connection refused"))) {
                throw new GenerationException("Ollama server unavailable at " + baseUrl, e);
            }
            throw new GenerationException("Ollama generation failed: " + e.getMessage(), e);
        } finally {
            llmGate.release();
        }
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
