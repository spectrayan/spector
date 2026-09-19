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

import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatSessionRecord;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatSessionSummary;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatTurnView;

import java.util.List;
import java.util.Optional;

/**
 * Port interface for the operational chat execution plane (ADR-0084).
 *
 * <p>Handles transactional session management, immutable chronological event logging,
 * turn status tracking, and structured message history reconstruction.</p>
 */
public interface ChatTranscriptPort {

    /**
     * Creates a new chat session in the operational store.
     */
    ChatSessionRecord createSession(String sessionId, String title);

    /**
     * Lists active chat sessions with preview text and message counts.
     */
    List<ChatSessionSummary> listSessions(int limit);

    /**
     * Retrieves an operational session record by its unique identifier.
     */
    Optional<ChatSessionRecord> getSession(String sessionId);

    /**
     * Updates the user-visible title of a session.
     */
    void renameSession(String sessionId, String title);

    /**
     * Deletes an operational session and all associated turns, events, and checkpoints.
     * Note: Does NOT cascade to cognitive memories in Spector Memory.
     */
    void deleteSession(String sessionId);

    /**
     * Initializes a new turn execution record.
     */
    void startTurn(String turnId, String sessionId, int seq, String model, int primedCount);

    /**
     * Finalizes or updates turn status and usage metrics.
     */
    void updateTurnStatus(String turnId, String status, int inTokens, int outTokens, long latencyMs);

    /**
     * Appends an immutable operational event to the turn audit log.
     */
    void appendEvent(String eventId, String turnId, int seq, String type, String payloadJson);

    /**
     * Loads and reconstructs structured turns for a given session.
     */
    List<ChatTurnView> loadTurns(String sessionId);

    /**
     * Counts the total number of turns recorded for a session.
     */
    int countTurns(String sessionId);
}
