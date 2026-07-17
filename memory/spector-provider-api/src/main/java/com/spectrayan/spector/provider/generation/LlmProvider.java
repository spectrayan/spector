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
package com.spectrayan.spector.provider.generation;

import com.spectrayan.spector.provider.model.ChatMessage;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;

import java.util.List;

/**
 * Unified SPI interface for multimodal LLM generation.
 */
public interface LlmProvider {

    /**
     * Executes a multimodal chat generation request.
     *
     * @param request the structured multimodal payload
     * @param options generation parameters
     * @return rich generation output response
     */
    LlmResponse generate(LlmRequest request, GenerationOptions options);

    /**
     * Executes a generation request with default options.
     *
     * @param request the structured multimodal payload
     * @return rich generation output response
     */
    default LlmResponse generate(LlmRequest request) {
        return generate(request, GenerationOptions.DEFAULT);
    }

    /**
     * Simple backward-compatible text-to-text generation path.
     *
     * @param prompt simple string prompt
     * @return generated text response
     */
    default String generate(String prompt) {
        var request = new LlmRequest(List.of(ChatMessage.user(prompt)), null);
        var response = generate(request, GenerationOptions.DEFAULT);
        return response.text();
    }

    /**
     * Simple backward-compatible text-to-text generation path with options.
     *
     * @param prompt  simple string prompt
     * @param options generation options
     * @return generated text response
     */
    default String generate(String prompt, GenerationOptions options) {
        var request = new LlmRequest(List.of(ChatMessage.user(prompt)), null);
        var response = generate(request, options);
        return response.text();
    }

    /**
     * Custom exception thrown when text generation fails.
     */
    class GenerationException extends RuntimeException {
        public GenerationException(String message) {
            super(message);
        }

        public GenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Returns the unique model identifier.
     */
    String modelName();

    /**
     * Connectivity/availability check for the provider.
     */
    boolean isAvailable();
}
