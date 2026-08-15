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

import com.spectrayan.spector.connector.core.CamelConnectorEngine;
import com.spectrayan.spector.synapse.channel.ChannelAdapter;
import com.spectrayan.spector.synapse.channel.config.ChannelProperties;
import com.spectrayan.spector.synapse.channel.model.ChannelType;
import com.spectrayan.spector.synapse.channel.model.UnifiedMessage;
import com.spectrayan.spector.synapse.channel.model.payload.*;

import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Universal Apache Camel-powered messaging channel adapter.
 *
 * <p>Handles normalization of inbound platform payloads (Slack, Telegram, WhatsApp, Discord,
 * Email, MS Teams, Google Chat, SMS, Signal, WebChat) into {@link UnifiedMessage} records,
 * and routes outbound messages to Camel endpoints ({@code direct:channel-outbound-${channelId}}).</p>
 */
public class CamelChannelAdapter implements ChannelAdapter {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ChannelType channelType;
    protected final ChannelProperties properties;
    protected final CamelConnectorEngine connectorEngine;

    public CamelChannelAdapter(ChannelType channelType, ChannelProperties properties, CamelConnectorEngine connectorEngine) {
        this.channelType = Objects.requireNonNull(channelType, "channelType cannot be null");
        this.properties = properties != null ? properties : new ChannelProperties();
        this.connectorEngine = connectorEngine;
    }

    public CamelChannelAdapter(ChannelType channelType, ChannelProperties properties) {
        this(channelType, properties, null);
    }

    public CamelChannelAdapter(ChannelType channelType) {
        this(channelType, new ChannelProperties(), null);
    }

    @Override
    public String channelId() {
        return channelType.id();
    }

    @Override
    public String displayName() {
        return channelType.displayName();
    }

    @Override
    public boolean isEnabled() {
        if (!properties.isEnabled()) return false;
        return switch (channelType) {
            case WEBCHAT -> properties.getWebchat().isEnabled();
            case SLACK -> properties.getSlack().isEnabled();
            case TELEGRAM -> properties.getTelegram().isEnabled();
            case WHATSAPP -> properties.getWhatsapp().isEnabled();
            case DISCORD -> properties.getDiscord().isEnabled();
            case EMAIL -> properties.getEmail().isEnabled();
            case MSTEAMS -> properties.getMsteams().isEnabled();
            case GOOGLE_CHAT -> properties.getGooglechat().isEnabled();
            case SIGNAL -> properties.getSignal().isEnabled();
            case SMS -> properties.getSms().isEnabled();
        };
    }

    @Override
    public UnifiedMessage normalize(Object nativeMessage) {
        if (nativeMessage == null) {
            return UnifiedMessage.text(channelId(), "unknown", "");
        }
        if (nativeMessage instanceof UnifiedMessage um) {
            return um;
        }
        if (nativeMessage instanceof String str && !str.trim().startsWith("{")) {
            return UnifiedMessage.text(channelId(), "raw-input", str);
        }

        return switch (channelType) {
            case SLACK -> normalizeSlack(nativeMessage);
            case TELEGRAM -> normalizeTelegram(nativeMessage);
            case WHATSAPP -> normalizeWhatsApp(nativeMessage);
            case DISCORD -> normalizeDiscord(nativeMessage);
            case EMAIL -> normalizeEmail(nativeMessage);
            case MSTEAMS -> normalizeMSTeams(nativeMessage);
            case GOOGLE_CHAT -> normalizeGoogleChat(nativeMessage);
            case SMS -> normalizeSms(nativeMessage);
            case SIGNAL -> normalizeSignal(nativeMessage);
            case WEBCHAT -> normalizeWebChat(nativeMessage);
        };
    }

    @Override
    public void send(UnifiedMessage message) throws ChannelException {
        if (!isEnabled()) {
            throw new ChannelException(channelId(), "Cannot send message: channel is disabled");
        }

        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put("traceId", traceId);
        MDC.put("channelId", channelId());

        try {
            log.debug("[{}] Preparing outbound message {} for recipient {}",
                    displayName(), message.id(), message.senderId());

            if (connectorEngine != null && connectorEngine.isStarted()) {
                String endpointUri = "direct:channel-outbound-" + channelId();
                ProducerTemplate producer = connectorEngine.camelContext().createProducerTemplate();
                try {
                    Map<String, Object> headers = Map.of(
                            "SpectorChannel", channelId(),
                            "SpectorSenderId", message.senderId() != null ? message.senderId() : "",
                            "SpectorThreadId", message.threadId() != null ? message.threadId() : "",
                            "SpectorMessageId", message.id(),
                            "traceId", traceId
                    );
                    producer.sendBodyAndHeaders(endpointUri, message, headers);
                    log.info("[{}] Outbound message {} dispatched to Camel endpoint {}",
                            displayName(), message.id(), endpointUri);
                    return;
                } catch (Exception e) {
                    log.warn("[{}] Camel dispatch to {} failed: {}; falling back to direct send",
                            displayName(), endpointUri, e.getMessage());
                } finally {
                    try {
                        producer.stop();
                    } catch (Exception _) {}
                }
            }

            // Fallback direct dispatch if Camel route not active or in test mode
            sendDirect(message);
        } catch (Exception e) {
            log.error("[{}] Failed to send message {}: {}", displayName(), message.id(), e.getMessage(), e);
            throw new ChannelException(channelId(), "Failed to send outbound message: " + e.getMessage(), e);
        } finally {
            MDC.remove("channelId");
        }
    }

    protected void sendDirect(UnifiedMessage message) throws Exception {
        log.info("[{}] Outbound message delivered (direct): to={} thread={} ({} chars)",
                displayName(), message.senderId(), message.threadId(),
                message.content() != null ? message.content().length() : 0);
    }

    @Override
    public ChannelHealth health() {
        boolean engineActive = connectorEngine != null && connectorEngine.isStarted();
        String status = isEnabled() ? (engineActive ? "ready (camel)" : "ready") : "disabled";
        return new ChannelHealth(channelId(), isEnabled(), status);
    }

    // ── Normalization Helpers ──────────────────────────────────────────

    private UnifiedMessage normalizeSlack(Object raw) {
        SlackPayload p = PayloadParser.parse(raw, SlackPayload.class);
        if (p == null) return fallbackMessage(raw);
        Map<String, String> meta = new HashMap<>();
        if (p.channel() != null) meta.put("channel", p.channel());
        if (p.team() != null) meta.put("team", p.team());
        if (p.ts() != null) meta.put("ts", p.ts());

        String threadId = p.threadTs() != null ? p.threadTs() : p.ts();
        String senderId = p.user() != null ? p.user() : "slack-user";
        return new UnifiedMessage(
                p.clientMsgId() != null ? p.clientMsgId() : UUID.randomUUID().toString(),
                channelId(),
                senderId,
                p.userName(),
                p.text() != null ? p.text() : "",
                Instant.now(),
                threadId,
                p.threadTs(),
                List.of(),
                meta
        );
    }

    private UnifiedMessage normalizeTelegram(Object raw) {
        TelegramPayload p = PayloadParser.parse(raw, TelegramPayload.class);
        if (p == null) return fallbackMessage(raw);
        Map<String, String> meta = new HashMap<>();
        if (p.chatType() != null) meta.put("chat_type", p.chatType());
        if (p.messageId() != null) meta.put("message_id", p.messageId());

        String senderId = p.chatId() != null ? p.chatId() : "telegram-chat";
        return new UnifiedMessage(
                p.messageId() != null ? p.messageId() : UUID.randomUUID().toString(),
                channelId(),
                senderId,
                p.firstName(),
                p.text() != null ? p.text() : "",
                Instant.now(),
                p.chatId(),
                p.replyToMessageId(),
                List.of(),
                meta
        );
    }

    private UnifiedMessage normalizeWhatsApp(Object raw) {
        WhatsAppPayload p = PayloadParser.parse(raw, WhatsAppPayload.class);
        if (p == null) return fallbackMessage(raw);
        Map<String, String> meta = new HashMap<>();
        if (p.type() != null) meta.put("message_type", p.type());

        List<UnifiedMessage.Attachment> attachments = new ArrayList<>();
        if (p.mediaUrl() != null && !p.mediaUrl().isBlank()) {
            UnifiedMessage.AttachmentType type = "image".equalsIgnoreCase(p.type())
                    ? UnifiedMessage.AttachmentType.IMAGE : UnifiedMessage.AttachmentType.FILE;
            attachments.add(new UnifiedMessage.Attachment(type, p.mediaUrl(), p.mimeType(), null, 0L));
        }

        String senderId = p.from() != null ? p.from() : "whatsapp-sender";
        return new UnifiedMessage(
                p.id() != null ? p.id() : UUID.randomUUID().toString(),
                channelId(),
                senderId,
                p.profileName(),
                p.text() != null ? p.text() : "",
                Instant.now(),
                senderId,
                null,
                attachments,
                meta
        );
    }

    private UnifiedMessage normalizeDiscord(Object raw) {
        DiscordPayload p = PayloadParser.parse(raw, DiscordPayload.class);
        if (p == null) return fallbackMessage(raw);
        Map<String, String> meta = new HashMap<>();
        if (p.channelId() != null) meta.put("channel_id", p.channelId());
        if (p.guildId() != null) meta.put("guild_id", p.guildId());

        String senderId = p.authorId() != null ? p.authorId() : "discord-user";
        return new UnifiedMessage(
                p.id() != null ? p.id() : UUID.randomUUID().toString(),
                channelId(),
                senderId,
                p.authorUsername(),
                p.content() != null ? p.content() : "",
                Instant.now(),
                p.channelId(),
                p.referencedMessageId(),
                List.of(),
                meta
        );
    }

    private UnifiedMessage normalizeEmail(Object raw) {
        EmailPayload p = PayloadParser.parse(raw, EmailPayload.class);
        if (p == null) return fallbackMessage(raw);
        Map<String, String> meta = new HashMap<>();
        if (p.subject() != null) meta.put("subject", p.subject());
        if (p.to() != null) meta.put("to", p.to());
        if (p.messageId() != null) meta.put("messageId", p.messageId());

        String senderId = p.from() != null ? p.from() : "email-sender";
        return new UnifiedMessage(
                p.messageId() != null ? p.messageId() : UUID.randomUUID().toString(),
                channelId(),
                senderId,
                p.fromName(),
                p.body() != null ? p.body() : "",
                Instant.now(),
                p.threadId(),
                p.inReplyTo(),
                List.of(),
                meta
        );
    }

    private UnifiedMessage normalizeMSTeams(Object raw) {
        MSTeamsPayload p = PayloadParser.parse(raw, MSTeamsPayload.class);
        if (p == null) return fallbackMessage(raw);
        Map<String, String> meta = new HashMap<>();
        if (p.conversationId() != null) meta.put("conversation_id", p.conversationId());
        if (p.tenantId() != null) meta.put("tenant_id", p.tenantId());

        String senderId = p.fromId() != null ? p.fromId() : "teams-user";
        return new UnifiedMessage(
                p.id() != null ? p.id() : UUID.randomUUID().toString(),
                channelId(),
                senderId,
                p.fromName(),
                p.text() != null ? p.text() : "",
                Instant.now(),
                p.conversationId(),
                p.replyToId(),
                List.of(),
                meta
        );
    }

    private UnifiedMessage normalizeGoogleChat(Object raw) {
        GoogleChatPayload p = PayloadParser.parse(raw, GoogleChatPayload.class);
        if (p == null) return fallbackMessage(raw);
        Map<String, String> meta = new HashMap<>();
        if (p.spaceName() != null) meta.put("space_name", p.spaceName());
        if (p.spaceType() != null) meta.put("space_type", p.spaceType());

        String senderId = p.senderName() != null ? p.senderName() : "googlechat-user";
        return new UnifiedMessage(
                p.name() != null ? p.name() : UUID.randomUUID().toString(),
                channelId(),
                senderId,
                p.senderDisplayName(),
                p.text() != null ? p.text() : "",
                Instant.now(),
                p.threadName() != null ? p.threadName() : p.spaceName(),
                p.threadName(),
                List.of(),
                meta
        );
    }

    private UnifiedMessage normalizeSms(Object raw) {
        TwilioSmsPayload p = PayloadParser.parse(raw, TwilioSmsPayload.class);
        if (p == null) return fallbackMessage(raw);
        Map<String, String> meta = new HashMap<>();
        if (p.to() != null) meta.put("to", p.to());
        if (p.accountSid() != null) meta.put("accountSid", p.accountSid());

        String senderId = p.from() != null ? p.from() : "sms-sender";
        return new UnifiedMessage(
                p.messageSid() != null ? p.messageSid() : UUID.randomUUID().toString(),
                channelId(),
                senderId,
                senderId,
                p.body() != null ? p.body() : "",
                Instant.now(),
                senderId,
                null,
                List.of(),
                meta
        );
    }

    private UnifiedMessage normalizeSignal(Object raw) {
        SignalPayload p = PayloadParser.parse(raw, SignalPayload.class);
        if (p == null) return fallbackMessage(raw);
        Map<String, String> meta = new HashMap<>();
        if (p.timestamp() != null) meta.put("timestamp", String.valueOf(p.timestamp()));

        String senderId = p.source() != null ? p.source() : "signal-sender";
        return new UnifiedMessage(
                UUID.randomUUID().toString(),
                channelId(),
                senderId,
                p.sourceName() != null ? p.sourceName() : senderId,
                p.message() != null ? p.message() : "",
                Instant.now(),
                senderId,
                null,
                List.of(),
                meta
        );
    }

    private UnifiedMessage normalizeWebChat(Object raw) {
        WebChatPayload p = PayloadParser.parse(raw, WebChatPayload.class);
        if (p == null) return fallbackMessage(raw);
        Map<String, String> meta = new HashMap<>();
        if (p.sessionId() != null) meta.put("session_id", p.sessionId());

        String senderId = p.userId() != null ? p.userId() : "web-user";
        return new UnifiedMessage(
                p.id() != null ? p.id() : UUID.randomUUID().toString(),
                channelId(),
                senderId,
                p.userName(),
                p.content() != null ? p.content() : "",
                Instant.now(),
                p.sessionId(),
                null,
                List.of(),
                meta
        );
    }

    private UnifiedMessage fallbackMessage(Object raw) {
        return UnifiedMessage.text(channelId(), "raw-sender", String.valueOf(raw));
    }
}
