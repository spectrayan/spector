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

import com.spectrayan.spector.synapse.channel.ChannelAdapter;
import com.spectrayan.spector.synapse.channel.model.UnifiedMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Contract tests for all 10 channel adapters — validates normalize/send
 * behavior, UnifiedMessage formatting, and error handling without calling
 * real APIs.
 *
 * @see <a href="https://github.com/spectrayan/spector-enterprise/issues/143">#143-#152</a>
 */
class ChannelAdapterContractTest {

    // ── Helper ──────────────────────────────────────────────────────────

    private static UnifiedMessage testMessage(String channel) {
        return new UnifiedMessage(
                "msg-001", channel, "user-1", "Test User",
                "Hello from Spector", Instant.now(), "thread-1",
                null, List.of(), Map.of()
        );
    }

    private static UnifiedMessage emptyMessage(String channel) {
        return new UnifiedMessage(
                "msg-002", channel, "user-2", null,
                "", Instant.now(), null,
                null, null, null
        );
    }

    // ── Slack (#143) ────────────────────────────────────────────────────

    @Nested @DisplayName("Slack Adapter")
    class SlackAdapterTest {
        private final SlackChannelAdapter adapter = new SlackChannelAdapter();

        @Test void channelId() { assertThat(adapter.channelId()).isEqualTo("slack"); }

        @Test void displayName() { assertThat(adapter.displayName()).isEqualTo("Slack"); }

        @Test void isEnabled() { assertThat(adapter.isEnabled()).isTrue(); }

        @Test void normalizeMapMessage() {
            var slackEvent = Map.of(
                    "text", "Hello world",
                    "user", "U12345",
                    "channel", "C99999",
                    "ts", "1625000000.000100"
            );
            var msg = adapter.normalize(slackEvent);
            assertThat(msg.content()).isEqualTo("Hello world");
            assertThat(msg.senderId()).isEqualTo("U12345");
            assertThat(msg.channel()).isEqualTo("slack");
            assertThat(msg.metadata()).containsKey("channel");
        }

        @Test void normalizeStringFallback() {
            var msg = adapter.normalize("plain text message");
            assertThat(msg.content()).isEqualTo("plain text message");
            assertThat(msg.channel()).isEqualTo("slack");
        }

        @Test void sendDoesNotThrow() {
            assertThatCode(() -> adapter.send(testMessage("slack")))
                    .doesNotThrowAnyException();
        }

        @Test void healthReturnsReady() {
            var health = adapter.health();
            assertThat(health.channelId()).isEqualTo("slack");
            assertThat(health.enabled()).isTrue();
        }
    }

    // ── Discord (#144) ──────────────────────────────────────────────────

    @Nested @DisplayName("Discord Adapter")
    class DiscordAdapterTest {
        private final DiscordChannelAdapter adapter = new DiscordChannelAdapter();

        @Test void channelId() { assertThat(adapter.channelId()).isEqualTo("discord"); }
        @Test void displayName() { assertThat(adapter.displayName()).isEqualTo("Discord"); }

        @Test void normalizeMapMessage() {
            var discordEvent = Map.of(
                    "content", "Hello Discord",
                    "author_id", "DISC12345",
                    "author_name", "TestUser",
                    "channel_id", "CH99999"
            );
            var msg = adapter.normalize(discordEvent);
            assertThat(msg.content()).isEqualTo("Hello Discord");
            assertThat(msg.senderId()).isEqualTo("DISC12345");
            assertThat(msg.channel()).isEqualTo("discord");
        }

        @Test void sendDoesNotThrow() {
            assertThatCode(() -> adapter.send(testMessage("discord")))
                    .doesNotThrowAnyException();
        }
    }

    // ── Telegram (#145) ─────────────────────────────────────────────────

    @Nested @DisplayName("Telegram Adapter")
    class TelegramAdapterTest {
        private final TelegramChannelAdapter adapter = new TelegramChannelAdapter();

        @Test void channelId() { assertThat(adapter.channelId()).isEqualTo("telegram"); }
        @Test void displayName() { assertThat(adapter.displayName()).isEqualTo("Telegram"); }

        @Test void normalizeMapMessage() {
            var tgUpdate = Map.of(
                    "text", "Hello Telegram",
                    "from_id", "TG12345",
                    "from_name", "TGUser",
                    "chat_id", "CHAT99"
            );
            var msg = adapter.normalize(tgUpdate);
            assertThat(msg.content()).isEqualTo("Hello Telegram");
            assertThat(msg.channel()).isEqualTo("telegram");
        }

        @Test void sendDoesNotThrow() {
            assertThatCode(() -> adapter.send(testMessage("telegram")))
                    .doesNotThrowAnyException();
        }
    }

    // ── WhatsApp (#146) ─────────────────────────────────────────────────

    @Nested @DisplayName("WhatsApp Adapter")
    class WhatsAppAdapterTest {
        private final WhatsAppChannelAdapter adapter = new WhatsAppChannelAdapter();

        @Test void channelId() { assertThat(adapter.channelId()).isEqualTo("whatsapp"); }
        @Test void displayName() { assertThat(adapter.displayName()).isEqualTo("WhatsApp"); }

        @Test void normalizeMapMessage() {
            var waMessage = Map.of(
                    "text", "Hello WhatsApp",
                    "from", "+1234567890",
                    "profile_name", "John"
            );
            var msg = adapter.normalize(waMessage);
            assertThat(msg.content()).isEqualTo("Hello WhatsApp");
            assertThat(msg.channel()).isEqualTo("whatsapp");
        }

        @Test void sendDoesNotThrow() {
            assertThatCode(() -> adapter.send(testMessage("whatsapp")))
                    .doesNotThrowAnyException();
        }
    }

    // ── Email (#147) ────────────────────────────────────────────────────

    @Nested @DisplayName("Email Adapter")
    class EmailAdapterTest {
        private final EmailChannelAdapter adapter = new EmailChannelAdapter();

        @Test void channelId() { assertThat(adapter.channelId()).isEqualTo("email"); }
        @Test void displayName() { assertThat(adapter.displayName()).isEqualTo("Email (SMTP/IMAP)"); }

        @Test void normalizeMapMessage() {
            var emailData = Map.of(
                    "subject", "Test Subject",
                    "body", "Hello Email",
                    "from", "test@example.com",
                    "messageId", "msg-12345"
            );
            var msg = adapter.normalize(emailData);
            assertThat(msg.content()).contains("Hello Email");
            assertThat(msg.channel()).isEqualTo("email");
        }

        @Test void sendDoesNotThrow() {
            assertThatCode(() -> adapter.send(testMessage("email")))
                    .doesNotThrowAnyException();
        }
    }

    // ── MS Teams (#148) ─────────────────────────────────────────────────

    @Nested @DisplayName("MS Teams Adapter")
    class MSTeamsAdapterTest {
        private final MSTeamsChannelAdapter adapter = new MSTeamsChannelAdapter();

        @Test void channelId() { assertThat(adapter.channelId()).isEqualTo("msteams"); }
        @Test void displayName() { assertThat(adapter.displayName()).isEqualTo("Microsoft Teams"); }

        @Test void normalizeMapMessage() {
            var teamsMessage = Map.of(
                    "text", "Hello Teams",
                    "from_id", "TEAMS12345",
                    "from_name", "TeamsUser"
            );
            var msg = adapter.normalize(teamsMessage);
            assertThat(msg.content()).isEqualTo("Hello Teams");
            assertThat(msg.channel()).isEqualTo("msteams");
        }

        @Test void sendDoesNotThrow() {
            assertThatCode(() -> adapter.send(testMessage("msteams")))
                    .doesNotThrowAnyException();
        }
    }

    // ── SMS (#149) ──────────────────────────────────────────────────────

    @Nested @DisplayName("SMS Adapter")
    class SmsAdapterTest {
        private final SmsChannelAdapter adapter = new SmsChannelAdapter();

        @Test void channelId() { assertThat(adapter.channelId()).isEqualTo("sms"); }
        @Test void displayName() { assertThat(adapter.displayName()).isEqualTo("SMS (Twilio)"); }

        @Test void normalizeMapMessage() {
            var smsData = Map.of(
                    "Body", "Hello SMS",
                    "from", "+1234567890"
            );
            var msg = adapter.normalize(smsData);
            assertThat(msg.content()).isEqualTo("Hello SMS");
            assertThat(msg.channel()).isEqualTo("sms");
        }

        @Test void sendDoesNotThrow() {
            assertThatCode(() -> adapter.send(testMessage("sms")))
                    .doesNotThrowAnyException();
        }
    }

    // ── Signal (#150) ───────────────────────────────────────────────────

    @Nested @DisplayName("Signal Adapter")
    class SignalAdapterTest {
        private final SignalChannelAdapter adapter = new SignalChannelAdapter();

        @Test void channelId() { assertThat(adapter.channelId()).isEqualTo("signal"); }
        @Test void displayName() { assertThat(adapter.displayName()).isEqualTo("Signal Messenger"); }

        @Test void normalizeMapMessage() {
            var signalData = Map.of(
                    "message", "Hello Signal",
                    "source", "+9876543210"
            );
            var msg = adapter.normalize(signalData);
            assertThat(msg.content()).isEqualTo("Hello Signal");
            assertThat(msg.channel()).isEqualTo("signal");
        }

        @Test void sendDoesNotThrow() {
            assertThatCode(() -> adapter.send(testMessage("signal")))
                    .doesNotThrowAnyException();
        }
    }

    // ── Google Chat (#151) ──────────────────────────────────────────────

    @Nested @DisplayName("Google Chat Adapter")
    class GoogleChatAdapterTest {
        private final GoogleChatChannelAdapter adapter = new GoogleChatChannelAdapter();

        @Test void channelId() { assertThat(adapter.channelId()).isEqualTo("googlechat"); }
        @Test void displayName() { assertThat(adapter.displayName()).isEqualTo("Google Chat"); }

        @Test void normalizeMapMessage() {
            var chatData = Map.of(
                    "text", "Hello Google Chat",
                    "sender_name", "users/12345",
                    "sender_displayName", "GCUser"
            );
            var msg = adapter.normalize(chatData);
            assertThat(msg.content()).isEqualTo("Hello Google Chat");
            assertThat(msg.channel()).isEqualTo("googlechat");
        }

        @Test void sendDoesNotThrow() {
            assertThatCode(() -> adapter.send(testMessage("googlechat")))
                    .doesNotThrowAnyException();
        }
    }

    // ── WebChat (#152) ──────────────────────────────────────────────────

    @Nested @DisplayName("WebChat Adapter")
    class WebChatAdapterTest {
        private final WebChatChannelAdapter adapter = new WebChatChannelAdapter();

        @Test void channelId() { assertThat(adapter.channelId()).isEqualTo("webchat"); }
        @Test void displayName() { assertThat(adapter.displayName()).isEqualTo("WebChat (WebSocket/SSE)"); }

        @Test void normalizeMapMessage() {
            var webData = Map.of(
                    "content", "Hello Web",
                    "session_id", "sess-001",
                    "user_id", "web-user"
            );
            var msg = adapter.normalize(webData);
            assertThat(msg.content()).isEqualTo("Hello Web");
            assertThat(msg.channel()).isEqualTo("webchat");
        }

        @Test void sendDoesNotThrow() {
            assertThatCode(() -> adapter.send(testMessage("webchat")))
                    .doesNotThrowAnyException();
        }

        @Test void emptyMessageHandledGracefully() {
            assertThatCode(() -> adapter.send(emptyMessage("webchat")))
                    .doesNotThrowAnyException();
        }
    }

    // ── Cross-Adapter Contract ──────────────────────────────────────────

    @Nested @DisplayName("Cross-Adapter Contract")
    class CrossAdapterContractTest {
        private final List<ChannelAdapter> allAdapters = List.of(
                new SlackChannelAdapter(),
                new DiscordChannelAdapter(),
                new TelegramChannelAdapter(),
                new WhatsAppChannelAdapter(),
                new EmailChannelAdapter(),
                new MSTeamsChannelAdapter(),
                new SmsChannelAdapter(),
                new SignalChannelAdapter(),
                new GoogleChatChannelAdapter(),
                new WebChatChannelAdapter()
        );

        @Test void allAdaptersHaveUniqueChannelIds() {
            var ids = allAdapters.stream().map(ChannelAdapter::channelId).toList();
            assertThat(ids).doesNotHaveDuplicates();
        }

        @Test void allAdaptersHaveNonBlankDisplayNames() {
            allAdapters.forEach(a ->
                    assertThat(a.displayName())
                            .as("displayName for %s", a.channelId())
                            .isNotBlank());
        }

        @Test void allAdaptersNormalizeStringFallback() {
            allAdapters.forEach(a -> {
                var msg = a.normalize("plain text fallback");
                assertThat(msg).as("normalize string for %s", a.channelId()).isNotNull();
                assertThat(msg.content()).as("content for %s", a.channelId()).isNotBlank();
            });
        }

        @Test void allAdaptersSendWithoutException() {
            allAdapters.forEach(a ->
                    assertThatCode(() -> a.send(testMessage(a.channelId())))
                            .as("send for %s", a.channelId())
                            .doesNotThrowAnyException());
        }

        @Test void allAdaptersReturnValidHealth() {
            allAdapters.forEach(a -> {
                var health = a.health();
                assertThat(health).as("health for %s", a.channelId()).isNotNull();
                assertThat(health.channelId()).as("health.channelId for %s", a.channelId())
                        .isEqualTo(a.channelId());
            });
        }
    }
}
