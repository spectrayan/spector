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
package com.spectrayan.spector.synapse.channel.adapters;

import com.spectrayan.spector.synapse.channel.model.UnifiedMessage;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for all channel adapters — Email, WhatsApp, Telegram, Discord, Slack.
 */
class ChannelAdaptersTest {

    @Test
    void emailNormalizesMapPayload() {
        var adapter = new EmailChannelAdapter();
        assertThat(adapter.channelId()).isEqualTo("email");
        assertThat(adapter.displayName()).isEqualTo("Email (SMTP/IMAP)");

        var msg = adapter.normalize(Map.of(
                "from", "user@example.com",
                "fromName", "Test User",
                "subject", "Hello",
                "body", "This is a test email"
        ));
        assertThat(msg.channel()).isEqualTo("email");
        assertThat(msg.senderId()).isEqualTo("user@example.com");
        assertThat(msg.content()).isEqualTo("This is a test email");
        assertThat(msg.metadata()).containsEntry("subject", "Hello");
    }

    @Test
    void emailNormalizesStringPayload() {
        var adapter = new EmailChannelAdapter();
        var msg = adapter.normalize("raw email text");
        assertThat(msg.content()).isEqualTo("raw email text");
    }

    @Test
    void whatsAppNormalizesMapPayload() {
        var adapter = new WhatsAppChannelAdapter();
        assertThat(adapter.channelId()).isEqualTo("whatsapp");

        var msg = adapter.normalize(Map.of(
                "from", "+14155552671",
                "profile_name", "John",
                "text", "Hello from WhatsApp",
                "type", "text"
        ));
        assertThat(msg.channel()).isEqualTo("whatsapp");
        assertThat(msg.senderId()).isEqualTo("+14155552671");
        assertThat(msg.senderName()).isEqualTo("John");
        assertThat(msg.content()).isEqualTo("Hello from WhatsApp");
        assertThat(msg.metadata()).containsEntry("message_type", "text");
    }

    @Test
    void whatsAppParsesImageAttachment() {
        var adapter = new WhatsAppChannelAdapter();
        var msg = adapter.normalize(Map.of(
                "from", "+14155552671",
                "text", "",
                "type", "image",
                "media_url", "https://wa.me/img.jpg"
        ));
        assertThat(msg.attachments()).hasSize(1);
        assertThat(msg.attachments().getFirst().type()).isEqualTo(UnifiedMessage.AttachmentType.IMAGE);
    }

    @Test
    void telegramNormalizesMapPayload() {
        var adapter = new TelegramChannelAdapter();
        assertThat(adapter.channelId()).isEqualTo("telegram");

        var msg = adapter.normalize(Map.of(
                "message_id", "12345",
                "chat_id", "67890",
                "first_name", "Alice",
                "text", "Hello from Telegram",
                "chat_type", "private"
        ));
        assertThat(msg.channel()).isEqualTo("telegram");
        assertThat(msg.senderId()).isEqualTo("67890");
        assertThat(msg.senderName()).isEqualTo("Alice");
        assertThat(msg.content()).isEqualTo("Hello from Telegram");
    }

    @Test
    void discordNormalizesMapPayload() {
        var adapter = new DiscordChannelAdapter();
        assertThat(adapter.channelId()).isEqualTo("discord");

        var msg = adapter.normalize(Map.of(
                "id", "msg-123",
                "author_id", "user-456",
                "author_username", "SpectorBot",
                "content", "Hello from Discord",
                "channel_id", "ch-789",
                "guild_id", "guild-1"
        ));
        assertThat(msg.channel()).isEqualTo("discord");
        assertThat(msg.senderId()).isEqualTo("user-456");
        assertThat(msg.senderName()).isEqualTo("SpectorBot");
        assertThat(msg.metadata()).containsEntry("guild_id", "guild-1");
    }

    @Test
    void slackNormalizesMapPayload() {
        var adapter = new SlackChannelAdapter();
        assertThat(adapter.channelId()).isEqualTo("slack");

        var msg = adapter.normalize(Map.of(
                "user", "U12345",
                "user_name", "bob",
                "text", "Hello from Slack",
                "channel", "C67890",
                "ts", "1625000000.000100",
                "team", "T111"
        ));
        assertThat(msg.channel()).isEqualTo("slack");
        assertThat(msg.senderId()).isEqualTo("U12345");
        assertThat(msg.content()).isEqualTo("Hello from Slack");
        assertThat(msg.metadata()).containsEntry("channel", "C67890");
        assertThat(msg.metadata()).containsEntry("team", "T111");
    }

    @Test
    void allAdaptersReportHealthy() {
        assertThat(new EmailChannelAdapter().health().status()).isEqualTo("ready");
        assertThat(new WhatsAppChannelAdapter().health().status()).isEqualTo("ready");
        assertThat(new TelegramChannelAdapter().health().status()).isEqualTo("ready");
        assertThat(new DiscordChannelAdapter().health().status()).isEqualTo("ready");
        assertThat(new SlackChannelAdapter().health().status()).isEqualTo("ready");
    }
}
