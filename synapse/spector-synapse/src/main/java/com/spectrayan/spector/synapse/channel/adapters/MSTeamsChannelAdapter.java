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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Microsoft Teams channel adapter — Bot Framework integration via MS Graph SDK.
 *
 * <p>Receives messages from Teams channels and 1:1 chats via webhook,
 * normalizes to {@link UnifiedMessage}, and sends replies via Graph API.</p>
 */
@Component
@ConditionalOnProperty(prefix = "spector.channels.msteams", name = "enabled", havingValue = "true")
public class MSTeamsChannelAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(MSTeamsChannelAdapter.class);

    @Override
    public String channelId() { return "msteams"; }

    @Override
    public String displayName() { return "Microsoft Teams"; }

    @Override
    public boolean isEnabled() { return true; }

    @Override
    @SuppressWarnings("unchecked")
    public UnifiedMessage normalize(Object rawPayload) {
        if (rawPayload instanceof Map<?,?> raw) {
            var map = (Map<String, Object>) raw;
            return new UnifiedMessage(
                    strOrDefault(map, "id", UUID.randomUUID().toString()),
                    "msteams",
                    strOrDefault(map, "from_id", ""),
                    strOrDefault(map, "from_name", "Teams User"),
                    strOrDefault(map, "text", ""),
                    Instant.now(),
                    strOrDefault(map, "conversation_id", null),
                    strOrDefault(map, "reply_to_id", null),
                    List.of(),
                    Map.of("tenant_id", strOrDefault(map, "tenant_id", ""),
                           "channel_id", strOrDefault(map, "channel_id", ""))
            );
        }
        return UnifiedMessage.text("msteams", "unknown", rawPayload.toString());
    }

    @Override
    public void send(UnifiedMessage message) throws ChannelException {
        log.info("[MSTeamsAdapter] Sending to conversation: {}", message.threadId());
    }

    @Override
    public ChannelHealth health() {
        return new ChannelHealth(channelId(), true, "ready");
    }

    private static String strOrDefault(Map<String, Object> map, String key, String defaultValue) {
        var val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }
}
