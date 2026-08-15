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

/**
 * Structured WhatsApp Business Cloud API webhook payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppPayload(
        @JsonProperty("id") String id,
        @JsonProperty("from") String from,
        @JsonProperty("profile_name") String profileName,
        @JsonProperty("text") String text,
        @JsonProperty("phone_number_id") String phoneNumberId,
        @JsonProperty("type") String type,
        @JsonProperty("media_url") String mediaUrl,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("filename") String filename,
        @JsonProperty("context_id") String contextId,
        @JsonProperty("reply_to") String replyTo
) {}
