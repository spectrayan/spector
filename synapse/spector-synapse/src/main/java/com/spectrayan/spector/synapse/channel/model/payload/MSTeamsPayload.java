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
package com.spectrayan.spector.synapse.channel.model.payload;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured Microsoft Teams Bot Framework payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MSTeamsPayload(
        @JsonProperty("id") @JsonAlias({"message_id", "messageId"}) String id,
        @JsonProperty("from_id") @JsonAlias({"fromId", "sender_id", "senderId", "from"}) String fromId,
        @JsonProperty("from_name") @JsonAlias({"fromName", "sender_name", "senderName"}) String fromName,
        @JsonProperty("text") @JsonAlias({"content", "message"}) String text,
        @JsonProperty("conversation_id") @JsonAlias({"conversationId", "channel_id", "channelId"}) String conversationId,
        @JsonProperty("reply_to_id") @JsonAlias({"replyToId", "reply_to"}) String replyToId,
        @JsonProperty("tenant_id") @JsonAlias({"tenantId"}) String tenantId,
        @JsonProperty("channel_id") @JsonAlias({"channelId"}) String channelId
) {}
