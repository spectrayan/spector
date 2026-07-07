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
package com.spectrayan.spector.synapse.agent.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for priming the LLM context with relevant cognitive memories.
 *
 * <p>This is the Spector differentiator — before each LLM call, the context
 * priming service recalls semantically relevant memories from past conversations
 * and injects them into the system prompt. This gives the agent long-term
 * memory across sessions.</p>
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Load current session message history</li>
 *   <li>Recall cross-session memories from SpectorMemory (semantic similarity)</li>
 *   <li>Format as a context block injected into the system prompt</li>
 * </ol>
 */
@Service
public class ContextPrimingService {

    private static final Logger log = LoggerFactory.getLogger(ContextPrimingService.class);
    private final ChatMemoryPort chatMemoryPort;

    public ContextPrimingService(ChatMemoryPort chatMemoryPort) {
        this.chatMemoryPort = chatMemoryPort;
    }

    /**
     * Result of context priming — contains session history, cross-session
     * memories, and the formatted context block for system prompt injection.
     */
    public record PrimedContext(
            List<Map<String, Object>> sessionMessages,
            List<ChatMemoryPort.PrimedMemory> crossSessionMemories,
            String contextBlock
    ) {}

    /**
     * Primes the context for a chat turn.
     *
     * @param message   the user's current message (used for semantic recall)
     * @param sessionId the current session ID
     * @param depth     maximum number of cross-session memories to recall
     * @return primed context with session history and cognitive memories
     */
    public PrimedContext prime(String message, String sessionId, int depth) {
        var history = chatMemoryPort.loadSessionHistory(sessionId);
        var memories = chatMemoryPort.recallRelevantMemories(message, sessionId, depth);

        String block = formatBlock(memories);

        log.debug("[ContextPriming] Primed {} session messages, {} cross-session memories",
                history.size(), memories.size());

        return new PrimedContext(history, memories, block);
    }

    /**
     * Formats recalled memories as a context block for system prompt injection.
     */
    private String formatBlock(List<ChatMemoryPort.PrimedMemory> memories) {
        if (memories.isEmpty()) return "";

        var sb = new StringBuilder("\n--- RELEVANT MEMORIES ---\n");
        for (var m : memories) {
            sb.append(String.format("[%s | %s] %s%n", m.memoryType(), m.ageDescription(), m.text()));
        }
        sb.append("--- END MEMORIES ---\n");
        return sb.toString();
    }
}
