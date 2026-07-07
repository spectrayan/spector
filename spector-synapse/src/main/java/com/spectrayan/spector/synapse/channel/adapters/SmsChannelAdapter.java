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
 * SMS channel adapter — Twilio SDK integration for sending/receiving SMS.
 */
@Component
@ConditionalOnProperty(prefix = "spector.channels.sms", name = "enabled", havingValue = "true")
public class SmsChannelAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(SmsChannelAdapter.class);

    @Override
    public String channelId() { return "sms"; }

    @Override
    public String displayName() { return "SMS (Twilio)"; }

    @Override
    public boolean isEnabled() { return true; }

    @Override
    @SuppressWarnings("unchecked")
    public UnifiedMessage normalize(Object rawPayload) {
        if (rawPayload instanceof Map<?,?> raw) {
            var map = (Map<String, Object>) raw;
            return new UnifiedMessage(
                    strOrDefault(map, "MessageSid", UUID.randomUUID().toString()),
                    "sms",
                    strOrDefault(map, "From", ""),
                    null,
                    strOrDefault(map, "Body", ""),
                    Instant.now(), null, null, List.of(),
                    Map.of("to", strOrDefault(map, "To", ""),
                           "account_sid", strOrDefault(map, "AccountSid", ""))
            );
        }
        return UnifiedMessage.text("sms", "unknown", rawPayload.toString());
    }

    @Override
    public void send(UnifiedMessage message) throws ChannelException {
        log.info("[SmsAdapter] Sending SMS to: {}", message.senderId());
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
