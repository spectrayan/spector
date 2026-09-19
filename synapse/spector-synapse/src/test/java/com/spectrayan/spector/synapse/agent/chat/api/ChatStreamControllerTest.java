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
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.AgentChatRequest;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.TokenUsageDto;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatStreamEvent;
import com.spectrayan.spector.synapse.agent.chat.service.ChatService;
import com.spectrayan.spector.synapse.agent.chat.service.ChatService.ChatStreamSink;
import com.spectrayan.spector.synapse.agent.chat.service.ChatTranscriptPort;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
import com.spectrayan.spector.synapse.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ChatStreamController SSE Streaming Endpoint Slice Tests")
class ChatStreamControllerTest {

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
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/chat/stream returns 200 OK and initiates text/event-stream async processing")
    void testStreamChatSuccess() throws Exception {
        AgentSoul mockSoul = AgentSoul.builder()
                .id("soul-default")
                .name("Spector Default")
                .soulVersion((short) 1)
                .model("qwen-2.5")
                .build();
        when(soulService.getEffectiveSoul(null)).thenReturn(mockSoul);

        doAnswer(invocation -> {
            SseEmitter emitter = invocation.getArgument(2);
            emitter.send(SseEmitter.event()
                    .name("session")
                    .id("turn-1:0")
                    .data(new ChatStreamEvent.Session("sess-1", "turn-1", 0, System.currentTimeMillis(), true, "qwen-2.5"), MediaType.APPLICATION_JSON));
            emitter.send(SseEmitter.event()
                    .name("token")
                    .id("turn-1:1")
                    .data(new ChatStreamEvent.Token("sess-1", "turn-1", 1, System.currentTimeMillis(), "Hello world"), MediaType.APPLICATION_JSON));
            emitter.send(SseEmitter.event()
                    .name("done")
                    .id("turn-1:2")
                    .data(new ChatStreamEvent.Done("sess-1", "turn-1", 2, System.currentTimeMillis(), "Completed", 150L, 0, new TokenUsageDto(10, 5, 15)), MediaType.APPLICATION_JSON));
            emitter.complete();
            return null;
        }).when(chatService).streamChat(any(AgentChatRequest.class), eq(mockSoul), any(SseEmitter.class));

        AgentChatRequest request = new AgentChatRequest("Hello Spector", "sess-1");
        String requestJson = objectMapper.writeValueAsString(request);

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:session")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:token")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));

        verify(soulService).getEffectiveSoul(null);
        verify(chatService).streamChat(any(AgentChatRequest.class), eq(mockSoul), any(SseEmitter.class));
    }

    @Test
    @DisplayName("POST /api/v1/chat/stream rejects blank messages with 400 Bad Request")
    void testStreamChatRejectsBlankMessage() throws Exception {
        AgentChatRequest emptyRequest = new AgentChatRequest("", "sess-1");
        mockMvc.perform(post("/api/v1/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyRequest)))
                .andExpect(status().isBadRequest());

        AgentChatRequest whitespaceRequest = new AgentChatRequest("   ", "sess-1");
        mockMvc.perform(post("/api/v1/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(whitespaceRequest)))
                .andExpect(status().isBadRequest());

        AgentChatRequest nullRequest = new AgentChatRequest(null, "sess-1");
        mockMvc.perform(post("/api/v1/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }

    @Test
    @DisplayName("ChatStreamSink triggers abort action and writes INTERRUPTED status to CHAT_TURN on client disconnect")
    void testChatStreamSinkDisconnectAbort() {
        SseEmitter mockEmitter = mock(SseEmitter.class);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        ChatStreamSink sink = new ChatStreamSink(
                mockEmitter, "sess-abort-1", "turn-abort-1", transcriptPort, scheduler);

        AtomicBoolean abortActionCalled = new AtomicBoolean(false);
        sink.setAbortAction(() -> abortActionCalled.set(true));

        // Simulate client disconnect via triggerAbort()
        sink.triggerAbort();

        assertThat(sink.isAborted()).isTrue();
        assertThat(abortActionCalled.get()).isTrue();
        verify(transcriptPort).updateTurnStatus("turn-abort-1", "INTERRUPTED", 0, 0, 0L);

        scheduler.shutdownNow();
    }

    @Test
    @DisplayName("ChatStreamSink client disconnect during send triggers abort and sets INTERRUPTED")
    void testChatStreamSinkSendIOExceptionTriggersAbort() throws IOException {
        SseEmitter mockEmitter = mock(SseEmitter.class);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        org.mockito.Mockito.doThrow(new IOException("Broken pipe"))
                .when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));

        ChatStreamSink sink = new ChatStreamSink(
                mockEmitter, "sess-abort-2", "turn-abort-2", transcriptPort, scheduler);

        AtomicBoolean abortActionCalled = new AtomicBoolean(false);
        sink.setAbortAction(() -> abortActionCalled.set(true));

        ChatStreamEvent.Token tokenEvent = new ChatStreamEvent.Token(
                "sess-abort-2", "turn-abort-2", sink.nextSeq(), System.currentTimeMillis(), "Test token");

        sink.send(tokenEvent);

        assertThat(sink.isAborted()).isTrue();
        assertThat(abortActionCalled.get()).isTrue();
        verify(transcriptPort).updateTurnStatus("turn-abort-2", "INTERRUPTED", 0, 0, 0L);

        scheduler.shutdownNow();
    }

    @Test
    @DisplayName("ChatStreamSink keepalive heartbeat send comments")
    void testChatStreamSinkKeepaliveSend() {
        SseEmitter mockEmitter = mock(SseEmitter.class);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        ChatStreamSink sink = new ChatStreamSink(
                mockEmitter, "sess-keepalive", "turn-keepalive", transcriptPort, scheduler);

        sink.sendKeepalive();

        try {
            verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        sink.close();
        scheduler.shutdownNow();
    }
}
