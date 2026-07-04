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

import com.spectrayan.spector.synapse.agent.chat.model.Conversation;

import java.util.List;
import java.util.Map;

/**
 * Port for persisting and recalling chat-related information.
 *
 * <p>Separates chat logic from the actual persistence mechanism
 * (Spector Memory, JDBC, etc.). Implementations bridge to the
 * underlying storage layer.</p>
 */
public interface ChatMemoryPort {

    /**
     * Loads the message history for a given session.
     *
     * @param sessionId the session identifier
     * @return ordered list of messages (oldest first), each with "role" and "content"
     */
    List<Map<String, Object>> loadSessionHistory(String sessionId);

    /**
     * Saves a turn (user message + assistant response) to session memory.
     *
     * @param sessionId         the session identifier
     * @param userMessage       the user's message
     * @param assistantResponse the assistant's response
     * @param model             the LLM model used
     */
    void saveToSession(String sessionId, String userMessage, String assistantResponse, String model);

    /**
     * Lists recent conversations.
     *
     * @param limit maximum number of sessions to return
     * @return list of conversation summaries
     */
    List<Conversation> listSessions(int limit);

    /**
     * Recalls semantically relevant memories across all sessions.
     *
     * <p>This is the cognitive differentiator — the agent recalls relevant
     * context from past conversations to enrich the current turn.</p>
     *
     * @param query            the current user query for semantic matching
     * @param excludeSessionId session to exclude (current session)
     * @param limit            maximum memories to return
     * @return list of primed memories with relevance metadata
     */
    List<PrimedMemory> recallRelevantMemories(String query, String excludeSessionId, int limit);

    /** Record for a recalled memory. */
    record PrimedMemory(
            String text,
            String memoryType,
            String ageDescription,
            float score
    ) {}
}
