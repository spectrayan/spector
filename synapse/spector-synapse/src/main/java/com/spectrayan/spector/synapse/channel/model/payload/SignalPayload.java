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
 * Structured Signal Messenger payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SignalPayload(
        @JsonProperty("source") @JsonAlias({"sender", "from", "author"}) String source,
        @JsonProperty("source_name") @JsonAlias({"sourceName", "sender_name", "name"}) String sourceName,
        @JsonProperty("message") @JsonAlias({"text", "content"}) String message,
        @JsonProperty("group_id") @JsonAlias({"groupId", "channel_id"}) String groupId,
        @JsonProperty("timestamp") @JsonAlias({"ts", "time"}) Long timestamp
) {}
