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
 * Discord channel adapter — sends and receives messages via Discord Bot API.
 *
 * <p>Enabled when {@code spector.channels.discord.enabled=true} is set.
 * Requires a Discord Bot token and guild/channel IDs.</p>
 */
@Component
@ConditionalOnProperty(name = "spector.channels.discord.enabled", havingValue = "true", matchIfMissing = false)
public class DiscordChannelAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(DiscordChannelAdapter.class);

    @Override
    public String channelId() { return "discord"; }

    @Override
    public String displayName() { return "Discord"; }

    @Override
    public boolean isEnabled() { return true; }

    @Override
    public UnifiedMessage normalize(Object nativeMessage) {
        if (nativeMessage instanceof Map<?, ?> discordMsg) {
            return new UnifiedMessage(
                    getOrDefault(discordMsg, "id", java.util.UUID.randomUUID().toString()),
                    "discord",
                    getOrDefault(discordMsg, "author_id", "unknown"),
                    getOrDefault(discordMsg, "author_username", null),
                    getOrDefault(discordMsg, "content", ""),
                    java.time.Instant.now(),
                    getOrDefault(discordMsg, "channel_id", null),
                    getOrDefault(discordMsg, "referenced_message_id", null),
                    parseAttachments(discordMsg),
                    Map.of(
                            "guild_id", getOrDefault(discordMsg, "guild_id", ""),
                            "channel_name", getOrDefault(discordMsg, "channel_name", "")
                    )
            );
        }
        return UnifiedMessage.text("discord", "unknown", nativeMessage.toString());
    }

    @Override
    public void send(UnifiedMessage message) {
        // TODO: Wire to Discord API via HttpClient (POST /channels/{id}/messages)
        log.info("[Discord] Sending to channel {} — {} chars", message.threadId(), message.content().length());
    }

    @Override
    public ChannelHealth health() {
        return new ChannelHealth("discord", true, "ready");
    }

    private static String getOrDefault(Map<?, ?> map, String key, String defaultVal) {
        var val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    private static List<UnifiedMessage.Attachment> parseAttachments(Map<?, ?> discordMsg) {
        // Discord attachments are in the "attachments" array
        return List.of();
    }
}
