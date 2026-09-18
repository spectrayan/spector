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
package com.spectrayan.spector.synapse.provider.usage;

import java.time.Instant;
import java.util.Objects;

/**
 * Event capturing a discrete token consumption occurrence from LLM generation or embedding.
 *
 * @param timestamp       time when the event occurred
 * @param category        operational category (chat, embedding, etc.)
 * @param provider        provider identifier (e.g. ollama, openai, google)
 * @param model           model identifier (e.g. gemini-2.0-flash, llama3.3)
 * @param userId          user/tenant ID (optional, nullable)
 * @param sessionId       chat or conversation session ID (optional, nullable)
 * @param inputTokens     input/prompt tokens consumed
 * @param outputTokens    output/completion tokens generated
 * @param embeddingTokens embedding/vector tokens consumed
 */
public record TokenUsageEvent(
        Instant timestamp,
        TokenUsageCategory category,
        String provider,
        String model,
        String userId,
        String sessionId,
        long inputTokens,
        long outputTokens,
        long embeddingTokens
) {

    public TokenUsageEvent {
        timestamp = timestamp != null ? timestamp : Instant.now();
        category = category != null ? category : TokenUsageCategory.SYSTEM;
        provider = provider != null ? provider : "unknown";
        model = model != null ? model : "unknown";
        inputTokens = Math.max(0, inputTokens);
        outputTokens = Math.max(0, outputTokens);
        embeddingTokens = Math.max(0, embeddingTokens);
    }

    /**
     * Creates a generation token event.
     */
    public static TokenUsageEvent ofGeneration(TokenUsageCategory category, String provider, String model,
                                               String userId, String sessionId, long inputTokens, long outputTokens) {
        return new TokenUsageEvent(Instant.now(), category, provider, model, userId, sessionId, inputTokens, outputTokens, 0);
    }

    /**
     * Creates an embedding token event.
     */
    public static TokenUsageEvent ofEmbedding(String provider, String model, String userId, long embeddingTokens) {
        return new TokenUsageEvent(Instant.now(), TokenUsageCategory.EMBEDDING, provider, model, userId, null, 0, 0, embeddingTokens);
    }

    /**
     * Calculates the total tokens consumed across all dimensions in this event.
     *
     * @return sum of input, output, and embedding tokens
     */
    public long totalTokens() {
        return inputTokens + outputTokens + embeddingTokens;
    }
}
