/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
