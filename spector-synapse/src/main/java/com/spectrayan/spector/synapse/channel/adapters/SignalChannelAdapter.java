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
 * Signal channel adapter — signal-cli or libsignal-java integration.
 */
@Component
@ConditionalOnProperty(prefix = "spector.channels.signal", name = "enabled", havingValue = "true")
public class SignalChannelAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(SignalChannelAdapter.class);

    @Override
    public String channelId() { return "signal"; }

    @Override
    public String displayName() { return "Signal Messenger"; }

    @Override
    public boolean isEnabled() { return true; }

    @Override
    @SuppressWarnings("unchecked")
    public UnifiedMessage normalize(Object rawPayload) {
        if (rawPayload instanceof Map<?,?> raw) {
            var map = (Map<String, Object>) raw;
            return new UnifiedMessage(
                    UUID.randomUUID().toString(),
                    "signal",
                    strOrDefault(map, "source", ""),
                    strOrDefault(map, "source_name", null),
                    strOrDefault(map, "message", ""),
                    Instant.now(),
                    strOrDefault(map, "group_id", null),
                    null, List.of(), Map.of()
            );
        }
        return UnifiedMessage.text("signal", "unknown", rawPayload.toString());
    }

    @Override
    public void send(UnifiedMessage message) throws ChannelException {
        log.info("[SignalAdapter] Sending to: {}", message.senderId());
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
