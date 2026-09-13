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

import com.spectrayan.spector.provider.embedding.EmbeddingConfig;
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
 *
 * <p>Generation requests are serialized by a fair single-permit gate, so only one request runs at a
 * time per instance. No retries are configured on the underlying model.</p>
 */
public class OllamaLlmProvider implements LlmProvider {

    private final String model;
    private final String baseUrl;
    private final Duration timeout;
    private final OllamaChatModel delegate;
    private final LangChain4jGenerationAdapter adapter;
    private final Semaphore llmGate = new Semaphore(1, true);

    /**
     * Creates a provider for the given model and server.
     *
     * @param model   Ollama model name
     * @param baseUrl Ollama server base URL
     * @param timeout request timeout
     * @throws NullPointerException if any argument is {@code null}
     */
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

    /**
     * Creates a provider for the given model at {@code http://localhost:11434} with a 60 second timeout.
     *
     * @param model Ollama model name
     * @return a new provider
     */
    public static OllamaLlmProvider create(String model) {
        return new OllamaLlmProvider(
                model,
                EmbeddingConfig.OLLAMA_DEFAULT.baseUrl(),
                Duration.ofSeconds(60));
    }

    /**
     * Creates a provider for the given model and server with a 60 second timeout.
     *
     * @param model   Ollama model name
     * @param baseUrl Ollama server base URL
     * @return a new provider
     */
    public static OllamaLlmProvider create(String model, String baseUrl) {
        return new OllamaLlmProvider(
                model,
                baseUrl,
                Duration.ofSeconds(60));
    }

    /**
     * Creates a provider for {@code qwen3:0.6b} at {@code http://localhost:11434} with a 60 second timeout.
     *
     * @return a new provider
     */
    public static OllamaLlmProvider createDefault() {
        return new OllamaLlmProvider(
                "qwen3:0.6b",
                EmbeddingConfig.OLLAMA_DEFAULT.baseUrl(),
                Duration.ofSeconds(60));
    }

    /**
     * Generates a response for the given request.
     *
     * @param request the generation request; must contain at least one message and no blank text content
     * @param options generation options
     * @return the model response
     * @throws NullPointerException if {@code request} is {@code null}
     * @throws GenerationException  if the request is invalid, the thread is interrupted while waiting,
     *                              the server is unreachable, or generation fails
     */
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

    /**
     * Checks whether the Ollama server responds to {@code GET {baseUrl}/api/tags}.
     *
     * <p>Uses a 500 millisecond connect and request timeout.</p>
     *
     * @return {@code true} if the server returns a 2xx or 3xx status, {@code false} otherwise
     */
    @Override
    public boolean isAvailable() {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(500))
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofMillis(500))
                    .GET()
                    .build();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 400;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the underlying LangChain4j chat model.
     *
     * @return the underlying chat model
     */
    public dev.langchain4j.model.chat.ChatModel delegate() {
        return delegate;
    }
}
