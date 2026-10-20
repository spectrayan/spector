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
package com.spectrayan.spector.synapse.platform.events.api;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import com.spectrayan.sse.server.emitter.SseEmitter;
import com.spectrayan.spector.synapse.platform.events.SseEventConstants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@DisplayName("Empirical Verification: EventController SSE Topics & Filter Streams")
class EventControllerEmpiricalTest {

    private SseEmitter sseEmitter;
    private EventController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        sseEmitter = mock(SseEmitter.class);
        when(sseEmitter.connect(anyString())).thenAnswer(invocation -> {
            String topic = invocation.getArgument(0);
            return Flux.just(ServerSentEvent.builder().event(topic + ".event").data("data-for-" + topic).build());
        });

        controller = new EventController(sseEmitter);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Default topic is spector and merges both spector and cortex streams for dual compatibility")
    void defaultTopicMergesSpectorAndCortex() {
        Flux<ServerSentEvent<Object>> flux = controller.events("spector", "");
        List<ServerSentEvent<Object>> events = flux.collectList().block(Duration.ofSeconds(2));

        assertThat(events).hasSize(2);
        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactlyInAnyOrder("spector.event", "cortex.event");

        verify(sseEmitter).connect("spector");
        verify(sseEmitter).connect("cortex");
        verifyNoMoreInteractions(sseEmitter);
    }

    @Test
    @DisplayName("Legacy cortex topic request merges both spector and cortex streams for dual compatibility")
    void legacyCortexTopicMergesSpectorAndCortex() {
        Flux<ServerSentEvent<Object>> flux = controller.events("cortex", "");
        List<ServerSentEvent<Object>> events = flux.collectList().block(Duration.ofSeconds(2));

        assertThat(events).hasSize(2);
        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactlyInAnyOrder("spector.event", "cortex.event");

        verify(sseEmitter).connect("spector");
        verify(sseEmitter).connect("cortex");
        verifyNoMoreInteractions(sseEmitter);
    }

    @Test
    @DisplayName("Case-insensitive topic matching for SPECTOR and CORTEX aliases")
    void caseInsensitiveTopicMatching() {
        Flux<ServerSentEvent<Object>> fluxUpperSpector = controller.events("SPECTOR", "");
        List<ServerSentEvent<Object>> spectorEvents = fluxUpperSpector.collectList().block(Duration.ofSeconds(2));
        assertThat(spectorEvents).hasSize(2);

        reset(sseEmitter);
        when(sseEmitter.connect(anyString())).thenAnswer(invocation -> {
            String topic = invocation.getArgument(0);
            return Flux.just(ServerSentEvent.builder().event(topic + ".event").data("data-for-" + topic).build());
        });

        Flux<ServerSentEvent<Object>> fluxUpperCortex = controller.events("CORTEX", "");
        List<ServerSentEvent<Object>> cortexEvents = fluxUpperCortex.collectList().block(Duration.ofSeconds(2));
        assertThat(cortexEvents).hasSize(2);
    }

    @Test
    @DisplayName("Null and blank topic defaults to spector and merges spector and cortex streams")
    void nullAndBlankTopicDefaultsToSpector() {
        Flux<ServerSentEvent<Object>> fluxNull = controller.events(null, null);
        List<ServerSentEvent<Object>> nullEvents = fluxNull.collectList().block(Duration.ofSeconds(2));
        assertThat(nullEvents).hasSize(2);

        Flux<ServerSentEvent<Object>> fluxBlank = controller.events("   ", "   ");
        List<ServerSentEvent<Object>> blankEvents = fluxBlank.collectList().block(Duration.ofSeconds(2));
        assertThat(blankEvents).hasSize(2);
    }

    @Test
    @DisplayName("Dedicated non-default topics (e.g. agent, chat) connect only to requested topic")
    void dedicatedTopicsConnectDirectly() {
        Flux<ServerSentEvent<Object>> fluxAgent = controller.events(SseEventConstants.TOPIC_AGENT, "");
        List<ServerSentEvent<Object>> agentEvents = fluxAgent.collectList().block(Duration.ofSeconds(2));

        assertThat(agentEvents).hasSize(1);
        assertThat(agentEvents.get(0).event()).isEqualTo("agent.event");
        verify(sseEmitter).connect("agent");

        reset(sseEmitter);
        when(sseEmitter.connect(anyString())).thenAnswer(invocation -> {
            String topic = invocation.getArgument(0);
            return Flux.just(ServerSentEvent.builder().event(topic + ".event").data("data-for-" + topic).build());
        });

        Flux<ServerSentEvent<Object>> fluxChat = controller.events(SseEventConstants.TOPIC_CHAT, null);
        List<ServerSentEvent<Object>> chatEvents = fluxChat.collectList().block(Duration.ofSeconds(2));
        assertThat(chatEvents).hasSize(1);
        assertThat(chatEvents.get(0).event()).isEqualTo("chat.event");
        verify(sseEmitter).connect("chat");
    }

    @Test
    @DisplayName("Filter stream parses comma-separated topics and merges all target streams")
    void filterStreamMergesCommaSeparatedTopics() {
        Flux<ServerSentEvent<Object>> flux = controller.events("spector", "spector,cortex,ingestion");
        List<ServerSentEvent<Object>> events = flux.collectList().block(Duration.ofSeconds(2));

        assertThat(events).hasSize(3);
        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactlyInAnyOrder("spector.event", "cortex.event", "ingestion.event");

        verify(sseEmitter).connect("spector");
        verify(sseEmitter).connect("cortex");
        verify(sseEmitter).connect("ingestion");
        verifyNoMoreInteractions(sseEmitter);
    }

    @Test
    @DisplayName("Filter stream handles leading, trailing, and redundant commas/whitespace cleanly")
    void filterStreamSanitizesDirtyTokens() {
        Flux<ServerSentEvent<Object>> flux = controller.events("spector", "  , spector  ,  ,, cortex  ,   ingestion,  ");
        List<ServerSentEvent<Object>> events = flux.collectList().block(Duration.ofSeconds(2));

        assertThat(events).hasSize(3);
        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactlyInAnyOrder("spector.event", "cortex.event", "ingestion.event");

        verify(sseEmitter).connect("spector");
        verify(sseEmitter).connect("cortex");
        verify(sseEmitter).connect("ingestion");
    }

    @Test
    @DisplayName("Filter stream takes precedence over topic parameter")
    void filterStreamPrecedesTopicParam() {
        Flux<ServerSentEvent<Object>> flux = controller.events("ignored-topic", "spector,cortex");
        List<ServerSentEvent<Object>> events = flux.collectList().block(Duration.ofSeconds(2));

        assertThat(events).hasSize(2);
        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactlyInAnyOrder("spector.event", "cortex.event");

        verify(sseEmitter, times(0)).connect("ignored-topic");
        verify(sseEmitter).connect("spector");
        verify(sseEmitter).connect("cortex");
    }

    @Test
    @DisplayName("MockMvc: GET /api/v1/events initiates async text/event-stream response")
    void mockMvcEndpointInitiatesSseStream() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/v1/events"))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(mvcResult.getResponse().getContentType()).contains(MediaType.TEXT_EVENT_STREAM_VALUE);
        verify(sseEmitter).connect("spector");
        verify(sseEmitter).connect("cortex");
    }

    @Test
    @DisplayName("MockMvc: GET /api/v1/events with filter query parameter dispatches target streams")
    void mockMvcEndpointWithFilter() throws Exception {
        mockMvc.perform(get("/api/v1/events").param("filter", "spector,cortex,ingestion"))
                .andExpect(request().asyncStarted());

        verify(sseEmitter).connect("spector");
        verify(sseEmitter).connect("cortex");
        verify(sseEmitter).connect("ingestion");
    }
}
