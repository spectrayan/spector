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

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Unified message model — normalizes messages from all channels into a single format.
 *
 * <p>Every channel adapter converts its native message format into this record
 * before routing to the agent orchestration layer. This ensures agent tools
 * and FlowSpecs work identically regardless of the inbound channel.</p>
 *
 * @param id         unique message ID (channel-specific or generated UUID)
 * @param channel    source channel identifier (e.g., "whatsapp", "telegram", "email", "slack")
 * @param senderId   sender identifier (phone number, user ID, email address)
 * @param senderName display name of the sender (may be null)
 * @param content    message text content
 * @param timestamp  when the message was sent/received
 * @param threadId   conversation/thread identifier (for multi-turn context)
 * @param replyToId  ID of the message being replied to (null if not a reply)
 * @param attachments list of attachments (images, files, audio, etc.)
 * @param metadata   channel-specific metadata that doesn't map to standard fields
 */
public record UnifiedMessage(
        String id,
        String channel,
        String senderId,
        String senderName,
        String content,
        Instant timestamp,
        String threadId,
        String replyToId,
        List<Attachment> attachments,
        Map<String, String> metadata
) {
    /** Compact constructor — ensures non-null collections. */
    public UnifiedMessage {
        if (attachments == null) attachments = List.of();
        if (metadata == null) metadata = Map.of();
        if (timestamp == null) timestamp = Instant.now();
    }

    /** Quick text-only message factory. */
    public static UnifiedMessage text(String channel, String senderId, String content) {
        return new UnifiedMessage(
                java.util.UUID.randomUUID().toString(),
                channel, senderId, null, content,
                Instant.now(), null, null, List.of(), Map.of()
        );
    }

    /** Quick reply factory. */
    public static UnifiedMessage reply(UnifiedMessage original, String content) {
        return new UnifiedMessage(
                java.util.UUID.randomUUID().toString(),
                original.channel(), original.senderId(), original.senderName(),
                content, Instant.now(), original.threadId(),
                original.id(), List.of(), Map.of()
        );
    }

    /**
     * File, image, audio, or video attachment.
     *
     * @param type     attachment type (image, file, audio, video, location, contact)
     * @param url      URL or file path to the attachment
     * @param mimeType MIME type (e.g., "image/jpeg", "application/pdf")
     * @param fileName original file name
     * @param sizeBytes file size in bytes (-1 if unknown)
     */
    public record Attachment(
            AttachmentType type,
            String url,
            String mimeType,
            String fileName,
            long sizeBytes
    ) {
        public static Attachment image(String url, String mimeType) {
            return new Attachment(AttachmentType.IMAGE, url, mimeType, null, -1);
        }

        public static Attachment file(String url, String mimeType, String fileName, long size) {
            return new Attachment(AttachmentType.FILE, url, mimeType, fileName, size);
        }
    }

    /** Attachment type enumeration. */
    public enum AttachmentType {
        IMAGE, FILE, AUDIO, VIDEO, LOCATION, CONTACT, STICKER
    }
}
