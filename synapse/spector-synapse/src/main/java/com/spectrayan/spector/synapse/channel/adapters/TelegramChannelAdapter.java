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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Telegram channel adapter — sends and receives messages via Telegram Bot API.
 *
 * <p>Enabled when {@code spector.channels.telegram.enabled=true} is set.
 * Requires a Telegram Bot token obtained from @BotFather.</p>
 */
@Component
@ConditionalOnProperty(name = "spector.channels.telegram.enabled", havingValue = "true", matchIfMissing = false)
public class TelegramChannelAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(TelegramChannelAdapter.class);

    @Override
    public String channelId() { return "telegram"; }

    @Override
    public String displayName() { return "Telegram"; }

    @Override
    public boolean isEnabled() { return true; }

    @Override
    public UnifiedMessage normalize(Object nativeMessage) {
        if (nativeMessage instanceof Map<?, ?> tgMsg) {
            // Telegram Bot API Update structure
            return new UnifiedMessage(
                    getOrDefault(tgMsg, "message_id", java.util.UUID.randomUUID().toString()),
                    "telegram",
                    getOrDefault(tgMsg, "chat_id", "unknown"),
                    getOrDefault(tgMsg, "first_name", null),
                    getOrDefault(tgMsg, "text", ""),
                    java.time.Instant.now(),
                    getOrDefault(tgMsg, "chat_id", null), // Telegram uses chat_id as thread
                    getOrDefault(tgMsg, "reply_to_message_id", null),
                    parseMediaAttachments(tgMsg),
                    Map.of(
                            "chat_type", getOrDefault(tgMsg, "chat_type", "private"),
                            "username", getOrDefault(tgMsg, "username", "")
                    )
            );
        }
        return UnifiedMessage.text("telegram", "unknown", nativeMessage.toString());
    }

    @Override
    public void send(UnifiedMessage message) {
        // TODO: Wire to Telegram Bot API via HttpClient (sendMessage endpoint)
        log.info("[Telegram] Sending to chat {} — {} chars", message.threadId(), message.content().length());
    }

    @Override
    public ChannelHealth health() {
        return new ChannelHealth("telegram", true, "ready");
    }

    private static String getOrDefault(Map<?, ?> map, String key, String defaultVal) {
        var val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    private static List<UnifiedMessage.Attachment> parseMediaAttachments(Map<?, ?> tgMsg) {
        if (tgMsg.containsKey("photo")) {
            return List.of(UnifiedMessage.Attachment.image(
                    getOrDefault(tgMsg, "photo_url", ""), "image/jpeg"));
        }
        if (tgMsg.containsKey("document")) {
            return List.of(UnifiedMessage.Attachment.file(
                    getOrDefault(tgMsg, "document_url", ""),
                    getOrDefault(tgMsg, "mime_type", "application/octet-stream"),
                    getOrDefault(tgMsg, "file_name", "document"),
                    -1));
        }
        return List.of();
    }
}
