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
package com.spectrayan.spector.synapse.agent.chat.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.AssistantMessageView;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatSessionRecord;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatSessionSummary;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatTurnView;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.RenameSessionRequest;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ThinkingView;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.TokenUsageDto;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ToolCardView;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.UserMessageView;
import com.spectrayan.spector.synapse.agent.chat.service.ChatService;
import com.spectrayan.spector.synapse.agent.chat.service.ChatTranscriptPort;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
import com.spectrayan.spector.synapse.error.SynapseNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ChatController Session Endpoints Slice Tests")
class ChatControllerSessionTest {

    private MockMvc mockMvc;
    private ChatService chatService;
    private CognitiveSoulService soulService;
    private ChatTranscriptPort transcriptPort;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        soulService = mock(CognitiveSoulService.class);
        transcriptPort = mock(ChatTranscriptPort.class);
        objectMapper = new ObjectMapper();

        ChatController controller = new ChatController(chatService, soulService, transcriptPort);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.spectrayan.spector.synapse.config.GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/chat/sessions returns session summaries and hasMore flag")
    void testListSessions() throws Exception {
        var summary = new ChatSessionSummary("sess-001", "Relocation planning", Instant.parse("2026-09-19T06:00:00Z"), 4);
        when(transcriptPort.listSessions(10)).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/chat/sessions?limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].sessionId").value("sess-001"))
                .andExpect(jsonPath("$.sessions[0].preview").value("Relocation planning"))
                .andExpect(jsonPath("$.sessions[0].messageCount").value(4))
                .andExpect(jsonPath("$.hasMore").value(false));

        verify(transcriptPort).listSessions(10);
    }

    @Test
    @DisplayName("PATCH /api/v1/chat/sessions/{id} successfully renames session")
    void testRenameSessionSuccess() throws Exception {
        var request = new RenameSessionRequest("Austin Apartment Search");
        Instant now = Instant.parse("2026-09-19T06:30:00Z");
        var existing = new ChatSessionRecord("sess-001", "Old Title", "ACTIVE", false, now, now);
        var updated = new ChatSessionRecord("sess-001", "Austin Apartment Search", "ACTIVE", false, now, now);

        when(transcriptPort.getSession("sess-001")).thenReturn(Optional.of(existing)).thenReturn(Optional.of(updated));
        doNothing().when(transcriptPort).renameSession("sess-001", "Austin Apartment Search");
        when(transcriptPort.countTurns("sess-001")).thenReturn(4);

        mockMvc.perform(patch("/api/v1/chat/sessions/sess-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("sess-001"))
                .andExpect(jsonPath("$.preview").value("Austin Apartment Search"))
                .andExpect(jsonPath("$.messageCount").value(4));

        verify(transcriptPort).renameSession("sess-001", "Austin Apartment Search");
    }

    @Test
    @DisplayName("PATCH /api/v1/chat/sessions/{id} returns 404 when session does not exist")
    void testRenameSessionNotFound() throws Exception {
        var request = new RenameSessionRequest("Non-existent Session");
        when(transcriptPort.getSession("missing-id")).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/v1/chat/sessions/missing-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /api/v1/chat/sessions/{id} rejects blank title with 400 Bad Request")
    void testRenameSessionBlankTitle() throws Exception {
        String payload = "{\"title\": \"   \"}";

        mockMvc.perform(patch("/api/v1/chat/sessions/sess-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transcriptPort);
    }

    @Test
    @DisplayName("DELETE /api/v1/chat/sessions/{id} deletes operational records and returns 204 No Content")
    void testDeleteSessionSuccess() throws Exception {
        doNothing().when(transcriptPort).deleteSession("sess-001");

        mockMvc.perform(delete("/api/v1/chat/sessions/sess-001"))
                .andExpect(status().isNoContent());

        verify(transcriptPort).deleteSession("sess-001");
    }

    @Test
    @DisplayName("GET /api/v1/chat/sessions/{id}/messages returns structured turns")
    void testLoadSessionHistory() throws Exception {
        var toolCard = new ToolCardView("call-1", "memory_recall", Map.of("query", "Austin"), "success", "[{\"text\":\"match\"}]", false, 45L);
        var turn = new ChatTurnView(
                "turn-101",
                0,
                "DONE",
                new UserMessageView("Tell me about Austin"),
                new ThinkingView("Searching memory for Austin...", 250L),
                List.of(toolCard),
                new AssistantMessageView("Austin is great."),
                TokenUsageDto.of(150, 45),
                1
        );
        var session = new ChatSessionRecord("sess-001", "Austin Discussion", "ACTIVE", false, Instant.now(), Instant.now());

        when(transcriptPort.getSession("sess-001")).thenReturn(Optional.of(session));
        when(transcriptPort.loadTurns("sess-001")).thenReturn(List.of(turn));

        mockMvc.perform(get("/api/v1/chat/sessions/sess-001/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("sess-001"))
                .andExpect(jsonPath("$.title").value("Austin Discussion"))
                .andExpect(jsonPath("$.turns[0].turnId").value("turn-101"))
                .andExpect(jsonPath("$.turns[0].status").value("DONE"))
                .andExpect(jsonPath("$.turns[0].user.text").value("Tell me about Austin"))
                .andExpect(jsonPath("$.turns[0].thinking.text").value("Searching memory for Austin..."))
                .andExpect(jsonPath("$.turns[0].thinking.elapsedMs").value(250))
                .andExpect(jsonPath("$.turns[0].tools[0].name").value("memory_recall"))
                .andExpect(jsonPath("$.turns[0].tools[0].status").value("success"))
                .andExpect(jsonPath("$.turns[0].assistant.text").value("Austin is great."))
                .andExpect(jsonPath("$.turns[0].usage.totalTokens").value(195))
                .andExpect(jsonPath("$.turns[0].primedMemories").value(1));

        verify(transcriptPort).loadTurns("sess-001");
    }

    @Test
    @DisplayName("GET /api/v1/chat/sessions/{id}/messages returns 404 when session not found")
    void testLoadSessionHistoryNotFound() throws Exception {
        when(transcriptPort.getSession("missing-id")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/chat/sessions/missing-id/messages"))
                .andExpect(status().isNotFound());
    }
}
