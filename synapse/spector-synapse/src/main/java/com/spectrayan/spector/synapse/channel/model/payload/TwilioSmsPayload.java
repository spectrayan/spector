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
 * Structured Twilio SMS webhook payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TwilioSmsPayload(
        @JsonProperty("MessageSid") @JsonAlias({"messageSid", "id", "sid", "message_id"}) String messageSid,
        @JsonProperty("From") @JsonAlias({"from", "sender", "source", "from_id"}) String from,
        @JsonProperty("To") @JsonAlias({"to", "recipient"}) String to,
        @JsonProperty("Body") @JsonAlias({"body", "text", "message", "content"}) String body,
        @JsonProperty("AccountSid") @JsonAlias({"accountSid"}) String accountSid
) {}
