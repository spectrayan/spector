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
package com.spectrayan.spector.provider.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a request payload to a multimodal LLM.
 */
public record LlmRequest(
        List<ChatMessage> messages,
        String responseJsonSchema
) {

    public LlmRequest {
        Objects.requireNonNull(messages, "messages must not be null");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages list must not be empty");
        }
    }

    public static LlmRequest fromPrompt(String prompt) {
        return new LlmRequest(List.of(ChatMessage.user(prompt)), null);
    }

    public static LlmRequest fromMessages(List<ChatMessage> messages) {
        return new LlmRequest(messages, null);
    }
}
