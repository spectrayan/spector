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
package com.spectrayan.spector.synapse.chat;

import java.time.Instant;
import java.util.List;

/**
 * Cognitive chat data transfer objects.
 */
public final class ChatDto {

    private ChatDto() {}

    /**
     * Chat message request.
     *
     * @param sessionId  chat session ID (null = create new)
     * @param message    user message content
     * @param agentId    optional agent soul ID (null = default assistant)
     */
    public record ChatRequest(
            String sessionId,
            String message,
            String agentId
    ) {}

    /**
     * Chat message response.
     *
     * @param sessionId  chat session ID
     * @param messageId  unique message ID
     * @param role       message role (user, assistant, system, tool)
     * @param content    message content
     * @param model      LLM model used
     * @param timestamp  message timestamp
     */
    public record ChatResponse(
            String sessionId,
            long messageId,
            String role,
            String content,
            String model,
            Instant timestamp
    ) {}

    /**
     * Chat session summary.
     */
    public record SessionSummary(
            String sessionId,
            String preview,
            String model,
            int messageCount,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /**
     * Chat message in history.
     */
    public record ChatMessage(
            long id,
            String sessionId,
            String role,
            String content,
            String model,
            Instant createdAt
    ) {}

    /**
     * Session detail with messages.
     */
    public record SessionDetail(
            String sessionId,
            String model,
            Instant createdAt,
            Instant updatedAt,
            List<ChatMessage> messages
    ) {}

    /**
     * SSE event for streaming responses.
     */
    public record StreamEvent(
            String sessionId,
            String type,     // "token", "done", "error"
            String content,
            Instant timestamp
    ) {
        public static StreamEvent token(String sessionId, String content) {
            return new StreamEvent(sessionId, "token", content, Instant.now());
        }

        public static StreamEvent done(String sessionId) {
            return new StreamEvent(sessionId, "done", "", Instant.now());
        }

        public static StreamEvent error(String sessionId, String message) {
            return new StreamEvent(sessionId, "error", message, Instant.now());
        }
    }
}
