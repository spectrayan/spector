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
 * Structured Email message payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailPayload(
        @JsonProperty("messageId") String messageId,
        @JsonProperty("from") String from,
        @JsonProperty("fromName") String fromName,
        @JsonProperty("to") String to,
        @JsonProperty("cc") String cc,
        @JsonProperty("subject") String subject,
        @JsonProperty("body") String body,
        @JsonProperty("threadId") String threadId,
        @JsonProperty("inReplyTo") String inReplyTo,
        @JsonProperty("attachments") List<EmailAttachment> attachments
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmailAttachment(
            @JsonProperty("name") String name,
            @JsonProperty("mimeType") String mimeType,
            @JsonProperty("url") String url,
            @JsonProperty("size") Long size
    ) {}
}
