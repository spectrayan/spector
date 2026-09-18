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
