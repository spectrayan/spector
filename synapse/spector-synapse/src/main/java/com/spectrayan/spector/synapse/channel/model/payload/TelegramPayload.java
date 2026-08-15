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
 * Structured Telegram Bot API update payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramPayload(
        @JsonProperty("update_id") @JsonAlias({"updateId"}) Long updateId,
        @JsonProperty("message_id") @JsonAlias({"messageId", "id"}) String messageId,
        @JsonProperty("chat_id") @JsonAlias({"chatId", "from_id", "fromId", "chat"}) String chatId,
        @JsonProperty("first_name") @JsonAlias({"firstName", "from_name", "fromName", "name"}) String firstName,
        @JsonProperty("username") @JsonAlias({"userName"}) String username,
        @JsonProperty("text") @JsonAlias({"content", "message"}) String text,
        @JsonProperty("chat_type") @JsonAlias({"chatType"}) String chatType,
        @JsonProperty("reply_to_message_id") @JsonAlias({"replyToMessageId", "reply_to"}) String replyToMessageId,
        @JsonProperty("photo_url") @JsonAlias({"photoUrl"}) String photoUrl,
        @JsonProperty("document_url") @JsonAlias({"documentUrl"}) String documentUrl,
        @JsonProperty("mime_type") @JsonAlias({"mimeType"}) String mimeType,
        @JsonProperty("file_name") @JsonAlias({"fileName"}) String fileName
) {}
