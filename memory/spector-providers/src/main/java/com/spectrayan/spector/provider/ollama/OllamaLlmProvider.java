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
import com.spectrayan.spector.provider.langchain4j.LangChain4jGenerationAdapter;
import com.spectrayan.spector.provider.model.*;

import dev.langchain4j.model.ollama.OllamaChatModel;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * Text generation provider backed by a local Ollama server, utilizing LangChain4j and reusing the core adapter.
 */
public class OllamaLlmProvider implements LlmProvider {

    private final String model;
    private final String baseUrl;
    private final Duration timeout;
    private final OllamaChatModel delegate;
    private final LangChain4jGenerationAdapter adapter;
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
        this.adapter = new LangChain4jGenerationAdapter(delegate, model);
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

        // Validate request's prompt or messages
        if (request.messages().isEmpty()) {
            throw new GenerationException("Request must contain at least one message");
        }

        // Check for null or blank text content in messages
        for (var msg : request.messages()) {
            if (msg.content() != null) {
                for (var content : msg.content()) {
                    if (content instanceof com.spectrayan.spector.provider.model.TextContent tc) {
                        if (tc.text() == null || tc.text().isBlank()) {
                            throw new GenerationException("Prompt text cannot be null or blank");
                        }
                    }
                }
            }
        }

        try {
            llmGate.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationException("Interrupted waiting for LLM gate", e);
        }

        try {
            return adapter.generate(request, options);
        } catch (GenerationException ge) {
            throw ge;
        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("ConnectException") || e.getMessage().contains("UnknownHostException") || e.getMessage().contains("Connection refused"))) {
                throw new GenerationException("Ollama server unavailable at " + baseUrl, e);
            }
            throw new GenerationException("Generation failed: " + e.getMessage(), e);
        } finally {
            llmGate.release();
        }
    }

    @Override
    public String generate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new GenerationException("Prompt text cannot be null or blank");
        }
        return LlmProvider.super.generate(prompt);
    }

    @Override
    public String generate(String prompt, GenerationOptions options) {
        if (prompt == null || prompt.isBlank()) {
            throw new GenerationException("Prompt text cannot be null or blank");
        }
        return LlmProvider.super.generate(prompt, options);
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public boolean isAvailable() {
        return adapter.isAvailable();
    }

    public dev.langchain4j.model.chat.ChatModel delegate() {
        return delegate;
    }
}
