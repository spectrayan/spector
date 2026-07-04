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

import com.spectrayan.spector.synapse.chat.ChatDto.ChatRequest;
import com.spectrayan.spector.synapse.chat.ChatDto.ChatResponse;
import com.spectrayan.spector.synapse.chat.ChatDto.SessionDetail;
import com.spectrayan.spector.synapse.chat.ChatDto.SessionSummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Cognitive chat REST API.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/v1/chat} — Send a message, get a response</li>
 *   <li>{@code GET /api/v1/chat/sessions} — List sessions</li>
 *   <li>{@code GET /api/v1/chat/sessions/{id}} — Get session with messages</li>
 *   <li>{@code POST /api/v1/chat/sessions} — Create new session</li>
 *   <li>{@code DELETE /api/v1/chat/sessions/{id}} — Delete session</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** Send a chat message. */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        ChatResponse response = chatService.chat(request);
        return ResponseEntity.ok(response);
    }

    /** List all chat sessions. */
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionSummary>> listSessions() {
        return ResponseEntity.ok(chatService.listSessions());
    }

    /** Get a session with its messages. */
    @GetMapping("/sessions/{id}")
    public ResponseEntity<SessionDetail> getSession(@PathVariable String id) {
        return chatService.getSession(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Create a new chat session. */
    @PostMapping("/sessions")
    public ResponseEntity<Map<String, String>> createSession() {
        String sessionId = chatService.createSession();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "sessionId", sessionId,
                "message", "Session created"
        ));
    }

    /** Delete a chat session. */
    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable String id) {
        return chatService.deleteSession(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
