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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Structured Slack Events API message payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SlackPayload(
        @JsonProperty("client_msg_id") String clientMsgId,
        @JsonProperty("user") String user,
        @JsonProperty("user_name") String userName,
        @JsonProperty("text") String text,
        @JsonProperty("ts") String ts,
        @JsonProperty("thread_ts") String threadTs,
        @JsonProperty("channel") String channel,
        @JsonProperty("team") String team,
        @JsonProperty("files") List<SlackFile> files
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SlackFile(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("mimetype") String mimetype,
            @JsonProperty("url_private") String urlPrivate,
            @JsonProperty("size") Long size
    ) {}
}
