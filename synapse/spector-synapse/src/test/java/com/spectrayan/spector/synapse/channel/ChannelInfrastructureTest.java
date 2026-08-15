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
package com.spectrayan.spector.synapse.channel;

import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.AgentChatResponse;
import com.spectrayan.spector.synapse.agent.chat.service.ChatService;
import com.spectrayan.spector.synapse.channel.model.ChannelType;
import com.spectrayan.spector.synapse.channel.model.UnifiedMessage;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests for UnifiedMessage, ChannelAdapter, and ChannelRouter.
 */
class ChannelInfrastructureTest {

    // ── UnifiedMessage ──────────────────────────────────────────────────

    @Test
    void textFactoryCreatesValidMessage() {
        var msg = UnifiedMessage.text("whatsapp", "+1234567890", "Hello!");
        assertThat(msg.channel()).isEqualTo("whatsapp");
        assertThat(msg.senderId()).isEqualTo("+1234567890");
        assertThat(msg.content()).isEqualTo("Hello!");
        assertThat(msg.id()).isNotNull();
        assertThat(msg.timestamp()).isNotNull();
        assertThat(msg.attachments()).isEmpty();
        assertThat(msg.metadata()).isEmpty();
    }

    @Test
    void replyFactoryLinksToOriginal() {
        var original = UnifiedMessage.text("telegram", "user123", "What's the weather?");
        var reply = UnifiedMessage.reply(original, "It's sunny!");
        assertThat(reply.replyToId()).isEqualTo(original.id());
        assertThat(reply.channel()).isEqualTo("telegram");
        assertThat(reply.content()).isEqualTo("It's sunny!");
    }

    @Test
    void attachmentsSupported() {
        var msg = new UnifiedMessage("id1", "slack", "U123", "Bob", "Check this",
                null, "thread1", null,
                List.of(UnifiedMessage.Attachment.image("https://example.com/img.jpg", "image/jpeg")),
                Map.of("slack_ts", "1234567890.123456"));
        assertThat(msg.attachments()).hasSize(1);
        assertThat(msg.attachments().getFirst().type()).isEqualTo(UnifiedMessage.AttachmentType.IMAGE);
        assertThat(msg.metadata()).containsKey("slack_ts");
    }

    // ── ChannelRouter ───────────────────────────────────────────────────

    @Test
    void routerRegistersAdapters() {
        var adapter = createTestAdapter("test", true);
        var router = new ChannelRouter(List.of(adapter));

        assertThat(router.size()).isEqualTo(1);
        assertThat(router.channelIds()).containsExactly("test");
        assertThat(router.adapter("test")).isPresent();
        assertThat(router.adapter(ChannelType.SLACK)).isEmpty();
    }

    @Test
    void routerFiltersEnabledAdapters() {
        var enabled = createTestAdapter("enabled", true);
        var disabled = createTestAdapter("disabled", false);
        var router = new ChannelRouter(List.of(enabled, disabled));

        assertThat(router.enabledAdapters()).hasSize(1);
        assertThat(router.enabledAdapters().getFirst().channelId()).isEqualTo("enabled");
    }

    @Test
    void routeInboundNormalizesMessage() {
        var adapter = createTestAdapter("test", true);
        var router = new ChannelRouter(List.of(adapter));

        var msg = router.routeInbound("test", "raw message");
        assertThat(msg.channel()).isEqualTo("test");
        assertThat(msg.content()).isEqualTo("raw message");
    }

    @Test
    void routeInboundThrowsForUnknownChannel() {
        var router = new ChannelRouter(List.of());
        assertThatThrownBy(() -> router.routeInbound("unknown", "msg"))
                .isInstanceOf(ChannelAdapter.ChannelException.class);
    }

    @Test
    void routeInboundThrowsForDisabledChannel() {
        var adapter = createTestAdapter("disabled", false);
        var router = new ChannelRouter(List.of(adapter));
        assertThatThrownBy(() -> router.routeInbound("disabled", "msg"))
                .isInstanceOf(ChannelAdapter.ChannelException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void healthStatusReportsAll() {
        var router = new ChannelRouter(List.of(
                createTestAdapter("a", true),
                createTestAdapter("b", false)));
        var health = router.healthStatus();
        assertThat(health).hasSize(2);
    }

    @Test
    void routeOutboundDispatchesToAdapter() {
        var sentMessages = new ArrayList<UnifiedMessage>();
        var adapter = new ChannelAdapter() {
            @Override public String channelId() { return "slack"; }
            @Override public String displayName() { return "Slack"; }
            @Override public boolean isEnabled() { return true; }
            @Override public UnifiedMessage normalize(Object nativeMessage) { return UnifiedMessage.text("slack", "u", nativeMessage.toString()); }
            @Override public void send(UnifiedMessage message) { sentMessages.add(message); }
        };

        var router = new ChannelRouter(List.of(adapter));
        var msg = UnifiedMessage.text("slack", "user-1", "Outbound test");
        router.routeOutbound(msg);

        assertThat(sentMessages).hasSize(1);
        assertThat(sentMessages.getFirst().content()).isEqualTo("Outbound test");
    }

    @Test
    void routeInboundAndProcessExecutesChatTurn() {
        ChatService mockChatService = Mockito.mock(ChatService.class);
        AgentChatResponse mockResponse = Mockito.mock(AgentChatResponse.class);
        when(mockResponse.response()).thenReturn("Agent reply text");
        when(mockChatService.executeChat(eq("User question"), any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(mockResponse);

        var sentMessages = new ArrayList<UnifiedMessage>();
        var adapter = new ChannelAdapter() {
            @Override public String channelId() { return "telegram"; }
            @Override public String displayName() { return "Telegram"; }
            @Override public boolean isEnabled() { return true; }
            @Override public UnifiedMessage normalize(Object nativeMessage) {
                return new UnifiedMessage("m1", "telegram", "user-42", "Alice", nativeMessage.toString(),
                        null, "chat-100", null, List.of(), Map.of());
            }
            @Override public void send(UnifiedMessage message) { sentMessages.add(message); }
        };

        var router = new ChannelRouter(List.of(adapter), mockChatService);
        UnifiedMessage reply = router.routeInboundAndProcess("telegram", "User question");

        assertThat(reply).isNotNull();
        assertThat(reply.content()).isEqualTo("Agent reply text");
        assertThat(reply.channel()).isEqualTo("telegram");
        assertThat(reply.replyToId()).isEqualTo("m1");
        assertThat(sentMessages).hasSize(1);
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private static ChannelAdapter createTestAdapter(String id, boolean enabled) {
        return new ChannelAdapter() {
            @Override public String channelId() { return id; }
            @Override public String displayName() { return id.toUpperCase(); }
            @Override public boolean isEnabled() { return enabled; }

            @Override
            public UnifiedMessage normalize(Object nativeMessage) {
                return UnifiedMessage.text(id, "test-sender", nativeMessage.toString());
            }

            @Override
            public void send(UnifiedMessage message) {
                // No-op for testing
            }
        };
    }
}
