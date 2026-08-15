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
 * Structured Google Chat API payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleChatPayload(
        @JsonProperty("name") @JsonAlias({"id", "message_id"}) String name,
        @JsonProperty("sender_name") @JsonAlias({"senderName", "from_id", "from"}) String senderName,
        @JsonProperty("sender_display_name") @JsonAlias({"sender_displayName", "senderDisplayName", "displayName", "from_name"}) String senderDisplayName,
        @JsonProperty("text") @JsonAlias({"content", "message"}) String text,
        @JsonProperty("space_name") @JsonAlias({"spaceName", "space", "chat_id"}) String spaceName,
        @JsonProperty("space_type") @JsonAlias({"spaceType"}) String spaceType,
        @JsonProperty("thread_name") @JsonAlias({"threadName", "thread_id"}) String threadName
) {}
