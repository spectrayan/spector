/*
 * Copyright 2025–2026 Spectrayan. Licensed under the Apache License, Version 2.0.
 */
package com.spectrayan.spector.synapse.channel;

import com.spectrayan.spector.synapse.channel.model.UnifiedMessage;

/**
 * Service Provider Interface for messaging channel adapters.
 *
 * <p>Each channel (WhatsApp, Telegram, Discord, Slack, Email, etc.) implements
 * this interface to normalize its native message format into {@link UnifiedMessage}
 * and send responses back through the channel.</p>
 *
 * <p>Channel adapters are Spring {@code @Component} beans — they're auto-discovered
 * and registered in the {@link ChannelRouter} at startup.</p>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Inbound message → channel's webhook/API → {@link #normalize(Object)}</li>
 *   <li>Agent processes → produces response → {@link #send(UnifiedMessage)}</li>
 * </ol>
 */
public interface ChannelAdapter {

    /**
     * Returns the channel identifier (e.g., "whatsapp", "telegram", "slack").
     * Must be unique across all registered adapters.
     */
    String channelId();

    /**
     * Returns the display name of the channel (e.g., "WhatsApp", "Telegram").
     */
    String displayName();

    /**
     * Whether this channel is currently enabled and configured.
     * Disabled channels are skipped during routing.
     */
    boolean isEnabled();

    /**
     * Normalizes a channel-specific inbound message into a {@link UnifiedMessage}.
     *
     * @param nativeMessage the raw message object from the channel's API/webhook
     * @return normalized unified message
     * @throws ChannelException if the message cannot be normalized
     */
    UnifiedMessage normalize(Object nativeMessage) throws ChannelException;

    /**
     * Sends a response message back through this channel.
     *
     * @param message the unified message to send (will be converted to channel-native format)
     * @throws ChannelException if sending fails
     */
    void send(UnifiedMessage message) throws ChannelException;

    /**
     * Returns the channel's health status.
     */
    default ChannelHealth health() {
        return new ChannelHealth(channelId(), isEnabled(), "unknown");
    }

    /**
     * Channel health status record.
     */
    record ChannelHealth(String channelId, boolean enabled, String status) {}

    /**
     * Exception for channel-specific errors.
     */
    class ChannelException extends RuntimeException {
        private final String channelId;

        public ChannelException(String channelId, String message) {
            super("[" + channelId + "] " + message);
            this.channelId = channelId;
        }

        public ChannelException(String channelId, String message, Throwable cause) {
            super("[" + channelId + "] " + message, cause);
            this.channelId = channelId;
        }

        public String channelId() { return channelId; }
    }
}
