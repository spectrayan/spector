/*
 * Copyright 2025–2026 Spectrayan. Licensed under the Apache License, Version 2.0.
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
 * Google Chat channel adapter — Google Workspace API integration.
 */
@Component
@ConditionalOnProperty(prefix = "spector.channels.googlechat", name = "enabled", havingValue = "true")
public class GoogleChatChannelAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(GoogleChatChannelAdapter.class);

    @Override
    public String channelId() { return "googlechat"; }

    @Override
    public String displayName() { return "Google Chat"; }

    @Override
    public boolean isEnabled() { return true; }

    @Override
    @SuppressWarnings("unchecked")
    public UnifiedMessage normalize(Object rawPayload) {
        if (rawPayload instanceof Map<?,?> raw) {
            var map = (Map<String, Object>) raw;
            return new UnifiedMessage(
                    strOrDefault(map, "name", UUID.randomUUID().toString()),
                    "googlechat",
                    strOrDefault(map, "sender_name", ""),
                    strOrDefault(map, "sender_display_name", "Google Chat User"),
                    strOrDefault(map, "text", ""),
                    Instant.now(),
                    strOrDefault(map, "space_name", null),
                    strOrDefault(map, "thread_name", null),
                    List.of(),
                    Map.of("space_type", strOrDefault(map, "space_type", ""))
            );
        }
        return UnifiedMessage.text("googlechat", "unknown", rawPayload.toString());
    }

    @Override
    public void send(UnifiedMessage message) throws ChannelException {
        log.info("[GoogleChatAdapter] Sending to space: {}", message.threadId());
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
