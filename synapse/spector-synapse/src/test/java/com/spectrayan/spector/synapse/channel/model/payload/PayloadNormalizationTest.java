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
package com.spectrayan.spector.synapse.channel.model.payload;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadNormalizationTest {

    @Test
    void parseSlackPayloadFromJsonAndMap() {
        String json = """
                {
                    "client_msg_id": "c-123",
                    "user": "U1234",
                    "user_name": "alice",
                    "text": "Hello Spector",
                    "channel": "C999",
                    "ts": "1700000000.123456"
                }
                """;
        SlackPayload payloadFromJson = PayloadParser.parse(json, SlackPayload.class);
        assertThat(payloadFromJson).isNotNull();
        assertThat(payloadFromJson.clientMsgId()).isEqualTo("c-123");
        assertThat(payloadFromJson.user()).isEqualTo("U1234");
        assertThat(payloadFromJson.text()).isEqualTo("Hello Spector");

        Map<String, Object> map = Map.of(
                "client_msg_id", "c-456",
                "user", "U5678",
                "text", "From Map"
        );
        SlackPayload payloadFromMap = PayloadParser.parse(map, SlackPayload.class);
        assertThat(payloadFromMap).isNotNull();
        assertThat(payloadFromMap.clientMsgId()).isEqualTo("c-456");
        assertThat(payloadFromMap.text()).isEqualTo("From Map");
    }

    @Test
    void parseTelegramPayloadWithAliases() {
        Map<String, Object> map = Map.of(
                "chat_id", "123456",
                "first_name", "Bob",
                "text", "Hello Bot"
        );
        TelegramPayload payload = PayloadParser.parse(map, TelegramPayload.class);
        assertThat(payload).isNotNull();
        assertThat(payload.chatId()).isEqualTo("123456");
        assertThat(payload.firstName()).isEqualTo("Bob");
        assertThat(payload.text()).isEqualTo("Hello Bot");
    }

    @Test
    void parseWhatsAppPayload() {
        Map<String, Object> map = Map.of(
                "id", "wa-msg-1",
                "from", "+14155550199",
                "profile_name", "Charlie",
                "text", "WhatsApp test"
        );
        WhatsAppPayload payload = PayloadParser.parse(map, WhatsAppPayload.class);
        assertThat(payload).isNotNull();
        assertThat(payload.id()).isEqualTo("wa-msg-1");
        assertThat(payload.from()).isEqualTo("+14155550199");
        assertThat(payload.profileName()).isEqualTo("Charlie");
    }

    @Test
    void parseDiscordPayload() {
        Map<String, Object> map = Map.of(
                "id", "disc-1",
                "author_id", "usr-1",
                "author_username", "discord_dev",
                "content", "Discord message"
        );
        DiscordPayload payload = PayloadParser.parse(map, DiscordPayload.class);
        assertThat(payload).isNotNull();
        assertThat(payload.id()).isEqualTo("disc-1");
        assertThat(payload.authorId()).isEqualTo("usr-1");
        assertThat(payload.content()).isEqualTo("Discord message");
    }

    @Test
    void parseEmailPayload() {
        Map<String, Object> map = Map.of(
                "messageId", "email-1",
                "from", "alice@example.com",
                "to", "spector@example.com",
                "subject", "Project Update",
                "body", "Everything is on track."
        );
        EmailPayload payload = PayloadParser.parse(map, EmailPayload.class);
        assertThat(payload).isNotNull();
        assertThat(payload.messageId()).isEqualTo("email-1");
        assertThat(payload.from()).isEqualTo("alice@example.com");
        assertThat(payload.subject()).isEqualTo("Project Update");
        assertThat(payload.body()).isEqualTo("Everything is on track.");
    }

    @Test
    void parseTwilioSmsPayload() {
        Map<String, Object> map = Map.of(
                "MessageSid", "SM12345",
                "From", "+1234567890",
                "To", "+0987654321",
                "Body", "Your verification code is 123456"
        );
        TwilioSmsPayload payload = PayloadParser.parse(map, TwilioSmsPayload.class);
        assertThat(payload).isNotNull();
        assertThat(payload.messageSid()).isEqualTo("SM12345");
        assertThat(payload.from()).isEqualTo("+1234567890");
        assertThat(payload.body()).isEqualTo("Your verification code is 123456");
    }

    @Test
    void parseNullAndUnknownFieldsGracefully() {
        assertThat(PayloadParser.parse(null, SlackPayload.class)).isNull();

        Map<String, Object> map = Map.of(
                "unexpected_field_1", "foo",
                "unexpected_field_2", 12345
        );
        SlackPayload payload = PayloadParser.parse(map, SlackPayload.class);
        assertThat(payload).isNotNull();
        assertThat(payload.text()).isNull();
    }
}
