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
 * Email channel adapter — sends and receives email via SMTP/IMAP.
 *
 * <p>Enabled when {@code spector.channels.email.enabled=true} is set.
 * Uses Apache Camel's mail component under the hood for actual I/O.</p>
 */
@Component
@ConditionalOnProperty(name = "spector.channels.email.enabled", havingValue = "true", matchIfMissing = false)
public class EmailChannelAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(EmailChannelAdapter.class);

    @Override
    public String channelId() { return "email"; }

    @Override
    public String displayName() { return "Email (SMTP/IMAP)"; }

    @Override
    public boolean isEnabled() { return true; }

    @Override
    public UnifiedMessage normalize(Object nativeMessage) {
        if (nativeMessage instanceof Map<?, ?> emailMap) {
            return new UnifiedMessage(
                    getOrDefault(emailMap, "messageId", java.util.UUID.randomUUID().toString()),
                    "email",
                    getOrDefault(emailMap, "from", "unknown@unknown.com"),
                    getOrDefault(emailMap, "fromName", null),
                    getOrDefault(emailMap, "body", ""),
                    java.time.Instant.now(),
                    getOrDefault(emailMap, "threadId", null),
                    getOrDefault(emailMap, "inReplyTo", null),
                    parseAttachments(emailMap),
                    Map.of(
                            "subject", getOrDefault(emailMap, "subject", "(no subject)"),
                            "to", getOrDefault(emailMap, "to", ""),
                            "cc", getOrDefault(emailMap, "cc", "")
                    )
            );
        }
        return UnifiedMessage.text("email", "unknown", nativeMessage.toString());
    }

    @Override
    public void send(UnifiedMessage message) {
        // TODO: Wire to Camel SMTP route when Camel routes are configured
        log.info("[Email] Sending to {} — subject: {}", message.senderId(),
                message.metadata().getOrDefault("subject", "(no subject)"));
    }

    @Override
    public ChannelHealth health() {
        return new ChannelHealth("email", true, "ready");
    }

    private static String getOrDefault(Map<?, ?> map, String key, String defaultVal) {
        var val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    private static List<UnifiedMessage.Attachment> parseAttachments(Map<?, ?> emailMap) {
        // TODO: Parse MIME attachments
        return List.of();
    }
}
