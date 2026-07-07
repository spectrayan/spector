/*
 * Copyright 2025–2026 Spectrayan. Licensed under the Apache License, Version 2.0.
 */
package com.spectrayan.spector.synapse.channel;

import com.spectrayan.spector.synapse.channel.ChannelAdapter;
import com.spectrayan.spector.synapse.channel.ChannelRouter;
import com.spectrayan.spector.synapse.channel.model.UnifiedMessage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
