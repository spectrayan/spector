/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.ratelimit;

import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.AgentChatResponse;
import com.spectrayan.spector.synapse.agent.chat.service.ChatService;
import com.spectrayan.spector.synapse.channel.ChannelAdapter;
import com.spectrayan.spector.synapse.channel.ChannelRouter;
import com.spectrayan.spector.synapse.channel.model.ChannelType;
import com.spectrayan.spector.synapse.channel.model.UnifiedMessage;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ChannelRouterRateLimitTest {

    private ChannelAdapter slackAdapter;
    private ChatService chatService;
    private SynapseProperties properties;
    private RateLimitStateStore stateStore;
    private ChannelRouter channelRouter;

    @BeforeEach
    void setUp() {
        slackAdapter = Mockito.mock(ChannelAdapter.class);
        when(slackAdapter.channelId()).thenReturn("slack");
        when(slackAdapter.displayName()).thenReturn("Slack Adapter");
        when(slackAdapter.isEnabled()).thenReturn(true);
        when(slackAdapter.normalize(any())).thenAnswer(inv -> {
            Object raw = inv.getArgument(0);
            return UnifiedMessage.text("slack", "user-123", raw.toString());
        });

        chatService = Mockito.mock(ChatService.class);
        when(chatService.executeChat(anyString(), anyString(), any(), any(), anyInt(), any(), any()))
                .thenReturn(new AgentChatResponse(
                        "Agent answer",
                        "session-1",
                        false,
                        "mock-model",
                        "COMPLETED",
                        50L,
                        50L,
                        100,
                        List.of(),
                        List.of(),
                        List.of()
                ));

        properties = new SynapseProperties();
        properties.getRateLimit().getChannels().setEnabled(true);
        properties.getRateLimit().getChannels().setInboundUserBurst(2);
        properties.getRateLimit().getChannels().setInboundUserRpm(2);

        stateStore = new CaffeineRateLimitStateStore(Duration.ofMinutes(5), 1000);

        ObjectProvider<ChatService> chatProvider = mockObjectProvider(chatService);
        ObjectProvider<SynapseProperties> propsProvider = mockObjectProvider(properties);
        ObjectProvider<RateLimitStateStore> storeProvider = mockObjectProvider(stateStore);

        channelRouter = new ChannelRouter(List.of(slackAdapter), chatProvider, propsProvider, storeProvider);
    }

    @Test
    @DisplayName("Should process messages within burst and throttle subsequent messages from same sender")
    void testInboundChannelAntiFlood() {
        // Message 1 -> OK
        UnifiedMessage r1 = channelRouter.routeInboundAndProcess("slack", "Hello 1");
        assertThat(r1.content()).isEqualTo("Agent answer");

        // Message 2 -> OK
        UnifiedMessage r2 = channelRouter.routeInboundAndProcess("slack", "Hello 2");
        assertThat(r2.content()).isEqualTo("Agent answer");

        // Message 3 from same user -> Throttled!
        UnifiedMessage r3 = channelRouter.routeInboundAndProcess("slack", "Hello 3");
        assertThat(r3.content()).contains("Rate limit exceeded");

        // Verify ChatService was only called 2 times, skipping the 3rd throttled request
        verify(chatService, times(2)).executeChat(anyString(), anyString(), any(), any(), anyInt(), any(), any());
    }

    private <T> ObjectProvider<T> mockObjectProvider(T instance) {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(instance);
        when(provider.getObject()).thenReturn(instance);
        return provider;
    }
}
