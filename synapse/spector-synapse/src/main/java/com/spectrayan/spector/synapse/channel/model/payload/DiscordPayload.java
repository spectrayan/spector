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
 * Structured Discord Bot/Webhook message payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiscordPayload(
        @JsonProperty("id") @JsonAlias({"message_id", "messageId"}) String id,
        @JsonProperty("author_id") @JsonAlias({"authorId", "from_id", "fromId", "user_id", "userId"}) String authorId,
        @JsonProperty("author_username") @JsonAlias({"author_name", "authorName", "username", "userName", "authorUsername"}) String authorUsername,
        @JsonProperty("content") @JsonAlias({"text", "message"}) String content,
        @JsonProperty("channel_id") @JsonAlias({"channelId", "channel"}) String channelId,
        @JsonProperty("guild_id") @JsonAlias({"guildId", "guild"}) String guildId,
        @JsonProperty("channel_name") @JsonAlias({"channelName"}) String channelName,
        @JsonProperty("referenced_message_id") @JsonAlias({"referencedMessageId", "reply_to"}) String referencedMessageId
) {}
