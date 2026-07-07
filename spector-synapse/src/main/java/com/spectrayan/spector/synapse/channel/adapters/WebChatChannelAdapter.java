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
 * WebChat channel adapter — embeddable web-based chat widget using WebSocket/SSE.
 *
 * <p>Normalizes WebSocket/SSE messages into {@link UnifiedMessage} format.
 * Supports rich messages (Markdown, code blocks, images), file uploads,
 * typing indicators, and session-based conversations.</p>
 */
@Component
@ConditionalOnProperty(prefix = "spector.channels.webchat", name = "enabled", havingValue = "true")
public class WebChatChannelAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(WebChatChannelAdapter.class);

    @Override
    public String channelId() { return "webchat"; }

    @Override
    public String displayName() { return "WebChat (WebSocket/SSE)"; }

    @Override
    public boolean isEnabled() { return true; }

    @Override
    @SuppressWarnings("unchecked")
    public UnifiedMessage normalize(Object rawPayload) {
        if (rawPayload instanceof Map<?,?> raw) {
            var map = (Map<String, Object>) raw;

            var messageId = strOrDefault(map, "id", UUID.randomUUID().toString());
            var sessionId = strOrDefault(map, "session_id", "");
            var userId = strOrDefault(map, "user_id", "anonymous");
            var userName = strOrDefault(map, "user_name", "Web User");
            var content = strOrDefault(map, "content", "");

            // Parse file attachments
            List<UnifiedMessage.Attachment> attachments = List.of();
            if (map.containsKey("file_url")) {
                var fileUrl = map.get("file_url").toString();
                var fileName = strOrDefault(map, "file_name", "upload");
                var mimeType = strOrDefault(map, "mime_type", "application/octet-stream");
                attachments = List.of(
                        new UnifiedMessage.Attachment(UnifiedMessage.AttachmentType.FILE,
                                fileUrl, mimeType, fileName, 0)
                );
            }

            var metadata = new java.util.HashMap<String, String>();
            metadata.put("session_id", sessionId);
            if (map.containsKey("client_info")) metadata.put("client_info", map.get("client_info").toString());
            if (map.containsKey("page_url")) metadata.put("page_url", map.get("page_url").toString());

            return new UnifiedMessage(
                    messageId, "webchat", userId, userName, content,
                    Instant.now(), sessionId,
                    strOrDefault(map, "reply_to", null),
                    attachments, Map.copyOf(metadata)
            );
        }

        // String payload — simple text message
        return new UnifiedMessage(
                UUID.randomUUID().toString(), "webchat",
                "anonymous", "Web User", rawPayload.toString(),
                Instant.now(), null, null, List.of(), Map.of()
        );
    }

    @Override
    public void send(UnifiedMessage message) throws ChannelException {
        // TODO: Wire to WebSocket/SSE broadcast endpoint
        log.info("[WebChatAdapter] Sending message to session: {}", message.threadId());
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
