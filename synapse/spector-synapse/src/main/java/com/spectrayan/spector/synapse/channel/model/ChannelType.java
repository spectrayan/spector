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
package com.spectrayan.spector.synapse.channel.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enumeration of all supported messaging and communication channels in Spector.
 */
public enum ChannelType {
    WEBCHAT("webchat", "WebChat (WebSocket/SSE)", true, false),
    SLACK("slack", "Slack", true, true),
    TELEGRAM("telegram", "Telegram", true, true),
    WHATSAPP("whatsapp", "WhatsApp", true, true),
    DISCORD("discord", "Discord", true, true),
    EMAIL("email", "Email (SMTP/IMAP)", true, false),
    MSTEAMS("msteams", "Microsoft Teams", true, true),
    GOOGLE_CHAT("googlechat", "Google Chat", true, true),
    SIGNAL("signal", "Signal Messenger", true, false),
    SMS("sms", "SMS (Twilio)", true, true);

    private final String id;
    private final String displayName;
    private final boolean bidirectional;
    private final boolean webhookCapable;

    ChannelType(String id, String displayName, boolean bidirectional, boolean webhookCapable) {
        this.id = id;
        this.displayName = displayName;
        this.bidirectional = bidirectional;
        this.webhookCapable = webhookCapable;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isBidirectional() {
        return bidirectional;
    }

    public boolean isWebhookCapable() {
        return webhookCapable;
    }

    /**
     * Look up a ChannelType by its string identifier (case-insensitive).
     */
    public static Optional<ChannelType> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String normalized = id.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(type -> type.id.equalsIgnoreCase(normalized) || type.name().equalsIgnoreCase(normalized))
                .findFirst();
    }
}
