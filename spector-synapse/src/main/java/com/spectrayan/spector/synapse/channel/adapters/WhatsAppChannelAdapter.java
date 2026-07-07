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

import java.util.List;
import java.util.Map;

/**
 * WhatsApp channel adapter — sends and receives messages via WhatsApp Business API.
 *
 * <p>Enabled when {@code spector.channels.whatsapp.enabled=true} is set.
 * Requires a WhatsApp Business API token and phone number ID.</p>
 */
@Component
@ConditionalOnProperty(name = "spector.channels.whatsapp.enabled", havingValue = "true", matchIfMissing = false)
public class WhatsAppChannelAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppChannelAdapter.class);

    @Override
    public String channelId() { return "whatsapp"; }

    @Override
    public String displayName() { return "WhatsApp"; }

    @Override
    public boolean isEnabled() { return true; }

    @Override
    public UnifiedMessage normalize(Object nativeMessage) {
        if (nativeMessage instanceof Map<?, ?> waMsg) {
            // WhatsApp Cloud API webhook payload structure
            return new UnifiedMessage(
                    getOrDefault(waMsg, "id", java.util.UUID.randomUUID().toString()),
                    "whatsapp",
                    getOrDefault(waMsg, "from", "unknown"),
                    getOrDefault(waMsg, "profile_name", null),
                    getOrDefault(waMsg, "text", ""),
                    java.time.Instant.now(),
                    getOrDefault(waMsg, "context_id", null),
                    getOrDefault(waMsg, "reply_to", null),
                    parseMediaAttachments(waMsg),
                    Map.of(
                            "phone_number_id", getOrDefault(waMsg, "phone_number_id", ""),
                            "message_type", getOrDefault(waMsg, "type", "text")
                    )
            );
        }
        return UnifiedMessage.text("whatsapp", "unknown", nativeMessage.toString());
    }

    @Override
    public void send(UnifiedMessage message) {
        // TODO: Wire to WhatsApp Cloud API via HttpClient
        log.info("[WhatsApp] Sending to {} — {} chars", message.senderId(), message.content().length());
    }

    @Override
    public ChannelHealth health() {
        return new ChannelHealth("whatsapp", true, "ready");
    }

    private static String getOrDefault(Map<?, ?> map, String key, String defaultVal) {
        var val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    private static List<UnifiedMessage.Attachment> parseMediaAttachments(Map<?, ?> waMsg) {
        var type = getOrDefault(waMsg, "type", "text");
        return switch (type) {
            case "image" -> List.of(UnifiedMessage.Attachment.image(
                    getOrDefault(waMsg, "media_url", ""), "image/jpeg"));
            case "document" -> List.of(UnifiedMessage.Attachment.file(
                    getOrDefault(waMsg, "media_url", ""),
                    getOrDefault(waMsg, "mime_type", "application/octet-stream"),
                    getOrDefault(waMsg, "filename", "document"),
                    -1));
            default -> List.of();
        };
    }
}
